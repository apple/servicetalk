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
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpServerContext;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.BuilderUtils.newClientBuilder;
import static io.servicetalk.http.netty.BuilderUtils.newServerBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

class NettyHttpServerRequestTargetTest {

    @RegisterExtension
    static final ExecutionContextExtension SERVER_CTX =
            ExecutionContextExtension.cached("server-io", "server-executor").setClassLevel(true);
    @RegisterExtension
    static final ExecutionContextExtension CLIENT_CTX =
            ExecutionContextExtension.cached("client-io", "client-executor").setClassLevel(true);

    @ParameterizedTest(name = "{displayName} [{index}] protocol={0}")
    @EnumSource(HttpProtocol.class)
    void doubleSlashRequestTargetDoesNotOverrideHostHeader(HttpProtocol protocol) throws Exception {
        HttpRequest received = sendAndCapture(protocol, "//a.example/b?c=d");
        assertThat(received.requestTarget(), is("//a.example/b?c=d"));
        assertThat(received.host(), is(nullValue()));
        assertThat(received.path(), is("//a.example/b"));
        assertEffectiveHostAndPort(received, "b.example", -1);
    }

    private static HttpRequest sendAndCapture(HttpProtocol protocol, String requestTarget) throws Exception {
        BlockingQueue<HttpRequest> requests = new LinkedBlockingQueue<>();
        try (HttpServerContext server = newServerBuilder(SERVER_CTX, protocol)
                .listenAndAwait((ctx, request, factory) -> {
                    requests.add(request);
                    return succeeded(factory.ok());
                });
             HttpClient client = newClientBuilder(server, CLIENT_CTX, protocol).build()) {
            HttpResponse response = client.request(client.get(requestTarget).setHeader(HOST, "b.example"))
                    .toFuture().get();
            assertThat(response.status(), is(OK));
        }
        return requests.take();
    }

    private static void assertEffectiveHostAndPort(HttpRequest request, String expectedHost, int expectedPort) {
        HostAndPort effective = request.effectiveHostAndPort();
        assertThat(effective, is(notNullValue()));
        assertThat(effective.hostName(), is(expectedHost));
        assertThat(effective.port(), is(expectedPort));
    }
}
