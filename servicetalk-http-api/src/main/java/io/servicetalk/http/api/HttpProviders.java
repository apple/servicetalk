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
package io.servicetalk.http.api;

import java.net.SocketAddress;
import java.util.ServiceLoader;

/**
 * A holder for all HTTP-specific providers that can be registered using {@link ServiceLoader}.
 */
public final class HttpProviders {

    private HttpProviders() {
        // No instances.
    }

    /**
     * Provider for {@link SingleAddressHttpClientBuilder}.
     */
    @FunctionalInterface
    public interface SingleAddressHttpClientBuilderProvider {

        /**
         * Returns a {@link SingleAddressHttpClientBuilder} based on the address and pre-initialized
         * {@link SingleAddressHttpClientBuilder}.
         *
         * @param address a remote address used to create a {@link SingleAddressHttpClientBuilder}, it can be resolved
         * or unresolved based on the factory used
         * @param builder pre-initialized {@link SingleAddressHttpClientBuilder}
         * @param <U> the type of address before resolution (unresolved address)
         * @param <R> the type of address after resolution (resolved address)
         * @return a {@link SingleAddressHttpClientBuilder} based on the address and pre-initialized
         * {@link SingleAddressHttpClientBuilder}.
         */
        <U, R> SingleAddressHttpClientBuilder<U, R> newBuilder(U address, SingleAddressHttpClientBuilder<U, R> builder);
    }

    /**
     * Provider for {@link MultiAddressHttpClientBuilder}.
     */
    @FunctionalInterface
    public interface MultiAddressHttpClientBuilderProvider {

        /**
         * Returns a {@link MultiAddressHttpClientBuilder} based on the pre-initialized
         * {@link MultiAddressHttpClientBuilder}.
         *
         * @param builder pre-initialized {@link MultiAddressHttpClientBuilder}
         * @param <U> the type of address before resolution (unresolved address)
         * @param <R> the type of address after resolution (resolved address)
         * @return a {@link MultiAddressHttpClientBuilder} based on the pre-initialized
         * {@link MultiAddressHttpClientBuilder}.
         */
        <U, R> MultiAddressHttpClientBuilder<U, R> newBuilder(MultiAddressHttpClientBuilder<U, R> builder);
    }

    /**
     * Provider for {@link HttpServerBuilder}.
     */
    @FunctionalInterface
    public interface HttpServerBuilderProvider {

        /**
         * Returns a {@link HttpServerBuilder} based on the address and pre-initialized {@link HttpServerBuilder}.
         *
         * @param address a server address used to create a {@link HttpServerBuilder}
         * @param builder pre-initialized {@link HttpServerBuilder}
         * @return a {@link HttpServerBuilder} based on the address and pre-initialized{@link HttpServerBuilder}.
         */
        HttpServerBuilder newBuilder(SocketAddress address, HttpServerBuilder builder);
    }
}
