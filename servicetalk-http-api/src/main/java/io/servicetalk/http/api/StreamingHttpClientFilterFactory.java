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

import static io.servicetalk.http.api.StrategyInfluencerAwareConversions.toMultiAddressClientFactory;

/**
 * A factory for {@link StreamingHttpClientFilter}.
 */
@FunctionalInterface
public interface StreamingHttpClientFilterFactory {

    /**
     * Creates a {@link StreamingHttpClientFilter} using the provided {@link StreamingHttpClientFilter}.
     *
     * @param client {@link FilterableStreamingHttpClient} to filter
     * @return {@link StreamingHttpClientFilter} using the provided {@link StreamingHttpClientFilter}.
     */
    StreamingHttpClientFilter create(FilterableStreamingHttpClient client);

    /**
     * Returns a {@link MultiAddressHttpClientFilterFactory} that adapts from a
     * {@link StreamingHttpClientFilterFactory}.
     *
     * @param <U> the type of address before resolution (unresolved address).
     * @return a {@link MultiAddressHttpClientFilterFactory} function
     */
    default <U> MultiAddressHttpClientFilterFactory<U> asMultiAddressClientFilter() {
        return toMultiAddressClientFactory(this);
    }
}
