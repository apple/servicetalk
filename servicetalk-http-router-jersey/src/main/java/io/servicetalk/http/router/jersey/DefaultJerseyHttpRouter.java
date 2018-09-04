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

import io.servicetalk.concurrent.Single.Subscriber;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.DelayedCancellable;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpService;
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
import static io.servicetalk.concurrent.api.Completable.error;
import static io.servicetalk.http.router.jersey.CharSequenceUtils.ensureNoLeadingSlash;
import static io.servicetalk.http.router.jersey.Context.CONNECTION_CONTEXT_REF_TYPE;
import static io.servicetalk.http.router.jersey.Context.HTTP_REQUEST_REF_TYPE;
import static io.servicetalk.http.router.jersey.ExecutionStrategyUtils.validateExecutorConfiguration;
import static io.servicetalk.http.router.jersey.internal.RequestProperties.initRequestProperties;
import static java.util.Objects.requireNonNull;
import static org.glassfish.jersey.server.internal.ContainerUtils.encodeUnsafeCharacters;

final class DefaultJerseyHttpRouter extends HttpService {

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
    private final BiFunction<ConnectionContext, HttpRequest<HttpPayloadChunk>, String> baseUriFunction;
    private final Container container;

    DefaultJerseyHttpRouter(final Application application,
                            final int publisherInputStreamQueueCapacity,
                            final BiFunction<ConnectionContext, HttpRequest<HttpPayloadChunk>, String> baseUriFunction,
                            final Function<String, Executor> executorFactory) {
        this(new ApplicationHandler(application), publisherInputStreamQueueCapacity, baseUriFunction,
                executorFactory);
    }

    DefaultJerseyHttpRouter(final Class<? extends Application> applicationClass,
                            final int publisherInputStreamQueueCapacity,
                            final BiFunction<ConnectionContext, HttpRequest<HttpPayloadChunk>, String> baseUriFunction,
                            final Function<String, Executor> executorFactory) {
        this(new ApplicationHandler(applicationClass), publisherInputStreamQueueCapacity, baseUriFunction,
                executorFactory);
    }

    private DefaultJerseyHttpRouter(final ApplicationHandler applicationHandler,
                                    final int publisherInputStreamQueueCapacity,
                                    final BiFunction<ConnectionContext,
                                            HttpRequest<HttpPayloadChunk>, String> baseUriFunction,
                                    final Function<String, Executor> executorFactory) {

        if (!applicationHandler.getConfiguration().isEnabled(ServiceTalkFeature.class)) {
            throw new IllegalStateException("The " + ServiceTalkFeature.class.getSimpleName()
                    + " needs to be enabled for this application.");
        }

        final ExecutorConfig executorConfig = validateExecutorConfiguration(applicationHandler, executorFactory);

        this.applicationHandler = applicationHandler;
        this.publisherInputStreamQueueCapacity = publisherInputStreamQueueCapacity;
        this.baseUriFunction = requireNonNull(baseUriFunction);

        if (!executorConfig.executors.isEmpty()) {
            applicationHandler.getInjectionManager().register(new AbstractBinder() {
                @Override
                protected void configure() {
                    bind(executorConfig).to(ExecutorConfig.class).proxy(false);
                }
            });
        }

        container = new DefaultContainer(applicationHandler);
        applicationHandler.onStartup(container);
    }

    Configuration getConfiguration() {
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
                return error(t);
            }
        });
    }

    @Override
    public Single<HttpResponse<HttpPayloadChunk>> handle(final ConnectionContext ctx,
                                                         final HttpRequest<HttpPayloadChunk> req) {
        return new Single<HttpResponse<HttpPayloadChunk>>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super HttpResponse<HttpPayloadChunk>> subscriber) {
                final DelayedCancellable delayedCancellable = new DelayedCancellable();
                subscriber.onSubscribe(delayedCancellable);
                try {
                    handle0(ctx, req, subscriber, delayedCancellable);
                } catch (final Throwable t) {
                    subscriber.onError(t);
                }
            }
        };
    }

    private void handle0(final ConnectionContext ctx,
                         final HttpRequest<HttpPayloadChunk> req,
                         final Subscriber<? super HttpResponse<HttpPayloadChunk>> subscriber,
                         final DelayedCancellable delayedCancellable) {

        final CharSequence baseUri = baseUriFunction.apply(ctx, req);
        final CharSequence path = ensureNoLeadingSlash(req.getRawPath());

        // Jersey needs URI-unsafe query chars to be encoded
        @Nullable
        final String encodedQuery = req.getRawQuery().isEmpty() ? null : encodeUnsafeCharacters(req.getRawQuery());

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
                req.getMethod().getName(),
                UNAUTHENTICATED_SECURITY_CONTEXT,
                new MapPropertiesDelegate());

        req.getHeaders().forEach(h ->
                containerRequest.getHeaders().add(h.getKey().toString(), h.getValue().toString()));

        final ChunkPublisherInputStream entityStream = new ChunkPublisherInputStream(req.getPayloadBody(),
                publisherInputStreamQueueCapacity);
        containerRequest.setEntityStream(entityStream);
        initRequestProperties(entityStream, containerRequest);

        final DefaultContainerResponseWriter responseWriter = new DefaultContainerResponseWriter(containerRequest,
                req.getVersion(), ctx.getExecutionContext().getBufferAllocator(),
                ctx.getExecutionContext().getExecutor(), subscriber);

        containerRequest.setWriter(responseWriter);

        containerRequest.setRequestScopedInitializer(injectionManager -> {
            injectionManager.<Ref<ConnectionContext>>getInstance(CONNECTION_CONTEXT_REF_TYPE).set(ctx);
            injectionManager.<Ref<HttpRequest<HttpPayloadChunk>>>getInstance(HTTP_REQUEST_REF_TYPE).set(req);
        });

        delayedCancellable.setDelayedCancellable(responseWriter::cancelSuspendedTimer);

        applicationHandler.handle(containerRequest);
    }
}
