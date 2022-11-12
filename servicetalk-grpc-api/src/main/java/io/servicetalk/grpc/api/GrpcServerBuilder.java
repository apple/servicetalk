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
import io.servicetalk.http.api.HttpLifecycleObserver;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;

import java.time.Duration;

/**
 * A builder for building a <a href="https://www.grpc.io">gRPC</a> server.
 */
public interface GrpcServerBuilder {

    /**
     * Initializes the underlying {@link HttpServerBuilder} used for the transport layer.
     */
    @FunctionalInterface
    interface HttpInitializer {

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
    GrpcServerBuilder initializeHttp(HttpInitializer initializer);

    /**
     * Set a default timeout during which gRPC calls are expected to complete. This default will be used only if the
     * request includes no timeout; any value specified in client request will supersede this default.
     *
     * @param defaultTimeout {@link Duration} of default timeout which must be positive non-zero.
     * @return {@code this}.
     */
    GrpcServerBuilder defaultTimeout(Duration defaultTimeout);

    /**
     * Determine if a filter will be inserted by this builder that enforces the
     * <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md#requests">Timeout deadline
     * propagation</a>.
     * <p>
     * To insert {@link GrpcFilters#newGrpcDeadlineServerFilterFactory(Duration)} in your preferred order use
     * {@link #initializeHttp} and
     * {@link HttpServerBuilder#appendNonOffloadingServiceFilter(StreamingHttpServiceFilterFactory)} (to force ordering
     * before any offloading filters) or
     * {@link HttpServerBuilder#appendServiceFilter(StreamingHttpServiceFilterFactory)} (if you require different
     * ordering).
     * <p>
     * {@link #defaultTimeout(Duration)} may be ignored if {@code append} is false.
     * @param append {@code true} if this builder should append the timeout filter, {@code false} if it should not.
     * @return {@code this}.
     * @see GrpcFilters#newGrpcDeadlineServerFilterFactory(Duration)
     */
    default GrpcServerBuilder appendTimeoutFilter(boolean append) {
        // FIXME: 0.43 - remove default implementation
        throw new UnsupportedOperationException(
                "GrpcServerBuilder#appendTimeoutFilter(boolean) is not supported by " + getClass());
    }

    /**
     * Sets a {@link GrpcLifecycleObserver} that provides visibility into gRPC lifecycle events.
     * <p>
     * Note, if {@link #initializeHttp(HttpInitializer)} is used to configure
     * {@link HttpServerBuilder#lifecycleObserver(HttpLifecycleObserver)} – that will override the value specified
     * using this method. Please choose only one approach.
     * @param lifecycleObserver A {@link GrpcLifecycleObserver} that provides visibility into gRPC lifecycle events.
     * @return {@code this}.
     */
    GrpcServerBuilder lifecycleObserver(GrpcLifecycleObserver lifecycleObserver);

    /**
     * Starts this server and returns the {@link GrpcServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param services {@link GrpcBindableService}(s) to create a <a href="https://www.grpc.io">gRPC</a> service.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    Single<GrpcServerContext> listen(GrpcBindableService<?>... services);

    /**
     * Starts this server and returns the {@link GrpcServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param serviceFactories {@link GrpcServiceFactory}(s) to create a <a href="https://www.grpc.io">gRPC</a> service.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    Single<GrpcServerContext> listen(GrpcServiceFactory<?>... serviceFactories);

    /**
     * Starts this server and returns the {@link GrpcServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param serviceFactories {@link GrpcServiceFactory}(s) to create a <a href="https://www.grpc.io">gRPC</a> service.
     * @return A {@link GrpcServerContext} by blocking the calling thread until the server is successfully started or
     * throws an {@link Exception} if the server could not be started.
     * @throws Exception if the server could not be started.
     */
    GrpcServerContext listenAndAwait(GrpcServiceFactory<?>... serviceFactories) throws Exception;

     /**
      * Starts this server and returns the {@link GrpcServerContext} after the server has been successfully started.
      * <p>
      * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
      *
      * @param services {@link GrpcBindableService}(s) to create a <a href="https://www.grpc.io">gRPC</a> service.
      * @return A {@link GrpcServerContext} by blocking the calling thread until the server is successfully started or
      * throws an {@link Exception} if the server could not be started.
      * @throws Exception if the server could not be started.
      */
     GrpcServerContext listenAndAwait(GrpcBindableService<?>... services) throws Exception;
}
