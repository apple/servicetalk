/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.DelegatingConnectionAcceptor;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpSerializers.appSerializerUtf8FixLen;
import static io.servicetalk.http.netty.HttpServers.forAddress;
import static io.servicetalk.http.netty.NettyHttpServer.NettyHttpServerConnection;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static java.lang.String.valueOf;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class ServerGracefulConnectionClosureHandlingTest {

    @RegisterExtension
    static final ExecutionContextExtension SERVER_CTX =
        ExecutionContextExtension.cached("server-io", "server-executor");

    private static final String REQUEST_CONTENT = "request_content";
    private static final String RESPONSE_CONTENT = "response_content";

    private final CountDownLatch serverConnectionClosing = new CountDownLatch(1);
    private final CountDownLatch serverConnectionClosed = new CountDownLatch(1);
    private final CountDownLatch serverContextClosed = new CountDownLatch(1);

    private ServerContext serverContext;
    private InetSocketAddress serverAddress;

    @BeforeEach
    void setUp() throws Exception {
        AtomicReference<Runnable> serverClose = new AtomicReference<>();
        serverContext = forAddress(localAddress(0))
            .ioExecutor(SERVER_CTX.ioExecutor())
            .executionStrategy(defaultStrategy(SERVER_CTX.executor()))
            .executionStrategy(noOffloadsStrategy())
            .appendConnectionAcceptorFilter(original -> new DelegatingConnectionAcceptor(original) {
                @Override
                public Completable accept(final ConnectionContext context) {
                    ((NettyHttpServerConnection) context).onClosing()
                        .whenFinally(serverConnectionClosing::countDown).subscribe();
                    context.onClose().whenFinally(serverConnectionClosed::countDown).subscribe();
                    return completed();
                }
            }).listenStreamingAndAwait((ctx, request, responseFactory) -> succeeded(responseFactory.ok()
                        .addHeader(CONTENT_LENGTH, valueOf(
                                RESPONSE_CONTENT.length()))
                        .payloadBody(
                                request.payloadBody().ignoreElements()
                                        .concat(from(RESPONSE_CONTENT)),
                                appSerializerUtf8FixLen())
                        // Close ServerContext after response is complete
                        .transformMessageBody(payload -> payload
                                .whenFinally(serverClose.get()))));
        serverContext.onClose().whenFinally(serverContextClosed::countDown).subscribe();
        serverClose.set(() -> serverContext.closeAsyncGracefully().subscribe());

        serverAddress = (InetSocketAddress) serverContext.listenAddress();
    }

    @AfterEach
    void tearDown() throws Exception {
        serverContext.close();
    }

    @Test
    void test() throws Exception {
        try (Socket clientSocket = new Socket(serverAddress.getAddress(), serverAddress.getPort());
             OutputStream out = clientSocket.getOutputStream();
             InputStream in = clientSocket.getInputStream()) {

            out.write(newRequestAsBytes("/first"));
            out.flush();

            serverConnectionClosing.await();

            out.write(newRequestAsBytes("/second"));
            out.flush();

            int total = 0;
            while (in.read() >= 0) {
                total++;
            }
            assertThat(total, is(114));
        }

        awaitServerConnectionClosed();
    }

    private byte[] newRequestAsBytes(String path) {
        return ("POST " + path + " HTTP/1.1\r\n" +
                "host: localhost\r\n" +
                "content-type: text/plain\r\n" +
                "content-length: " + REQUEST_CONTENT.length() + "\r\n\r\n" +
                REQUEST_CONTENT).getBytes(US_ASCII);
    }

    private void awaitServerConnectionClosed() throws Exception {
        serverConnectionClosed.await();
        serverContextClosed.await();
    }
}
