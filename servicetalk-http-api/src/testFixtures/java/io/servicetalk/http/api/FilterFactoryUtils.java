/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
 * Utilities for filter factories.
 */
public final class FilterFactoryUtils {

    private FilterFactoryUtils() {
        // No instances
    }

    /**
     * Returns a composed function of two sequential
     * {@link StreamingHttpClientFilterFactory client filter factories}.
     * <p>
     * The order of execution of these filters are in order of append. If 2 filters are appended as follows:
     * <pre>
     *     StreamingHttpClientFilterFactory result = appendClientFilterFactory(filter1, filter2);
     * </pre>
     * making a request to a client wrapped by this filter chain the order of invocation of these filters will
     * be:
     * <pre>
     *     filter1 =&gt; filter2 =&gt; client
     * </pre>
     *
     * @param first the factory for the first filter in execution chain
     * @param second the factory for the second filter in execution chain
     * @return a composed function of two sequential filters
     */
    public static StreamingHttpClientFilterFactory appendClientFilterFactory(
            StreamingHttpClientFilterFactory first, StreamingHttpClientFilterFactory second) {
        requireNonNull(first);
        requireNonNull(second);
        return connection -> first.create(second.create(connection));
    }

    /**
     * Returns a composed function of two sequential
     * {@link StreamingHttpConnectionFilterFactory connection filter factories}.
     * <p>
     * The order of execution of these filters are in order of append. If 2 filters are appended as follows:
     * <pre>
     *     StreamingHttpConnectionFilterFactory result = appendConnectionFilterFactory(filter1, filter2);
     * </pre>
     * making a request to a connection wrapped by this filter chain the order of invocation of these filters will
     * be:
     * <pre>
     *     filter1 =&gt; filter2 =&gt; connection
     * </pre>
     *
     * @param first the factory for the first filter in execution chain
     * @param second the factory for the second filter in execution chain
     * @return a composed function of two sequential filters
     */
    public static StreamingHttpConnectionFilterFactory appendConnectionFilterFactory(
            StreamingHttpConnectionFilterFactory first, StreamingHttpConnectionFilterFactory second) {
        requireNonNull(first);
        requireNonNull(second);
        return connection -> first.create(second.create(connection));
    }

    /**
     * Returns a composed function of two sequential
     * {@link StreamingHttpServiceFilterFactory service filter factories}.
     * <p>
     * The order of execution of these filters are in order of append. If 2 filters are appended as follows:
     * <pre>
     *     StreamingHttpServiceFilterFactory result = appendServiceFilterFactory(filter1, filter2);
     * </pre>
     * making a request to a client wrapped by this filter chain the order of invocation of these filters will
     * be:
     * <pre>
     *     filter1 =&gt; filter2 =&gt; client
     * </pre>
     *
     * @param first the factory for the first filter in execution chain
     * @param second the factory for the second filter in execution chain
     * @return a composed function of two sequential filters
     */
    public static StreamingHttpServiceFilterFactory appendServiceFilterFactory(
            StreamingHttpServiceFilterFactory first, StreamingHttpServiceFilterFactory second) {
        requireNonNull(first);
        requireNonNull(second);
        return connection -> first.create(second.create(connection));
    }
}
