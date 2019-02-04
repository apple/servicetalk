/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.BlockingHttpConnection;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;

import org.junit.Test;

import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpSerializationProviders.textDeserializer;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.http.netty.HttpClients.forSingleAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.Objects.requireNonNull;
import static org.junit.Assert.assertEquals;

@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
public class HostHeaderHttpRequesterFilterTest {
    @Test
    public void ipv4NotEscaped() throws Exception {
        doHostHeaderTest("1.2.3.4", "1.2.3.4");
    }

    @Test
    public void ipv6IsEscaped() throws Exception {
        doHostHeaderTest("::1", "[::1]");
    }

    private static void doHostHeaderTest(String hostHeader, String expectedValue) throws Exception {
        try (ServerContext context = buildServer();
             BlockingHttpClient client = forSingleAddress(serverHostAndPort(context))
                .enableHostHeaderFallback(hostHeader)
                .buildBlocking()) {
            assertEquals(expectedValue,
                    client.request(client.get("/")).payloadBody(textDeserializer()));
        }
    }

    private static ServerContext buildServer() throws Exception {
        return HttpServers.forAddress(localAddress(0))
                .listenStreamingAndAwait((ctx, request, responseFactory) ->
                            success(responseFactory.ok().payloadBody(
                                    just(requireNonNull(request.headers().get(HOST)).toString()), textSerializer())));
    }

    @Test
    public void clientBuilderAppendClientFilter() throws Exception {
        try (ServerContext context = buildServer();
             BlockingHttpClient client = forSingleAddress(serverHostAndPort(context))
                    .disableHostHeaderFallback() // turn off the default
                    .appendClientFilter(new HostHeaderHttpRequesterFilter(HostAndPort.of("foo.bar", -1)))
                    .buildBlocking()) {
                assertEquals("foo.bar:-1",
                        client.request(client.get("/")).payloadBody(textDeserializer()));
        }
    }

    @Test
    public void clientBuilderAppendConnectionFilter() throws Exception {
        try (ServerContext context = buildServer();
             BlockingHttpClient client = forSingleAddress(serverHostAndPort(context))
                    .disableHostHeaderFallback() // turn off the default
                    .appendConnectionFilter(new HostHeaderHttpRequesterFilter(HostAndPort.of("foo.bar", -1)))
                    .buildBlocking()) {
                assertEquals("foo.bar:-1",
                        client.request(client.get("/")).payloadBody(textDeserializer()));
        }
    }

    @Test
    public void connectionBuilderAppendConnectionFilter() throws Exception {
        try (ServerContext context = buildServer();
             BlockingHttpConnection conn = new DefaultHttpConnectionBuilder<>()
                    .appendConnectionFilter(new HostHeaderHttpRequesterFilter(HostAndPort.of("foo.bar", -1)))
                    .buildBlocking(context.listenAddress())) {
                assertEquals("foo.bar:-1",
                        conn.request(conn.get("/")).payloadBody(textDeserializer()));
        }
    }
}
