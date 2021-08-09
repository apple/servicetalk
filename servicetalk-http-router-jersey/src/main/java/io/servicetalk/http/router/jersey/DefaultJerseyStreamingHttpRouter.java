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

import io.servicetalk.buffer.api.Buffer;
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

import org.glassfish.jersey.internal.LocalizationMessages;
import org.glassfish.jersey.internal.MapPropertiesDelegate;
import org.glassfish.jersey.internal.PropertiesDelegate;
import org.glassfish.jersey.internal.inject.AbstractBinder;
import org.glassfish.jersey.internal.util.collection.Ref;
import org.glassfish.jersey.server.ApplicationHandler;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.spi.Container;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.net.URI;
import java.security.Principal;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.BiFunction;
import javax.annotation.Nullable;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.SecurityContext;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.defer;
import static io.servicetalk.concurrent.api.Completable.failed;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.internal.SubscriberUtils.handleExceptionFromOnSubscribe;
import static io.servicetalk.concurrent.internal.SubscriberUtils.safeOnError;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN;
import static io.servicetalk.http.router.jersey.Context.CONNECTION_CONTEXT_REF_TYPE;
import static io.servicetalk.http.router.jersey.Context.HTTP_REQUEST_REF_TYPE;
import static io.servicetalk.http.router.jersey.JerseyRouteExecutionStrategyUtils.validateRouteStrategies;
import static io.servicetalk.http.router.jersey.internal.RequestProperties.initRequestProperties;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

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

        final URI baseURI;
        final URI requestURI;
        try {
            baseURI = URI.create(baseUri.toString());
            requestURI = URI.create(requestUriBuilder.toString());
        } catch (IllegalArgumentException cause) {
            Buffer message = serviceCtx.executionContext().bufferAllocator().fromAscii(cause.getMessage());
            StreamingHttpResponse response = factory.badRequest().payloadBody(from(message));
            response.headers().set(CONTENT_LENGTH, Integer.toString(message.readableBytes()));
            response.headers().set(CONTENT_TYPE, TEXT_PLAIN);
            subscriber.onSuccess(response);
            return;
        }

        final ContainerRequest containerRequest = new CloseSignalHandoffAbleContainerRequest(
                baseURI,
                requestURI,
                req.method().name(),
                UNAUTHENTICATED_SECURITY_CONTEXT,
                new MapPropertiesDelegate(),
                new ResourceConfig());

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

    /**
     * {@link ContainerRequest#close()} may get called outside the thread that executes the
     * {@link ApplicationHandler#handle(ContainerRequest)}. As a result, the close can be racy when the
     * {@link org.glassfish.jersey.message.internal.InboundMessageContext} is accessed at the same time.
     * This wrapper allows the {@link #close()} to be deferred after the reading is done by handing the {@link #close()}
     * over to the {@link ApplicationHandler#handle(ContainerRequest)} owner thread. This also offers better thread
     * visibility between the threads and the unsafely accessed variables.
     */
    private static final class CloseSignalHandoffAbleContainerRequest extends ContainerRequest {
        private static final AtomicReferenceFieldUpdater<CloseSignalHandoffAbleContainerRequest, State> stateUpdater =
                newUpdater(CloseSignalHandoffAbleContainerRequest.class, State.class, "state");

        private enum State {
            INIT,
            READING,
            PENDING_CLOSE,
            CLOSED
        }

        private volatile State state = State.INIT;

        private CloseSignalHandoffAbleContainerRequest(final URI baseUri, final URI requestUri, final String httpMethod,
                                                      final SecurityContext securityContext,
                                                      final PropertiesDelegate propertiesDelegate,
                                                      final Configuration configuration) {
            super(baseUri, requestUri, httpMethod, securityContext, propertiesDelegate, configuration);
        }

        /**
         * The following overloads are overriden because the inherited ones call directly {@code super}
         * {@link ContainerRequest#readEntity(Class, Type, Annotation[], PropertiesDelegate)} thus our
         * implementation of {@link ContainerRequest#readEntity(Class, Type, Annotation[], PropertiesDelegate)} doesn't
         * get invoked when not called directly.
         */

        @Override
        public <T> T readEntity(final Class<T> rawType) {
            return readEntity(rawType, getPropertiesDelegate());
        }

        @Override
        public <T> T readEntity(final Class<T> rawType, final Annotation[] annotations) {
            return readEntity(rawType, annotations, getPropertiesDelegate());
        }

        @Override
        public <T> T readEntity(final Class<T> rawType, final Type type) {
            return readEntity(rawType, type, getPropertiesDelegate());
        }

        @Override
        public <T> T readEntity(final Class<T> rawType, final Type type, final Annotation[] annotations) {
            return readEntity(rawType, type, annotations, getPropertiesDelegate());
        }

        @Override
        public <T> T readEntity(final Class<T> rawType, final Type type, final Annotation[] annotations,
                                final PropertiesDelegate propertiesDelegate) {
            final State prevState = state;
            final boolean reentry = prevState == State.READING;
            if (reentry || stateUpdater.compareAndSet(this, State.INIT, State.READING)) {
                try {
                    return super.readEntity(rawType, type, annotations, propertiesDelegate);
                } finally {
                    if (!reentry && !stateUpdater.compareAndSet(this, State.READING, State.INIT)) {
                        // Closed while we were in progress.
                        close0();
                    }
                }
            }

            throw new IllegalStateException(LocalizationMessages.ERROR_ENTITY_STREAM_CLOSED());
        }

        @Override
        public boolean bufferEntity() throws ProcessingException {
            final State prevState = state;
            final boolean reentry = prevState == State.READING;
            if (reentry || stateUpdater.compareAndSet(this, State.INIT, State.READING)) {
                try {
                    return super.bufferEntity();
                } finally {
                    if (!reentry && !stateUpdater.compareAndSet(this, State.READING, State.INIT)) {
                        // Closed while we were in progress.
                        close0();
                    }
                }
            }

            throw new IllegalStateException(LocalizationMessages.ERROR_ENTITY_STREAM_CLOSED());
        }

        @Override
        public boolean hasEntity() {
            if (state == State.CLOSED) {
                throw new IllegalStateException(LocalizationMessages.ERROR_ENTITY_STREAM_CLOSED());
            }

            return super.hasEntity();
        }

        @Override
        public InputStream getEntityStream() {
            if (state == State.CLOSED) {
                throw new IllegalStateException(LocalizationMessages.ERROR_ENTITY_STREAM_CLOSED());
            }
            return super.getEntityStream();
        }

        @Override
        public void close() {
            final State prevState = stateUpdater.getAndSet(this, State.PENDING_CLOSE);
            if (prevState == State.INIT) {
                close0();
            }
        }

        private void close0() {
            state = State.CLOSED;
            super.close();
        }
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
