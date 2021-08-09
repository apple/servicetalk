/*
 * Copyright © 2018-2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.ClientGroup;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.internal.partition.DefaultPartitionAttributesBuilder;
import io.servicetalk.client.api.partition.PartitionAttributes;
import io.servicetalk.client.api.partition.PartitionAttributesBuilder;
import io.servicetalk.client.api.partition.PartitionedServiceDiscovererEvent;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.net.InetAddress.getLoopbackAddress;
import static java.util.Objects.requireNonNull;
import static org.hamcrest.Matchers.hasToString;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PartitionedHttpClientTest {
    private static final PartitionAttributes.Key<String> SRV_NAME = PartitionAttributes.Key.newKey();
    private static final PartitionAttributes.Key<Boolean> SRV_LEADER = PartitionAttributes.Key.newKey();
    private static final String SRV_1 = "srv1";
    private static final String SRV_2 = "srv2";
    private static final String X_SERVER = "X-SERVER";
    private static final String LOOPBACK_ADDRESS = getLoopbackAddress().getHostAddress();
    private static ServerContext srv1;
    private static ServerContext srv2;
    private TestPublisher<PartitionedServiceDiscovererEvent<ServerAddress>> sdPublisher;
    private ServiceDiscoverer<String, InetSocketAddress, PartitionedServiceDiscovererEvent<InetSocketAddress>> psd;

    @BeforeAll
    static void setUpServers() throws Exception {
        srv1 = HttpServers.forAddress(localAddress(0))
                .listenBlockingAndAwait((ctx, request, responseFactory) ->
                        responseFactory.ok().setHeader(X_SERVER, SRV_1));

        srv2 = HttpServers.forAddress(localAddress(0))
                .listenBlockingAndAwait((ctx, request, responseFactory) ->
                        responseFactory.ok().setHeader(X_SERVER, SRV_2));
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        sdPublisher = new TestPublisher.Builder<PartitionedServiceDiscovererEvent<ServerAddress>>()
                .disableAutoOnSubscribe().build();
        psd = mock(ServiceDiscoverer.class);
        Publisher<List<PartitionedServiceDiscovererEvent<InetSocketAddress>>> mappedSd =
                sdPublisher.map(psde -> new PartitionedServiceDiscovererEvent<InetSocketAddress>() {
                    @Override
                    public InetSocketAddress address() {
                        return psde.address().isa;
                    }

                    @Override
                    public boolean isAvailable() {
                        return psde.isAvailable();
                    }

                    @Override
                    public PartitionAttributes partitionAddress() {
                        return psde.partitionAddress();
                    }
                }).map(Collections::singletonList);
        when(psd.discover("test-cluster")).then(__ -> mappedSd);
    }

    @AfterAll
    static void tearDownServers() throws Exception {
        newCompositeCloseable().mergeAll(srv1, srv2).close();
    }

    @Test
    void testPartitionByHeader() throws Exception {

        final Function<HttpRequestMetaData, PartitionAttributesBuilder> selector = req ->
                new DefaultPartitionAttributesBuilder(1)
                        .add(SRV_NAME, requireNonNull(req.headers().get(X_SERVER)).toString());

        try (BlockingHttpClient clt = HttpClients.forPartitionedAddress(psd, "test-cluster", selector)
                // TODO(jayv) This *hack* only works because SRV_NAME is part of the selection criteria,
                // we need to consider adding metadata to PartitionAttributes.
                .initializer((pa, builder) -> builder.unresolvedAddressToHost(addr -> pa.get(SRV_NAME)))
                .buildBlocking()) {

            sdPublisher.onSubscribe(new TestSubscription());
            sdPublisher.onNext(
                    new TestPSDE(SRV_1, (InetSocketAddress) srv1.listenAddress()),
                    new TestPSDE(SRV_2, (InetSocketAddress) srv2.listenAddress()));

            final HttpResponse httpResponse1 = clt.request(clt.get("/").addHeader(X_SERVER, SRV_2));
            final HttpResponse httpResponse2 = clt.request(clt.get("/").addHeader(X_SERVER, SRV_1));

            MatcherAssert.assertThat(httpResponse1.headers().get(X_SERVER), hasToString(SRV_2));
            MatcherAssert.assertThat(httpResponse2.headers().get(X_SERVER), hasToString(SRV_1));
        }
    }

    @Test
    void testPartitionByTarget() throws Exception {

        final Function<HttpRequestMetaData, PartitionAttributesBuilder> selector = req ->
                new DefaultPartitionAttributesBuilder(1)
                        .add(SRV_NAME, req.requestTarget().substring(1));

        try (BlockingHttpClient clt = HttpClients.forPartitionedAddress(psd, "test-cluster", selector)
                // TODO(jayv) This *hack* only works because SRV_NAME is part of the selection criteria,
                // we need to consider adding metadata to PartitionAttributes.
                .initializer((pa, builder) -> builder.unresolvedAddressToHost(addr -> pa.get(SRV_NAME)))
                .buildBlocking()) {

            sdPublisher.onSubscribe(new TestSubscription());
            sdPublisher.onNext(
                    new TestPSDE(SRV_1, (InetSocketAddress) srv1.listenAddress()),
                    new TestPSDE(SRV_2, (InetSocketAddress) srv2.listenAddress()));

            final HttpResponse httpResponse1 = clt.request(clt.get("/" + SRV_2));
            final HttpResponse httpResponse2 = clt.request(clt.get("/" + SRV_1));

            MatcherAssert.assertThat(httpResponse1.headers().get(X_SERVER), hasToString(SRV_2));
            MatcherAssert.assertThat(httpResponse2.headers().get(X_SERVER), hasToString(SRV_1));
        }
    }

    @Test
    void testPartitionByLeader() throws Exception {
        final Function<HttpRequestMetaData, PartitionAttributesBuilder> selector = req ->
                new DefaultPartitionAttributesBuilder(1).add(SRV_LEADER, true);

        try (BlockingHttpClient clt = HttpClients.forPartitionedAddress(psd, "test-cluster", selector)
                // TODO(jayv) This *hack* doesn't work because SRV_NAME is NOT part of the selection criteria,
                // we need to consider adding metadata to PartitionAttributes.
                // .appendClientFactoryFilter((pa, builder) ->
                //         builder.enableHostHeaderFallback(pa.get(SRV_NAME)))
                .buildBlocking()) {

            sdPublisher.onSubscribe(new TestSubscription());
            sdPublisher.onNext(
                    new TestPSDE(SRV_1, false, (InetSocketAddress) srv1.listenAddress()),
                    new TestPSDE(SRV_2, true, (InetSocketAddress) srv2.listenAddress()));

            final HttpResponse httpResponse1 = clt.request(clt.get("/foo"));
            final HttpResponse httpResponse2 = clt.request(clt.get("/bar"));

            MatcherAssert.assertThat(httpResponse1.headers().get(X_SERVER), hasToString(SRV_2));
            MatcherAssert.assertThat(httpResponse2.headers().get(X_SERVER), hasToString(SRV_2));
        }
    }

    /**
     * Custom address type.
     */
    private static final class ServerAddress {
        final InetSocketAddress isa;
        final String name; // some metadata

        ServerAddress(final InetSocketAddress isa, final String name) {
            this.isa = isa;
            this.name = name;
        }
    }

    /**
     * Discoverer for custom address type {@link ServerAddress} that is partition-aware.
     */
    private static final class TestPSDE implements PartitionedServiceDiscovererEvent<ServerAddress> {
        private final PartitionAttributes pa;
        private final ServerAddress sa;

        private TestPSDE(final String srvName, final InetSocketAddress isa) {
            this.pa = new DefaultPartitionAttributesBuilder(1).add(SRV_NAME, srvName).build();
            this.sa = new ServerAddress(isa, srvName);
        }

        private TestPSDE(final String srvName, final boolean leader, final InetSocketAddress isa) {
            this.pa = new DefaultPartitionAttributesBuilder(2)
                    .add(SRV_LEADER, leader)
                    .build();
            this.sa = new ServerAddress(isa, srvName);
        }

        @Override
        public PartitionAttributes partitionAddress() {
            return pa;
        }

        @Override
        public ServerAddress address() {
            return sa;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }
    }

    @Test
    void testClientGroupPartitioning() throws Exception {
        // user partition discovery service, userId=1 => srv1 | userId=2 => srv2
        try (ServerContext userDisco = HttpServers.forAddress(localAddress(0))
                .listenBlockingAndAwait((ctx, request, responseFactory) -> {
                    if ("/partition".equals(request.path())) {
                        String userIdParam = request.queryParameter("userId");
                        if (userIdParam == null || userIdParam.isEmpty()) {
                            return responseFactory.badRequest();
                        }
                        int userId = Integer.parseInt(userIdParam);
                        if (userId != 1 && userId != 2) {
                            return responseFactory.notFound();
                        }
                        ServerContext dSrv = userId == 1 ? srv1 : srv2;
                        InetSocketAddress socketAddress = (InetSocketAddress) dSrv.listenAddress();
                        return responseFactory.ok().payloadBody(socketAddress.getPort() + "", textSerializerUtf8());
                    }
                    return responseFactory.notFound();
                })) {

            try (PartitioningHttpClientWithOutOfBandDiscovery client =
                         new PartitioningHttpClientWithOutOfBandDiscovery(userDisco)) {

                StreamingHttpResponse httpResponse1 = client.request(new User(1), client.get("/foo")).toFuture().get();
                StreamingHttpResponse httpResponse2 = client.request(new User(2), client.get("/foo")).toFuture().get();

                MatcherAssert.assertThat(httpResponse1.status(), is(OK));
                MatcherAssert.assertThat(httpResponse1.headers().get(X_SERVER), hasToString(SRV_1));

                MatcherAssert.assertThat(httpResponse2.status(), is(OK));
                MatcherAssert.assertThat(httpResponse2.headers().get(X_SERVER), hasToString(SRV_2));
            }
        }
    }

    /**
     * A user defined object influencing partitioning yet not part of the request metadata.
     */
    private static final class User {
        private final int id;

        User(final int id) {
            this.id = id;
        }

        int id() {
            return id;
        }
    }

    /**
     * Example of a user defined ClientGroup Key.
     */
    private static final class Group {
        private final HostAndPort address;
        private final ExecutionContext executionContext;

        Group(final HostAndPort address, final ExecutionContext executionContext) {
            this.address = address;
            this.executionContext = executionContext;
        }

        HostAndPort address() {
            return address;
        }
    }

    /**
     * The sort of composite HttpClient we expect users to write to solve such use-cases.
     */
    private static class PartitioningHttpClientWithOutOfBandDiscovery
            implements AutoCloseable, StreamingHttpRequestFactory {

        private final ClientGroup<Group, StreamingHttpClient> clients;
        private final HttpClient udClient;
        private final StreamingHttpRequestFactory requestFactory;

        PartitioningHttpClientWithOutOfBandDiscovery(ServerContext disco) {
            // User Partition Discovery Service - IRL this client should typically cache responses
            udClient = HttpClients.forSingleAddress(serverHostAndPort(disco)).build();

            requestFactory = new DefaultStreamingHttpRequestResponseFactory(
                    udClient.executionContext().bufferAllocator(), DefaultHttpHeadersFactory.INSTANCE, HTTP_1_1);

            clients = ClientGroup.from(group ->
                    HttpClients.forSingleAddress(group.address())
                            .ioExecutor(group.executionContext.ioExecutor())
                            .executionStrategy(defaultStrategy(group.executionContext.executor()))
                            .bufferAllocator(group.executionContext.bufferAllocator())
                            .buildStreaming());
        }

        Single<StreamingHttpResponse> request(User user, StreamingHttpRequest req) {
            return udClient
                    .request(udClient.get("/partition?userId=" + user.id()))
                    .flatMap(resp -> {
                        // user discovery returns the port of the server (both run on loopback address)
                        int port = Integer.parseInt(resp.payloadBody().toString(StandardCharsets.UTF_8));
                        Group key = new Group(HostAndPort.of(LOOPBACK_ADDRESS, port), udClient.executionContext());
                        return clients.get(key).request(req);
                    });
        }

        @Override
        public StreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
            return requestFactory.newRequest(method, requestTarget);
        }

        @Override
        public void close() throws Exception {
            newCompositeCloseable().prependAll(udClient, clients).closeAsync().toFuture().get();
        }
    }
}
