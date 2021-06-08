/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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
 * A factory for {@link StreamingHttpServiceFilter}.
 */
@FunctionalInterface
public interface StreamingHttpServiceFilterFactory {

    /**
     * Create a {@link StreamingHttpServiceFilter} using the provided {@link StreamingHttpService}.
     *
     * @param service {@link StreamingHttpService} to filter
     * @return {@link StreamingHttpServiceFilter} using the provided {@link StreamingHttpService}.
     */
    StreamingHttpServiceFilter create(StreamingHttpService service);

    /**
     * Returns a composed function that first applies the {@code before} function to its input, and then applies
     * this function to the result.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.append(filter1).append(filter2).append(filter3)
     * </pre>
     * accepting a request by a service wrapped by this filter chain, the order of invocation of these filters will be:
     * <pre>
     *     filter1 =&gt; filter2 =&gt; filter3 =&gt; service
     * </pre>
     *
     * @deprecated Use {@link HttpServerBuilder#appendServiceFilter(StreamingHttpServiceFilterFactory)}
     * @param before the function to apply before this function is applied
     * @return a composed function that first applies the {@code before}
     * function and then applies this function
     */
    @Deprecated
    default StreamingHttpServiceFilterFactory append(StreamingHttpServiceFilterFactory before) {
        requireNonNull(before);
        return service -> create(before.create(service));
    }
}
