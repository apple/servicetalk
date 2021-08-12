/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;

final class StrategyInfluencerAwareConversions {

    private StrategyInfluencerAwareConversions() {
        // No instances.
    }

    @Deprecated
    static <U> StreamingHttpClientFilterFactory toClientFactory(final U address,
                                                                final MultiAddressHttpClientFilterFactory<U> original) {
        requireNonNull(address);
        return new StrategyInfluencingStreamingClientFilterFactory() {
            @Override
            public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
                return HttpExecutionStrategyInfluencer.applyInfluence(original, strategy);
            }

            @Override
            public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
                return original.create(address, client);
            }
        };
    }

    @Deprecated
    static <U> MultiAddressHttpClientFilterFactory<U> toMultiAddressClientFactory(
            final StreamingHttpClientFilterFactory original) {
        return new StrategyInfluencingMultiAddressHttpClientFilterFactory<U>() {
            @Override
            public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
                return HttpExecutionStrategyInfluencer.applyInfluence(original, strategy);
            }

            @Override
            public StreamingHttpClientFilter create(final U address, final FilterableStreamingHttpClient client) {
                return new StreamingHttpClientFilter(original.create(client));
            }
        };
    }

    static StreamingHttpServiceFilterFactory toConditionalServiceFilterFactory(
            final Predicate<StreamingHttpRequest> predicate, final StreamingHttpServiceFilterFactory original) {
        requireNonNull(predicate);
        requireNonNull(original);

        return new StrategyInfluencingStreamingServiceFilterFactory() {
            @Override
            public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
                return HttpExecutionStrategyInfluencer.applyInfluence(predicate,
                        HttpExecutionStrategyInfluencer.applyInfluence(original, strategy));
            }

            @Override
            public StreamingHttpServiceFilter create(final StreamingHttpService service) {
                return new ConditionalHttpServiceFilter(predicate, original.create(service), service);
            }
        };
    }

    static StreamingHttpConnectionFilterFactory toConditionalConnectionFilterFactory(
            final Predicate<StreamingHttpRequest> predicate, final StreamingHttpConnectionFilterFactory original) {
        requireNonNull(predicate);
        requireNonNull(original);

        return new StrategyInfluencingStreamingConnectionFilterFactory() {
            @Override
            public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
                return HttpExecutionStrategyInfluencer.applyInfluence(predicate,
                        HttpExecutionStrategyInfluencer.applyInfluence(original, strategy));
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

        return new StrategyInfluencingStreamingClientFilterFactory() {
            @Override
            public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
                return HttpExecutionStrategyInfluencer.applyInfluence(predicate,
                        HttpExecutionStrategyInfluencer.applyInfluence(original, strategy));
            }

            @Override
            public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
                return new ConditionalHttpClientFilter(predicate, original.create(client), client);
            }
        };
    }

    @Deprecated
    static <U> MultiAddressHttpClientFilterFactory<U> toMultiAddressConditionalFilterFactory(
            final Predicate<StreamingHttpRequest> predicate,
            final MultiAddressHttpClientFilterFactory<U> original) {
        requireNonNull(predicate);
        requireNonNull(original);

        return new StrategyInfluencingMultiAddressHttpClientFilterFactory<U>() {
            @Override
            public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
                return HttpExecutionStrategyInfluencer.applyInfluence(predicate,
                        HttpExecutionStrategyInfluencer.applyInfluence(original, strategy));
            }

            @Override
            public StreamingHttpClientFilter create(final U address,
                                                    final FilterableStreamingHttpClient client) {
                return new ConditionalHttpClientFilter(predicate, original.create(address, client), client);
            }
        };
    }

    interface StrategyInfluencingStreamingServiceFilterFactory
            extends StreamingHttpServiceFilterFactory, HttpExecutionStrategyInfluencer {
    }

    interface StrategyInfluencingStreamingConnectionFilterFactory
            extends StreamingHttpConnectionFilterFactory, HttpExecutionStrategyInfluencer {
    }

    interface StrategyInfluencingStreamingClientFilterFactory
            extends StreamingHttpClientFilterFactory, HttpExecutionStrategyInfluencer {
    }

    @Deprecated
    interface StrategyInfluencingMultiAddressHttpClientFilterFactory<U>
            extends MultiAddressHttpClientFilterFactory<U>, HttpExecutionStrategyInfluencer {
    }
}
