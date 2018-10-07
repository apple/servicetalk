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

import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.StreamingHttpRequestHandler;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.transport.api.ServerContext;

import org.junit.Test;
import org.mockito.InOrder;

import java.net.InetSocketAddress;
import java.util.function.Function;

import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.netty.HttpClients.forSingleAddress;
import static io.servicetalk.http.netty.HttpServers.newHttpServerBuilder;
import static io.servicetalk.transport.api.HostAndPort.of;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HttpServerFilterOrderTest {

    @Test
    public void prependOrder() throws Exception {
        StreamingHttpRequestHandler filter1 = newMockHandler();
        StreamingHttpRequestHandler filter2 = newMockHandler();
        ServerContext serverContext = newHttpServerBuilder(0)
                .appendServiceFilter(addFilter(filter1))
                .appendServiceFilter(addFilter(filter2))
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok());
        BlockingHttpClient client = forSingleAddress(of((InetSocketAddress) serverContext.listenAddress()))
                .buildBlocking();
        HttpResponse resp = client.request(client.get("/"));
        assertThat("Unexpected response.", resp.status(), is(OK));

        InOrder verifier = inOrder(filter1, filter2);
        verifier.verify(filter1).handle(any(), any(), any());
        verifier.verify(filter2).handle(any(), any(), any());
    }

    private StreamingHttpRequestHandler newMockHandler() {
        StreamingHttpRequestHandler mock = mock(StreamingHttpRequestHandler.class);
        when(mock.asStreamingService()).thenCallRealMethod();
        return mock;
    }

    private static Function<StreamingHttpService, ? extends StreamingHttpRequestHandler> addFilter(
            StreamingHttpRequestHandler filter) {
        return orig -> {
            when(filter.handle(any(), any(), any()))
                    .thenAnswer(i -> orig.handle(i.getArgument(0), i.getArgument(1), i.getArgument(2)));
            return filter;
        };
    }
}
