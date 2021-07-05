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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.Function;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.encoding.api.Identity.identity;
import static io.servicetalk.grpc.api.GrpcExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.grpc.api.GrpcStatusCode.OK;
import static io.servicetalk.http.api.HttpApiConversions.toHttpService;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.lang.String.valueOf;
import static java.util.Collections.singletonList;

final class GrpcClientValidatesContentTypeTest {

    private static final GrpcSerializationProvider SERIALIZATION_PROVIDER = new ProtoBufSerializationProviderBuilder()
            .registerMessageType(TestRequest.class, TestRequest.parser())
            .registerMessageType(TesterProto.TestResponse.class, TesterProto.TestResponse.parser())
            .build();

    private static final Function<Boolean, HttpSerializer<TesterProto.TestResponse>>
            SERIALIZER_OVERRIDING_CONTENT_TYPE =
            (withCharset) -> new HttpSerializer<TesterProto.TestResponse>() {

                final HttpSerializer<TesterProto.TestResponse> delegate = SERIALIZATION_PROVIDER
                        .serializerFor(identity(), TesterProto.TestResponse.class);

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
                public HttpPayloadWriter<TesterProto.TestResponse> serialize(
                        final HttpHeaders headers, final HttpPayloadWriter<Buffer> payloadWriter,
                        final BufferAllocator allocator) {
                    try {
                        return delegate.serialize(headers, payloadWriter, allocator);
                    } finally {
                        headers.set(CONTENT_TYPE, headers.get(CONTENT_TYPE) + (withCharset ? "; charset=UTF-8" : ""));
                    }
                }
            };

    @Nullable
    private ServerContext serverContext;
    @Nullable
    private TesterProto.Tester.BlockingTesterClient client;

    void setUp(boolean streaming, boolean withCharset) throws Exception {
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

    static Stream<Arguments> params() {
        return Stream.of(
                Arguments.of(false, false),
                Arguments.of(false, true),
                Arguments.of(true, false),
                Arguments.of(true, true));
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            client.close();
        } finally {
            serverContext.close();
        }
    }

    @ParameterizedTest(name = "streaming={0} with-charset={1}")
    @MethodSource("params")
    void testBlockingAggregated(boolean streaming, boolean withCharset) throws Exception {
        setUp(streaming, withCharset);
        client.test(request());
    }

    @ParameterizedTest(name = "streaming={0} with-charset={1}")
    @MethodSource("params")
    void testBlockingRequestStreaming(boolean streaming, boolean withCharset) throws Exception {
        setUp(streaming, withCharset);
        client.testRequestStream(singletonList(request()));
    }

    @ParameterizedTest(name = "streaming={0} with-charset={1}")
    @MethodSource("params")
    void testBlockingResponseStreaming(boolean streaming, boolean withCharset) throws Exception {
        setUp(streaming, withCharset);
        client.testResponseStream(request()).forEach(__ -> { /* noop */ });
    }

    @ParameterizedTest(name = "streaming={0} with-charset={1}")
    @MethodSource("params")
    void testBlockingBiDiStreaming(boolean streaming, boolean withCharset) throws Exception {
        setUp(streaming, withCharset);
        client.testBiDiStream(singletonList(request()))
                .forEach(__ -> { /* noop */ });
    }

    @ParameterizedTest(name = "streaming={0} with-charset={1}")
    @MethodSource("params")
    void testAggregated(boolean streaming, boolean withCharset) throws Exception {
        setUp(streaming, withCharset);
        client.asClient().test(request()).toFuture().get();
    }

    @ParameterizedTest(name = "streaming={0} with-charset={1}")
    @MethodSource("params")
    void testRequestStreaming(boolean streaming, boolean withCharset) throws Exception {
        setUp(streaming, withCharset);
        client.asClient().testRequestStream(from(request())).toFuture().get();
    }

    @ParameterizedTest(name = "streaming={0} with-charset={1}")
    @MethodSource("params")
    void testResponseStreaming(boolean streaming, boolean withCharset) throws Exception {
        setUp(streaming, withCharset);
        client.asClient().testResponseStream(request()).toFuture().get();
    }

    @ParameterizedTest(name = "streaming={0} with-charset={1}")
    @MethodSource("params")
    void testBiDiStreaming(boolean streaming, boolean withCharset) throws Exception {
        setUp(streaming, withCharset);
        client.asClient().testBiDiStream(from(request())).toFuture().get();
    }

    private static TestRequest request() {
        return TestRequest.newBuilder().setName("request").build();
    }
}
