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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.DelegatingConnectionContext;
import io.servicetalk.transport.api.DelegatingExecutionContext;
import io.servicetalk.transport.api.ExecutionContext;

import org.glassfish.jersey.internal.util.collection.Ref;
import org.glassfish.jersey.message.internal.OutboundJaxrsResponse;
import org.glassfish.jersey.message.internal.OutboundMessageContext;
import org.glassfish.jersey.process.internal.RequestContext;
import org.glassfish.jersey.process.internal.RequestScope;
import org.glassfish.jersey.server.AsyncContext;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.internal.process.Endpoint;
import org.glassfish.jersey.server.internal.process.RequestProcessingContext;
import org.glassfish.jersey.server.internal.routing.UriRoutingContext;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.concurrent.CompletionStage;
import javax.annotation.Nullable;
import javax.annotation.Priority;
import javax.inject.Provider;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.router.jersey.RouteExecutionStrategyUtils.getRouteExecutionStrategy;
import static io.servicetalk.http.router.jersey.internal.RequestProperties.getRequestBufferPublisherInputStream;
import static io.servicetalk.http.router.jersey.internal.RequestProperties.setRequestCancellable;
import static io.servicetalk.http.router.jersey.internal.RequestProperties.setResponseExecutionStrategy;
import static java.lang.Integer.MAX_VALUE;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
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
    @Context
    private Provider<Ref<ConnectionContext>> ctxRefProvider;

    @Context
    private Provider<RouteStrategiesConfig> routeStrategiesConfigProvider;

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

        final HttpExecutionStrategy routeExecutionStrategy =
                getRouteExecutionStrategy(resourceClass, resourceMethod, routeStrategiesConfigProvider.get());

        final Class<?> returnType = resourceMethod.getReturnType();
        if (Single.class.isAssignableFrom(returnType)) {
            urc.setEndpoint(new SingleAwareEndpoint(urc, requestScope, ctxRefProvider, routeExecutionStrategy));
        } else if (Completable.class.isAssignableFrom(returnType)) {
            urc.setEndpoint(new CompletableAwareEndpoint(urc, requestScope, ctxRefProvider, routeExecutionStrategy));
        } else if (routeExecutionStrategy != null) {
            urc.setEndpoint(new ExecutorOffloadingEndpoint(urc, requestScope, ctxRefProvider, routeExecutionStrategy));
        }
    }

    private abstract static class AbstractWrappedEndpoint implements Endpoint, ResourceInfo {
        private final UriRoutingContext uriRoutingContext;
        private final Endpoint originalEndpoint;
        private final RequestScope requestScope;
        @Nullable
        private final HttpExecutionStrategy routeExecutionStrategy;
        @Nullable
        private final Ref<ConnectionContext> ctxRef;
        @Nullable
        private final ConnectionContext currentConnectionContext;

        protected AbstractWrappedEndpoint(final UriRoutingContext uriRoutingContext,
                                          final RequestScope requestScope,
                                          final Provider<Ref<ConnectionContext>> ctxRefProvider,
                                          @Nullable final HttpExecutionStrategy routeExecutionStrategy) {
            this.uriRoutingContext = uriRoutingContext;
            this.originalEndpoint = uriRoutingContext.getEndpoint();
            this.requestScope = requestScope;
            this.routeExecutionStrategy = routeExecutionStrategy;

            if (routeExecutionStrategy != null) {
                ctxRef = ctxRefProvider.get();
                currentConnectionContext = ctxRef.get();
            } else {
                ctxRef = null;
                currentConnectionContext = null;
            }
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

            final Single<Response> objectSingle = callOriginalEndpoint(requestProcessingCtx)
                    .flatMap(this::handleContainerResponse)
                    .doBeforeFinally(() -> uriRoutingContext.setEndpoint(originalEndpoint))
                    .doAfterOnError(asyncContext::resume)
                    .doAfterCancel(asyncContext::cancel);

            final Cancellable cancellable;
            if (routeExecutionStrategy != null) {
                assert currentConnectionContext != null : "currentConnectionContext can't be null";
                cancellable = routeExecutionStrategy
                        .offloadSend(currentConnectionContext.executionContext().executor(), objectSingle)
                        .subscribe(asyncContext::resume);
            } else {
                cancellable = objectSingle.subscribe(asyncContext::resume);
            }
            setRequestCancellable(cancellable, requestProcessingCtx.request());

            // Return null on current thread since response will be delivered asynchronously
            return null;
        }

        private Single<ContainerResponse> callOriginalEndpoint(final RequestProcessingContext requestProcessingCtx) {
            if (routeExecutionStrategy != null) {
                final RequestContext requestContext = requestScope.referenceCurrent();
                final ContainerRequest request = requestProcessingCtx.request();

                assert currentConnectionContext != null : "currentConnectionContext can't be null";
                final ExecutionContext currentExecutionContext = currentConnectionContext.executionContext();

                return routeExecutionStrategy.invokeService(currentExecutionContext.executor(),
                        actualExecutor -> {
                            assert ctxRef != null : "ctxRef can't be null";
                            ctxRef.set(new ExecutorOverrideConnectionContext(currentConnectionContext, actualExecutor));
                            return requestScope.runInScope(requestContext, () -> {
                                getRequestBufferPublisherInputStream(request)
                                        .offloadSourcePublisher(routeExecutionStrategy,
                                                currentExecutionContext.executor());
                                setResponseExecutionStrategy(routeExecutionStrategy, request);

                                return originalEndpoint.apply(requestProcessingCtx);
                            });
                        });
            }

            return defer(() -> {
                try {
                    return succeeded(originalEndpoint.apply(requestProcessingCtx));
                } catch (final Throwable t) {
                    return failed(t);
                }
            });
        }

        protected Single<Response> handleContainerResponse(final ContainerResponse res) {
            return succeeded(new OutboundJaxrsResponse(res.getStatusInfo(), res.getWrappedMessageContext()));
        }
    }

    private static final class ExecutorOffloadingEndpoint extends AbstractWrappedEndpoint {

        protected ExecutorOffloadingEndpoint(final UriRoutingContext uriRoutingContext,
                                             final RequestScope requestScope,
                                             final Provider<Ref<ConnectionContext>> ctxRefProvider,
                                             @Nullable final HttpExecutionStrategy routeExecutionStrategy) {
            super(uriRoutingContext, requestScope, ctxRefProvider, routeExecutionStrategy);
        }
    }

    private abstract static class AbstractSourceAwareEndpoint<T> extends AbstractWrappedEndpoint {
        private final Class<T> sourceType;

        protected AbstractSourceAwareEndpoint(final UriRoutingContext uriRoutingContext,
                                              final Class<T> sourceType,
                                              final RequestScope requestScope,
                                              final Provider<Ref<ConnectionContext>> ctxRefProvider,
                                              @Nullable final HttpExecutionStrategy routeExecutionStrategy) {
            super(uriRoutingContext, requestScope, ctxRefProvider, routeExecutionStrategy);
            this.sourceType = sourceType;
        }

        @Override
        protected final Single<Response> handleContainerResponse(final ContainerResponse res) {
            if (!res.hasEntity()) {
                return super.handleContainerResponse(res);
            }

            final Object responseEntity = res.getEntity();
            return sourceType.isAssignableFrom(responseEntity.getClass()) ?
                    handleSourceResponse(sourceType.cast(responseEntity), res) : super.handleContainerResponse(res);
        }

        protected abstract Single<Response> handleSourceResponse(T source, ContainerResponse res);
    }

    private static final class CompletableAwareEndpoint extends AbstractSourceAwareEndpoint<Completable> {
        private CompletableAwareEndpoint(final UriRoutingContext uriRoutingContext,
                                         final RequestScope requestScope,
                                         final Provider<Ref<ConnectionContext>> ctxRefProvider,
                                         @Nullable final HttpExecutionStrategy routeExecutionStrategy) {
            super(uriRoutingContext, Completable.class, requestScope, ctxRefProvider, routeExecutionStrategy);
        }

        @Override
        protected Single<Response> handleSourceResponse(final Completable source, final ContainerResponse res) {
            return source.concat(defer(() -> succeeded(noContent().build())));
        }
    }

    @SuppressWarnings("rawtypes")
    private static final class SingleAwareEndpoint extends AbstractSourceAwareEndpoint<Single> {
        private SingleAwareEndpoint(final UriRoutingContext uriRoutingContext,
                                    final RequestScope requestScope,
                                    final Provider<Ref<ConnectionContext>> ctxRefProvider,
                                    @Nullable final HttpExecutionStrategy routeExecutionStrategy) {
            super(uriRoutingContext, Single.class, requestScope, ctxRefProvider, routeExecutionStrategy);
        }

        @SuppressWarnings("unchecked")
        @Override
        protected Single<Response> handleSourceResponse(final Single source, final ContainerResponse res) {
            // Since we offer Single as an alternative to JAX-RS' supported CompletionStage, we have to manually deal
            // with aligning the generic entity type associated with the response by Jersey (which is Single<T>) to
            // what is expected by the downstream filter/interceptors/body writers (the actual T, which we get in map).
            return source.map(content -> {
                if (content instanceof Response) {
                    final Response contentResponse = (Response) content;
                    if (!contentResponse.hasEntity()) {
                        return contentResponse;
                    }
                    final OutboundJaxrsResponse jaxrsResponse = OutboundJaxrsResponse.from(contentResponse);
                    final OutboundMessageContext context = jaxrsResponse.getContext();
                    if (context.getEntityType() instanceof ParameterizedType) {
                        return jaxrsResponse;
                    } else {
                        context.setEntityType(new NestedParameterizedType(context.getEntityClass()));
                        return new OutboundJaxrsResponse(jaxrsResponse.getStatusInfo(), context);
                    }
                }

                final OutboundMessageContext requestContext = res.getWrappedMessageContext();
                if (content == null) {
                    requestContext.setEntity(null);
                    return new OutboundJaxrsResponse(NO_CONTENT, requestContext);
                }

                requestContext.setEntity(content);
                requestContext.setEntityType(new NestedParameterizedType(content.getClass()));
                return new OutboundJaxrsResponse(res.getStatusInfo(), requestContext);
            });
        }
    }

    private static final class NestedParameterizedType implements ParameterizedType {
        private static final Type[] EMPTY_TYPE_ARRAY = new Type[0];

        private final Class<?> nestedClass;

        private NestedParameterizedType(final Class<?> nestedClass) {
            this.nestedClass = nestedClass;
        }

        @Override
        public Type[] getActualTypeArguments() {
            return EMPTY_TYPE_ARRAY;
        }

        @Override
        public Type getRawType() {
            return nestedClass;
        }

        @Nullable
        @Override
        public Type getOwnerType() {
            return null;
        }
    }

    private static final class ExecutorOverrideConnectionContext extends DelegatingConnectionContext {
        private final ExecutionContext execCtx;

        private ExecutorOverrideConnectionContext(final ConnectionContext original,
                                                  final Executor executor) {
            super(original);

            this.execCtx = new DelegatingExecutionContext(original.executionContext()) {
                @Override
                public Executor executor() {
                    return executor;
                }
            };
        }

        @Override
        public ExecutionContext executionContext() {
            return execCtx;
        }
    }
}
