/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.servicetalk.http.router.jersey;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.DefaultExecutionContext;
import io.servicetalk.transport.api.ExecutionContext;

import org.glassfish.jersey.internal.util.collection.Ref;
import org.glassfish.jersey.message.internal.OutboundJaxrsResponse;
import org.glassfish.jersey.process.internal.RequestContext;
import org.glassfish.jersey.process.internal.RequestScope;
import org.glassfish.jersey.server.AsyncContext;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.internal.process.Endpoint;
import org.glassfish.jersey.server.internal.process.RequestProcessingContext;
import org.glassfish.jersey.server.internal.routing.UriRoutingContext;

import java.lang.reflect.Method;
import java.net.SocketAddress;
import java.util.concurrent.CompletionStage;
import javax.annotation.Nullable;
import javax.annotation.Priority;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.net.ssl.SSLSession;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;

import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.Single.error;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.http.router.jersey.Context.getRequestChunkPublisherInputStream;
import static io.servicetalk.http.router.jersey.ExecutionStrategyUtils.getResourceExecutor;
import static java.lang.Integer.MAX_VALUE;
import static javax.ws.rs.core.Response.noContent;

/**
 * A {@link ContainerRequestFilter} that allows using {@link Single Single&lt;Response|MyPojo&gt;}
 * or {@link Completable} in lieu of {@link CompletionStage CompletionStage&lt;Response|MyPojo&gt;}
 * or {@link CompletionStage CompletionStage&lt;Void&gt;} respectively.
 * <p>
 * This class decorates the target {@link Endpoint} if its return type is a {@link Single} or {@link Completable}.
 * The {@link Endpoint} decorator wires the {@link Single} events to Jersey's {@link AsyncContext} events.
 */
// We must run after all other filters have kicked in, so MAX_VALUE for the lowest priority. The reason for this is
// that if a filter runs after this one and calls abort(), no response filter will be executed since Jersey relies
// on Endpoint instances of type ResourceMethodInvoker to get the response filters.
@Priority(MAX_VALUE)
final class EndpointEnhancingRequestFilter implements ContainerRequestFilter {
    @Inject
    private Provider<Ref<ConnectionContext>> ctxRefProvider;

    @Inject
    private Provider<ExecutorConfig> executorConfigProvider;

    @Context
    private RequestScope requestScope;

    @Override
    public void filter(final ContainerRequestContext requestCtx) {
        final UriRoutingContext urc = (UriRoutingContext) requestCtx.getUriInfo();
        final Class<?> resourceClass = urc.getResourceClass();
        final Method resourceMethod = urc.getResourceMethod();
        if (resourceClass == null || resourceMethod == null) {
            return;
        }

        final ExecutorOverrideConnectionContext executorOverrideConnectionCtx =
                getExecutorOverrideConnectionCtx(resourceClass, resourceMethod);

        final Class<?> returnType = resourceMethod.getReturnType();
        if (Single.class.isAssignableFrom(returnType)) {
            urc.setEndpoint(new SingleAwareEndpoint(urc, requestScope, executorOverrideConnectionCtx));
        } else if (Completable.class.isAssignableFrom(returnType)) {
            urc.setEndpoint(new CompletableAwareEndpoint(urc, requestScope, executorOverrideConnectionCtx));
        } else if (executorOverrideConnectionCtx != null) {
            urc.setEndpoint(new ExecutorOffloadingEndpoint(urc, requestScope, executorOverrideConnectionCtx));
        }
    }

    @Nullable
    private ExecutorOverrideConnectionContext getExecutorOverrideConnectionCtx(final Class<?> resourceClass,
                                                                               final Method resourceMethod) {
        final ExecutorConfig executorConfig = executorConfigProvider.get();
        if (executorConfig == null) {
            return null;
        }

        final Ref<ConnectionContext> ctxRef = ctxRefProvider.get();
        final Executor currentExecutor = ctxRef.get().getExecutionContext().getExecutor();
        final Executor resourceExecutor =
                getResourceExecutor(resourceClass, resourceMethod, currentExecutor, executorConfig);

        return resourceExecutor == currentExecutor ? null :
                new ExecutorOverrideConnectionContext(ctxRef, resourceExecutor);
    }

    private abstract static class AbstractWrappedEndpoint implements Endpoint, ResourceInfo {
        private final UriRoutingContext uriRoutingContext;
        private final Endpoint originalEndpoint;
        private final RequestScope requestScope;
        @Nullable
        private final ExecutorOverrideConnectionContext execOverrideCnxCtx;

        protected AbstractWrappedEndpoint(final UriRoutingContext uriRoutingContext,
                                          final RequestScope requestScope,
                                          @Nullable final ExecutorOverrideConnectionContext execOverrideCnxCtx) {
            this.uriRoutingContext = uriRoutingContext;
            this.originalEndpoint = uriRoutingContext.getEndpoint();
            this.requestScope = requestScope;
            this.execOverrideCnxCtx = execOverrideCnxCtx;
        }

        @Nullable
        @Override
        public Class<?> getResourceClass() {
            return originalEndpoint instanceof ResourceInfo ?
                    ((ResourceInfo) originalEndpoint).getResourceClass() : null;
        }

        @Nullable
        @Override
        public Method getResourceMethod() {
            return originalEndpoint instanceof ResourceInfo ?
                    ((ResourceInfo) originalEndpoint).getResourceMethod() : null;
        }

