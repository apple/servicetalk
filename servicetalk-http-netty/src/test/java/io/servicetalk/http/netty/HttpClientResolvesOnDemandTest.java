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

import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpServerContext;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class HttpClientResolvesOnDemandTest {

    @ParameterizedTest(name = "{displayName} [{index}]: protocol={0}")
    @EnumSource(HttpProtocol.class)
    void test(HttpProtocol protocol) throws Exception {
        try (HttpServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .protocols(protocol.config)
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok());
             BlockingHttpClient client = HttpClients.forSingleAddressResolveOnDemand(serverHostAndPort(serverContext))
                     .protocols(protocol.config)
                     .buildBlocking()) {
            HttpResponse response = client.request(client.get("/"));
            assertThat(response.status(), is(OK));
        }
    }
}
