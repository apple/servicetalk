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
package io.servicetalk.http.api;

import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.concurrent.api.Publisher;

import static java.util.Objects.requireNonNull;

/**
 * A factory for {@link StreamingHttpClientFilter}.
 */
@FunctionalInterface
public interface HttpClientFilterFactory {

    /**
     * Creates a {@link StreamingHttpClientFilter} using the provided {@link StreamingHttpClientFilter}.
     *
     * @param client {@link FilterableStreamingHttpClient} to filter
     * @param lbEvents the {@link LoadBalancer} events stream
     * @return {@link StreamingHttpClientFilter} using the provided {@link StreamingHttpClientFilter}.
     */
    StreamingHttpClientFilter create(FilterableStreamingHttpClient client, Publisher<Object> lbEvents);

    /**
     * Returns a composed function that first applies the {@code before} function to its input, and then applies
     * this function to the result.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     filter1.append(filter2).append(filter3)
     * </pre>
     * making a request to a client wrapped by this filter chain the order of invocation of these filters will be:
     * <pre>
     *     filter1 =&gt; filter2 =&gt; filter3 =&gt; client
     * </pre>
     * @param before the function to apply before this function is applied
     * @return a composed function that first applies the {@code before}
     * function and then applies this function
     */
    default HttpClientFilterFactory append(HttpClientFilterFactory before) {
        requireNonNull(before);
        return (client, lbEvents) -> create(before.create(client, lbEvents), lbEvents);
    }

    /**
     * Returns a {@link MultiAddressHttpClientFilterFactory} that adapts from a {@link HttpClientFilterFactory}.
     *
     * @param <U> the type of address before resolution (unresolved address).
     * @return a {@link MultiAddressHttpClientFilterFactory} function
     */
    default <U> MultiAddressHttpClientFilterFactory<U> asMultiAddressClientFilter() {
        return (address, client, lbEvents) -> new StreamingHttpClientFilter(create(client, lbEvents)) {
            @Override
            public HttpExecutionStrategy computeExecutionStrategy(HttpExecutionStrategy other) {
                // Since this filter does not have any blocking code, we do not need to alter the effective strategy.
                return delegate().computeExecutionStrategy(other);
            }
        };
    }
}
