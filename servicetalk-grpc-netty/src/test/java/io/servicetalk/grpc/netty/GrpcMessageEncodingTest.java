/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.encoding.api.ContentCodec;
import io.servicetalk.grpc.api.GrpcServerBuilder;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.api.GrpcStatusCode;
import io.servicetalk.grpc.api.GrpcStatusException;
import io.servicetalk.grpc.netty.TesterProto.TestRequest;
import io.servicetalk.grpc.netty.TesterProto.TestResponse;
import io.servicetalk.grpc.netty.TesterProto.Tester.TestRequestStreamMetadata;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static io.grpc.internal.GrpcUtil.MESSAGE_ACCEPT_ENCODING;
import static io.grpc.internal.GrpcUtil.MESSAGE_ENCODING;
import static io.servicetalk.buffer.api.Buffer.asInputStream;
import static io.servicetalk.buffer.api.Buffer.asOutputStream;
import static io.servicetalk.buffer.api.CharSequences.contentEquals;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.encoding.api.Identity.identity;
import static io.servicetalk.encoding.api.internal.HeaderUtils.encodingFor;
import static io.servicetalk.encoding.netty.ContentCodings.deflateDefault;
import static io.servicetalk.encoding.netty.ContentCodings.gzipDefault;
import static io.servicetalk.grpc.netty.TesterProto.Tester.ClientFactory;
import static io.servicetalk.grpc.netty.TesterProto.Tester.ServiceFactory;
import static io.servicetalk.grpc.netty.TesterProto.Tester.TestBiDiStreamMetadata;
import static io.servicetalk.grpc.netty.TesterProto.Tester.TestMetadata;
import static io.servicetalk.grpc.netty.TesterProto.Tester.TestResponseStreamMetadata;
import static io.servicetalk.grpc.netty.TesterProto.Tester.TesterClient;
import static io.servicetalk.grpc.netty.TesterProto.Tester.TesterService;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.lang.String.valueOf;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Collections.disjoint;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static java.util.zip.GZIPInputStream.GZIP_MAGIC;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.internal.util.io.IOUtil.closeQuietly;

class GrpcMessageEncodingTest {

