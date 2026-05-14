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

import io.servicetalk.client.api.ConnectTimeoutException;
import io.servicetalk.client.api.TransportObserverConnectionFactoryFilter;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.RetryableException;
import io.servicetalk.transport.api.TransportObserver;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;

import static io.netty.util.internal.PlatformDependent.normalizedOs;
import static io.servicetalk.client.api.LimitingConnectionFactoryFilter.withMax;
import static io.servicetalk.concurrent.api.BlockingTestUtils.awaitIndefinitely;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED;
import static io.servicetalk.http.netty.RetryingHttpRequesterFilter.disableAutoRetries;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_ECHO;
import static io.servicetalk.logging.api.LogLevel.TRACE;
import static io.servicetalk.transport.api.ServiceTalkSocketOptions.CONNECT_TIMEOUT;
import static io.servicetalk.transport.api.ServiceTalkSocketOptions.SO_BACKLOG;
import static java.lang.Boolean.TRUE;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConnectionAcceptingNettyHttpServerTest extends AbstractNettyHttpServerTest {

    private static final boolean IS_LINUX = "linux".equals(normalizedOs());
    // There is an off-by-one behavior difference between macOS & Linux.
    // Linux has a greater-than check
    // (see. https://github.com/torvalds/linux/blob/5bfc75d92efd494db37f5c4c173d3639d4772966/include/net/sock.h#L941)
    private static final int TCP_BACKLOG = IS_LINUX ? 0 : 1;
    private static final int CONNECT_TIMEOUT_MILLIS = (int) SECONDS.toMillis(2);
    private static final int TRY_REQUEST_AWAIT_MILLIS = 500;

    @Override
    protected void configureServerBuilder(final HttpServerBuilder serverBuilder) {
        serverBuilder.listenSocketOption(SO_BACKLOG, TCP_BACKLOG);
    }

    @Override
    protected SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> newClientBuilder() {
        return super.newClientBuilder()
                .appendConnectionFactoryFilter(withMax(5))
                .appendClientFilter(disableAutoRetries())
                .enableWireLogging("servicetalk-tests-wire-logger", TRACE, TRUE::booleanValue);
    }

    private StreamingHttpClient newClientWithConnectTimeout(TransportObserver observer) {
        return newClientBuilder()
                // It's important to use CONNECT_TIMEOUT here to verify that connections aren't establishing.
                .socketOption(CONNECT_TIMEOUT, CONNECT_TIMEOUT_MILLIS)
                .appendConnectionFactoryFilter(
                        new TransportObserverConnectionFactoryFilter<>(observer))
                .buildStreaming();
    }

    @Test
    void testStopAcceptingAndResume() throws Exception {
        setUp(CACHED, CACHED);
        final StreamingHttpRequest request = streamingHttpClient().newRequest(GET, SVC_ECHO);

        assertConnectionRequestSucceeds(request);

        serverContext().acceptConnections(false);
        // Netty will evaluate the auto-read on the next round, so the next connection will go through.
        assertConnectionRequestSucceeds(request);
        // This connection should get established but not accepted.
        assertConnectionRequestReceiveTimesOut(request);

        // Restoring auto-read will resume accepting.
        serverContext().acceptConnections(true);
        assertConnectionRequestSucceeds(request);
    }

    @Test
    void testIdleTimeout() throws Exception {
        BlockingQueue<Throwable> observed = new LinkedBlockingQueue<>();
        setUp(CACHED, CACHED);
        final StreamingHttpRequest request = streamingHttpClient().newRequest(GET, SVC_ECHO);

        assertConnectionRequestSucceeds(request);

        serverContext().acceptConnections(false);
        // Netty will evaluate the auto-read on the next round, so the next connection will go through.
        assertConnectionRequestSucceeds(request);
        // Connection will establish but remain in the accept-queue
        // (i.e., NOT accepted by the server => occupying 1 backlog entry)
        assertConnectionRequestReceiveTimesOut(request);
        try (StreamingHttpClient client = newClientWithConnectTimeout(new ConnectionClosedObserver(observed))) {
            // Since we control the backlog size, this connection won't establish (i.e., NO syn-ack)
            // timeout operator can be used to kill it or socket connection-timeout
            final Single<StreamingHttpResponse> response =
                    client.reserveConnection(request).flatMap(conn -> conn.request(request));
            final ExecutionException e = assertThrows(ExecutionException.class, () -> awaitIndefinitely(response));
            assertConnectTimeoutException(e.getCause());
        }
        assertThat(observed, hasSize(1));
        observed.forEach(ConnectionAcceptingNettyHttpServerTest::assertConnectTimeoutException);
    }

    private static void assertConnectTimeoutException(Throwable t) {
        assertThat(t, is(instanceOf(ConnectTimeoutException.class)));
        assertThat(t, is(instanceOf(RetryableException.class)));
        assertThat(t.getCause(), is(instanceOf(io.netty.channel.ConnectTimeoutException.class)));
    }

    private void assertConnectionRequestReceiveTimesOut(final StreamingHttpRequest request) {
        Future<StreamingHttpResponse> future = streamingHttpClient().reserveConnection(request)
                .flatMap(conn -> conn.request(request))
                        .toFuture();
        assertThrows(TimeoutException.class, () -> future.get(TRY_REQUEST_AWAIT_MILLIS, MILLISECONDS));
        future.cancel(true);
    }

    private void assertConnectionRequestSucceeds(final StreamingHttpRequest request) throws Exception {
        final StreamingHttpResponse response =
                awaitIndefinitely(streamingHttpClient().reserveConnection(request)
                        .flatMap(conn -> conn.request(request)));
        assert response != null;
        assertResponse(response, HTTP_1_1, OK, "");
    }

    private static final class ConnectionClosedObserver implements TransportObserver, ConnectionObserver {

        private final BlockingQueue<Throwable> observed;

        ConnectionClosedObserver(BlockingQueue<Throwable> observed) {
            this.observed = observed;
        }

        @Override
        public ConnectionObserver onNewConnection(@Nullable final Object localAddress, final Object remoteAddress) {
            return this;
        }

        @Override
        public void connectionClosed(final Throwable error) {
            observed.add(error);
        }

        @Override
        public void connectionClosed() {
            observed.add(new IllegalStateException("Unexpected call to connectionClosed()"));
        }
    }
}
