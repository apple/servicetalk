/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseFactory;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.transport.api.ServerContext;

import io.netty.channel.unix.Errors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.SocketAddress;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionAcceptorRejectionTest {

    /**
     * Verifies that the {@link io.servicetalk.transport.api.EarlyConnectionAcceptor} can reject incoming connections.
     */
    @Test
    void earlyConnectionAcceptorCanReject() throws Exception {
        HttpServerBuilder builder = HttpServers.forPort(0)
                .appendEarlyConnectionAcceptor(info -> Completable.failed(new Exception("woops")));

        try (ServerContext server = builder.listenAndAwait(this::helloWorld)) {
            SocketAddress serverAddress = server.listenAddress();

            try (BlockingHttpClient client = HttpClients.forResolvedAddress(serverAddress).buildBlocking()) {

                Errors.NativeIoException ex = Assertions.assertThrows(
                        Errors.NativeIoException.class, () -> client.request(client.get("/sayHello")));
                assertTrue(ex.getMessage().contains("Connection reset by peer"));
            }
        }
    }

    /**
     * Verifies that the {@link io.servicetalk.transport.api.LateConnectionAcceptor} can reject incoming connections.
     */
    @Test
    void lateConnectionAcceptorCanReject() throws Exception {
        HttpServerBuilder builder = HttpServers.forPort(0)
                .appendLateConnectionAcceptor(info -> Completable.failed(new Exception("woops")));

        try (ServerContext server = builder.listenAndAwait(this::helloWorld)) {
            SocketAddress serverAddress = server.listenAddress();

            try (BlockingHttpClient client = HttpClients.forResolvedAddress(serverAddress).buildBlocking()) {

                Errors.NativeIoException ex = Assertions.assertThrows(
                        Errors.NativeIoException.class, () -> client.request(client.get("/sayHello")));
                assertTrue(ex.getMessage().contains("Connection reset by peer"));
            }
        }
    }

    private Single<HttpResponse> helloWorld(HttpServiceContext ctx, HttpRequest request,
                                                   HttpResponseFactory responseFactory) {
        return succeeded(responseFactory.ok().payloadBody("Hello World!", textSerializerUtf8()));
    }
}
