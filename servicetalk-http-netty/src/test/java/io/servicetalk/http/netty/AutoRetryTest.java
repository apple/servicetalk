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
package io.servicetalk.http.netty;

import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.DefaultAutoRetryStrategyProvider;
import io.servicetalk.client.api.DelegatingConnectionFactory;
import io.servicetalk.concurrent.api.AsyncCloseables;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.RetryableException;
import io.servicetalk.transport.api.ServerContext;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import static io.servicetalk.client.api.AutoRetryStrategyProvider.DISABLE_AUTO_RETRIES;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.netty.HttpClients.forSingleAddress;
import static io.servicetalk.http.netty.HttpServers.forPort;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

public class AutoRetryTest {

    @Rule
    public final ExpectedException expectedException = ExpectedException.none();
    private final ServerContext svcCtx;
    private final SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> clientBuilder;
    private final AtomicInteger connectCalledCount;

    @Nullable
    private BlockingHttpClient client;

    public AutoRetryTest() throws Exception {
        svcCtx = forPort(0).listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok());
        clientBuilder = forSingleAddress(serverHostAndPort(svcCtx))
                .appendConnectionFactoryFilter(ClosingConnectionFactory::new);
        connectCalledCount = new AtomicInteger();
    }

    @After
    public void tearDown() throws Exception {
        CompositeCloseable closeable = AsyncCloseables.newCompositeCloseable();
        if (client != null) {
            closeable.append(client.asClient());
        }
        closeable.append(svcCtx);
        closeable.close();
    }

    @Test
    public void disableAutoRetry() throws Exception {
        client = clientBuilder
                .autoRetryStrategy(DISABLE_AUTO_RETRIES)
                .buildBlocking();
        expectedException.expect(instanceOf(RetryableException.class));
        client.request(client.get("/"));
    }

    @Test
    public void updateMaxRetry() {
        client = clientBuilder
                .autoRetryStrategy(new DefaultAutoRetryStrategyProvider.Builder().maxRetries(1).build())
                .buildBlocking();
        try {
            client.request(client.get("/"));
        } catch (Exception e) {
            assertThat("Unexpected exception.", e, instanceOf(RetryableException.class));
            assertThat("Unexpected calls to connect.", connectCalledCount.get(), is(2));
        }
    }

    private final class ClosingConnectionFactory
            extends DelegatingConnectionFactory<InetSocketAddress, FilterableStreamingHttpConnection> {
        ClosingConnectionFactory(
                final ConnectionFactory<InetSocketAddress, FilterableStreamingHttpConnection> original) {
            super(original);
        }

        @Override
        public Single<FilterableStreamingHttpConnection> newConnection(final InetSocketAddress inetSocketAddress) {
            return delegate().newConnection(inetSocketAddress)
                    .flatMap(c -> c.closeAsync().concat(succeeded(c))
                            .whenOnSuccess(__ -> connectCalledCount.incrementAndGet()));
        }
    }
}
