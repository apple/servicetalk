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
import io.servicetalk.concurrent.SingleSource.Subscriber;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.SubscribableSingle;
import io.servicetalk.concurrent.internal.DelayedCancellable;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.router.jersey.internal.BufferPublisherInputStream;
import io.servicetalk.router.api.RouteExecutionStrategyFactory;
import io.servicetalk.transport.api.ConnectionContext;

import org.glassfish.jersey.internal.MapPropertiesDelegate;
import org.glassfish.jersey.internal.inject.AbstractBinder;
import org.glassfish.jersey.internal.util.collection.Ref;
import org.glassfish.jersey.server.ApplicationHandler;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.spi.Container;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.security.Principal;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.BiFunction;
import javax.annotation.Nullable;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.SecurityContext;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.defer;
import static io.servicetalk.concurrent.api.Completable.failed;
import static io.servicetalk.concurrent.internal.SubscriberUtils.handleExceptionFromOnSubscribe;
import static io.servicetalk.concurrent.internal.SubscriberUtils.safeOnError;
import static io.servicetalk.http.router.jersey.Context.CONNECTION_CONTEXT_REF_TYPE;
import static io.servicetalk.http.router.jersey.Context.HTTP_REQUEST_REF_TYPE;
import static io.servicetalk.http.router.jersey.JerseyRouteExecutionStrategyUtils.validateRouteStrategies;
import static io.servicetalk.http.router.jersey.internal.RequestProperties.initRequestProperties;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

final class DefaultJerseyStreamingHttpRouter implements StreamingHttpService {
    private static final SecurityContext UNAUTHENTICATED_SECURITY_CONTEXT = new SecurityContext() {
        @Nullable
        @Override
        public Principal getUserPrincipal() {
            return null;
        }

        @Override
        public boolean isUserInRole(final String role) {
            return false;
        }

        @Override
        public boolean isSecure() {
            return false;
        }

        @Nullable
        @Override
        public String getAuthenticationScheme() {
            return null;
        }
    };

    private final ApplicationHandler applicationHandler;
    private final int publisherInputStreamQueueCapacity;
    private final BiFunction<ConnectionContext, HttpRequestMetaData, String> baseUriFunction;
    private final Container container;

    DefaultJerseyStreamingHttpRouter(final Application application,
                                     final int publisherInputStreamQueueCapacity,
                                     final BiFunction<ConnectionContext, HttpRequestMetaData, String> baseUriFunction,
                                     final RouteExecutionStrategyFactory<HttpExecutionStrategy> strategyFactory) {
        this(new ApplicationHandler(application), publisherInputStreamQueueCapacity, baseUriFunction,
                strategyFactory);
    }

    DefaultJerseyStreamingHttpRouter(final Class<? extends Application> applicationClass,
                                     final int publisherInputStreamQueueCapacity,
                                     final BiFunction<ConnectionContext, HttpRequestMetaData, String> baseUriFunction,
                                     final RouteExecutionStrategyFactory<HttpExecutionStrategy> strategyFactory) {
        this(new ApplicationHandler(applicationClass), publisherInputStreamQueueCapacity, baseUriFunction,
                strategyFactory);
    }

