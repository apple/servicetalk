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
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;

import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;

public final class ConditionalFilterFactory
        implements StreamingHttpConnectionFilterFactory, StreamingHttpClientFilterFactory {
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
            public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
                return clientFactory.create(client);
            }

            @Override
            public StreamingHttpConnectionFilter create(final FilterableStreamingHttpConnection connection) {
                return connectionFactory.create(connection);
            }
        };
    }

    public interface FilterFactory extends StreamingHttpClientFilterFactory, StreamingHttpConnectionFilterFactory {

        static <FF extends StreamingHttpClientFilterFactory & StreamingHttpConnectionFilterFactory> FilterFactory from(
                FF original) {
            return new FilterFactory() {
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

    private static StreamingHttpConnectionFilterFactory appendConnectionFilterFactory(
            StreamingHttpConnectionFilterFactory first, StreamingHttpConnectionFilterFactory second) {
        requireNonNull(first);
        requireNonNull(second);
        return connection -> first.create(second.create(connection));
    }

    private static StreamingHttpClientFilterFactory appendClientFilterFactory(
            StreamingHttpClientFilterFactory first, StreamingHttpClientFilterFactory second) {
        requireNonNull(first);
        requireNonNull(second);
        return client -> first.create(second.create(client));
    }
}
