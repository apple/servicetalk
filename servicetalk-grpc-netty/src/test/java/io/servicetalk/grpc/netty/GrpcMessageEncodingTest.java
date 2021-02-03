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
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.encoding.api.ContentCodec;
import io.servicetalk.grpc.api.GrpcServerBuilder;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.api.GrpcStatusCode;
import io.servicetalk.grpc.api.GrpcStatusException;
import io.servicetalk.grpc.netty.TesterProto.TestRequest;
import io.servicetalk.grpc.netty.TesterProto.TestResponse;
import io.servicetalk.grpc.netty.TesterProto.Tester.TestRequestStreamMetadata;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.transport.api.ServerContext;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import java.util.function.Supplier;
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
import static io.servicetalk.buffer.api.Matchers.contentEqualTo;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.encoding.api.ContentCodings.deflateDefault;
import static io.servicetalk.encoding.api.ContentCodings.gzipDefault;
import static io.servicetalk.encoding.api.ContentCodings.identity;
import static io.servicetalk.encoding.api.internal.HeaderUtils.encodingFor;
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
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.internal.util.io.IOUtil.closeQuietly;

@RunWith(Parameterized.class)
public class GrpcMessageEncodingTest {

    private static final int PAYLOAD_SIZE = 512;
    static final ContentCodec CUSTOM_ENCODING = new ContentCodec() {

        private static final int OUGHT_TO_BE_ENOUGH = 1 << 20;

        @Override
        public String name() {
            return "CUSTOM_ENCODING";
        }

        @Override
        public Buffer encode(final Buffer src, final int offset, final int length,
                             final BufferAllocator allocator) {
            src.readerIndex(src.readerIndex() + offset);

            final Buffer dst = allocator.newBuffer(OUGHT_TO_BE_ENOUGH);
            DeflaterOutputStream output = null;
            try {
                output = new GZIPOutputStream(asOutputStream(dst));
                output.write(src.array(), src.arrayOffset() + src.readerIndex(), length);
                src.readerIndex(src.readerIndex() + length);
                output.finish();
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                closeQuietly(output);
            }

            return dst;
        }

        @Override
        public Buffer decode(final Buffer src, final int offset, final int length,
                             final BufferAllocator allocator) {
            src.readerIndex(src.readerIndex() + offset);

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

    private static final BiFunction<TestEncodingScenario, List<Throwable>, StreamingHttpServiceFilterFactory>
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

                                if (reqEncoding != identity()) {
                                    assertTrue("Compressed content length should be less than the " +
                                                    "original payload size", buffer.readableBytes() < PAYLOAD_SIZE);
                                } else {
                                    assertTrue("Uncompressed content length should be more than the " +
                                                    "original payload size", buffer.readableBytes() > PAYLOAD_SIZE);
                                }

                                assertEquals(reqEncoding != identity() ? 1 : 0, compressedFlag);
                            } catch (Throwable t) {
                                errors.add(t);
                                throw t;
                            }
                            return buffer;
                        })));

                        final List<String> actualReqAcceptedEncodings = stream(request.headers()
                                .get(MESSAGE_ACCEPT_ENCODING, "NOT_PRESENT").toString().split(","))
                                    .map((String::trim)).collect(toList());

                        final List<String> expectedReqAcceptedEncodings = encodingsAsStrings(clientSupportedEncodings);

                        assertTrue("Request encoding should be present in the request headers",
                                contentEquals(reqEncoding.name(), request.headers().get(MESSAGE_ENCODING, "identity")));
                        if (!expectedReqAcceptedEncodings.isEmpty() && !actualReqAcceptedEncodings.isEmpty()) {
                            assertEquals(expectedReqAcceptedEncodings, actualReqAcceptedEncodings);
                        }
                    } catch (Throwable t) {
                        errors.add(t);
                        throw t;
                    }

                    return super.handle(ctx, request, responseFactory).map((response -> {
                        try {
                            _handle(clientSupportedEncodings, serverSupportedEncodings, response);
                        } catch (Throwable t) {
                            errors.add(t);
                            throw t;
                        }

                        return response;
                    }));
                }

                private void _handle(final List<ContentCodec> clientSupportedEncodings,
                                     final List<ContentCodec> serverSupportedEncodings,
                                     final StreamingHttpResponse response) {
                    final List<String> actualRespAcceptedEncodings = stream(response.headers()
                            .get(MESSAGE_ACCEPT_ENCODING, "NOT_PRESENT").toString().split(","))
                            .map((String::trim)).collect(toList());

                    final List<String> expectedRespAcceptedEncodings = encodingsAsStrings(serverSupportedEncodings);

                    if (!expectedRespAcceptedEncodings.isEmpty() && !actualRespAcceptedEncodings.isEmpty()) {
                        assertEquals(expectedRespAcceptedEncodings, actualRespAcceptedEncodings);
                    }

                    final String respEncName = response.headers()
                            .get(MESSAGE_ENCODING, "identity").toString();

                    if (clientSupportedEncodings.isEmpty() || serverSupportedEncodings.isEmpty()) {
                        assertThat(identity().name(), contentEqualTo(respEncName));
                    } else {
                        if (disjoint(serverSupportedEncodings, clientSupportedEncodings)) {
                            assertEquals(identity().name().toString(), respEncName);
                        } else {
                            ContentCodec expected = identity();
                            for (ContentCodec codec : clientSupportedEncodings) {
                                if (serverSupportedEncodings.contains(codec)) {
                                    expected = codec;
                                    break;
                                }
                            }

                            assertEquals(expected, encodingFor(clientSupportedEncodings, response.headers()
                                    .get(MESSAGE_ENCODING, identity().name())));
                        }
                    }

                    response.transformPayloadBody(bufferPublisher -> bufferPublisher.map((buffer -> {
                        try {
                            final ContentCodec respEnc =
                                    encodingFor(clientSupportedEncodings, valueOf(response.headers()
                                            .get(MESSAGE_ENCODING, "identity")));

                            if (buffer.readableBytes() > 0) {
                                byte compressedFlag = buffer.getByte(0);
                                assertEquals(respEnc != identity() ? 1 : 0, compressedFlag);

                                if (respEnc == gzipDefault() || respEnc.name().equals(CUSTOM_ENCODING.name())) {
                                    int actualHeader = buffer.getShortLE(5) & 0xFFFF;
                                    assertEquals(GZIP_MAGIC, actualHeader);
                                }

                                if (respEnc != identity()) {
                                    assertTrue("Compressed content length should be less than the original " +
                                            "payload size", buffer.readableBytes() < PAYLOAD_SIZE);
                                } else {
                                    assertTrue("Uncompressed content length should be more than the original " +
                                                    "payload size " + buffer.readableBytes(),
                                            buffer.readableBytes() > PAYLOAD_SIZE);
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

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private final GrpcServerBuilder grpcServerBuilder;
    private final ServerContext serverContext;
    private final TesterClient client;
    private final ContentCodec requestEncoding;
    private final boolean expectedSuccess;
    private final List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

    public GrpcMessageEncodingTest(final List<ContentCodec> serverSupportedCodings,
                                   final List<ContentCodec> clientSupportedCodings,
                                   final ContentCodec requestEncoding,
                                   final boolean expectedSuccess) throws Exception {

        TestEncodingScenario options = new TestEncodingScenario(requestEncoding, clientSupportedCodings,
                serverSupportedCodings);

        grpcServerBuilder = GrpcServers.forAddress(localAddress(0));
        serverContext = listenAndAwait(options);
        client = newClient(clientSupportedCodings);
        this.requestEncoding = requestEncoding;
        this.expectedSuccess = expectedSuccess;
    }

    @Parameterized.Parameters(name = "server-supported-encodings={0} client-supported-encodings={1} " +
                                     "request-encoding={2} expected-success={3}")
    public static Object[][] params() {
        return new Object[][] {
                {null, null, identity(), true},
                {null, asList(gzipDefault(), identity()), gzipDefault(), false},
                {null, asList(deflateDefault(), identity()), deflateDefault(), false},
                {asList(gzipDefault(), deflateDefault(), identity()), null, identity(), true},
                {asList(identity(), gzipDefault(), deflateDefault()),
                        asList(gzipDefault(), identity()), gzipDefault(), true},
                {asList(identity(), gzipDefault(), deflateDefault()),
                        asList(deflateDefault(), identity()), deflateDefault(), true},
                {asList(identity(), gzipDefault()), asList(deflateDefault(), identity()), deflateDefault(), false},
                {asList(identity(), deflateDefault()), asList(gzipDefault(), identity()), gzipDefault(), false},
                {asList(identity(), deflateDefault()), asList(deflateDefault(), identity()), deflateDefault(), true},
                {asList(identity(), deflateDefault()), null, identity(), true},
                {singletonList(gzipDefault()), singletonList(identity()), identity(), true},
                {singletonList(gzipDefault()), asList(gzipDefault(), identity()), identity(), true},
                {singletonList(gzipDefault()), asList(gzipDefault(), identity()), identity(), true},
                {singletonList(gzipDefault()), asList(gzipDefault(), identity()), gzipDefault(), true},
                {singletonList(gzipDefault()), asList(deflateDefault(), gzipDefault()), gzipDefault(), true},
                {null, asList(gzipDefault(), identity()), gzipDefault(), false},
                {null, asList(gzipDefault(), deflateDefault(), identity()), deflateDefault(), false},
                {null, asList(gzipDefault(), identity()), identity(), true},
                {singletonList(CUSTOM_ENCODING), singletonList(CUSTOM_ENCODING), CUSTOM_ENCODING, true},
        };
    }

    @After
    public void tearDown() throws Exception {
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

    @Test
    public void test() throws Throwable {
        if (expectedSuccess) {
            assertSuccessful(requestEncoding);
        } else {
            assertUnimplemented(requestEncoding);
        }

        verifyNoErrors();
    }

    private void verifyNoErrors() throws Throwable {
        if (!errors.isEmpty()) {
            throw errors.get(0);
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

    private void assertThrowsGrpcStatusUnimplemented(final ThrowingRunnable runnable) {
        ExecutionException ex = assertThrows(ExecutionException.class, runnable);
        assertThat(ex.getCause(), is(instanceOf(GrpcStatusException.class)));
        assertGrpcStatusException((GrpcStatusException) ex.getCause());
    }

    private static void assertGrpcStatusException(GrpcStatusException grpcStatusException) {
        assertThat(grpcStatusException.status().code(), is(GrpcStatusCode.UNIMPLEMENTED));
    }

    @Nonnull
    private static List<String> encodingsAsStrings(final List<ContentCodec> supportedEncodings) {
        return supportedEncodings.stream()
                .filter(enc -> enc != identity())
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
