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
package io.servicetalk.grpc.netty;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.grpc.api.GrpcSerializationProvider;
import io.servicetalk.grpc.netty.TesterProto.TestRequest;
import io.servicetalk.grpc.protobuf.ProtoBufSerializationProviderBuilder;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpPayloadWriter;
import io.servicetalk.http.api.HttpSerializer;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.StatelessTrailersTransformer;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.netty.HttpServers;
import io.servicetalk.transport.api.ServerContext;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.function.Function;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.grpc.api.GrpcExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.grpc.api.GrpcStatusCode.OK;
import static io.servicetalk.http.api.HttpApiConversions.toHttpService;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.lang.String.valueOf;
import static java.util.Collections.singletonList;

@RunWith(Parameterized.class)
public final class GrpcClientValidatesContentTypeTest {

    private static final GrpcSerializationProvider SERIALIZATION_PROVIDER = new ProtoBufSerializationProviderBuilder()
            .registerMessageType(TestRequest.class, TestRequest.parser())
            .registerMessageType(TesterProto.TestResponse.class, TesterProto.TestResponse.parser())
            .build();

    public static final Function<Boolean, HttpSerializer<TesterProto.TestResponse>> SERIALIZER_OVERRIDING_CONTENT_TYPE =
            (withCharset) ->
        new HttpSerializer<TesterProto.TestResponse>() {

            final HttpSerializer<TesterProto.TestResponse> delegate = SERIALIZATION_PROVIDER
                    .serializerFor(() -> "", TesterProto.TestResponse.class);

            @Override
            public Buffer serialize(final HttpHeaders headers, final TesterProto.TestResponse value,
                                    final BufferAllocator allocator) {
                try {
                    return delegate.serialize(headers, value, allocator);
                } finally {
                    headers.set(CONTENT_TYPE, headers.get(CONTENT_TYPE) + (withCharset ? "; charset=UTF-8" : ""));
                }
            }

            @Override
            public BlockingIterable<Buffer> serialize(final HttpHeaders headers,
                                                      final BlockingIterable<TesterProto.TestResponse> value,
                                                      final BufferAllocator allocator) {
                try {
                    return delegate.serialize(headers, value, allocator);
                } finally {
                    headers.set(CONTENT_TYPE, headers.get(CONTENT_TYPE) + (withCharset ? "; charset=UTF-8" : ""));
                }
            }

            @Override
            public Publisher<Buffer> serialize(final HttpHeaders headers,
                                               final Publisher<TesterProto.TestResponse> value,
                                               final BufferAllocator allocator) {
                try {
                    return delegate.serialize(headers, value, allocator);
                } finally {
                    headers.set(CONTENT_TYPE, headers.get(CONTENT_TYPE) + (withCharset ? "; charset=UTF-8" : ""));
                }
            }

            @Override
            public HttpPayloadWriter<TesterProto.TestResponse> serialize(final HttpHeaders headers,
                                                                         final HttpPayloadWriter<Buffer> payloadWriter,
                                                                         final BufferAllocator allocator) {
                try {
                    return delegate.serialize(headers, payloadWriter, allocator);
                } finally {
                    headers.set(CONTENT_TYPE, headers.get(CONTENT_TYPE) + (withCharset ? "; charset=UTF-8" : ""));
                }
            }
        };

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private final ServerContext serverContext;
    private final TesterProto.Tester.BlockingTesterClient client;

    public GrpcClientValidatesContentTypeTest(boolean streaming, boolean withCharset) throws Exception {
        StreamingHttpService streamingService = (ctx, request, responseFactory) -> {
            final StreamingHttpResponse response = responseFactory.ok()
                    .version(request.version())
                    .payloadBody(from(TesterProto.TestResponse.newBuilder().setMessage("response").build()),
                            SERIALIZER_OVERRIDING_CONTENT_TYPE.apply(withCharset));

            response.transform(new StatelessTrailersTransformer<Buffer>() {
                @Override
                protected HttpHeaders payloadComplete(final HttpHeaders trailers) {
                    return trailers.set("grpc-status", valueOf(OK.value()));
                }
            });
            return succeeded(response);
        };
        HttpServerBuilder serverBuilder = HttpServers.forAddress(localAddress(0))
                .protocols(h2Default());
        serverContext = streaming ? serverBuilder.listenStreamingAndAwait(streamingService) :
                serverBuilder.listenAndAwait(toHttpService(streamingService));

        client = GrpcClients.forAddress(serverHostAndPort(serverContext))
                .executionStrategy(noOffloadsStrategy())
                .buildBlocking(new TesterProto.Tester.ClientFactory());
    }

    @Parameterized.Parameters(name = "streaming={0} with-charset={1}")
    public static Object[][] params() {
        return new Object[][]{{false, false}, {false, true}, {true, true}, {true, false}};
    }

    @After
    public void tearDown() throws Exception {
        try {
            client.close();
        } finally {
            serverContext.close();
        }
    }

    @Test
    public void testBlockingAggregated() throws Exception {
        client.test(request());
    }

    @Test
    public void testBlockingRequestStreaming() throws Exception {
        client.testRequestStream(singletonList(request()));
    }

    @Test
    public void testBlockingResponseStreaming() throws Exception {
        client.testResponseStream(request()).forEach(__ -> { /* noop */ });
    }

    @Test
    public void testBlockingBiDiStreaming() throws Exception {
        client.testBiDiStream(singletonList(request()))
                .forEach(__ -> { /* noop */ });
    }

    @Test
    public void testAggregated() throws Exception {
        client.asClient().test(request()).toFuture().get();
    }

    @Test
    public void testRequestStreaming() throws Exception {
        client.asClient().testRequestStream(from(request())).toFuture().get();
    }

    @Test
    public void testResponseStreaming() throws Exception {
        client.asClient().testResponseStream(request()).toFuture().get();
    }

    @Test
    public void testBiDiStreaming() throws Exception {
        client.asClient().testBiDiStream(from(request())).toFuture().get();
    }

    private static TestRequest request() {
        return TestRequest.newBuilder().setName("request").build();
    }
}
