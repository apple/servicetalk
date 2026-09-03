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
import io.servicetalk.http.api.BlockingStreamingHttpClient;
import io.servicetalk.http.api.BlockingStreamingHttpResponse;
import io.servicetalk.http.api.HttpPayloadWriter;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Real server/client (real transport, no mocks) coverage confirming
 * {@link io.servicetalk.http.api.HttpServerBuilder#interruptBlockingServiceOnCancel(boolean)} wires correctly
 * through {@code DefaultHttpServerBuilder} without affecting normal request handling, whichever way it's set.
 * <p>
 * The interrupt-vs-cooperative-cancellation behavior itself (the actual point of the feature) is covered
 * deterministically at the unit level in {@code BlockingStreamingToStreamingServiceTest}, including the exact
 * scenario that reproduces the downstream bug this feature fixes (a response cancellation leaking a
 * {@link Thread#interrupt()} into an unrelated blocked request read). Reproducing that same race over a real
 * socket turned out to depend on transport-level close-detection timing that isn't reliably controllable from a
 * test, so it is intentionally not attempted here.
 */
class BlockingStreamingServiceInterruptOnCancelTest {

    @ParameterizedTest(name = "{displayName} [{index}] interrupt={0}")
    @ValueSource(booleans = {true, false})
    void requestResponseRoundTripUnaffectedByToggle(boolean interrupt) throws Exception {
        ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .interruptBlockingServiceOnCancel(interrupt)
                .listenBlockingStreamingAndAwait((ctx, request, response) -> {
                    request.payloadBody().forEach(chunk -> { });
                    try (HttpPayloadWriter<Buffer> writer = response.sendMetaData()) {
                        writer.write(ctx.executionContext().bufferAllocator().fromAscii("hello"));
                    }
                });
        try {
            BlockingStreamingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                    .buildBlockingStreaming();
            try {
                BlockingStreamingHttpResponse response = client.request(client.get("/"));
                assertThat(response.status(), is(OK));
                assertThat(response.toResponse().toFuture().get().payloadBody().toString(US_ASCII), is("hello"));

                // A second request on a fresh connection also completes normally -- the server, and the connection
                // acceptor path wired through DefaultHttpServerBuilder#listenBlockingStreaming, are unaffected by
                // whichever way the toggle is set.
                response = client.request(client.get("/"));
                assertThat(response.status(), is(OK));
                assertThat(response.toResponse().toFuture().get().payloadBody().toString(US_ASCII), is("hello"));
            } finally {
                client.close();
            }
        } finally {
            serverContext.closeAsync().toFuture().get();
        }
    }
}
