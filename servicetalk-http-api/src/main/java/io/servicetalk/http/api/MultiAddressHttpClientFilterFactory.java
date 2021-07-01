/*
 * Copyright © 2018-2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.GroupKey;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static io.servicetalk.http.api.StrategyInfluencerAwareConversions.toClientFactory;
import static java.util.Objects.requireNonNull;

/**
 * A factory for {@link StreamingHttpClientFilter} to filter clients for different unresolved addresses.
 *
 * @param <U> the type of address before resolution (unresolved address).
 * @see StreamingHttpClientFilterFactory
 */
@FunctionalInterface
public interface MultiAddressHttpClientFilterFactory<U> {
    /**
     * Create a {@link StreamingHttpClientFilter} for the passed {@code address} using the provided
     * {@link FilterableStreamingHttpClient}.
     *
     * @param address the {@code UnresolvedAddress} for the {@link FilterableStreamingHttpClient}
     * @param client the {@link FilterableStreamingHttpClient} to filter
     * @return the filtered {@link FilterableStreamingHttpClient}
     */
    StreamingHttpClientFilter create(U address, FilterableStreamingHttpClient client);

    /**
     * Returns a {@link StreamingHttpClientFilterFactory} that adapts from a
     * {@link MultiAddressHttpClientFilterFactory}.
     *
     * @param address will be passed in all {@link StreamingHttpClientFilterFactory} applications
     * @return a {@link StreamingHttpClientFilterFactory} function with a provided {@link GroupKey}
     */
    default StreamingHttpClientFilterFactory asClientFilter(U address) {
        return toClientFactory(address, this);
    }

    /**
     * Returns a function that adapts from the {@link UnaryOperator}&lt;{@link FilterableStreamingHttpClient}&gt;
     * function type to the {@link MultiAddressHttpClientFilterFactory}.
     *
     * @param function the function that is applied to the input {@link GroupKey} and
     * {@link FilterableStreamingHttpClient}
     * @param <U> the type of address before resolution (unresolved address)
     * @return the resulting {@link MultiAddressHttpClientFilterFactory}
     */
    static <U> MultiAddressHttpClientFilterFactory<U> from(
            BiFunction<U, FilterableStreamingHttpClient, StreamingHttpClientFilter> function) {
        return function::apply;
    }

    /**
     * Returns a function that adapts from a {@link Function}&lt;{@link FilterableStreamingHttpClient},
     * {@link StreamingHttpClientFilter}&gt; to the {@link StreamingHttpClientFilterFactory}.
     *
     * @param function the function that is applied to the original {@link FilterableStreamingHttpClient}
     * @param <U> the type of address before resolution (unresolved address)
     * @return A {@link StreamingHttpClientFilterFactory} that uses the passed filter {@link Function}.
     */
    static <U> MultiAddressHttpClientFilterFactory<U> from(
            Function<FilterableStreamingHttpClient, StreamingHttpClientFilter> function) {
        requireNonNull(function);
        return (__, client) -> function.apply(client);
    }
}
