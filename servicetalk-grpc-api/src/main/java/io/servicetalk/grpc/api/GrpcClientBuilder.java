/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;

import java.time.Duration;
import javax.annotation.Nullable;

/**
 * A builder for building a <a href="https://www.grpc.io">gRPC</a> client.
 *
 * @param <U> the type of address before resolution (unresolved address)
 * @param <R> the type of address after resolution (resolved address)
 */
public interface GrpcClientBuilder<U, R> {

    /**
     * Initializes the underlying {@link SingleAddressHttpClientBuilder} used for the transport layer.
     * @param <U> unresolved address
     * @param <R> resolved address
     */
    @FunctionalInterface
    interface HttpInitializer<U, R> {

        /**
         * Configures the underlying {@link SingleAddressHttpClientBuilder}.
         * @param builder The builder to customize the HTTP layer.
         */
        void initialize(SingleAddressHttpClientBuilder<U, R> builder);

        /**
         * Appends the passed {@link HttpInitializer} to this {@link HttpInitializer} such that this instance is
         * applied first and then the argument's {@link HttpInitializer}.
         * @param toAppend {@link HttpInitializer} to append.
         * @return A composite {@link HttpInitializer} after the append operation.
         */
        default HttpInitializer<U, R> append(HttpInitializer<U, R> toAppend) {
            return builder -> {
                initialize(builder);
                toAppend.initialize(builder);
            };
        }
    }

    /**
     * Set a function which can configure the underlying {@link SingleAddressHttpClientBuilder} used for
     * the transport layer.
     * @param initializer Initializes the underlying HTTP transport builder.
     * @return {@code this}.
     */
    GrpcClientBuilder<U, R> initializeHttp(HttpInitializer<U, R> initializer);

    /**
     * Set default timeout during which gRPC calls are expected to complete. This default will be used only if the
     * request metadata includes no timeout; any value specified in client request will supersede this default.
     *
     * @param defaultTimeout {@link Duration} of default timeout which must be positive non-zero.
     * @return {@code this}.
     * @see #defaultTimeout(Duration, boolean)
     */
    GrpcClientBuilder<U, R> defaultTimeout(Duration defaultTimeout);

    /**
     * Set default timeout during which gRPC calls are expected to complete. This default will be used only if the
     * request metadata includes no timeout; any value specified in client request will supersede this default.
     *
     * @param defaultTimeout {@link Duration} of default timeout which must be positive non-zero, or {@code null} if a
     * default shouldn't be applied.
     * @param appendTimeoutFilter {@code true} to append the filter that enforces
     * <a href="https://grpc.io/blog/deadlines">deadline propagation</a>. {@code false} to not append the filter and
     * therefore not enforce deadlines. If {@code false} you can manually insert
     * {@link GrpcFilters#newGrpcDeadlineClientFilterFactory()} in your preferred order use {@link #initializeHttp} and
     * {@link SingleAddressHttpClientBuilder#appendClientFilter(StreamingHttpClientFilterFactory)}.
     * @return {@code this}.
     * @see GrpcFilters#newGrpcDeadlineClientFilterFactory()
     */
    default GrpcClientBuilder<U, R> defaultTimeout(@Nullable Duration defaultTimeout, boolean appendTimeoutFilter) {
        // FIXME: 0.43 - remove default implementation
        throw new UnsupportedOperationException("method not supported by " + getClass());
    }

    /**
     * Builds a <a href="https://www.grpc.io">gRPC</a> client.
     *
     * @param clientFactory {@link GrpcClientFactory} to use.
     * @param <Client> <a href="https://www.grpc.io">gRPC</a> service that any client built from
     * this factory represents.
     *
     * @return A <a href="https://www.grpc.io">gRPC</a> client.
     */
    <Client extends GrpcClient<?>> Client build(GrpcClientFactory<Client, ?> clientFactory);

    /**
     * Builds a blocking <a href="https://www.grpc.io">gRPC</a> client.
     *
     * @param clientFactory {@link GrpcClientFactory} to use.
     * @param <BlockingClient> Blocking <a href="https://www.grpc.io">gRPC</a> service that any
     * client built from this builder represents.
     *
     * @return A blocking <a href="https://www.grpc.io">gRPC</a> client.
     */
    <BlockingClient extends BlockingGrpcClient<?>> BlockingClient buildBlocking(
            GrpcClientFactory<?, BlockingClient> clientFactory);

    /**
     * Returns a {@link MultiClientBuilder} to be used to create multiple clients that share the underlying transport.
     * It is meant for a single backend that hosts different service APIs.
     *
     * @return A {@link MultiClientBuilder builder} that allows reusing underlying transport between
     * <a href="https://www.grpc.io">gRPC</a> clients.
     */
    MultiClientBuilder buildMulti();

    /**
     * An interface to create multiple <a href="https://www.grpc.io">gRPC</a> clients that share the underlying
     * transport. It is meant for a single backend that hosts different service APIs.
     */
    interface MultiClientBuilder {

        /**
         * Builds a <a href="https://www.grpc.io">gRPC</a> client.
         *
         * @param clientFactory {@link GrpcClientFactory} to use.
         * @param <Client> <a href="https://www.grpc.io">gRPC</a> service that any client built
         * from this factory represents.
         *
         * @return A <a href="https://www.grpc.io">gRPC</a> client.
         */
        <Client extends GrpcClient<?>> Client build(GrpcClientFactory<Client, ?> clientFactory);

        /**
         * Builds a blocking <a href="https://www.grpc.io">gRPC</a> client.
         *
         * @param clientFactory {@link GrpcClientFactory} to use.
         * @param <BlockingClient> Blocking <a href="https://www.grpc.io">gRPC</a> service that
         * any client built from this builder represents.
         *
         * @return A blocking <a href="https://www.grpc.io">gRPC</a> client.
         */
        <BlockingClient extends BlockingGrpcClient<?>> BlockingClient buildBlocking(
                GrpcClientFactory<?, BlockingClient> clientFactory);
    }
}
