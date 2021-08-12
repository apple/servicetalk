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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.Single;

import java.util.function.Predicate;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;

final class ConditionalHttpServiceFilter extends StreamingHttpServiceFilter {
    private final Predicate<StreamingHttpRequest> predicate;
    private final StreamingHttpServiceFilter predicatedFilter;
    private final CompositeCloseable closeable;

    ConditionalHttpServiceFilter(final Predicate<StreamingHttpRequest> predicate,
                                 final StreamingHttpServiceFilter predicatedFilter,
                                 final StreamingHttpService service) {
        super(service);
        this.predicate = predicate;
        this.predicatedFilter = predicatedFilter;
        closeable = newCompositeCloseable();
        closeable.append(predicatedFilter);
        closeable.append(service);
    }

    @Override
    public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                final StreamingHttpRequest req,
                                                final StreamingHttpResponseFactory resFactory) {
        return predicate.test(req) ?
                predicatedFilter.handle(ctx, req, resFactory) :
                delegate().handle(ctx, req, resFactory);
    }

    @Override
    public Completable closeAsync() {
        return closeable.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return closeable.closeAsyncGracefully();
    }
}
