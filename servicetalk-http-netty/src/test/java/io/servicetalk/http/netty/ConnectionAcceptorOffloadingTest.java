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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseFactory;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.transport.api.ConnectExecutionStrategy;
import io.servicetalk.transport.api.ConnectionAcceptorFactory;
import io.servicetalk.transport.api.IoThreadFactory;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;

class ConnectionAcceptorOffloadingTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testAcceptorOffloading(boolean offload) throws Exception {
        AtomicReference<Boolean> offloaded = new AtomicReference<>();
        ConnectionAcceptorFactory factory = ConnectionAcceptorFactory.withStrategy(original ->
                        context -> {
                            boolean isIoThread = IoThreadFactory.IoThread.currentThreadIsIoThread();
                            offloaded.set(!isIoThread);
                            return original.accept(context);
                        },
                offload ? ConnectExecutionStrategy.offload() : ConnectExecutionStrategy.anyStrategy());

        try (ServerContext server = HttpServers.forPort(0)
                .appendConnectionAcceptorFilter(factory)
                .listenAndAwait(this::helloWorld)) {
            SocketAddress serverAddress = server.listenAddress();

            try (BlockingHttpClient client = HttpClients.forResolvedAddress(serverAddress).buildBlocking()) {
                HttpResponse response = client.request(client.get("/sayHello"));
                assertThat("unexpected status", response.status(), is(HttpResponseStatus.OK));
            }
        }
        assertThat("factory was not invoked", offloaded.get(), is(notNullValue()));
        assertThat("incorrect offloading", offloaded.get(), is(offload));
    }

    private Single<HttpResponse> helloWorld(HttpServiceContext ctx,
                                            HttpRequest request,
                                            HttpResponseFactory responseFactory) {
        return succeeded(responseFactory.ok().payloadBody("Hello World!", textSerializerUtf8()));
    }
}
