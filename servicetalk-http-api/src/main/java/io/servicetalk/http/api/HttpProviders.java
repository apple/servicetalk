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
         * <p>
         * This method may return the pre-initialized {@code builder} as-is, or apply custom builder settings before
         * returning it, or wrap it ({@link DelegatingSingleAddressHttpClientBuilder} may be helpful).
         *
         * @param address a remote address used to create a {@link SingleAddressHttpClientBuilder}, it can be resolved
         * or unresolved based on the factory used
         * @param builder pre-initialized {@link SingleAddressHttpClientBuilder}
         * @param <U> the type of address before resolution (unresolved address)
         * @param <R> the type of address after resolution (resolved address)
         * @return a {@link SingleAddressHttpClientBuilder} based on the address and pre-initialized
         * {@link SingleAddressHttpClientBuilder}.
         * @see DelegatingSingleAddressHttpClientBuilder
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
         * <p>
         * This method may return the pre-initialized {@code builder} as-is, or apply custom builder settings before
         * returning it, or wrap it ({@link DelegatingMultiAddressHttpClientBuilder} may be helpful).
         *
         * @param builder pre-initialized {@link MultiAddressHttpClientBuilder}
         * @param <U> the type of address before resolution (unresolved address)
         * @param <R> the type of address after resolution (resolved address)
         * @return a {@link MultiAddressHttpClientBuilder} based on the pre-initialized
         * {@link MultiAddressHttpClientBuilder}.
         * @see DelegatingMultiAddressHttpClientBuilder
         * @deprecated Use {@link #newBuilder(String, MultiAddressHttpClientBuilder)}. To avoid breaking changes, all
         * current implementations must implement both methods. In the next version the default implementation will
         * swap. Then users will be able to keep implementation only for the new method. In the release after, the
         * deprecated method will be removed.
         */
        @Deprecated // FIXME: 0.43 - swap default impl
        <U, R> MultiAddressHttpClientBuilder<U, R> newBuilder(MultiAddressHttpClientBuilder<U, R> builder);

        /**
         * Returns a {@link MultiAddressHttpClientBuilder} based on the pre-initialized
         * {@link MultiAddressHttpClientBuilder}.
         * <p>
         * This method may return the pre-initialized {@code builder} as-is, or apply custom builder settings before
         * returning it, or wrap it ({@link DelegatingMultiAddressHttpClientBuilder} may be helpful).
         *
         * @param id identification of the {@link MultiAddressHttpClientBuilder builder}
         * @param builder pre-initialized {@link MultiAddressHttpClientBuilder}
         * @param <U> the type of address before resolution (unresolved address)
         * @param <R> the type of address after resolution (resolved address)
         * @return a {@link MultiAddressHttpClientBuilder} based on the pre-initialized
         * {@link MultiAddressHttpClientBuilder}.
         * @see DelegatingMultiAddressHttpClientBuilder
         */
        default <U, R> MultiAddressHttpClientBuilder<U, R> newBuilder(String id,
                                                                      MultiAddressHttpClientBuilder<U, R> builder) {
            return newBuilder(builder);
        }
    }

    /**
     * Provider for {@link HttpServerBuilder}.
     */
    @FunctionalInterface
    public interface HttpServerBuilderProvider {

        /**
         * Returns a {@link HttpServerBuilder} based on the address and pre-initialized {@link HttpServerBuilder}.
         * <p>
         * This method may return the pre-initialized {@code builder} as-is, or apply custom builder settings before
         * returning it, or wrap it ({@link DelegatingHttpServerBuilder} may be helpful).
         *
         * @param address a server address used to create a {@link HttpServerBuilder}
         * @param builder pre-initialized {@link HttpServerBuilder}
         * @return a {@link HttpServerBuilder} based on the address and pre-initialized{@link HttpServerBuilder}.
         * @see DelegatingHttpServerBuilder
         */
        HttpServerBuilder newBuilder(SocketAddress address, HttpServerBuilder builder);
    }
}
