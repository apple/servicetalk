/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.transport.api.ServerContext;

import org.junit.Test;

import java.net.InetSocketAddress;

import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpSerializationProviders.textDeserializer;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.http.netty.HttpClients.forSingleAddress;
import static io.servicetalk.transport.api.HostAndPort.of;
import static java.util.Objects.requireNonNull;
import static org.junit.Assert.assertEquals;

@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
public class HostHeaderHttpConnectionFilterTest {
    @Test
    public void ipv4NotEscaped() throws Exception {
        doHostHeaderTest("1.2.3.4", "1.2.3.4");
    }

    @Test
    public void ipv6IsEscaped() throws Exception {
        doHostHeaderTest("::1", "[::1]");
    }

    private void doHostHeaderTest(String hostHeader, String expectedValue) throws Exception {
        try (ServerContext context = buildServer()) {
            try (HttpClient client = forSingleAddress(of((InetSocketAddress) context.listenAddress()))
                    .enableHostHeaderFallback(hostHeader)
                    .build()) {
                assertEquals(expectedValue,
                        client.request(client.get("/")).toFuture().get().payloadBody(textDeserializer()));
            }
        }
    }

    private ServerContext buildServer() throws Exception {
        return HttpServers.newHttpServerBuilder(0)
                .listenStreamingAndAwait((ctx, request, responseFactory) ->
                            success(responseFactory.ok().payloadBody(
                                    just(requireNonNull(request.headers().get(HOST)).toString()), textSerializer())));
    }
}
