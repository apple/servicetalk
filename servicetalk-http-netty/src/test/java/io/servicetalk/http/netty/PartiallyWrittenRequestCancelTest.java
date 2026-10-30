/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.client.api.TransportObserverConnectionFactoryFilter;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.Http2Exception;
import io.servicetalk.http.api.Http2SettingsBuilder;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.ConnectionInfo;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.ConnectionObserver.DataObserver;
import io.servicetalk.transport.api.ConnectionObserver.MultiplexedObserver;
import io.servicetalk.transport.api.ConnectionObserver.ReadObserver;
import io.servicetalk.transport.api.ConnectionObserver.StreamObserver;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.TransportObserver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import static io.servicetalk.concurrent.api.Completable.never;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.internal.TestTimeoutConstants.DEFAULT_TIMEOUT_SECONDS;
import static io.servicetalk.http.api.Http2ErrorCode.CANCEL;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartiallyWrittenRequestCancelTest {

    private static final String PARTIAL_BODY = "partial";
    private static final int MAX_CONCURRENT_STREAMS = 2;

    @Test
    void cancelAfterEarlyH2ResponseWhileBodyIsWritingResetsStream() throws Exception {
        CountDownLatch responseRead = new CountDownLatch(1);
        CountDownLatch streamClosed = new CountDownLatch(1);
        CompletableFuture<Throwable> serverRequestBodyError = new CompletableFuture<>();
        try (ServerContext server = HttpServers.forAddress(localAddress(0)).protocols(h2Default())
                .listenStreamingAndAwait((ctx, request, factory) -> {
                    request.messageBody().ignoreElements().whenOnError(serverRequestBodyError::complete).subscribe();
                    return succeeded(factory.ok());
                });
             StreamingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(server))
                     .protocols(h2Default())
                     .appendConnectionFactoryFilter(new TransportObserverConnectionFactoryFilter<>(
                             observer(() -> { }, responseRead::countDown, streamClosed::countDown)))
                     .buildStreaming()) {
            StreamingHttpResponse response = client.request(client.post("/")
                    .payloadBody(neverEndingBody(client))).toFuture().get();
            // The transport has read the whole response, so only the write side can reset the stream.
            assertTrue(responseRead.await(DEFAULT_TIMEOUT_SECONDS, SECONDS));

            assertTrue(response.messageBody().ignoreElements().toFuture().cancel(true));
            assertTrue(streamClosed.await(DEFAULT_TIMEOUT_SECONDS, SECONDS));
            Throwable cause = serverRequestBodyError.get();
            assertThat(cause, instanceOf(Http2Exception.class));
            assertThat(((Http2Exception) cause).errorCode(), is(CANCEL));
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] requestReplaced={0}")
    @ValueSource(booleans = {false, true})
    void cancelAfterEarlyH2ResponseWhileBodyIsWritingFreesStreamSlot(boolean requestReplaced) throws Exception {
        Semaphore responsesRead = new Semaphore(0);
        AtomicInteger connections = new AtomicInteger();
        try (ServerContext server = HttpServers.forAddress(localAddress(0))
                .protocols(h2().initialSettings(new Http2SettingsBuilder()
                        .maxConcurrentStreams(MAX_CONCURRENT_STREAMS).build()).build())
                .listenStreamingAndAwait((ctx, request, factory) -> succeeded(factory.ok()))) {
            SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                    HttpClients.forSingleAddress(serverHostAndPort(server))
                            .protocols(h2Default())
                            .appendConnectionFactoryFilter(new TransportObserverConnectionFactoryFilter<>(
                                    observer(connections::incrementAndGet, responsesRead::release, () -> { })));
            if (requestReplaced) {
                // A new request lacks the original's context, so the client frees its stream slot on cancel, not on
                // stream close, and a stream left open counts only against Netty's limit.
                builder.appendConnectionFilter(ReplaceRequestFilter::new);
            }
            try (StreamingHttpClient client = builder.buildStreaming()) {
                for (int i = 0; i < MAX_CONCURRENT_STREAMS; i++) {
                    StreamingHttpResponse response = client.request(client.post("/")
                            .payloadBody(neverEndingBody(client))).toFuture().get();
                    assertTrue(responsesRead.tryAcquire(DEFAULT_TIMEOUT_SECONDS, SECONDS));
                    assertTrue(response.messageBody().ignoreElements().toFuture().cancel(true));
                }

                StreamingHttpResponse response = client.request(client.get("/")).toFuture().get();
                assertThat(response.status(), is(OK));
                response.messageBody().ignoreElements().toFuture().get();
                assertThat(connections.get(), is(1));
            }
        }
    }

    private static final class ReplaceRequestFilter extends StreamingHttpConnectionFilter {
        ReplaceRequestFilter(FilterableStreamingHttpConnection delegate) {
            super(delegate);
        }

        @Override
        public Single<StreamingHttpResponse> request(StreamingHttpRequest request) {
            StreamingHttpRequest replacement = delegate().newRequest(request.method(), request.requestTarget())
                    .payloadBody(request.payloadBody());
            replacement.headers().add(request.headers());
            return delegate().request(replacement);
        }
    }

    private static Publisher<Buffer> neverEndingBody(StreamingHttpClient client) {
        return Publisher.from(client.executionContext().bufferAllocator().fromAscii(PARTIAL_BODY)).concat(never());
    }

    private static TransportObserver observer(Runnable onConnection, Runnable onStreamReadComplete,
                                              Runnable onStreamClosed) {
        ReadObserver readObserver = new ReadObserver() {
            @Override
            public void readComplete() {
                onStreamReadComplete.run();
            }
        };
        DataObserver dataObserver = new DataObserver() {
            @Override
            public ReadObserver onNewRead() {
                return readObserver;
            }
        };
        StreamObserver streamObserver = new StreamObserver() {
            @Override
            public DataObserver streamEstablished() {
                return dataObserver;
            }

            @Override
            public void streamClosed(Throwable error) {
                onStreamClosed.run();
            }

            @Override
            public void streamClosed() {
                onStreamClosed.run();
            }
        };
        MultiplexedObserver multiplexedObserver = new MultiplexedObserver() {
            @Override
            public StreamObserver onNewStream() {
                return streamObserver;
            }
        };
        ConnectionObserver connectionObserver = new ConnectionObserver() {
            @Override
            public MultiplexedObserver multiplexedConnectionEstablished(ConnectionInfo info) {
                onConnection.run();
                return multiplexedObserver;
            }
        };
        return (localAddress, remoteAddress) -> connectionObserver;
    }
}
