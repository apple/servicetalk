/*
 * Copyright © 2019-2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.grpc.api;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.HttpLifecycleObserver;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.transport.api.ServerContext;

import java.time.Duration;
import java.util.Arrays;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.internal.FutureUtils.awaitResult;
import static io.servicetalk.grpc.api.GrpcUtils.GRPC_CONTENT_TYPE;
import static io.servicetalk.grpc.api.GrpcUtils.newErrorResponse;

/**
 * A builder for building a <a href="https://www.grpc.io">gRPC</a> server.
 */
public abstract class GrpcServerBuilder {

    /**
     * Initializes the underlying {@link HttpServerBuilder} used for the transport layer.
     */
    @FunctionalInterface
    public interface HttpInitializer {

        /**
         * Configures the underlying {@link HttpServerBuilder}.
         * @param builder The builder to customize the HTTP layer.
         */
        void initialize(HttpServerBuilder builder);

        /**
         * Appends the passed {@link HttpInitializer} to this {@link HttpInitializer} such that this instance is
         * applied first and then the argument's {@link HttpInitializer}.
         * @param toAppend {@link HttpInitializer} to append.
         * @return A composite {@link HttpInitializer} after the append operation.
         */
        default HttpInitializer append(HttpInitializer toAppend) {
            return builder -> {
                initialize(builder);
                toAppend.initialize(builder);
            };
        }
    }

    /**
     * Set a function which can configure the underlying {@link HttpServerBuilder} used for the transport layer.
     * @param initializer Initializes the underlying HTTP transport builder.
     * @return {@code this}.
     */
    public GrpcServerBuilder initializeHttp(HttpInitializer initializer) {
        throw new UnsupportedOperationException("Initializing the HttpServerBuilder using this method is not yet" +
                "supported by " + getClass().getName());
    }

    /**
     * Set a default timeout during which gRPC calls are expected to complete. This default will be used only if the
     * request includes no timeout; any value specified in client request will supersede this default.
     *
     * @param defaultTimeout {@link Duration} of default timeout which must be positive non-zero.
     * @return {@code this}.
     */
    public abstract GrpcServerBuilder defaultTimeout(Duration defaultTimeout);

    /**
     * Sets a {@link GrpcLifecycleObserver} that provides visibility into gRPC lifecycle events.
     * <p>
     * Note, if {@link #initializeHttp(HttpInitializer)} is used to configure
     * {@link HttpServerBuilder#lifecycleObserver(HttpLifecycleObserver)} – that will override the value specified
     * using this method. Please choose only one approach.
     * @param lifecycleObserver A {@link GrpcLifecycleObserver} that provides visibility into gRPC lifecycle events.
     * @return {@code this}.
     */
    public abstract GrpcServerBuilder lifecycleObserver(GrpcLifecycleObserver lifecycleObserver);

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param services {@link GrpcBindableService}(s) to create a <a href="https://www.grpc.io">gRPC</a> service.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    public final Single<ServerContext> listen(GrpcBindableService<?>... services) {
        GrpcServiceFactory<?>[] factories = Arrays.stream(services)
                .map(GrpcBindableService::bindService)
                .toArray(GrpcServiceFactory<?>[]::new);
        return listen(factories);
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param serviceFactories {@link GrpcServiceFactory}(s) to create a <a href="https://www.grpc.io">gRPC</a> service.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    public final Single<ServerContext> listen(GrpcServiceFactory<?>... serviceFactories) {
        return doListen(GrpcServiceFactory.merge(serviceFactories));
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param serviceFactories {@link GrpcServiceFactory}(s) to create a <a href="https://www.grpc.io">gRPC</a> service.
     * @return A {@link ServerContext} by blocking the calling thread until the server is successfully started or
     * throws an {@link Exception} if the server could not be started.
     * @throws Exception if the server could not be started.
     */
    public final ServerContext listenAndAwait(GrpcServiceFactory<?>... serviceFactories) throws Exception {
        return awaitResult(listen(serviceFactories).toFuture());
    }

     /**
      * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
      * <p>
      * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
      *
      * @param services {@link GrpcBindableService}(s) to create a <a href="https://www.grpc.io">gRPC</a> service.
      * @return A {@link ServerContext} by blocking the calling thread until the server is successfully started or
      * throws an {@link Exception} if the server could not be started.
      * @throws Exception if the server could not be started.
      */
     public final ServerContext listenAndAwait(GrpcBindableService<?>... services) throws Exception {
         GrpcServiceFactory<?>[] factories = Arrays.stream(services)
                 .map(GrpcBindableService::bindService)
                 .toArray(GrpcServiceFactory<?>[]::new);
         return listenAndAwait(factories);
     }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param serviceFactory {@link GrpcServiceFactory} to create a <a href="https://www.grpc.io">gRPC</a> service.
     * @return A {@link ServerContext} by blocking the calling thread until the server is successfully started or
     * throws an {@link Exception} if the server could not be started.
     */
    protected abstract Single<ServerContext> doListen(GrpcServiceFactory<?> serviceFactory);

    /**
     * Temporarily method to append "catch-all-exceptions" filter, required until we transition from abstract classes
     * to interfaces.
     *
     * @param httpServerBuilder {@link HttpServerBuilder} to add a filter to
     * @deprecated This method was introduced temporarily, should not be used, and will be removed in version 0.42.
     */
    @Deprecated
    protected static void appendCatchAllFilter(HttpServerBuilder httpServerBuilder) {
        // TODO(dj): Move to DefaultGrpcServerBuilder
        // This code depends on GrpcUtils which is inaccessible from the servicetalk-grpc-netty module.
        // When this class is converted to an interface we can also refactor that part.
        httpServerBuilder.appendNonOffloadingServiceFilter(CatchAllHttpServiceFilter.INSTANCE);
    }

    static final class CatchAllHttpServiceFilter implements StreamingHttpServiceFilterFactory,
                                                            HttpExecutionStrategyInfluencer {

        static final StreamingHttpServiceFilterFactory INSTANCE = new CatchAllHttpServiceFilter();

        private CatchAllHttpServiceFilter() {
            // Singleton
        }

        @Override
        public StreamingHttpServiceFilter create(final StreamingHttpService service) {
            return new StreamingHttpServiceFilter(service) {

                @Override
                public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                            final StreamingHttpRequest request,
                                                            final StreamingHttpResponseFactory responseFactory) {
                    final Single<StreamingHttpResponse> handle;
                    try {
                        handle = delegate().handle(ctx, request, responseFactory);
                    } catch (Throwable cause) {
                        return succeeded(convertToGrpcErrorResponse(ctx, responseFactory, cause));
                    }
                    return handle.onErrorReturn(cause -> convertToGrpcErrorResponse(ctx, responseFactory, cause));
                }
            };
        }

        private static StreamingHttpResponse convertToGrpcErrorResponse(
                final HttpServiceContext ctx, final StreamingHttpResponseFactory responseFactory,
                final Throwable cause) {
            return newErrorResponse(responseFactory, GRPC_CONTENT_TYPE, cause,
                    ctx.executionContext().bufferAllocator());
        }

        @Override
        public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
            return strategy;    // no influence since we do not block
        }
    }
}