    private DefaultJerseyStreamingHttpRouter(final ApplicationHandler applicationHandler,
                                             final int publisherInputStreamQueueCapacity,
                                             final BiFunction<ConnectionContext, HttpRequestMetaData,
                                                     String> baseUriFunction,
                                             final RouteExecutionStrategyFactory<HttpExecutionStrategy>
                                                     strategyFactory) {

        if (!applicationHandler.getConfiguration().isEnabled(ServiceTalkFeature.class)) {
            throw new IllegalStateException("The " + ServiceTalkFeature.class.getSimpleName()
                    + " needs to be enabled for this application.");
        }

        final RouteStrategiesConfig routeStrategiesConfig =
                validateRouteStrategies(applicationHandler, strategyFactory);

        this.applicationHandler = applicationHandler;
        this.publisherInputStreamQueueCapacity = publisherInputStreamQueueCapacity;
        this.baseUriFunction = requireNonNull(baseUriFunction);

        applicationHandler.getInjectionManager().register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(routeStrategiesConfig).to(RouteStrategiesConfig.class).proxy(false);
            }
        });

        container = new DefaultContainer(applicationHandler);
        applicationHandler.onStartup(container);
    }

    Configuration configuration() {
        return applicationHandler.getConfiguration();
    }

    @Override
    public Completable closeAsync() {
        // We do not prevent multiple close attempts on purpose as we do not know the intention behind calling
        // closeAsync more than once: maybe a ContainerLifecycleListener failed to handle the onShutdown signal
        // and closing must be retried? Therefore we keep attempting to dispatch onShutdown and handle any error
        // that could arise from the repeated attempts.
        return defer(() -> {
            try {
                applicationHandler.onShutdown(container);
                return completed();
            } catch (final Throwable t) {
                return failed(t);
            }
        });
    }

    @Override
    public Single<StreamingHttpResponse> handle(final HttpServiceContext serviceCtx,
                                                final StreamingHttpRequest req,
                                                final StreamingHttpResponseFactory factory) {
        return new SubscribableSingle<StreamingHttpResponse>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super StreamingHttpResponse> subscriber) {
                final DelayedCancellable delayedCancellable = new DelayedCancellable();
                DuplicateTerminateDetectorSingle<? super StreamingHttpResponse> dupSub =
                        new DuplicateTerminateDetectorSingle<>(subscriber);
                try {
                    dupSub.onSubscribe(delayedCancellable);
                } catch (Throwable cause) {
                    handleExceptionFromOnSubscribe(dupSub, cause);
                    return;
                }
                try {
                    handle0(serviceCtx, req, factory, dupSub, delayedCancellable);
                } catch (final Throwable t) {
                    safeOnError(dupSub, t);
                }
            }
        };
    }

    private void handle0(final HttpServiceContext serviceCtx, final StreamingHttpRequest req,
                         final StreamingHttpResponseFactory factory,
                         final Subscriber<? super StreamingHttpResponse> subscriber,
                         final DelayedCancellable delayedCancellable) {
        final CharSequence baseUri = baseUriFunction.apply(serviceCtx, req);
        final CharSequence requestTarget = req.requestTarget();

        final StringBuilder requestUriBuilder = new StringBuilder(baseUri.length() + requestTarget.length())
                .append(baseUri);

        // Jersey expects the baseUri ends in a '/' and if not will return 404s
        assert baseUri.length() == 0 || baseUri.charAt(baseUri.length() - 1) == '/';

        if (requestTarget.length() > 0 && requestTarget.charAt(0) == '/') {
            requestUriBuilder.append(requestTarget, 1, requestTarget.length());
        } else {
            requestUriBuilder.append(requestTarget);
        }

        final ContainerRequest containerRequest = new ContainerRequest(
                URI.create(baseUri.toString()),
                URI.create(requestUriBuilder.toString()),
                req.method().name(),
                UNAUTHENTICATED_SECURITY_CONTEXT,
                new MapPropertiesDelegate(), null);

        req.headers().forEach(h ->
                containerRequest.getHeaders().add(h.getKey().toString(), h.getValue().toString()));

        final BufferPublisherInputStream entityStream = new BufferPublisherInputStream(req.payloadBody(),
                publisherInputStreamQueueCapacity);
        containerRequest.setEntityStream(entityStream);
        initRequestProperties(entityStream, containerRequest);

        final DefaultContainerResponseWriter responseWriter = new DefaultContainerResponseWriter(containerRequest,
                req.version(), serviceCtx, factory, subscriber);

        containerRequest.setWriter(responseWriter);

        containerRequest.setRequestScopedInitializer(injectionManager -> {
            injectionManager.<Ref<ConnectionContext>>getInstance(CONNECTION_CONTEXT_REF_TYPE).set(serviceCtx);
            injectionManager.<Ref<StreamingHttpRequest>>getInstance(HTTP_REQUEST_REF_TYPE).set(req);
        });

        delayedCancellable.delayedCancellable(responseWriter::dispose);

        applicationHandler.handle(containerRequest);
    }

    private static final class DuplicateTerminateDetectorSingle<T> implements Subscriber<T> {
        private static final Logger LOGGER = LoggerFactory.getLogger(DuplicateTerminateDetectorSingle.class);
        @SuppressWarnings("rawtypes")
        private static final AtomicIntegerFieldUpdater<DuplicateTerminateDetectorSingle> doneUpdater =
                newUpdater(DuplicateTerminateDetectorSingle.class, "done");
        private final Subscriber<T> delegate;
        private volatile int done;

        private DuplicateTerminateDetectorSingle(final Subscriber<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onSubscribe(final Cancellable cancellable) {
            delegate.onSubscribe(cancellable);
        }

        @Override
        public void onSuccess(@Nullable final T result) {
            if (doneUpdater.compareAndSet(this, 0, 1)) {
                delegate.onSuccess(result);
            } else {
                LOGGER.error("duplicate termination in onSuccess {} {}", result, delegate);
            }
        }

        @Override
        public void onError(final Throwable t) {
            if (doneUpdater.compareAndSet(this, 0, 1)) {
                delegate.onError(t);
            } else {
                LOGGER.error("duplicate termination in onError {}", delegate, t);
            }
        }
    }
}
