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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.ServerContext;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.DefaultHttp2ResetFrame;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2StreamChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.concurrent.CountDownLatch;

import static io.servicetalk.concurrent.internal.TestTimeoutConstants.DEFAULT_TIMEOUT_SECONDS;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;
import static io.servicetalk.http.netty.RawH2Peers.requestHeaders;
import static io.servicetalk.http.netty.RawH2Peers.streamClosedObserver;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A peer that resets a stream while the service has not yet read the request body must not leave the service running.
 */
class H2ReceivedResetTest {

    private final RawH2Peers peers = new RawH2Peers();

    @AfterEach
    void closePeers() throws Exception {
        peers.close();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {0}")
    @EnumSource(value = Http2Error.class, names = {"NO_ERROR", "CANCEL"})
    void resetCancelsServiceThatHasNotReadRequestBody(Http2Error errorCode) throws Exception {
        CountDownLatch serviceInvoked = new CountDownLatch(1);
        CountDownLatch responseCancelled = new CountDownLatch(1);
        CountDownLatch serverStreamClosed = new CountDownLatch(1);
        try (ServerContext server = HttpServers.forAddress(localAddress(0))
                .protocols(h2Default())
                .transportObserver(streamClosedObserver(serverStreamClosed))
                .listenStreamingAndAwait((ctx, request, factory) -> {
                    // Slow work that starts before the request body is read, and that only the reset can end.
                    serviceInvoked.countDown();
                    return Single.<StreamingHttpResponse>never().whenCancel(responseCancelled::countDown);
                })) {
            Http2StreamChannel stream = peers.openStream(peers.connect(server.listenAddress()));
            stream.write(new DefaultHttp2HeadersFrame(requestHeaders("POST", "/"), false));
            // The server buffers this, because nothing reads the request body.
            stream.writeAndFlush(new DefaultHttp2DataFrame(Unpooled.copiedBuffer("body", US_ASCII), false)).sync();
            assertTrue(serviceInvoked.await(DEFAULT_TIMEOUT_SECONDS, SECONDS));

            stream.writeAndFlush(new DefaultHttp2ResetFrame(errorCode)).sync();

            assertTrue(responseCancelled.await(DEFAULT_TIMEOUT_SECONDS, SECONDS));
            assertTrue(serverStreamClosed.await(DEFAULT_TIMEOUT_SECONDS, SECONDS));
        }
    }
}