        @Nullable
        @Override
        public ContainerResponse apply(final RequestProcessingContext requestProcessingCtx) {
            final AsyncContext asyncContext = requestProcessingCtx.asyncContext();
            if (asyncContext.isSuspended()) {
                throw new IllegalStateException("JAX-RS suspended responses can't be used with " +
                        getClass().getSimpleName());
            }
            if (!asyncContext.suspend()) {
                throw new IllegalStateException("Failed to suspend request processing");
            }

            callOriginalEndpoint(requestProcessingCtx)
                    .flatMap(this::handleContainerResponse)
                    .doBeforeFinally(() -> uriRoutingContext.setEndpoint(originalEndpoint))
                    .doAfterError(asyncContext::resume)
                    .doAfterCancel(asyncContext::cancel)
                    .subscribe(asyncContext::resume);

            // Return null on current thread since response will be delivered asynchronously
            return null;
        }

        private Single<ContainerResponse> callOriginalEndpoint(final RequestProcessingContext requestProcessingCtx) {
            if (execOverrideCnxCtx != null) {
                final RequestContext requestContext = requestScope.referenceCurrent();

                final Executor executor = execOverrideCnxCtx.getExecutionContext().getExecutor();

                return executor.submit(() -> {
                    execOverrideCnxCtx.activate();
                    return requestScope.runInScope(requestContext,
                            () -> {
                                getRequestChunkPublisherInputStream(requestProcessingCtx.request())
                                        .offloadSourcePublisher(executor);

                                return originalEndpoint.apply(requestProcessingCtx);
                            });
                });
            }

            return defer(() -> {
                try {
                    return success(originalEndpoint.apply(requestProcessingCtx));
                } catch (final Throwable t) {
                    return error(t);
                }
            });
        }

        @SuppressWarnings("unchecked")
        protected Single<Object> handleContainerResponse(final ContainerResponse res) {
            return success(new OutboundJaxrsResponse(res.getStatusInfo(), res.getWrappedMessageContext()));
        }
    }

    private static final class ExecutorOffloadingEndpoint extends AbstractWrappedEndpoint {

        private ExecutorOffloadingEndpoint(final UriRoutingContext uriRoutingContext,
                                           final RequestScope requestScope,
                                           final ExecutorOverrideConnectionContext execOverrideCnxCtx) {
            super(uriRoutingContext, requestScope, execOverrideCnxCtx);
        }
    }

    private abstract static class AbstractSourceAwareEndpoint<T> extends AbstractWrappedEndpoint {
        private final Class<T> sourceType;

        protected AbstractSourceAwareEndpoint(final UriRoutingContext uriRoutingContext,
                                              final Class<T> sourceType,
                                              final RequestScope requestScope,
                                              @Nullable final ExecutorOverrideConnectionContext execOverrideCnxCtx) {
            super(uriRoutingContext, requestScope, execOverrideCnxCtx);
            this.sourceType = sourceType;
        }

        @Override
        protected final Single<Object> handleContainerResponse(final ContainerResponse res) {
            if (!res.hasEntity()) {
                return super.handleContainerResponse(res);
            }

            final Object responseEntity = res.getEntity();
            return sourceType.isAssignableFrom(responseEntity.getClass()) ?
                    handleSourceResponse(sourceType.cast(responseEntity)) : super.handleContainerResponse(res);
        }

        protected abstract Single<Object> handleSourceResponse(T source);
    }

    @SuppressWarnings("rawtypes")
    private static final class SingleAwareEndpoint extends AbstractSourceAwareEndpoint<Single> {
        private SingleAwareEndpoint(final UriRoutingContext uriRoutingContext,
                                    final RequestScope requestScope,
                                    @Nullable final ExecutorOverrideConnectionContext execOverrideCnxCtx) {
            super(uriRoutingContext, Single.class, requestScope, execOverrideCnxCtx);
        }

        @SuppressWarnings("unchecked")
        @Override
        protected Single<Object> handleSourceResponse(final Single source) {
            return source;
        }
    }

    private static final class CompletableAwareEndpoint extends AbstractSourceAwareEndpoint<Completable> {
        private CompletableAwareEndpoint(final UriRoutingContext uriRoutingContext,
                                         final RequestScope requestScope,
                                         @Nullable final ExecutorOverrideConnectionContext execOverrideCnxCtx) {
            super(uriRoutingContext, Completable.class, requestScope, execOverrideCnxCtx);
        }

        @Override
        protected Single<Object> handleSourceResponse(final Completable source) {
            return source.andThen(defer(() -> success(noContent().build())));
        }
    }

    private static final class ExecutorOverrideConnectionContext implements ConnectionContext {
        private final Ref<ConnectionContext> ctxRef;
        private final ConnectionContext original;
        private final ExecutionContext execCtx;

        private ExecutorOverrideConnectionContext(final Ref<ConnectionContext> ctxRef, final Executor executor) {
            this.ctxRef = ctxRef;
            this.original = ctxRef.get();
            this.execCtx = new DefaultExecutionContext(original.getExecutionContext().getBufferAllocator(),
                    original.getExecutionContext().getIoExecutor(), executor);
        }

        private void activate() {
            ctxRef.set(this);
        }

        @Override
        public SocketAddress getLocalAddress() {
            return original.getLocalAddress();
        }

        @Override
        public SocketAddress getRemoteAddress() {
            return original.getRemoteAddress();
        }

        @Nullable
        @Override
        public SSLSession getSslSession() {
            return original.getSslSession();
        }

        @Override
        public ExecutionContext getExecutionContext() {
            return execCtx;
        }

        @Override
        public Completable onClose() {
            return original.onClose();
        }

        @Override
        public Completable closeAsync() {
            return original.closeAsync();
        }

        @Override
        public Completable closeAsyncGracefully() {
            return original.closeAsyncGracefully();
        }
    }
}
