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

import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.ReservedHttpConnection;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;

import io.netty.channel.Channel;
import io.netty.handler.codec.DecoderException;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

import static io.servicetalk.concurrent.internal.TestTimeoutConstants.DEFAULT_TIMEOUT_SECONDS;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static io.servicetalk.transport.netty.internal.ChannelCloseUtils.channelError;
import static java.net.InetAddress.getLoopbackAddress;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * An HTTP/1.1 client must not match a response with a request it was not sent for.
 */
class ResponseWithoutRequestTest {

    private static final int SOCKET_TIMEOUT_MS = (int) SECONDS.toMillis(DEFAULT_TIMEOUT_SECONDS);

    @Test
    void extraResponseClosesConnection() throws Exception {
        Queue<Channel> channels = new LinkedBlockingQueue<>();
        try (ServerSocket server = newServerSocket();
             HttpClient client = newClient(server, 1, channels);
             ReservedHttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get();
             Socket socket = accept(server)) {
            Future<HttpResponse> response = connection.request(connection.get("/")).toFuture();
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), US_ASCII));
            assertThat(in.readLine(), startsWith("GET / "));
            // The second response can't be matched with any request, it must not reach the next one.
            socket.getOutputStream().write(("HTTP/1.1 200 OK\r\ncontent-length: 2\r\n\r\nok" +
                    "HTTP/1.1 408 Request Timeout\r\ncontent-length: 0\r\n\r\n").getBytes(US_ASCII));
            socket.getOutputStream().flush();
            assertThat(response.get().status(), is(OK));
            assertThat(response.get().payloadBody().toString(US_ASCII), is("ok"));
            connection.onClose().toFuture().get();

            Channel channel = channels.poll();
            assertThat(channel, is(notNullValue()));
            Throwable cause = channelError(channel);
            assertThat(cause, is(instanceOf(DecoderException.class)));
            assertThat(cause.getMessage(), startsWith("Received a response without an outstanding request"));
        }
    }

    @Test
    void failedRequestEncodingClosesConnection() throws Exception {
        try (ServerSocket server = newServerSocket();
             HttpClient client = newClient(server, 2, new LinkedBlockingQueue<>());
             ReservedHttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get();
             Socket socket = accept(server)) {
            Future<HttpResponse> first = connection.request(connection.get("/first")).toFuture();
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), US_ASCII));
            assertThat(in.readLine(), startsWith("GET /first "));

            // The request-target can't be encoded. The failed write closes the connection, so the decoder never
            // matches a response with this request.
            assertThrows(ExecutionException.class,
                    () -> connection.request(connection.get("/invalid target")).toFuture().get());
            connection.onClose().toFuture().get();
            assertThrows(ExecutionException.class, first::get);
        }
    }

    private static ServerSocket newServerSocket() throws IOException {
        ServerSocket server = new ServerSocket(0, 50, getLoopbackAddress());
        server.setSoTimeout(SOCKET_TIMEOUT_MS);
        return server;
    }

    private static Socket accept(ServerSocket server) throws IOException {
        Socket socket = server.accept();
        socket.setSoTimeout(SOCKET_TIMEOUT_MS);
        return socket;
    }

    private static HttpClient newClient(ServerSocket server, int maxPipelinedRequests, Queue<Channel> channels) {
        return HttpClients.forSingleAddress(serverHostAndPort(server.getLocalSocketAddress()))
                .protocols(h1().maxPipelinedRequests(maxPipelinedRequests).build())
                .appendConnectionFilter(c -> {
                    channels.add(((NettyConnectionContext) c.connectionContext()).nettyChannel());
                    return new StreamingHttpConnectionFilter(c) { };
                })
                .build();
    }
}