    private static final int PAYLOAD_SIZE = 512;
    private static final ContentCodec CUSTOM_ENCODING = new ContentCodec() {

        private static final int OUGHT_TO_BE_ENOUGH = 1 << 20;

        @Override
        public String name() {
            return "CUSTOM_ENCODING";
        }

        @Override
        public Buffer encode(final Buffer src, final BufferAllocator allocator) {
            final Buffer dst = allocator.newBuffer(OUGHT_TO_BE_ENOUGH);
            DeflaterOutputStream output = null;
            try {
                output = new GZIPOutputStream(asOutputStream(dst));
                output.write(src.array(), src.arrayOffset() + src.readerIndex(), src.readableBytes());
                src.skipBytes(src.readableBytes());
                output.finish();
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                closeQuietly(output);
            }

            return dst;
        }

        @Override
        public Buffer decode(final Buffer src, final BufferAllocator allocator) {
            final Buffer dst = allocator.newBuffer(OUGHT_TO_BE_ENOUGH);
            InflaterInputStream input = null;
            try {
                input = new GZIPInputStream(asInputStream(src));

                int read = dst.setBytesUntilEndStream(0, input, OUGHT_TO_BE_ENOUGH);
                dst.writerIndex(read);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                closeQuietly(input);
            }

            return dst;
        }

        @Override
        public Publisher<Buffer> encode(final Publisher<Buffer> from, final BufferAllocator allocator) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Publisher<Buffer> decode(final Publisher<Buffer> from, final BufferAllocator allocator) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            return "GrpcMessageEncoding{encoding='CUSTOM_ENCODING'}";
        }
    };

    private static final BiFunction<TestEncodingScenario, BlockingQueue<Throwable>, StreamingHttpServiceFilterFactory>
            REQ_RESP_VERIFIER = (options, errors) -> new StreamingHttpServiceFilterFactory() {
        @Override
        public StreamingHttpServiceFilter create(final StreamingHttpService service) {
            return new StreamingHttpServiceFilter(service) {

                @Override
                public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                            final StreamingHttpRequest request,
                                                            final StreamingHttpResponseFactory responseFactory) {
                    final ContentCodec reqEncoding = options.requestEncoding;
                    final List<ContentCodec> clientSupportedEncodings = options.clientSupported;
                    final List<ContentCodec> serverSupportedEncodings = options.serverSupported;

                    try {
                        request.transformPayloadBody(bufferPublisher -> bufferPublisher.map((buffer -> {
                            try {
                                byte compressedFlag = buffer.getByte(0);

                                if (reqEncoding == gzipDefault() || reqEncoding.name().equals(CUSTOM_ENCODING.name())) {
                                    int actualHeader = buffer.getShortLE(5) & 0xFFFF;
                                    assertEquals(GZIP_MAGIC, actualHeader);
                                }

                                if (!identity().equals(reqEncoding)) {
                                    assertTrue(buffer.readableBytes() < PAYLOAD_SIZE,
                                                          "Compressed content length should be less than the " +
                                                                                "original payload size");
                                } else {
                                    assertTrue(buffer.readableBytes() > PAYLOAD_SIZE,
                                                          "Uncompressed content length should be more than the " +
                                                                                "original payload size");
                                }

                                assertEquals(!identity().equals(reqEncoding) ? 1 : 0, compressedFlag);
                            } catch (Throwable t) {
                                errors.add(t);
                                throw t;
                            }
                            return buffer;
                        })));

                        assertValidCodingHeader(clientSupportedEncodings, request.headers());
                        if (identity().equals(reqEncoding)) {
                            assertThat("Message-Encoding should NOT be present in the headers if identity",
                                    request.headers().contains(MESSAGE_ENCODING), is(false));
                        } else {
                            assertTrue(
                                contentEquals(reqEncoding.name(), request.headers().get(MESSAGE_ENCODING)),
                                "Message-Encoding should be present in the headers if not identity");
                        }
                    } catch (Throwable t) {
                        errors.add(t);
                        throw t;
                    }

                    return super.handle(ctx, request, responseFactory).map((response -> {
                        try {
                            handle0(clientSupportedEncodings, serverSupportedEncodings, response);
                        } catch (Throwable t) {
                            errors.add(t);
                            throw t;
                        }

                        return response;
                    }));
                }

                private void handle0(final List<ContentCodec> clientSupportedEncodings,
                                     final List<ContentCodec> serverSupportedEncodings,
                                     final StreamingHttpResponse response) {

                    assertValidCodingHeader(serverSupportedEncodings, response.headers());

                    if (clientSupportedEncodings.isEmpty() || serverSupportedEncodings.isEmpty() ||
                            disjoint(serverSupportedEncodings, clientSupportedEncodings)) {
                        assertThat("Response shouldn't contain Message-Encoding header if identity",
                                response.headers().contains(MESSAGE_ENCODING), is(false));
                    } else {
                        ContentCodec expected = identity();
                        for (ContentCodec codec : clientSupportedEncodings) {
                            if (serverSupportedEncodings.contains(codec)) {
                                expected = codec;
                                break;
                            }
                        }

                        if (identity().equals(expected)) {
                            assertThat("Response shouldn't contain Message-Encoding header if identity",
                                    response.headers().contains(MESSAGE_ENCODING), is(false));
                        } else {
                            assertEquals(expected, encodingFor(clientSupportedEncodings,
                                                                          response.headers().get(MESSAGE_ENCODING)));
                        }
                    }

                    response.transformPayloadBody(bufferPublisher -> bufferPublisher.map((buffer -> {
                        try {
                            final ContentCodec respEnc = encodingFor(clientSupportedEncodings,
                                    valueOf(response.headers().get(MESSAGE_ENCODING)));

                            if (buffer.readableBytes() > 0) {
                                byte compressedFlag = buffer.getByte(0);
                                assertEquals(respEnc != null ? 1 : 0, compressedFlag);

                                if (respEnc != null &&
                                        (respEnc == gzipDefault() || respEnc.name().equals(CUSTOM_ENCODING.name()))) {
                                    int actualHeader = buffer.getShortLE(5) & 0xFFFF;
                                    assertEquals(GZIP_MAGIC, actualHeader);
                                }

                                if (respEnc != null) {
                                    assertTrue(buffer.readableBytes() < PAYLOAD_SIZE,
                                            "Compressed content length should be less than the original " +
                                                    "payload size");
                                } else {
                                    assertTrue(
                                        buffer.readableBytes() > PAYLOAD_SIZE,
                                        "Uncompressed content length should be more than the original " +
                                            "payload size " + buffer.readableBytes());
                                }
                            }
                        } catch (Throwable t) {
                            errors.add(t);
                            throw t;
                        }
                        return buffer;
                    })));
                }
            };
        }
    };

    private static class TesterServiceImpl implements TesterService {

        @Override
        public Single<TestResponse> test(GrpcServiceContext ctx, TestRequest request) {
            return succeeded(TestResponse.newBuilder().setMessage("Reply: " + request.getName()).build());
        }

        @Override
        public Single<TestResponse> testRequestStream(GrpcServiceContext ctx, Publisher<TestRequest> request) {
            try {
                List<TestRequest> requestList = request.collect((Supplier<ArrayList<TestRequest>>) ArrayList::new,
                        (testRequests, testRequest) -> {
                            testRequests.add(testRequest);
                            return testRequests;
                        }).toFuture().get();

                TestRequest elem = requestList.get(0);
                return succeeded(TestResponse.newBuilder().setMessage("Reply: " + elem.getName()).build());
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }

            return failed(new IllegalStateException());
        }

        @Override
        public Publisher<TestResponse> testBiDiStream(GrpcServiceContext ctx, Publisher<TestRequest> request) {
            return request.map((req) -> TestResponse.newBuilder().setMessage("Reply: " + req.getName()).build());
        }

        @Override
        public Publisher<TestResponse> testResponseStream(GrpcServiceContext ctx, TestRequest request) {
            return from(TestResponse.newBuilder().setMessage("Reply: " + request.getName()).build());
        }
    }

    @Nullable
    private GrpcServerBuilder grpcServerBuilder;
    @Nullable
    private ServerContext serverContext;
    @Nullable
    private TesterClient client;
    private final BlockingQueue<Throwable> errors = new LinkedBlockingQueue<>();

    void setUp(final List<ContentCodec> serverSupportedCodings,
               final List<ContentCodec> clientSupportedCodings,
               final ContentCodec requestEncoding) throws Exception {

        TestEncodingScenario options = new TestEncodingScenario(requestEncoding, clientSupportedCodings,
                serverSupportedCodings);

        grpcServerBuilder = GrpcServers.forAddress(localAddress(0));
        serverContext = listenAndAwait(options);
        client = newClient(clientSupportedCodings);
    }

    static Stream<Arguments> params() {
        return Stream.of(
                Arguments.of(null, null, identity(), true),
                Arguments.of(null, asList(gzipDefault(), identity()), gzipDefault(), false),
                Arguments.of(null, asList(deflateDefault(), identity()), deflateDefault(), false),
                Arguments.of(asList(gzipDefault(), deflateDefault(), identity()), null, identity(), true),
                Arguments.of(asList(identity(), gzipDefault(), deflateDefault()),
                        asList(gzipDefault(), identity()), gzipDefault(), true),
                Arguments.of(asList(identity(), gzipDefault(), deflateDefault()),
                        asList(deflateDefault(), identity()), deflateDefault(), true),
                Arguments.of(asList(identity(), gzipDefault()), asList(deflateDefault(), identity()),
                        deflateDefault(), false),
                Arguments.of(asList(identity(), deflateDefault()), asList(gzipDefault(), identity()),
                        gzipDefault(), false),
                Arguments.of(asList(identity(), deflateDefault()), asList(deflateDefault(), identity()),
                        deflateDefault(), true),
                Arguments.of(asList(identity(), deflateDefault()), null, identity(), true),
                Arguments.of(singletonList(gzipDefault()), singletonList(identity()), identity(), true),
                Arguments.of(singletonList(gzipDefault()), asList(gzipDefault(), identity()), identity(), true),
                Arguments.of(singletonList(gzipDefault()), asList(gzipDefault(), identity()), identity(), true),
                Arguments.of(singletonList(gzipDefault()), asList(gzipDefault(), identity()), gzipDefault(), true),
                Arguments.of(singletonList(gzipDefault()), asList(deflateDefault(), gzipDefault()),
                        gzipDefault(), true),
                Arguments.of(null, asList(gzipDefault(), identity()), gzipDefault(), false),
                Arguments.of(null, asList(gzipDefault(), deflateDefault(), identity()), deflateDefault(), false),
                Arguments.of(null, asList(gzipDefault(), identity()), identity(), true),
                Arguments.of(singletonList(CUSTOM_ENCODING), singletonList(CUSTOM_ENCODING), CUSTOM_ENCODING, true)
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            client.close();
        } finally {
            serverContext.close();
        }
    }

    private ServerContext listenAndAwait(final TestEncodingScenario encodingOptions) throws Exception {

        StreamingHttpServiceFilterFactory filterFactory = REQ_RESP_VERIFIER.apply(encodingOptions, errors);
        return grpcServerBuilder.appendHttpServiceFilter(filterFactory)
                .listenAndAwait(new ServiceFactory(new TesterServiceImpl(), encodingOptions.serverSupported));
    }

    private TesterClient newClient(@Nullable final List<ContentCodec> supportedCodings) {
        return GrpcClients.forAddress(serverHostAndPort(serverContext))
                .build(supportedCodings != null ?
                        new ClientFactory().supportedMessageCodings(supportedCodings) :
                        new ClientFactory());
    }

    @ParameterizedTest(name = "server-supported-encodings={0} client-supported-encodings={1} " +
                              "request-encoding={2} expected-success={3}")
    @MethodSource("params")
    void test(final List<ContentCodec> serverSupportedCodings,
              final List<ContentCodec> clientSupportedCodings,
              final ContentCodec requestEncoding,
              final boolean expectedSuccess) throws Throwable {
        setUp(serverSupportedCodings, clientSupportedCodings, requestEncoding);
        try {
            if (expectedSuccess) {
                assertSuccessful(requestEncoding);
            } else {
                assertUnimplemented(requestEncoding);
            }
        } catch (Throwable t) {
            throwAsyncErrors();
            throw t;
        }
    }

    private void throwAsyncErrors() throws Throwable {
        final Throwable error = errors.poll();
        if (error != null) {
            throw error;
        }
    }

    private static TestRequest request() {
        byte[] payload = new byte[PAYLOAD_SIZE];
        Arrays.fill(payload, (byte) 'a');
        String name = new String(payload, StandardCharsets.US_ASCII);
        return TestRequest.newBuilder().setName(name).build();
    }

    private void assertSuccessful(final ContentCodec encoding) throws ExecutionException, InterruptedException {
        client.test(new TestMetadata(encoding), request()).toFuture().get();
        client.testRequestStream(new TestRequestStreamMetadata(encoding), from(request(), request(), request(),
                request(), request())).toFuture().get();

        client.testResponseStream(new TestResponseStreamMetadata(encoding), request()).forEach(__ -> { /* noop */ });
        client.testBiDiStream(new TestBiDiStreamMetadata(encoding), from(request(), request(), request(),
                request(), request())).toFuture().get();
    }

    private void assertUnimplemented(final ContentCodec encoding) {
        assertThrowsGrpcStatusUnimplemented(() -> client.test(new TestMetadata(encoding), request()).toFuture().get());
        assertThrowsGrpcStatusUnimplemented(() -> client.testRequestStream(new TestRequestStreamMetadata(encoding),
                from(request(), request(), request(), request(), request())).toFuture().get());
        assertThrowsGrpcStatusUnimplemented(() -> client.testResponseStream(new TestResponseStreamMetadata(encoding),
                request()).toFuture().get().forEach(__ -> { /* noop */ }));
        assertThrowsGrpcStatusUnimplemented(() -> client.testBiDiStream(new TestBiDiStreamMetadata(encoding),
                from(request(), request(), request(), request(), request())).toFuture().get());
    }

    private void assertThrowsGrpcStatusUnimplemented(final Executable executable) {
        ExecutionException ex = assertThrows(ExecutionException.class, executable);
        assertThat(ex.getCause(), is(instanceOf(GrpcStatusException.class)));
        assertGrpcStatusException((GrpcStatusException) ex.getCause());
    }

    private static void assertGrpcStatusException(GrpcStatusException grpcStatusException) {
        assertThat(grpcStatusException.status().code(), is(GrpcStatusCode.UNIMPLEMENTED));
    }

    private static void assertValidCodingHeader(final List<ContentCodec> supportedEncodings,
                                                final HttpHeaders headers) {
        if (supportedEncodings.size() == 1 && supportedEncodings.contains(identity())) {
            assertThat(headers, not(contains(MESSAGE_ACCEPT_ENCODING)));
        } else {
            final List<String> actualRespAcceptedEncodings = stream(headers
                    .get(MESSAGE_ACCEPT_ENCODING, "NOT_PRESENT").toString().split(","))
                    .map((String::trim)).collect(toList());

            final List<String> expectedRespAcceptedEncodings = encodingsAsStrings(supportedEncodings);

            if (!expectedRespAcceptedEncodings.isEmpty() && !actualRespAcceptedEncodings.isEmpty()) {
                assertEquals(expectedRespAcceptedEncodings, actualRespAcceptedEncodings);
            }
        }
    }

    @Nonnull
    private static List<String> encodingsAsStrings(final List<ContentCodec> supportedEncodings) {
        return supportedEncodings.stream()
                .filter(enc -> !identity().equals(enc))
                .map(ContentCodec::name)
                .map(CharSequence::toString)
                .collect(toList());
    }

    static class TestEncodingScenario {
        final ContentCodec requestEncoding;
        final List<ContentCodec> clientSupported;
        final List<ContentCodec> serverSupported;

        TestEncodingScenario(final ContentCodec requestEncoding,
                             @Nullable final List<ContentCodec> clientSupported,
                             @Nullable final List<ContentCodec> serverSupported) {
            this.requestEncoding = requestEncoding;
            this.clientSupported = clientSupported == null ? singletonList(identity()) : clientSupported;
            this.serverSupported = serverSupported == null ? singletonList(identity()) : serverSupported;
        }
    }
}
