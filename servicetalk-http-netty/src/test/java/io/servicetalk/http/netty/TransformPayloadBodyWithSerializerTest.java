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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializers.appSerializerUtf8FixLen;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class TransformPayloadBodyWithSerializerTest {

    private static final String HELLO_WORLD = "Hello\nWorld\n";

    @ParameterizedTest(name = "{displayName} [{index}] protocol={0}")
    @EnumSource(HttpProtocol.class)
    void clientFilterTransformsReceivedResponseBody(HttpProtocol protocol) throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .protocols(protocol.config)
                .listenStreamingAndAwait((ctx, request, factory) ->
                        Single.succeeded(factory.ok().payloadBody(helloWorldBuffers())));
             StreamingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                     .protocols(protocol.config)
                     .appendClientFilter(c -> new StreamingHttpClientFilter(c) {
                         @Override
                         protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                         final StreamingHttpRequest request) {
                             return delegate.request(request).map(response ->
                                     response.transformPayloadBody(
                                             TransformPayloadBodyWithSerializerTest::toStrings,
                                             appSerializerUtf8FixLen()));
                         }
                     })
                     .buildStreaming()) {
            StreamingHttpResponse response = client.request(client.get("/")).toFuture().get();
            assertThat(response.status(), is(OK));
            assertThat(concat(response.payloadBody(appSerializerUtf8FixLen())), is(HELLO_WORLD));
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocol={0}")
    @EnumSource(HttpProtocol.class)
    void serviceFilterTransformsReceivedRequestBody(HttpProtocol protocol) throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .protocols(protocol.config)
                .appendServiceFilter(s -> new StreamingHttpServiceFilter(s) {
                    @Override
                    public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                                final StreamingHttpRequest request,
                                                                final StreamingHttpResponseFactory factory) {
                        return delegate().handle(ctx, request.transformPayloadBody(
                                TransformPayloadBodyWithSerializerTest::toStrings, appSerializerUtf8FixLen()),
                                factory);
                    }
                })
                .listenStreamingAndAwait((ctx, request, factory) ->
                        Single.succeeded(factory.ok().payloadBody(
                                request.payloadBody(appSerializerUtf8FixLen()), appSerializerUtf8FixLen())));
             StreamingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                     .protocols(protocol.config)
                     .buildStreaming()) {
            StreamingHttpResponse response = client.request(client.post("/").payloadBody(helloWorldBuffers()))
                    .toFuture().get();
            assertThat(response.status(), is(OK));
            assertThat(concat(response.payloadBody(appSerializerUtf8FixLen())), is(HELLO_WORLD));
        }
    }

    private static Publisher<Buffer> helloWorldBuffers() {
        BufferAllocator alloc = DEFAULT_ALLOCATOR;
        return from(alloc.fromAscii("Hello\n"), alloc.fromAscii("World\n"));
    }

    private static Publisher<String> toStrings(Publisher<Buffer> buffers) {
        return buffers.map(buffer -> buffer.toString(UTF_8));
    }

    private static String concat(Publisher<String> strings) throws Exception {
        return strings.collect(StringBuilder::new, StringBuilder::append).toFuture().get().toString();
    }
}
