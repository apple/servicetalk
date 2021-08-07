/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.StatelessTrailersTransformer;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.ServerContext;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.DefaultHttpHeadersFactory.INSTANCE;
import static io.servicetalk.http.api.HttpSerializers.appSerializerUtf8FixLen;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class GracefulCloseTest {

    private enum TrailerAddType {
        Regular,
        Duplicate,
        None
    }

    private ServerContext context;
    private StreamingHttpClient client;

    @SuppressWarnings("unchecked")
    private void setUp(final TrailerAddType trailerAddType) throws Exception {
        context = HttpServers.forAddress(localAddress(0)).listenStreamingAndAwait((ctx, request, responseFactory) -> {
            StreamingHttpResponse resp = responseFactory.ok().payloadBody(from("Hello"), appSerializerUtf8FixLen());
            switch (trailerAddType) {
                case Regular:
                    resp.transform(new StaticTrailersTransformer());
                    break;
                case Duplicate:
                    resp.transformMessageBody(publisher ->
                            ((Publisher<Object>) publisher).concat(succeeded(INSTANCE.newEmptyTrailers())));
                    break;
                default:
                    break;
            }
            return succeeded(resp);
        });
        client = HttpClients.forSingleAddress(serverHostAndPort(context)).buildStreaming();
    }

    @AfterEach
    void tearDown() throws Exception {
        client.close();
        context.close();
    }

    @ParameterizedTest
    @EnumSource(TrailerAddType.class)
    void useConnection(TrailerAddType trailerAddType) throws Exception {
        setUp(trailerAddType);
        ReservedStreamingHttpConnection conn = client.reserveConnection(client.get("/")).toFuture().get();
        StreamingHttpResponse resp = conn.request(client.get("/")
                .payloadBody(from("Hello"), appSerializerUtf8FixLen())).toFuture().get();
        assertThat("Unexpected response.", resp.status().code(), equalTo(HttpResponseStatus.OK.code()));
        // Drain response.
        resp.payloadBody().toFuture().get();
        conn.close();
    }

    @ParameterizedTest
    @EnumSource(TrailerAddType.class)
    void useClient(TrailerAddType trailerAddType) throws Exception {
        setUp(trailerAddType);
        StreamingHttpResponse resp = client.request(client.get("/")
                .payloadBody(from("Hello"), appSerializerUtf8FixLen())).toFuture().get();
        assertThat("Unexpected response.", resp.status().code(), equalTo(HttpResponseStatus.OK.code()));
        // Drain response.
        resp.payloadBody().toFuture().get();
    }

    private static class StaticTrailersTransformer extends StatelessTrailersTransformer<Buffer> {
        @Override
        protected HttpHeaders payloadComplete(final HttpHeaders trailers) {
            trailers.add("foo", "bar");
            return trailers;
        }
    }
}
