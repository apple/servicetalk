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

import java.time.Duration;

/**
 * A builder for building a <a href="https://www.grpc.io">gRPC</a> client.
 *
 * @param <U> the type of address before resolution (unresolved address)
 * @param <R> the type of address after resolution (resolved address)
 */
public abstract class GrpcClientBuilder<U, R> {

    /**
     * Initializes the underlying {@link SingleAddressHttpClientBuilder} used for the transport layer.
     * @param <U> unresolved address
     * @param <R> resolved address
     */
    @FunctionalInterface
    public interface HttpInitializer<U, R> {

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
    public GrpcClientBuilder<U, R> initializeHttp(HttpInitializer<U, R> initializer) {
        throw new UnsupportedOperationException("Initializing the GrpcClientBuilder using this method is not yet" +
                " supported by " + getClass().getName());
    }

    /**
     * Set default timeout during which gRPC calls are expected to complete. This default will be used only if the
     * request metadata includes no timeout; any value specified in client request will supersede this default.
     *
     * @param defaultTimeout {@link Duration} of default timeout which must be positive non-zero.
     * @return {@code this}.
     */
    public abstract GrpcClientBuilder<U, R> defaultTimeout(Duration defaultTimeout);

    /**
     * Builds a <a href="https://www.grpc.io">gRPC</a> client.
     *
     * @param clientFactory {@link GrpcClientFactory} to use.
     * @param <Client> <a href="https://www.grpc.io">gRPC</a> service that any client built from
     * this factory represents.
     * @param <Filter> Type for client filter
     * @param <FilterableClient> Type of filterable client.
     * @param <FilterFactory> Type of {@link GrpcClientFilterFactory}
     *
     * @return A <a href="https://www.grpc.io">gRPC</a> client.
     */
    public final <Client extends GrpcClient<?>,
            Filter extends FilterableClient, FilterableClient extends FilterableGrpcClient,
            FilterFactory extends GrpcClientFilterFactory<Filter, FilterableClient>> Client
    build(GrpcClientFactory<Client, ?, Filter, FilterableClient, FilterFactory> clientFactory) {
        return clientFactory.newClientForCallFactory(newGrpcClientCallFactory());
    }

    /**
     * Builds a blocking <a href="https://www.grpc.io">gRPC</a> client.
     *
     * @param clientFactory {@link GrpcClientFactory} to use.
     * @param <BlockingClient> Blocking <a href="https://www.grpc.io">gRPC</a> service that any
     * client built from this builder represents.
     * @param <Filter> Type for client filter
     * @param <FilterableClient> Type of filterable client.
     * @param <FilterFactory> Type of {@link GrpcClientFilterFactory}
     *
     * @return A blocking <a href="https://www.grpc.io">gRPC</a> client.
     */
    public final <BlockingClient extends BlockingGrpcClient<?>,
            Filter extends FilterableClient, FilterableClient extends FilterableGrpcClient,
            FilterFactory extends GrpcClientFilterFactory<Filter, FilterableClient>> BlockingClient
    buildBlocking(GrpcClientFactory<?, BlockingClient, Filter, FilterableClient, FilterFactory> clientFactory) {
        return clientFactory.newBlockingClientForCallFactory(newGrpcClientCallFactory());
    }

    /**
     * Create a new {@link GrpcClientCallFactory}.
     *
     * @return A new {@link GrpcClientCallFactory}.
     */
    protected abstract GrpcClientCallFactory newGrpcClientCallFactory();
}
