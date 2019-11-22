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
import io.servicetalk.transport.api.ConnectionContext;

import org.glassfish.jersey.internal.MapPropertiesDelegate;
import org.glassfish.jersey.internal.inject.AbstractBinder;
import org.glassfish.jersey.internal.util.collection.Ref;
import org.glassfish.jersey.server.ApplicationHandler;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.spi.Container;

import java.net.URI;
import java.security.Principal;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.Nullable;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.SecurityContext;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.defer;
import static io.servicetalk.concurrent.api.Completable.failed;
import static io.servicetalk.http.router.jersey.CharSequenceUtils.ensureNoLeadingSlash;
import static io.servicetalk.http.router.jersey.Context.CONNECTION_CONTEXT_REF_TYPE;
import static io.servicetalk.http.router.jersey.Context.HTTP_REQUEST_REF_TYPE;
import static io.servicetalk.http.router.jersey.JerseyRouteExecutionStrategyUtils.validateRouteStrategies;
import static io.servicetalk.http.router.jersey.internal.RequestProperties.initRequestProperties;
import static java.util.Objects.requireNonNull;
import static org.glassfish.jersey.server.internal.ContainerUtils.encodeUnsafeCharacters;

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
                                     final Function<String, HttpExecutionStrategy> routeStrategyFactory) {
        this(new ApplicationHandler(application), publisherInputStreamQueueCapacity, baseUriFunction,
                routeStrategyFactory);
    }

    DefaultJerseyStreamingHttpRouter(final Class<? extends Application> applicationClass,
                                     final int publisherInputStreamQueueCapacity,
                                     final BiFunction<ConnectionContext, HttpRequestMetaData, String> baseUriFunction,
                                     final Function<String, HttpExecutionStrategy> routeStrategyFactory) {
        this(new ApplicationHandler(applicationClass), publisherInputStreamQueueCapacity, baseUriFunction,
                routeStrategyFactory);
    }

    private DefaultJerseyStreamingHttpRouter(final ApplicationHandler applicationHandler,
                                             final int publisherInputStreamQueueCapacity,
                                             final BiFunction<ConnectionContext, HttpRequestMetaData,
                                                     String> baseUriFunction,
                                             final Function<String, HttpExecutionStrategy> routeStrategyFactory) {

        if (!applicationHandler.getConfiguration().isEnabled(ServiceTalkFeature.class)) {
            throw new IllegalStateException("The " + ServiceTalkFeature.class.getSimpleName()
                    + " needs to be enabled for this application.");
        }

        final RouteStrategiesConfig routeStrategiesConfig =
                validateRouteStrategies(applicationHandler, routeStrategyFactory);

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
                subscriber.onSubscribe(delayedCancellable);
                try {
                    handle0(serviceCtx, req, factory, subscriber, delayedCancellable);
                } catch (final Throwable t) {
                    subscriber.onError(t);
                }
            }
        };
    }

    private void handle0(final HttpServiceContext serviceCtx, final StreamingHttpRequest req,
                         final StreamingHttpResponseFactory factory,
                         final Subscriber<? super StreamingHttpResponse> subscriber,
                         final DelayedCancellable delayedCancellable) {

        final CharSequence baseUri = baseUriFunction.apply(serviceCtx, req);
        final CharSequence path = ensureNoLeadingSlash(req.rawPath());

        // Jersey needs URI-unsafe query chars to be encoded
        @Nullable
        final String encodedQuery = req.rawQuery().isEmpty() ? null : encodeUnsafeCharacters(req.rawQuery());

        final StringBuilder requestUriBuilder =
                new StringBuilder(baseUri.length() + path.length() +
                        (encodedQuery != null ? 1 + encodedQuery.length() : 0))
                        .append(baseUri)
                        .append(path);

        if (encodedQuery != null) {
            requestUriBuilder.append('?').append(encodedQuery);
        }

        final ContainerRequest containerRequest = new ContainerRequest(
                URI.create(baseUri.toString()),
                URI.create(requestUriBuilder.toString()),
                req.method().name(),
                UNAUTHENTICATED_SECURITY_CONTEXT,
                new MapPropertiesDelegate());

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
}
