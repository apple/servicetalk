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
package io.servicetalk.http.netty;

import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;

import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;

final class StrategyInfluencerAwareConversions {

    private StrategyInfluencerAwareConversions() {
        // No instances.
    }

    static StreamingHttpServiceFilterFactory toConditionalServiceFilterFactory(
            final Predicate<StreamingHttpRequest> predicate, final StreamingHttpServiceFilterFactory original) {
        requireNonNull(predicate);
        requireNonNull(original);

        return new StreamingHttpServiceFilterFactory() {
            @Override
            public StreamingHttpServiceFilter create(final StreamingHttpService service) {
                return new ConditionalHttpServiceFilter(predicate, original.create(service), service);
            }

            @Override
            public HttpExecutionStrategy requiredOffloads() {
                return original.requiredOffloads();
            }
        };
    }

    static StreamingHttpConnectionFilterFactory toConditionalConnectionFilterFactory(
            final Predicate<StreamingHttpRequest> predicate, final StreamingHttpConnectionFilterFactory original) {
        requireNonNull(predicate);
        requireNonNull(original);

        return new StreamingHttpConnectionFilterFactory() {

            @Override
            public HttpExecutionStrategy requiredOffloads() {
                return original.requiredOffloads();
            }

            @Override
            public StreamingHttpConnectionFilter create(final FilterableStreamingHttpConnection connection) {
                return new ConditionalHttpConnectionFilter(predicate, original.create(connection), connection);
            }
        };
    }

    static StreamingHttpClientFilterFactory toConditionalClientFilterFactory(
            final Predicate<StreamingHttpRequest> predicate, final StreamingHttpClientFilterFactory original) {
        requireNonNull(predicate);
        requireNonNull(original);

        return new StreamingHttpClientFilterFactory() {
            @Override
            public HttpExecutionStrategy requiredOffloads() {
                return original.requiredOffloads();
            }

            @Override
            public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
                return new ConditionalHttpClientFilter(predicate, original.create(client), client);
            }
        };
    }
}
