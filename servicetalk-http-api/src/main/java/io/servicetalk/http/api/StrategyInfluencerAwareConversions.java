/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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

/**
 * Utility for converting filter factories to conditional factories which are applied when provided
 * {@link Predicate} returns {@code true}. The returned implementations comply to the
 * {@link HttpExecutionStrategyInfluencer} hint interface and apply chosen
 * {@link io.servicetalk.transport.api.ExecutionStrategy} when invoked.
 */
public final class StrategyInfluencerAwareConversions {

    private StrategyInfluencerAwareConversions() {
        // No instances.
    }

    /**
     * Converts a {@link StreamingHttpServiceFilterFactory} to one that is conditional upon the provided
     * {@link Predicate}.
     * @param predicate When {@code true} the filter logic is applied.
     * @param original Original {@link StreamingHttpServiceFilterFactory}.
     * @return {@link StreamingHttpServiceFilterFactory} which maintains the {@link HttpExecutionStrategyInfluencer}
     * interface if the original implements it.
     */
    public static StreamingHttpServiceFilterFactory toConditionalServiceFilterFactory(
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

    /**
     * Converts a {@link StreamingHttpConnectionFilterFactory} to one that is conditional upon the provided
     * {@link Predicate}.
     * @param predicate When {@code true} the filter logic is applied.
     * @param original Original {@link StreamingHttpConnectionFilterFactory}.
     * @return {@link StreamingHttpConnectionFilterFactory} which maintains the {@link HttpExecutionStrategyInfluencer}
     * interface if the original implements it.
     */
    public static StreamingHttpConnectionFilterFactory toConditionalConnectionFilterFactory(
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

    /**
     * Converts a {@link StreamingHttpClientFilterFactory} to one that is conditional upon the provided
     * {@link Predicate}.
     * @param predicate When {@code true} the filter logic is applied.
     * @param original Original {@link StreamingHttpClientFilterFactory}.
     * @return {@link StreamingHttpClientFilterFactory} which maintains the {@link HttpExecutionStrategyInfluencer}
     * interface if the original implements it.
     */
    public static StreamingHttpClientFilterFactory toConditionalClientFilterFactory(
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
                public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
                    return new ConditionalHttpClientFilter(predicate, original.create(client), client);
                }
            };
        }
        return client -> new ConditionalHttpClientFilter(predicate, original.create(client), client);
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
}
