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

import static io.servicetalk.http.api.FilterFactoryUtils.appendClientFilterFactory;
import static io.servicetalk.http.api.FilterFactoryUtils.appendConnectionFilterFactory;

/**
 * Factory for {@link ConditionalHttpClientFilter} and {@link ConditionalHttpConnectionFilter}.
 */
public final class ConditionalFilterFactory
        implements StreamingHttpConnectionFilterFactory, StreamingHttpClientFilterFactory,
                   HttpExecutionStrategyInfluencer {
    private final Predicate<StreamingHttpRequest> predicate;
    private final FilterFactory predicateFactory;

    public ConditionalFilterFactory(final Predicate<StreamingHttpRequest> predicate,
                                    final FilterFactory predicateFactory) {
        this.predicate = predicate;
        this.predicateFactory = predicateFactory;
    }

    @Override
    public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
        return new ConditionalHttpClientFilter(predicate, predicateFactory.create(client), client);
    }

    @Override
    public StreamingHttpConnectionFilter create(final FilterableStreamingHttpConnection connection) {
        return new ConditionalHttpConnectionFilter(predicate, predicateFactory.create(connection), connection);
    }

    public FilterFactory append(FilterFactory append) {
        StreamingHttpClientFilterFactory clientFactory = appendClientFilterFactory(this, append);
        StreamingHttpConnectionFilterFactory connectionFactory = appendConnectionFilterFactory(this, append);
        return new FilterFactory() {
            @Override
            public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
                return HttpExecutionStrategyInfluencer.applyInfluence(append,
                        ConditionalFilterFactory.this.influenceStrategy(strategy));
            }

            @Override
            public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
                return clientFactory.create(client);
            }

            @Override
            public StreamingHttpConnectionFilter create(final FilterableStreamingHttpConnection connection) {
                return connectionFactory.create(connection);
            }
        };
    }

    @Override
    public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
        return HttpExecutionStrategyInfluencer.applyInfluence(predicateFactory,
                HttpExecutionStrategyInfluencer.applyInfluence(predicate, strategy));
    }

    public interface FilterFactory extends StreamingHttpClientFilterFactory, StreamingHttpConnectionFilterFactory,
        HttpExecutionStrategyInfluencer {

        static <FF extends StreamingHttpClientFilterFactory & StreamingHttpConnectionFilterFactory> FilterFactory from(
                FF original) {
            return new FilterFactory() {
                @Override
                public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
                    return HttpExecutionStrategyInfluencer.applyInfluence(original, strategy);
                }

                @Override
                public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
                    return original.create(client);
                }

                @Override
                public StreamingHttpConnectionFilter create(final FilterableStreamingHttpConnection connection) {
                    return original.create(connection);
                }
            };
        }
    }
}
