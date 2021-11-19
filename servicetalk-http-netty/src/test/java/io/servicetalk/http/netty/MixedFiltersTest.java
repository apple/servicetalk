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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.ReservedBlockingHttpConnection;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.UnaryOperator;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.buffer.api.Matchers.contentEqualTo;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class MixedFiltersTest {

    private static final CharSequence FILTERS_HEADER = newAsciiString("filters-header");
    private static final Collection<Boolean> TRUE_FALSE = asList(true, false);

    public static Collection<Arguments> arguments() {
        List<List<ConditionalFilterFactory>> combinations = asList(
                asList(new MigratedFilter(1)),
                asList(new DeprecatedFilter(1)),
                asList(new MigratedFilter(1), new MigratedFilter(2)),
                asList(new MigratedFilter(1, false), new MigratedFilter(2)),
                asList(new DeprecatedFilter(1), new DeprecatedFilter(2)),
                asList(new DeprecatedFilter(1, false), new DeprecatedFilter(2)),
                asList(new MigratedFilter(1), new DeprecatedFilter(2)),
                asList(new MigratedFilter(1, false), new DeprecatedFilter(2)),
                asList(new DeprecatedFilter(1), new MigratedFilter(2)),
                asList(new DeprecatedFilter(1, false), new MigratedFilter(2)),
                asList(new MigratedFilter(1), new DeprecatedFilter(2), new MigratedFilter(3)),
                asList(new MigratedFilter(1), new DeprecatedFilter(2, false), new MigratedFilter(3)),
                asList(new DeprecatedFilter(1), new MigratedFilter(2), new DeprecatedFilter(3)),
                asList(new DeprecatedFilter(1), new MigratedFilter(2, false), new DeprecatedFilter(3)));
        Collection<Arguments> arguments = new ArrayList<>();
        for (List<ConditionalFilterFactory> filters : combinations) {
            for (Boolean forClient : TRUE_FALSE) {
                for (Boolean reservedConnection : TRUE_FALSE) {
                    for (Boolean conditional : TRUE_FALSE) {
                        if (!conditional && filters.stream().anyMatch(f -> !f.apply())) {
                            // Skip a non-conditional cases when any of the FF can not be applied
                            continue;
                        }
                        arguments.add(Arguments.of(filters, forClient, reservedConnection, conditional));
                    }
                }
            }
        }
        return arguments;
    }

    @ParameterizedTest(name = "{displayName} [{index}] filters={0}, forClient={1}, reservedConnection={2}, " +
            "conditional={3}")
    @MethodSource("arguments")
    void test(List<ConditionalFilterFactory> filters, boolean forClient, boolean reservedConnection,
              boolean conditional) throws Exception {

        String expected = filters.stream()
                .filter(ConditionalFilterFactory::apply)
                .map(ConditionalFilterFactory::toString)
                .reduce((first, second) -> first + ',' + second)
                .get();
        test(builder -> {
            if (forClient) {
                if (conditional) {
                    filters.forEach(cff -> builder.appendClientFilter(__ -> cff.apply(), cff));
                } else {
                    filters.forEach(builder::appendClientFilter);
                }
            } else if (conditional) {
                filters.forEach(cff -> builder.appendConnectionFilter(__ -> cff.apply(), cff));
            } else {
                filters.forEach(builder::appendConnectionFilter);
            }
            return builder;
        }, reservedConnection, expected);
    }

    private static void test(UnaryOperator<SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress>> filters,
                             boolean reservedConnection, CharSequence expectedValue) throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok()
                        .setHeader(FILTERS_HEADER, requireNonNull(request.headers().get(FILTERS_HEADER))));
             BlockingHttpClient client = filters.apply(HttpClients.forSingleAddress(serverHostAndPort(serverContext)))
                     .buildBlocking()) {

            HttpResponse response;
            if (reservedConnection) {
                ReservedBlockingHttpConnection conn = client.reserveConnection(client.get("/"));
                response = conn.request(conn.get("/"));
                conn.release();
            } else {
                response = client.request(client.get("/"));
            }
            assertThat(response.status(), is(OK));
            assertThat(response.headers().get(FILTERS_HEADER), contentEqualTo(expectedValue));
        }
    }

    private abstract static class ConditionalFilterFactory implements StreamingHttpClientFilterFactory,
                                                                      StreamingHttpConnectionFilterFactory {

        private final String name;
        private final boolean apply;

        ConditionalFilterFactory(int index, boolean apply) {
            this.name = getClass().getSimpleName() + index + (apply ? "" : "-false");
            this.apply = apply;
        }

        public final boolean apply() {
            return apply;
        }

        @Override
        public final String toString() {
            return name;
        }
    }

    private static final class DeprecatedFilter extends ConditionalFilterFactory {

        DeprecatedFilter(int index) {
            this(index, true);
        }

        DeprecatedFilter(int index, boolean apply) {
            super(index, apply);
        }

        @Override
        public StreamingHttpClientFilter create(FilterableStreamingHttpClient client) {
            return new StreamingHttpClientFilter(client) {

                @Override
                protected Single<StreamingHttpResponse> request(StreamingHttpRequester delegate,
                                                                HttpExecutionStrategy strategy,
                                                                StreamingHttpRequest request) {
                    return Single.defer(() -> delegate.request(strategy,
                            recordName(request, DeprecatedFilter.this.toString())));
                }
            };
        }

        @Override
        public StreamingHttpConnectionFilter create(FilterableStreamingHttpConnection connection) {
            return new StreamingHttpConnectionFilter(connection) {
                @Override
                public Single<StreamingHttpResponse> request(HttpExecutionStrategy strategy,
                                                             StreamingHttpRequest request) {
                    return Single.defer(() -> delegate().request(strategy,
                            recordName(request, DeprecatedFilter.this.toString())));
                }
            };
        }
    }

    private static final class MigratedFilter extends ConditionalFilterFactory {

        MigratedFilter(int index) {
            this(index, true);
        }

        MigratedFilter(int index, boolean apply) {
            super(index, apply);
        }

        @Override
        public StreamingHttpClientFilter create(FilterableStreamingHttpClient client) {
            return new StreamingHttpClientFilter(client) {

                @Override
                protected Single<StreamingHttpResponse> request(StreamingHttpRequester delegate,
                                                                StreamingHttpRequest request) {
                    return Single.defer(() -> delegate.request(recordName(request,
                            MigratedFilter.this.toString())));
                }
            };
        }

        @Override
        public StreamingHttpConnectionFilter create(FilterableStreamingHttpConnection connection) {
            return new StreamingHttpConnectionFilter(connection) {
                @Override
                public Single<StreamingHttpResponse> request(StreamingHttpRequest request) {
                    return Single.defer(() -> delegate().request(recordName(request,
                            MigratedFilter.this.toString())));
                }
            };
        }
    }

    private static StreamingHttpRequest recordName(StreamingHttpRequest request, String name) {
        CharSequence value = request.headers().get(FILTERS_HEADER);
        request.headers().set(FILTERS_HEADER, value == null ? name : (value + "," + name));
        return request;
    }
}
