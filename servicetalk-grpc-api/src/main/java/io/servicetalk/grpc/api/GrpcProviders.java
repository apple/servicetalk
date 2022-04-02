/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.HttpProviders;
import io.servicetalk.http.api.HttpProviders.HttpServerBuilderProvider;
import io.servicetalk.http.api.HttpProviders.SingleAddressHttpClientBuilderProvider;

import java.net.SocketAddress;
import java.util.ServiceLoader;

/**
 * A holder for all gRPC-specific providers that can be registered using {@link ServiceLoader}.
 *
 * @see HttpProviders
 */
public final class GrpcProviders {

    private GrpcProviders() {
        // No instances.
    }

    /**
     * Provider for {@link GrpcClientBuilder}.
     * <p>
     * An HTTP layer should use {@link SingleAddressHttpClientBuilderProvider}.
     */
    @FunctionalInterface
    public interface GrpcClientBuilderProvider {

        /**
         * Returns a {@link GrpcClientBuilder} based on the address and pre-initialized {@link GrpcClientBuilder}.
         * <p>
         * This method may return the pre-initialized {@code builder} as-is, or apply custom builder settings before
         * returning it, or wrap it ({@link DelegatingGrpcClientBuilder} may be helpful).
         *
         * @param address a remote address used to create a {@link GrpcClientBuilder}, it can be resolved or unresolved
         * based on the factory used
         * @param builder pre-initialized {@link GrpcClientBuilder}
         * @param <U> the type of address before resolution (unresolved address)
         * @param <R> the type of address after resolution (resolved address)
         * @return a {@link GrpcClientBuilder} based on the address and pre-initialized {@link GrpcClientBuilder}.
         * @see DelegatingGrpcClientBuilder
         */
        <U, R> GrpcClientBuilder<U, R> newBuilder(U address, GrpcClientBuilder<U, R> builder);
    }

    /**
     * Provider for {@link GrpcServerBuilder}.
     * <p>
     * An HTTP layer should use {@link HttpServerBuilderProvider}.
     */
    @FunctionalInterface
    public interface GrpcServerBuilderProvider {

        /**
         * Returns a {@link GrpcServerBuilder} based on the address and pre-initialized {@link GrpcServerBuilder}.
         * <p>
         * This method may return the pre-initialized {@code builder} as-is, or apply custom builder settings before
         * returning it, or wrap it ({@link DelegatingGrpcServerBuilder} may be helpful).
         *
         * @param address a server address used to create a {@link GrpcServerBuilder}
         * @param builder pre-initialized {@link GrpcServerBuilder}
         * @return a {@link GrpcServerBuilder} based on the address and pre-initialized{@link GrpcServerBuilder}.
         * @see DelegatingGrpcServerBuilder
         */
        GrpcServerBuilder newBuilder(SocketAddress address, GrpcServerBuilder builder);
    }
}
