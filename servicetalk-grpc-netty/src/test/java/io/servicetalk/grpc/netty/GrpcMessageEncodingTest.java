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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.grpc.api.GrpcMessageCodec;
import io.servicetalk.grpc.api.GrpcMessageEncoding;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;
import javax.annotation.Nullable;

import static io.grpc.internal.GrpcUtil.MESSAGE_ACCEPT_ENCODING;
import static io.grpc.internal.GrpcUtil.MESSAGE_ENCODING;
import static io.servicetalk.buffer.api.Buffer.asInputStream;
import static io.servicetalk.buffer.api.Buffer.asOutputStream;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.grpc.api.GrpcExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.grpc.api.GrpcMessageEncodings.DEFLATE;
import static io.servicetalk.grpc.api.GrpcMessageEncodings.GZIP;
import static io.servicetalk.grpc.api.GrpcMessageEncodings.NONE;
import static io.servicetalk.grpc.api.GrpcMessageEncodings.encodingFor;
import static io.servicetalk.grpc.netty.TesterProto.Tester.ClientFactory;
import static io.servicetalk.grpc.netty.TesterProto.Tester.ServiceFactory;
import static io.servicetalk.grpc.netty.TesterProto.Tester.TestBiDiStreamMetadata;
import static io.servicetalk.grpc.netty.TesterProto.Tester.TestMetadata;
import static io.servicetalk.grpc.netty.TesterProto.Tester.TestResponseStreamMetadata;
import static io.servicetalk.grpc.netty.TesterProto.Tester.TesterClient;
import static io.servicetalk.grpc.netty.TesterProto.Tester.TesterService;
import static io.servicetalk.http.api.CharSequences.contentEquals;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.lang.String.valueOf;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Collections.disjoint;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static java.util.zip.GZIPInputStream.GZIP_MAGIC;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.internal.util.io.IOUtil.closeQuietly;

@RunWith(Parameterized.class)
public class GrpcMessageEncodingTest {

