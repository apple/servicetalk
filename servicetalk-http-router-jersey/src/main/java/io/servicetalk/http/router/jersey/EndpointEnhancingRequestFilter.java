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
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.SubscribableSingle;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.DelegatingConnectionContext;
import io.servicetalk.transport.api.DelegatingExecutionContext;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.ExecutionStrategy;

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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import javax.annotation.Nullable;
import javax.annotation.Priority;
import javax.inject.Provider;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.internal.SubscriberUtils.handleExceptionFromOnSubscribe;
import static io.servicetalk.http.api.HttpExecutionStrategies.customStrategyBuilder;
import static io.servicetalk.http.router.jersey.JerseyRouteExecutionStrategyUtils.getRouteExecutionStrategy;
import static io.servicetalk.http.router.jersey.internal.RequestProperties.getRequestBufferPublisherInputStream;
import static io.servicetalk.http.router.jersey.internal.RequestProperties.setRequestCancellable;
import static io.servicetalk.http.router.jersey.internal.RequestProperties.setResponseExecutionStrategy;
import static java.lang.Integer.MAX_VALUE;
import static java.util.Objects.requireNonNull;
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

    private final EnhancedEndpointCache enhancedEndpointCache = new EnhancedEndpointCache();
    private final Provider<Ref<ConnectionContext>> ctxRefProvider;
    private final Provider<RouteStrategiesConfig> routeStrategiesConfigProvider;
    private final RequestScope requestScope;

    EndpointEnhancingRequestFilter(@Context final Provider<Ref<ConnectionContext>> ctxRefProvider,
                                   @Context final Provider<RouteStrategiesConfig> routeStrategiesConfigProvider,
                                   @Context final RequestScope requestScope) {
        this.ctxRefProvider = requireNonNull(ctxRefProvider);
        this.routeStrategiesConfigProvider = requireNonNull(routeStrategiesConfigProvider);
        this.requestScope = requireNonNull(requestScope);
    }

    @Override
    public void filter(final ContainerRequestContext requestCtx) {
        // If we don't have a ConnectionContext then the request isn't from ServiceTalk and need not be filtered.
        if (ctxRefProvider.get().get() != null) {
            enhancedEndpointCache.enhance(requestScope, ctxRefProvider, routeStrategiesConfigProvider,
                    (UriRoutingContext) requestCtx.getUriInfo());
        }
    }

    private interface EnhancedEndpoint extends Endpoint, ResourceInfo {
    }

    private static final class EnhancedEndpointCache {

        private static final EnhancedEndpoint NOOP = new NoopEnhancedEndpoint();

        private final ConcurrentHashMap<Method, EnhancedEndpoint> enhancements = new ConcurrentHashMap<>();

        void enhance(final RequestScope requestScope,
                     final Provider<Ref<ConnectionContext>> ctxRefProvider,
                     final Provider<RouteStrategiesConfig> routeStrategiesConfigProvider,
                     final UriRoutingContext urc) {
            if (urc.getResourceMethod() == null) {
                return;
            }
            EnhancedEndpoint enhanced = enhancements.get(urc.getResourceMethod());
            if (enhanced == null) {
                // attempt get(..) first to avoid creating a capturing lambda per request in steady state
                enhanced = enhancements.computeIfAbsent(urc.getResourceMethod(),
                        resourceMethod -> defineEndpoint(urc.getEndpoint(), requestScope, ctxRefProvider,
                                routeStrategiesConfigProvider, urc.getResourceClass(), resourceMethod));
            }
            if (enhanced != NOOP) {
                urc.setEndpoint(enhanced);
            }
        }

        private static EnhancedEndpoint defineEndpoint(final Endpoint delegate,
                                                       final RequestScope requestScope,
                                                       final Provider<Ref<ConnectionContext>> ctxRefProvider,
                                                       final Provider<RouteStrategiesConfig> routeStratConfigProvider,
                                                       final Class<?> resourceClass,
                                                       final Method resourceMethod) {

            final HttpExecutionStrategy routeExecutionStrategy = getRouteExecutionStrategy(
                    resourceMethod, resourceClass, routeStratConfigProvider.get());

            final Class<?> returnType = resourceMethod.getReturnType();
            if (Single.class.isAssignableFrom(returnType)) {
                return new SingleAwareEndpoint(
                        delegate, resourceClass, resourceMethod, requestScope, ctxRefProvider, routeExecutionStrategy);
            }
            if (Completable.class.isAssignableFrom(returnType)) {
                return new CompletableAwareEndpoint(
                        delegate, resourceClass, resourceMethod, requestScope, ctxRefProvider, routeExecutionStrategy);
            }
            final ExecutionContext executionContext = ctxRefProvider.get().get().executionContext();
            final HttpExecutionStrategy difference = difference(executionContext.executor(),
                            (HttpExecutionStrategy) executionContext.executionStrategy(), routeExecutionStrategy);
            if (difference != null) {
                return new ExecutorOffloadingEndpoint(
                        delegate, resourceClass, resourceMethod, requestScope, ctxRefProvider, routeExecutionStrategy);
            }
            return NOOP;
        }
    }

    private abstract static class AbstractWrappedEndpoint implements EnhancedEndpoint {

        private final Endpoint delegate;
        private final Class<?> resourceClass;
        private final Method resourceMethod;
        private final RequestScope requestScope;
        @Nullable
        private final HttpExecutionStrategy routeExecutionStrategy;
        @Nullable
        private final HttpExecutionStrategy effectiveRouteStrategy;
        @Nullable
        private final Executor executor;
        @Nullable
        private final Provider<Ref<ConnectionContext>> ctxRefProvider;

        private AbstractWrappedEndpoint(
                final Endpoint delegate,
                final Class<?> resourceClass,
                final Method resourceMethod,
                final RequestScope requestScope,
                @Nullable final Provider<Ref<ConnectionContext>> ctxRefProvider,
                @Nullable final HttpExecutionStrategy routeExecutionStrategy) {
            this.delegate = delegate;
            this.resourceClass = resourceClass;
            this.resourceMethod = resourceMethod;
            this.requestScope = requestScope;
            this.ctxRefProvider = ctxRefProvider;
            this.routeExecutionStrategy = routeExecutionStrategy;
            if (routeExecutionStrategy != null) {
                final ExecutionContext executionContext = ctxRefProvider.get().get().executionContext();
                // ExecutionStrategy and Executor shared for all routes in JerseyRouter
                final ExecutionStrategy executionStrategy = executionContext.executionStrategy();
                executor = executionContext.executor();
                effectiveRouteStrategy = calculateEffectiveStrategy(executionStrategy, executor);
            } else {
                effectiveRouteStrategy = null;
                executor = null;
            }
        }

        @Override
        public Class<?> getResourceClass() {
            return resourceClass;
        }

        @Override
        public Method getResourceMethod() {
            return resourceMethod;
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

            final Single<Response> responseSingle = callOriginalEndpoint(requestProcessingCtx, effectiveRouteStrategy)
                    .flatMap(this::handleContainerResponse)
                    .liftSync(subscriber -> new SingleSource.Subscriber<Response>() {
                        @Override
                        public void onSubscribe(final Cancellable cancellable) {
                            subscriber.onSubscribe(() -> {
                                cancellable.cancel();
                                restoreEndPoint();
                                asyncContext.cancel();
                            });
                        }

                        @Override
                        public void onSuccess(@Nullable final Response result) {
                            restoreEndPoint();
                            subscriber.onSuccess(result);
                        }

                        @Override
                        public void onError(final Throwable t) {
                            restoreEndPoint();
                            subscriber.onError(t);
                            asyncContext.resume(t);
                        }

                        private void restoreEndPoint() {
                            requestProcessingCtx.routingContext().setEndpoint(delegate);
                        }
                    });

            final Cancellable cancellable;
            if (effectiveRouteStrategy != null) {
                assert executor != null;
                cancellable = effectiveRouteStrategy
                        .offloadSend(executor, responseSingle)
                        .subscribe(asyncContext::resume);
            } else {
                cancellable = responseSingle.subscribe(asyncContext::resume);
            }
            setRequestCancellable(cancellable, requestProcessingCtx.request());

            // Return null on current thread since response will be delivered asynchronously
            return null;
        }

        @Nullable
        private HttpExecutionStrategy calculateEffectiveStrategy(@Nullable final ExecutionStrategy executionStrategy,
                                                                 @Nullable final Executor executor) {
            assert routeExecutionStrategy != null;
            if (executor != null && executionStrategy instanceof HttpExecutionStrategy) {
                return difference(executor, (HttpExecutionStrategy) executionStrategy, routeExecutionStrategy);
            }
            return null;
        }

        private Single<ContainerResponse> callOriginalEndpoint(
                final RequestProcessingContext requestProcessingCtx,
                @Nullable final HttpExecutionStrategy effectiveRouteStrategy) {
            if (effectiveRouteStrategy == null) {
                return defer(() -> {
                    try {
                        return succeeded(delegate.apply(requestProcessingCtx));
                    } catch (final Throwable t) {
                        return failed(t);
                    }
                });
            }
            final RequestContext requestContext = requestScope.referenceCurrent();
            final ContainerRequest request = requestProcessingCtx.request();
            assert executor != null;
            assert ctxRefProvider != null;
            final Ref<ConnectionContext> ctxRef = ctxRefProvider.get();
            Function<Executor, ContainerResponse> service = actualExecutor ->
                    requestScope.runInScope(requestContext, () -> {
                        final ConnectionContext origConnectionContext = ctxRef.get();
                        if (!(origConnectionContext instanceof ExecutorOverrideConnectionContext)) {
                            ConnectionContext overrideConnectionContext = new ExecutorOverrideConnectionContext(
                                    origConnectionContext, actualExecutor);
                            ctxRef.set(overrideConnectionContext);
                        }
                        getRequestBufferPublisherInputStream(request)
                                .offloadSourcePublisher(effectiveRouteStrategy, actualExecutor);
                        setResponseExecutionStrategy(effectiveRouteStrategy, request);

                        return delegate.apply(requestProcessingCtx);
                    });

            Executor useExecutor = effectiveRouteStrategy instanceof JerseyRouteExecutionStrategy ?
                    ((JerseyRouteExecutionStrategy) effectiveRouteStrategy).executor() :
                    executor;

            return (effectiveRouteStrategy.isMetadataReceiveOffloaded() ?
                    useExecutor.submit(() -> service.apply(useExecutor)) :
                    new SubscribableSingle<ContainerResponse>() {

                        @Override
                        protected void handleSubscribe(
                                final SingleSource.Subscriber<? super ContainerResponse> subscriber) {
                            try {
                                subscriber.onSubscribe(IGNORE_CANCEL);
                            } catch (Throwable cause) {
                                handleExceptionFromOnSubscribe(subscriber, cause);
                                return;
                            }

                            final ContainerResponse result;
                            try {
                                result = service.apply(useExecutor);
                            } catch (Throwable t) {
                                subscriber.onError(t);
                                return;
                            }
                            subscriber.onSuccess(result);
                        }
                    })
                    .beforeFinally(requestContext::release);
        }

        protected Single<Response> handleContainerResponse(final ContainerResponse res) {
            return succeeded(new OutboundJaxrsResponse(res.getStatusInfo(), res.getWrappedMessageContext()));
        }
    }

    private static final class ExecutorOffloadingEndpoint extends AbstractWrappedEndpoint {

        private ExecutorOffloadingEndpoint(final Endpoint delegate,
                                           final Class<?> resourceClass,
                                           final Method resourceMethod,
                                           final RequestScope requestScope,
                                           final Provider<Ref<ConnectionContext>> ctxRefProvider,
                                           @Nullable final HttpExecutionStrategy routeExecutionStrategy) {
            super(delegate, resourceClass, resourceMethod, requestScope, ctxRefProvider, routeExecutionStrategy);
        }
    }

    private abstract static class AbstractSourceAwareEndpoint<T> extends AbstractWrappedEndpoint {
        private final Class<T> sourceType;

        private AbstractSourceAwareEndpoint(final Endpoint delegate,
                                            final Class<?> resourceClass,
                                            final Method resourceMethod,
                                            final Class<T> sourceType,
                                            final RequestScope requestScope,
                                            final Provider<Ref<ConnectionContext>> ctxRefProvider,
                                            @Nullable final HttpExecutionStrategy routeExecutionStrategy) {
            super(delegate, resourceClass, resourceMethod, requestScope, ctxRefProvider, routeExecutionStrategy);
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
        private CompletableAwareEndpoint(final Endpoint delegate,
                                         final Class<?> resourceClass,
                                         final Method resourceMethod,
                                         final RequestScope requestScope,
                                         final Provider<Ref<ConnectionContext>> ctxRefProvider,
                                         @Nullable final HttpExecutionStrategy routeExecutionStrategy) {
            super(delegate, resourceClass, resourceMethod, Completable.class, requestScope, ctxRefProvider,
                    routeExecutionStrategy);
        }

        @Override
        protected Single<Response> handleSourceResponse(final Completable source, final ContainerResponse res) {
            return source.concat(defer(() -> succeeded(noContent().build())));
        }
    }

    @SuppressWarnings("rawtypes")
    private static final class SingleAwareEndpoint extends AbstractSourceAwareEndpoint<Single> {
        private SingleAwareEndpoint(final Endpoint delegate,
                                    final Class<?> resourceClass,
                                    final Method resourceMethod,
                                    final RequestScope requestScope,
                                    final Provider<Ref<ConnectionContext>> ctxRefProvider,
                                    @Nullable final HttpExecutionStrategy routeExecutionStrategy) {
            super(delegate, resourceClass, resourceMethod, Single.class, requestScope, ctxRefProvider,
                    routeExecutionStrategy);
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

    private static class NoopEnhancedEndpoint implements EnhancedEndpoint {
        @Override
        @Nullable
        public ContainerResponse apply(final RequestProcessingContext requestProcessingContext) {
            throw new UnsupportedOperationException();
        }

        @Override
        @Nullable
        public Method getResourceMethod() {
            throw new UnsupportedOperationException();
        }

        @Nullable
        @Override
        public Class<?> getResourceClass() {
            throw new UnsupportedOperationException();
        }
    }

    // Variant of HttpExecutionStrategies#difference which is geared towards router logic
    @Nullable
    private static HttpExecutionStrategy difference(final Executor contextExecutor,
                                                    final HttpExecutionStrategy left,
                                                    final HttpExecutionStrategy right) {
        if (left.equals(right) || !right.hasOffloads()) {
            return null;
        }
        if (!left.hasOffloads()) {
            return right;
        }

        if (right instanceof JerseyRouteExecutionStrategy) {
            final Executor rightExecutor = ((JerseyRouteExecutionStrategy) right).executor();
            if (rightExecutor != contextExecutor) {
                // Since the original offloads were done on a different executor, we need to offload again
                // and for this we assume that the intention is to offload only the call to handle
                return new JerseyRouteExecutionStrategy(
                        customStrategyBuilder().offloadReceiveMetadata().build(), rightExecutor);
            }
        }

        // There is no need to offload differently than what the left side has deemed safe enough
        return null;
    }
}
