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
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;

import java.util.function.BiPredicate;

import static java.util.Objects.requireNonNull;

/**
 * A {@link StreamingHttpServiceFilter} that can conditionally apply another {@link StreamingHttpServiceFilter}.
 */
public final class ConditionalHttpServiceFilter extends StreamingHttpServiceFilter {
    private final BiPredicate<HttpServiceContext, StreamingHttpRequest> predicate;
    private final StreamingHttpServiceFilter filter;

    /**
     * Create a new instance.
     *
     * @param predicate the {@link BiPredicate} used to test if the provided {@code filter} applies
     * @param filter the {@link StreamingHttpServiceFilter} to conditionally apply
     * @param service the {@link StreamingHttpService} to call if the provided {@code filter} doesn't apply
     */
    public ConditionalHttpServiceFilter(final BiPredicate<HttpServiceContext, StreamingHttpRequest> predicate,
                                        final StreamingHttpServiceFilter filter,
                                        final StreamingHttpService service) {
        super(service);
        this.predicate = requireNonNull(predicate);
        this.filter = requireNonNull(filter);
    }

    @Override
    public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                final StreamingHttpRequest req,
                                                final StreamingHttpResponseFactory resFactory) {
        if (predicate.test(ctx, req)) {
            return filter.handle(ctx, req, resFactory);
        }
        return super.handle(ctx, req, resFactory);
    }
}
