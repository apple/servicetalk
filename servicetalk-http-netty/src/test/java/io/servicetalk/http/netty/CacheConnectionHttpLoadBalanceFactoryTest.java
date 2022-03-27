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

import io.servicetalk.client.api.TransportObserverConnectionFactoryFilter;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpLoadBalancedConnection;
import io.servicetalk.http.api.Http2SettingsBuilder;
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.utils.CacheConnectionHttpLoadBalanceFactory;
import io.servicetalk.loadbalancer.RoundRobinLoadBalancerFactory;
import io.servicetalk.logging.api.LogLevel;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfigBuilder;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.NoopTransportObserver;

import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.collectUnordered;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_2_0;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1Default;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;
import static io.servicetalk.http.netty.RetryingHttpRequesterFilter.BackOffPolicy.ofImmediate;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.lang.Math.ceil;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

final class CacheConnectionHttpLoadBalanceFactoryTest {
    @ParameterizedTest(name = "{displayName} [{index}] numRequests={0} maxConcurrency={1} clientH2={2} serverH2={3}")
    @CsvSource(value = {
            "1, 100, true, true", "2, 2, true, false",
            "100, 100, true, true", "100, 100, false, true", "100, 100, true, false", "100, 100, false, false",
            "199, 100, true, true", "201, 100, true, true",
            "1000, 100, true, true", "1001, 100, true, true"
    })
    void h1OrH2(int numRequests, int maxConcurrency, boolean clientPreferH2, boolean serverPreferH2) throws Exception {
        final H2ProtocolConfig h2ServerProtocol = h2().initialSettings(
                new Http2SettingsBuilder().maxConcurrentStreams(maxConcurrency).build()).build();
        final H1ProtocolConfig h1ServerProtocol = h1Default();
        final H2ProtocolConfig h2ClientProtocol = h2Default();
        final H1ProtocolConfig h1ClientProtocol = h1().maxPipelinedRequests(maxConcurrency).build();

        CountingConnectionObserver connectionObserver = new CountingConnectionObserver();
        final AtomicInteger numRetries = new AtomicInteger(0);
        try (ServerContext ctx = HttpServers.forAddress(localAddress(0))
                .sslConfig(new ServerSslConfigBuilder(DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey)
                        .build())
                .protocols(protocolConfigs(serverPreferH2, h1ServerProtocol, h2ServerProtocol))
                .listenStreamingAndAwait((ctx2, request, responseFactory) ->
                        succeeded(responseFactory.ok().payloadBody(
                                ctx2.protocol().equals(HTTP_2_0) ? Publisher.never() : Publisher.empty())));
             StreamingHttpClient client = HttpClients.forResolvedAddress(serverHostAndPort(ctx))
                     .sslConfig(new ClientSslConfigBuilder(InsecureTrustManagerFactory.INSTANCE).build())
                     .protocols(protocolConfigs(clientPreferH2, h1ClientProtocol, h2ClientProtocol))
                     .enableWireLogging("servicetalk-tests-h2-frame-logger", LogLevel.TRACE, () -> false)
                     .appendConnectionFactoryFilter(new TransportObserverConnectionFactoryFilter<>(connectionObserver))
                     .loadBalancerFactory(new CacheConnectionHttpLoadBalanceFactory<>(
                             DefaultHttpLoadBalancerFactory.Builder.from(
                                     new RoundRobinLoadBalancerFactory.Builder<InetSocketAddress,
                                             FilterableStreamingHttpLoadBalancedConnection>().build()).build(),
                             a -> maxConcurrency))
                     // The accounting for connection caching and the ConcurrencyController are done at different layers
                     // so it is possible the connection caching will give a connection to the load balancer that fails
                     // selection due to ConcurrencyController count being meet.
                     .appendClientFilter(new RetryingHttpRequesterFilter.Builder()
                             .maxTotalRetries(Integer.MAX_VALUE).retryRetryableExceptions(
                                     (req, e) -> {
                                         numRetries.incrementAndGet();
                                         return ofImmediate(Integer.MAX_VALUE);
                                     }).build())
                     .buildStreaming()) {
            List<Single<StreamingHttpResponse>> responseSingles = new ArrayList<>(numRequests);
            for (int i = 0; i < numRequests; ++i) {
                // Ideally we can always use Publisher.never() however for http1 requests must complete for us to make
                // progress on subsequent requests (pipelining).
                responseSingles.add(client.request(client.get("/" + i).payloadBody(
                        clientPreferH2 && serverPreferH2 ? Publisher.never() : Publisher.empty())));
            }

            Collection<StreamingHttpResponse> responses =
                    collectUnordered(responseSingles, numRequests).toFuture().get();
            assertThat(responses, hasSize(numRequests));
            assertThat(connectionObserver.count.get(),
                    // Initial number of streams is unbound, so we may create more streams on connections before the
                    // client acknowledges the servers max_concurrent_streams setting update.
                    lessThanOrEqualTo((int) ceil((double) (numRequests + numRetries.get()) / maxConcurrency)));
        }
    }

    private static class CountingConnectionObserver implements TransportObserver {
        private final AtomicInteger count = new AtomicInteger();

        @Override
        public ConnectionObserver onNewConnection(@Nullable final Object localAddress, final Object remoteAddress) {
            count.incrementAndGet();
            return NoopTransportObserver.NoopConnectionObserver.INSTANCE;
        }
    }

    private static HttpProtocolConfig[] protocolConfigs(boolean preferH2, H1ProtocolConfig h1Config,
                                                        H2ProtocolConfig h2Config) {
        return preferH2 ?
                new HttpProtocolConfig[] {h2Config, h1Config} :
                new HttpProtocolConfig[] {h1Config, h2Config};
    }
}
