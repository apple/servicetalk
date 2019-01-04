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

import io.servicetalk.concurrent.api.Single;

import java.util.function.Predicate;

import static io.servicetalk.concurrent.api.Single.error;

final class ConditionalHttpConnectionFilter extends StreamingHttpConnectionFilter {
    private final Predicate<StreamingHttpRequest> predicate;
    private final StreamingHttpConnectionFilter predicatedFilter;

    ConditionalHttpConnectionFilter(final Predicate<StreamingHttpRequest> predicate,
                                    final StreamingHttpConnectionFilter predicatedFilter,
                                    final StreamingHttpConnection connection) {
        super(connection);
        this.predicate = predicate;
        this.predicatedFilter = predicatedFilter;
    }

    @Override
    public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy, final StreamingHttpRequest req) {
        return new Single<StreamingHttpResponse>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super StreamingHttpResponse> subscriber) {
                predicatedRequest(strategy, req).subscribe(subscriber);
            }
        };
    }

    private Single<StreamingHttpResponse> predicatedRequest(final HttpExecutionStrategy strategy,
                                                            final StreamingHttpRequest req) {
        boolean b;
        try {
            b = predicate.test(req);
        } catch (Throwable t) {
            return error(new RuntimeException("Unexpected predicate failure", t));
        }

        if (b) {
            return predicatedFilter.request(strategy, req);
        }

        return delegate().request(strategy, req);
    }
}
