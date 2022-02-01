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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;

import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.NewToDeprecatedFilter.NEW_TO_DEPRECATED_FILTER;
import static java.util.Objects.requireNonNull;

final class StrategyInfluencerAwareConversions {

    private StrategyInfluencerAwareConversions() {
        // No instances.
    }

    static <U> StreamingHttpClientFilterFactory toClientFactory(final U address,
                                                                final MultiAddressHttpClientFilterFactory<U> original) {
        requireNonNull(address);
        if (original instanceof HttpExecutionStrategyInfluencer) {
            HttpExecutionStrategyInfluencer influencer = (HttpExecutionStrategyInfluencer) original;
            return new StrategyInfluencingStreamingClientFilterFactory() {
                @Override
                public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
                    return influencer.influenceStrategy(strategy);
                }

                @Override
                public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client,
                                                        @Nullable final Publisher<Object> lbEventStream,
                                                        @Nullable final Completable sdStatus) {
                    return original.create(address, client);
                }
            };
        }
        return client -> new StreamingHttpClientFilter(original.create(address, client));
    }

    static <U> MultiAddressHttpClientFilterFactory<U> toMultiAddressClientFactory(
            final StreamingHttpClientFilterFactory original) {
        if (original instanceof HttpExecutionStrategyInfluencer) {
            HttpExecutionStrategyInfluencer influencer = (HttpExecutionStrategyInfluencer) original;
            return new StrategyInfluencingMultiAddressHttpClientFilterFactory<U>() {
                @Override
                public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
                    return influencer.influenceStrategy(strategy);
                }

                @Override
                public StreamingHttpClientFilter create(final U address, final FilterableStreamingHttpClient client) {
                    return new StreamingHttpClientFilter(original.create(client));
                }
            };
        }
        return (address, client) -> new StreamingHttpClientFilter(
                NEW_TO_DEPRECATED_FILTER.create(original.create(client)));
    }

    static StreamingHttpServiceFilterFactory toConditionalServiceFilterFactory(
            final Predicate<StreamingHttpRequest> predicate, final StreamingHttpServiceFilterFactory original) {
        requireNonNull(predicate);
        requireNonNull(original);

        if (original instanceof HttpExecutionStrategyInfluencer) {
            HttpExecutionStrategyInfluencer influencer = (HttpExecutionStrategyInfluencer) original;
            return new StrategyInfluencingStreamingServiceFilterFactory() {
                @Override
                public StreamingHttpServiceFilter create(final StreamingHttpService service) {
                    return new ConditionalHttpServiceFilter(predicate, original.create(service), service);
                }

                @Override
                public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
                    return influencer.influenceStrategy(strategy);
                }
            };
        }
        return service -> new ConditionalHttpServiceFilter(predicate, original.create(service), service);
    }

    static StreamingHttpConnectionFilterFactory toConditionalConnectionFilterFactory(
            final Predicate<StreamingHttpRequest> predicate, final StreamingHttpConnectionFilterFactory original) {
        requireNonNull(predicate);
        requireNonNull(original);

        if (original instanceof HttpExecutionStrategyInfluencer) {
            HttpExecutionStrategyInfluencer influencer = (HttpExecutionStrategyInfluencer) original;
            return new StrategyInfluencingStreamingConnectionFilterFactory() {
                @Override
                public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
                    return influencer.influenceStrategy(strategy);
                }

                @Override
                public StreamingHttpConnectionFilter create(final FilterableStreamingHttpConnection connection) {
                    return new ConditionalHttpConnectionFilter(predicate, original.create(connection), connection);
                }
            };
        }
        return connection -> new ConditionalHttpConnectionFilter(predicate, original.create(connection), connection);
    }

    static ContextAwareStreamingHttpClientFilterFactory toConditionalClientFilterFactory(
            final Predicate<StreamingHttpRequest> predicate, final StreamingHttpClientFilterFactory original) {
        requireNonNull(predicate);
        requireNonNull(original);

        if (original instanceof HttpExecutionStrategyInfluencer) {
            HttpExecutionStrategyInfluencer influencer = (HttpExecutionStrategyInfluencer) original;
            return new StrategyInfluencingStreamingClientFilterFactory() {
                @Override
                public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
                    return influencer.influenceStrategy(strategy);
                }

                @Override
                public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client,
                                                        @Nullable final Publisher<Object> lbEventStream,
                                                        @Nullable final Completable sdStatus) {
                    if (original instanceof ContextAwareStreamingHttpClientFilterFactory) {
                        return new ConditionalHttpClientFilter(predicate,
                                ((ContextAwareStreamingHttpClientFilterFactory) original).create(client,
                                        lbEventStream, sdStatus), client);
                    }
                    return new ConditionalHttpClientFilter(predicate, original.create(client), client);
                }
            };
        }

        if (original instanceof ContextAwareStreamingHttpClientFilterFactory) {
            return (client, lbEventStream, sdStatus) -> new ConditionalHttpClientFilter(predicate,
                    ((ContextAwareStreamingHttpClientFilterFactory) original).create(client,
                            lbEventStream, sdStatus), client);
        }

        return (client, __, ___) -> new ConditionalHttpClientFilter(predicate, original.create(client), client);
    }

    static <U> MultiAddressHttpClientFilterFactory<U> toMultiAddressConditionalFilterFactory(
            final Predicate<StreamingHttpRequest> predicate,
            final MultiAddressHttpClientFilterFactory<U> original) {
        requireNonNull(predicate);
        requireNonNull(original);

        if (original instanceof HttpExecutionStrategyInfluencer) {
            HttpExecutionStrategyInfluencer influencer = (HttpExecutionStrategyInfluencer) original;
            return new StrategyInfluencingMultiAddressHttpClientFilterFactory<U>() {
                @Override
                public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
                    return influencer.influenceStrategy(strategy);
                }

                @Override
                public StreamingHttpClientFilter create(final U address,
                                                        final FilterableStreamingHttpClient client) {
                    return new ConditionalHttpClientFilter(predicate, original.create(address, client), client);
                }
           };
        }
        return (address, client) ->
                new ConditionalHttpClientFilter(predicate, original.create(address, client), client);
    }

    interface StrategyInfluencingStreamingServiceFilterFactory
            extends StreamingHttpServiceFilterFactory, HttpExecutionStrategyInfluencer {
    }

    interface StrategyInfluencingStreamingConnectionFilterFactory
            extends StreamingHttpConnectionFilterFactory, HttpExecutionStrategyInfluencer {
    }

    interface StrategyInfluencingStreamingClientFilterFactory
            extends ContextAwareStreamingHttpClientFilterFactory, HttpExecutionStrategyInfluencer {
    }

    interface StrategyInfluencingMultiAddressHttpClientFilterFactory<U>
            extends MultiAddressHttpClientFilterFactory<U>, HttpExecutionStrategyInfluencer {
    }
}
