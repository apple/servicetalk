/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.BlockingHttpService;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static io.servicetalk.http.api.HttpApiConversions.toStreamingHttpService;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializationProviders.textDeserializer;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED_SERVER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

@RunWith(Parameterized.class)
public class ConnectionContextToStringTest extends AbstractNettyHttpServerTest {

    private final HttpProtocol protocol;

    public ConnectionContextToStringTest(HttpProtocol protocol) {
        super(CACHED, CACHED_SERVER);
        this.protocol = protocol;
        protocol(protocol.config);
    }

    @Parameterized.Parameters(name = "protocol={0}")
    public static HttpProtocol[] data() {
        return HttpProtocol.values();
    }

    @Override
    protected void service(final StreamingHttpService service) {
        super.service((toStreamingHttpService((BlockingHttpService) (ctx, request, responseFactory) ->
                        responseFactory.ok().payloadBody(ctx.toString(), textSerializer()),
                strategy -> strategy)).adaptor());
    }

    @Test
    public void test() throws Exception {
        StreamingHttpResponse response = makeRequest(streamingHttpConnection().get("/"));
        assertResponse(response, protocol.version, OK);
        String serverContext = response.toResponse().toFuture().get().payloadBody(textDeserializer());

        assertThat("Client's ConnectionContext does not contain netty channel id",
                streamingHttpConnection().connectionContext().toString(), containsString("[id: "));
        assertThat("Server's ConnectionContext does not contain netty channel id",
                serverContext, containsString("[id: "));
    }
}
