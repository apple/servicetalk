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
package io.servicetalk.http.utils.filter;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;

import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;

/**
 * A {@link StreamingHttpClientFilter} that can conditionally apply another {@link StreamingHttpClientFilter}.
 */
public final class ConditionalHttpClientFilter extends StreamingHttpClientFilter {
    private final Predicate<StreamingHttpRequest> predicate;
    private final StreamingHttpClientFilter filter;

    /**
     * Create a new instance.
     *
     * @param predicate the {@link Predicate} used to test if the provided {@code filter} applies
     * @param filter the {@link StreamingHttpClientFilter} to conditionally apply
     * @param client the {@link StreamingHttpClient} to call if the provided {@code filter} doesn't apply
     */
    public ConditionalHttpClientFilter(final Predicate<StreamingHttpRequest> predicate,
                                       final StreamingHttpClientFilter filter,
                                       final StreamingHttpClient client) {
        super(client);
        this.predicate = requireNonNull(predicate);
        this.filter = requireNonNull(filter);
    }

    @Override
    public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy, final StreamingHttpRequest req) {
        if (predicate.test(req)) {
            return filter.request(strategy, req);
        }
        return super.request(strategy, req);
    }
}