    private static final int PAYLOAD_SIZE = 512;
    static final GrpcMessageEncoding CUSTOM_ENCODING = new GrpcMessageEncoding() {
        @Override
        public String name() {
            return "CUSTOM_ENCODING";
        }

        @Override
        public GrpcMessageCodec codec() {
            return new GrpcMessageCodec() {
                private static final int OUGHT_TO_BE_ENOUGH = 1 << 20;

                @Override
                public Buffer encode(final Buffer src, final int offset, final int length,
                                           final BufferAllocator allocator) {
                    final Buffer dst = allocator.newBuffer(OUGHT_TO_BE_ENOUGH);
                    DeflaterOutputStream output = null;
                    try {
                        output = new GZIPOutputStream(asOutputStream(dst));
                        output.write(src.array(), offset, length);
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
            };
        }

        @Override
        public String toString() {
            return "GrpcMessageEncoding{encoding='CUSTOM_ENCODING'}";
        }
    };

    private static final Function<TestEncodingScenario, StreamingHttpServiceFilterFactory> REQ_RESP_VERIFIER = (options)
                        -> new StreamingHttpServiceFilterFactory() {
        @Override
        public StreamingHttpServiceFilter create(final StreamingHttpService service) {
            return new StreamingHttpServiceFilter(service) {
                @Override

                public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                            final StreamingHttpRequest request,
                                                            final StreamingHttpResponseFactory responseFactory) {
                    final GrpcMessageEncoding requestEncoding = options.requestEncoding;
                    final Set<GrpcMessageEncoding> clientSupportedEncodings = options.clientSupported;
                    final Set<GrpcMessageEncoding> serverSupportedEncodings = options.serverSupported;

                    try {

                        request.transformPayloadBody(bufferPublisher -> bufferPublisher.map((buffer -> {
                            try {
                                byte compressedFlag = buffer.getByte(0);

                                if (requestEncoding == GZIP || requestEncoding.name().equals(CUSTOM_ENCODING.name())) {
                                    int actualHeader = buffer.getShortLE(5) & 0xFFFF;
                                    assertEquals(GZIP_MAGIC, actualHeader);
                                }

                                if (requestEncoding != NONE) {
                                    assertTrue("Compressed content length should be less than the " +
                                                    "original payload size", buffer.readableBytes() < PAYLOAD_SIZE);
                                } else {
                                    assertTrue("Uncompressed content length should be more than the " +
                                                    "original payload size", buffer.readableBytes() > PAYLOAD_SIZE);
                                }

                                assertEquals(requestEncoding != NONE ? 1 : 0, compressedFlag);
                            } catch (Throwable t) {
                                t.printStackTrace();
                                throw t;
                            }
                            return buffer;
                        })));

                        final List<String> actualReqAcceptedEncodings = stream(request.headers()
                                .get(MESSAGE_ACCEPT_ENCODING, "NOT_PRESENT").toString().split(","))
                                    .map((String::trim)).collect(toList());

                        final List<String> expectedReqAcceptedEncodings = (clientSupportedEncodings == null) ?
                                        emptyList() :
                                        clientSupportedEncodings.stream()
                                                .filter((enc) -> enc != NONE)
                                                .map((GrpcMessageEncoding::name))
                                                .collect(toList());

                        assertTrue("Request encoding should be present in the request headers",
                                contentEquals(requestEncoding.name(), request.headers().get(MESSAGE_ENCODING, "null")));
                        if (!expectedReqAcceptedEncodings.isEmpty() && !actualReqAcceptedEncodings.isEmpty()) {
                            assertEquals(expectedReqAcceptedEncodings, actualReqAcceptedEncodings);
                        }
                    } catch (Throwable t) {
                        t.printStackTrace();
                        throw t;
                    }

                    return super.handle(ctx, request, responseFactory).map((response -> {
                        try {
                            final List<String> actualRespAcceptedEncodings = stream(response.headers()
                                    .get(MESSAGE_ACCEPT_ENCODING, "NOT_PRESENT").toString().split(","))
                                    .map((String::trim)).collect(toList());

                            final List<String> expectedRespAcceptedEncodings = (serverSupportedEncodings == null) ?
                                    emptyList() :
                                    serverSupportedEncodings.stream()
                                            .filter((enc) -> enc != NONE)
                                            .map((GrpcMessageEncoding::name))
                                            .collect(toList());

                            if (!expectedRespAcceptedEncodings.isEmpty() && !actualRespAcceptedEncodings.isEmpty()) {
                                assertEquals(expectedRespAcceptedEncodings, actualRespAcceptedEncodings);
                            }

                            final String respEncName = response.headers()
                                    .get(MESSAGE_ENCODING, "identity").toString();

                            if (clientSupportedEncodings == null) {
                                assertEquals(NONE.name(), respEncName);
                            } else if (serverSupportedEncodings == null) {
                                assertEquals(NONE.name(), respEncName);
                            } else {
                                if (disjoint(serverSupportedEncodings, clientSupportedEncodings)) {
                                    assertEquals(NONE.name(), respEncName);
                                } else {
                                    assertNotNull("Response encoding not in the client supported list " +
                                                    "[" + clientSupportedEncodings + "]",
                                            encodingFor(clientSupportedEncodings, valueOf(response.headers()
                                                    .get(MESSAGE_ENCODING, "identity"))));

                                    assertNotNull("Response encoding not in the server supported list " +
                                                    "[" + serverSupportedEncodings + "]",
                                            encodingFor(serverSupportedEncodings, valueOf(response.headers()
                                            .get(MESSAGE_ENCODING, "identity"))));
                                }
                            }

                            response.transformPayloadBody(bufferPublisher -> bufferPublisher.map((buffer -> {
                                try {
                                    final GrpcMessageEncoding respEnc =
                                            encodingFor(clientSupportedEncodings == null ? of(NONE) :
                                                    clientSupportedEncodings, valueOf(response.headers()
                                                    .get(MESSAGE_ENCODING, "identity")));

                                    if (buffer.readableBytes() > 0) {
                                        byte compressedFlag = buffer.getByte(0);
                                        assertEquals(respEnc != NONE ? 1 : 0, compressedFlag);

                                        if (respEnc == GZIP || respEnc.name().equals(CUSTOM_ENCODING.name())) {
                                            int actualHeader = buffer.getShortLE(5) & 0xFFFF;
                                            assertEquals(GZIP_MAGIC, actualHeader);
                                        }

                                        if (respEnc != NONE) {
                                            assertTrue("Compressed content length should be less than the original " +
                                                    "payload size", buffer.readableBytes() < PAYLOAD_SIZE);
                                        } else {
                                            assertTrue("Uncompressed content length should be more than the original " +
                                                            "payload size " + buffer.readableBytes(),
                                                    buffer.readableBytes() > PAYLOAD_SIZE);
                                        }
                                    }
                                } catch (Throwable t) {
                                    t.printStackTrace();
                                    throw t;
                                }
                                return buffer;
                            })));
                        } catch (Throwable t) {
                            t.printStackTrace();
                            throw t;
                        }

                        return response;
                    }));
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
    private final GrpcMessageEncoding requestEncoding;
    private final boolean expectedSuccess;

    public GrpcMessageEncodingTest(final Set<GrpcMessageEncoding> serverSupportedEncodings,
                                   final Set<GrpcMessageEncoding> clientSupportedEncodings,
                                   final GrpcMessageEncoding requestEncoding,
                                   final boolean expectedSuccess) throws Exception {

        TestEncodingScenario options = new TestEncodingScenario(requestEncoding, clientSupportedEncodings,
                serverSupportedEncodings);

        grpcServerBuilder = GrpcServers.forAddress(localAddress(0));
        serverContext = listenAndAwait(options);
        client = newClient(clientSupportedEncodings);
        this.requestEncoding = requestEncoding;
        this.expectedSuccess = expectedSuccess;
    }

    @Parameterized.Parameters(name = "server-supported-encodings={0} client-supported-encodings={1} " +
                                     "request-encoding={2} expected-success={3}")
    public static Object[][] params() {
        return new Object[][] {
                {null, null, NONE, true},
                {null, of(GZIP, NONE), GZIP, false},
                {null, of(DEFLATE, NONE), DEFLATE, false},
                {of(GZIP, DEFLATE, NONE), null, NONE, true},
                {of(NONE, GZIP, DEFLATE), of(GZIP, NONE), GZIP, true},
                {of(NONE, GZIP, DEFLATE), of(DEFLATE, NONE), DEFLATE, true},
                {of(NONE, GZIP), of(DEFLATE, NONE), DEFLATE, false},
                {of(NONE, DEFLATE), of(GZIP, NONE), GZIP, false},
                {of(NONE, DEFLATE), of(DEFLATE, NONE), DEFLATE, true},
                {of(NONE, DEFLATE), null, NONE, true},
                {of(GZIP), of(NONE), NONE, true},
                {of(GZIP), of(GZIP, NONE), NONE, true},
                {of(GZIP), of(GZIP, NONE), NONE, true},
                {of(GZIP), of(GZIP, NONE), GZIP, true},
                {null, of(GZIP, NONE), GZIP, false},
                {null, of(GZIP, DEFLATE, NONE), DEFLATE, false},
                {null, of(GZIP, NONE), NONE, true},
                {of(CUSTOM_ENCODING), of(CUSTOM_ENCODING), CUSTOM_ENCODING, true},
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

        StreamingHttpServiceFilterFactory filterFactory = REQ_RESP_VERIFIER.apply(encodingOptions);
        if (encodingOptions.serverSupported == null) {
            return grpcServerBuilder.appendHttpServiceFilter(filterFactory)
                                             .listenAndAwait(new ServiceFactory(new TesterServiceImpl()));
        } else {
            return grpcServerBuilder.appendHttpServiceFilter(filterFactory)
                                             .listenAndAwait(new ServiceFactory(new TesterServiceImpl(),
                                                     encodingOptions.serverSupported));
        }
    }

    private TesterClient newClient(@Nullable final Set<GrpcMessageEncoding> supportedEncodings) {
        return GrpcClients.forAddress(serverHostAndPort(serverContext))
                .executionStrategy(noOffloadsStrategy())
                .build(supportedEncodings != null ?
                        new ClientFactory().supportedEncodings(supportedEncodings) :
                        new ClientFactory());
    }

    @Test
    public void test() throws ExecutionException, InterruptedException {
        if (expectedSuccess) {
            assertSuccessful(requestEncoding);
        } else {
            assertUnimplemented(requestEncoding);
        }
    }

    private static TestRequest request() {
        byte[] payload = new byte[PAYLOAD_SIZE];
        Arrays.fill(payload, (byte) 'a');
        String name = new String(payload, StandardCharsets.US_ASCII);
        return TestRequest.newBuilder().setName(name).build();
    }

    private void assertSuccessful(final GrpcMessageEncoding encoding) throws ExecutionException, InterruptedException {
        client.test(new TestMetadata(encoding), request()).toFuture().get();
        client.testRequestStream(new TestRequestStreamMetadata(encoding), from(request(), request(), request(),
                request(), request())).toFuture().get();

        client.testResponseStream(new TestResponseStreamMetadata(encoding), request()).forEach(__ -> { /* noop */ });
        client.testBiDiStream(new TestBiDiStreamMetadata(encoding), from(request(), request(), request(),
                request(), request())).toFuture().get();
    }

    private void assertUnimplemented(final GrpcMessageEncoding encoding) {
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

    private static Set<GrpcMessageEncoding> of(GrpcMessageEncoding... encodings) {
        return new HashSet<>(asList(encodings));
    }

    static class TestEncodingScenario {
        final GrpcMessageEncoding requestEncoding;
        @Nullable
        final Set<GrpcMessageEncoding> clientSupported;
        @Nullable
        final Set<GrpcMessageEncoding> serverSupported;

        TestEncodingScenario(final GrpcMessageEncoding requestEncoding,
                             final Set<GrpcMessageEncoding> clientSupported,
                             final Set<GrpcMessageEncoding> serverSupported) {
            this.requestEncoding = requestEncoding;
            this.clientSupported = clientSupported;
            this.serverSupported = serverSupported;
        }
    }
}
