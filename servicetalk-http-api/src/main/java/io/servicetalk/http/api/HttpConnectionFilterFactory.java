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

import static java.util.Objects.requireNonNull;

/**
 * A factory for {@link StreamingHttpConnectionFilter}.
 */
@FunctionalInterface
public interface HttpConnectionFilterFactory {

    /**
     * Create a {@link StreamingHttpConnectionFilter} using the provided {@link StreamingHttpConnection}.
     *
     * @param connection {@link StreamingHttpConnection} to filter
     * @return {@link StreamingHttpConnectionFilter} using the provided {@link StreamingHttpConnection}.
     */
    StreamingHttpConnectionFilter create(StreamingHttpConnection connection);

    /**
     * Returns a composed function that first applies the {@code before} function to its input, and then applies
     * this function to the result.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     filter1.append(filter2).append(filter3)
     * </pre>
     * making a request to a connection wrapped by this filter chain the order of invocation of these filters will
     * be:
     * <pre>
     *     filter1 =&gt; filter2 =&gt; filter3 =&gt; connection
     * </pre>
     * @param before the function to apply before this function is applied
     * @return a composed function that first applies the {@code before}
     * function and then applies this function
     */
    default HttpConnectionFilterFactory append(HttpConnectionFilterFactory before) {
        requireNonNull(before);
        return connection -> create(before.create(connection));
    }

    /**
     * Returns a function that always returns its input {@link StreamingHttpConnection}.
     *
     * @return a function that always returns its input {@link StreamingHttpConnection}.
     */
    static HttpConnectionFilterFactory identity() {
        return connection -> new StreamingHttpConnectionFilter(connection) {
            @Override
            protected HttpExecutionStrategy mergeForEffectiveStrategy(final HttpExecutionStrategy mergeWith) {
                // Since this filter does not have any blocking code, we do not need to alter the effective strategy.
                return mergeWith;
            }
        };
    }
}
