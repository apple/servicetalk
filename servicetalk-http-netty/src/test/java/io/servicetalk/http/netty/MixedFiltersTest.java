/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.DelegatingConnectionFactory;
import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.context.api.ContextMap.Key;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.TransportObserver;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

import static io.servicetalk.context.api.ContextMap.Key.newKey;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

class MixedFiltersTest {

    @SuppressWarnings("unchecked")
    private static final Key<AtomicReference<StringBuilder>> KEY = (Key<AtomicReference<StringBuilder>>)
            (Key<?>) newKey("key", AtomicReference.class);

    public static Collection<List<AbstractFactoryFilter>> arguments() {
        return asList(
                asList(new MigratedFilter(1)),
                asList(new DeprecatedFilter(1)),
                asList(new MigratedFilter(1), new MigratedFilter(2)),
                asList(new DeprecatedFilter(1), new DeprecatedFilter(2)),
                asList(new MigratedFilter(1), new DeprecatedFilter(2)),
                asList(new DeprecatedFilter(1), new MigratedFilter(2)),
                asList(new MigratedFilter(1), new DeprecatedFilter(2), new MigratedFilter(3)),
                asList(new DeprecatedFilter(1), new MigratedFilter(2), new DeprecatedFilter(3)));
    }

    @ParameterizedTest(name = "{displayName} [{index}] filters={0}")
    @MethodSource("arguments")
    void testSingleClient(List<AbstractFactoryFilter> filters) throws Exception {
        String expected = filters.stream()
                .map(AbstractFactoryFilter::toString)
                .reduce((first, second) -> first + ',' + second)
                .get();

        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok())) {
            SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                    HttpClients.forSingleAddress(serverHostAndPort(serverContext));
            filters.forEach(builder::appendConnectionFactoryFilter);
            try (BlockingHttpClient client = builder.buildBlocking()) {
                AtomicReference<StringBuilder> ref = new AtomicReference<>(new StringBuilder());
                AsyncContext.put(KEY, ref);
                HttpResponse response = client.request(client.get("/"));
                assertThat(response.status(), is(OK));
                assertThat(ref.toString(), is(equalTo(expected)));
            }
        }
    }

    private abstract static class AbstractFactoryFilter
            implements ConnectionFactoryFilter<InetSocketAddress, FilterableStreamingHttpConnection> {

        private final String name;

        AbstractFactoryFilter(int index) {
            this.name = getClass().getSimpleName() + index;
        }

        @Override
        public final String toString() {
            return name;
        }
    }

    private static final class DeprecatedFilter extends AbstractFactoryFilter {

        DeprecatedFilter(int index) {
            super(index);
        }

        @Override
        public ConnectionFactory<InetSocketAddress, FilterableStreamingHttpConnection> create(
                ConnectionFactory<InetSocketAddress, FilterableStreamingHttpConnection> cf) {
            return new DelegatingConnectionFactory<InetSocketAddress, FilterableStreamingHttpConnection>(cf) {
                @Override
                public Single<FilterableStreamingHttpConnection> newConnection(
                        InetSocketAddress address, @Nullable TransportObserver observer) {
                    return Single.defer(() -> {
                        AtomicReference<StringBuilder> ref = AsyncContext.get(KEY);
                        if (ref != null) {
                            if (ref.get().length() > 0) {
                                ref.get().append(',');
                            }
                            ref.get().append(DeprecatedFilter.this);
                        }
                        return delegate().newConnection(address, observer).shareContextOnSubscribe();
                    });
                }
            };
        }
    }

    private static final class MigratedFilter extends AbstractFactoryFilter {

        MigratedFilter(int index) {
            super(index);
        }

        @Override
        public ConnectionFactory<InetSocketAddress, FilterableStreamingHttpConnection> create(
                ConnectionFactory<InetSocketAddress, FilterableStreamingHttpConnection> cf) {
            return new DelegatingConnectionFactory<InetSocketAddress, FilterableStreamingHttpConnection>(cf) {
                @Override
                public Single<FilterableStreamingHttpConnection> newConnection(
                        InetSocketAddress address, @Nullable ContextMap context, @Nullable TransportObserver observer) {
                    return Single.defer(() -> {
                        AtomicReference<StringBuilder> ref = AsyncContext.get(KEY);
                        if (ref != null) {
                            if (ref.get().length() > 0) {
                                ref.get().append(',');
                            }
                            ref.get().append(MigratedFilter.this);
                            // Make sure the ContextMap is propagated:
                            if (context == null) {
                                ref.get().append("(MISSING_CONTEXT_MAP)");
                            }
                        }
                        return delegate().newConnection(address, context, observer).shareContextOnSubscribe();
                    });
                }
            };
        }
    }
}
