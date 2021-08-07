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
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.BlockingStreamingHttpConnection;
import io.servicetalk.http.api.BlockingStreamingHttpRequest;
import io.servicetalk.http.api.BlockingStreamingHttpResponse;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.HttpApiConversions;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpMetaData;
import io.servicetalk.http.api.HttpPayloadWriter;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.StatelessTrailersTransformer;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Publisher.empty;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HeaderUtils.isTransferEncodingChunked;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethod.CONNECT;
import static io.servicetalk.http.api.HttpRequestMethod.DELETE;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.HttpRequestMethod.HEAD;
import static io.servicetalk.http.api.HttpRequestMethod.OPTIONS;
import static io.servicetalk.http.api.HttpRequestMethod.PATCH;
import static io.servicetalk.http.api.HttpRequestMethod.POST;
import static io.servicetalk.http.api.HttpRequestMethod.PUT;
import static io.servicetalk.http.api.HttpRequestMethod.TRACE;
import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatus.NO_CONTENT;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializers.appSerializerUtf8FixLen;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static io.servicetalk.http.api.StreamingHttpRequests.newRequest;
import static io.servicetalk.http.api.StreamingHttpResponses.newResponse;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED;
import static io.servicetalk.http.netty.ContentHeadersTest.Expectation.HAVE_CONTENT_LENGTH;
import static io.servicetalk.http.netty.ContentHeadersTest.Expectation.HAVE_CONTENT_LENGTH_ZERO;
import static io.servicetalk.http.netty.ContentHeadersTest.Expectation.HAVE_EXISTING_CONTENT_LENGTH;
import static io.servicetalk.http.netty.ContentHeadersTest.Expectation.HAVE_NEITHER;
import static io.servicetalk.http.netty.ContentHeadersTest.Expectation.HAVE_TRANSFER_ENCODING_CHUNKED;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.function.UnaryOperator.identity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

class ContentHeadersTest extends AbstractNettyHttpServerTest {

    private static final DefaultHttpHeadersFactory headersFactory = new DefaultHttpHeadersFactory(false, false);
    private static final String EXISTING_CONTENT = "Hello World!";
    private static final int EXISTING_CONTENT_LENGTH = EXISTING_CONTENT.length();
    private static final String PAYLOAD = "Hello";
    private static final int PAYLOAD_LENGTH = PAYLOAD.length();

    private TestType testDefinition;

    private void setUp(final TestType testDefinition) {
        this.testDefinition = testDefinition;
        setUp(CACHED, CACHED);
    }

    static Collection<TestType> parameters() {
        return Arrays.asList(
                // ----- Request -----
                new RequestTest(aggregatedRequest(GET), defaults(), HAVE_CONTENT_LENGTH),
                new RequestTest(aggregatedRequest(HEAD), defaults(), HAVE_CONTENT_LENGTH),
                new RequestTest(aggregatedRequest(POST), defaults(), HAVE_CONTENT_LENGTH),
                new RequestTest(aggregatedRequest(PUT), defaults(), HAVE_CONTENT_LENGTH),
                new RequestTest(aggregatedRequest(DELETE), defaults(), HAVE_CONTENT_LENGTH),
                new RequestTest(aggregatedRequest(CONNECT), defaults(), HAVE_CONTENT_LENGTH),
                new RequestTest(aggregatedRequest(OPTIONS), defaults(), HAVE_CONTENT_LENGTH),
                new RequestTest(aggregatedRequest(PATCH), defaults(), HAVE_CONTENT_LENGTH),

                new RequestTest(aggregatedRequest(GET), withoutPayload(), HAVE_NEITHER),
                new RequestTest(aggregatedRequest(HEAD), withoutPayload(), HAVE_NEITHER),
                new RequestTest(aggregatedRequest(POST), withoutPayload(), HAVE_CONTENT_LENGTH_ZERO),
                new RequestTest(aggregatedRequest(PUT), withoutPayload(), HAVE_CONTENT_LENGTH_ZERO),
                new RequestTest(aggregatedRequest(DELETE), withoutPayload(), HAVE_NEITHER),
                new RequestTest(aggregatedRequest(CONNECT), withoutPayload(), HAVE_NEITHER),
                new RequestTest(aggregatedRequest(OPTIONS), withoutPayload(), HAVE_NEITHER),
                new RequestTest(aggregatedRequest(TRACE), withoutPayload(), HAVE_NEITHER),
                new RequestTest(aggregatedRequest(PATCH), withoutPayload(), HAVE_CONTENT_LENGTH_ZERO),

                new RequestTest(aggregatedRequest(GET), transferEncodingGzip(), HAVE_CONTENT_LENGTH),
                new RequestTest(aggregatedRequest(GET), transferEncodingChunked(), HAVE_TRANSFER_ENCODING_CHUNKED),
                new RequestTest(aggregatedRequest(GET), transferEncodingGzipAndChunked(),
                        HAVE_TRANSFER_ENCODING_CHUNKED),
                new RequestTest(aggregatedRequest(GET), contentLength(), HAVE_EXISTING_CONTENT_LENGTH),
                new RequestTest(aggregatedRequest(GET), trailers(), HAVE_TRANSFER_ENCODING_CHUNKED),
                new RequestTest(aggregatedRequestAsStreaming(GET), transform(), HAVE_TRANSFER_ENCODING_CHUNKED),
                new RequestTest(streamingRequest(GET), defaults(), HAVE_TRANSFER_ENCODING_CHUNKED),
                new RequestTest(streamingRequest(GET), withoutPayload(), HAVE_NEITHER),

                new BlockingRequestTest(aggregatedRequest(GET), defaults(), HAVE_CONTENT_LENGTH),
                new BlockingRequestTest(aggregatedRequestAsStreaming(GET), defaults(), HAVE_CONTENT_LENGTH),
                new BlockingRequestTest(streamingRequest(GET), defaults(), HAVE_TRANSFER_ENCODING_CHUNKED),
                new BlockingRequestTest(aggregatedRequest(GET), trailers(), HAVE_TRANSFER_ENCODING_CHUNKED),
                new BlockingRequestTest(streamingRequest(GET), withoutPayload(), HAVE_NEITHER),

                // ----- Response -----
                new ResponseTest(aggregatedResponse(OK), GET, defaults(), HAVE_CONTENT_LENGTH),
                new ResponseTest(aggregatedResponse(OK), HEAD, defaults(), HAVE_NEITHER),
                new ResponseTest(aggregatedResponse(OK), POST, defaults(), HAVE_CONTENT_LENGTH),
                new ResponseTest(aggregatedResponse(OK), PUT, defaults(), HAVE_CONTENT_LENGTH),
                new ResponseTest(aggregatedResponse(OK), DELETE, defaults(), HAVE_CONTENT_LENGTH),
                new ResponseTest(aggregatedResponse(OK), CONNECT, defaults(), HAVE_NEITHER),
                new ResponseTest(aggregatedResponse(OK), OPTIONS, defaults(), HAVE_CONTENT_LENGTH),
                new ResponseTest(aggregatedResponse(OK), TRACE, defaults(), HAVE_CONTENT_LENGTH),
                new ResponseTest(aggregatedResponse(OK), PATCH, defaults(), HAVE_CONTENT_LENGTH),

                new ResponseTest(aggregatedResponse(OK), GET, withoutPayload(), HAVE_CONTENT_LENGTH_ZERO),
                new ResponseTest(aggregatedResponse(OK), HEAD, withoutPayload(), HAVE_NEITHER),
                new ResponseTest(aggregatedResponse(OK), POST, withoutPayload(), HAVE_CONTENT_LENGTH_ZERO),
                new ResponseTest(aggregatedResponse(OK), PUT, withoutPayload(), HAVE_CONTENT_LENGTH_ZERO),
                new ResponseTest(aggregatedResponse(OK), DELETE, withoutPayload(), HAVE_CONTENT_LENGTH_ZERO),
                new ResponseTest(aggregatedResponse(OK), CONNECT, withoutPayload(), HAVE_NEITHER),
                new ResponseTest(aggregatedResponse(OK), OPTIONS, withoutPayload(), HAVE_CONTENT_LENGTH_ZERO),
                new ResponseTest(aggregatedResponse(OK), TRACE, withoutPayload(), HAVE_CONTENT_LENGTH_ZERO),
                new ResponseTest(aggregatedResponse(OK), PATCH, withoutPayload(), HAVE_CONTENT_LENGTH_ZERO),

                new ResponseTest(aggregatedResponse(NO_CONTENT), GET, withoutPayload(), HAVE_NEITHER),
                new ResponseTest(aggregatedResponse(NO_CONTENT), HEAD, withoutPayload(), HAVE_NEITHER),
                new ResponseTest(aggregatedResponse(NO_CONTENT), POST, withoutPayload(), HAVE_NEITHER),
                new ResponseTest(aggregatedResponse(NO_CONTENT), PUT, withoutPayload(), HAVE_NEITHER),
                new ResponseTest(aggregatedResponse(NO_CONTENT), DELETE, withoutPayload(), HAVE_NEITHER),
                new ResponseTest(aggregatedResponse(NO_CONTENT), CONNECT, withoutPayload(), HAVE_NEITHER),
                new ResponseTest(aggregatedResponse(NO_CONTENT), OPTIONS, withoutPayload(), HAVE_NEITHER),
                new ResponseTest(aggregatedResponse(NO_CONTENT), TRACE, withoutPayload(), HAVE_NEITHER),
                new ResponseTest(aggregatedResponse(NO_CONTENT), PATCH, withoutPayload(), HAVE_NEITHER),

                new ResponseTest(aggregatedResponse(OK), GET, transferEncodingGzip(), HAVE_CONTENT_LENGTH),
                new ResponseTest(aggregatedResponse(OK), GET, transferEncodingChunked(),
                        HAVE_TRANSFER_ENCODING_CHUNKED),
                new ResponseTest(aggregatedResponse(OK), GET, transferEncodingGzipAndChunked(),
                        HAVE_TRANSFER_ENCODING_CHUNKED),
                new ResponseTest(aggregatedResponse(OK), GET, contentLength(), HAVE_EXISTING_CONTENT_LENGTH),
                new ResponseTest(aggregatedResponse(OK), GET, trailers(), HAVE_TRANSFER_ENCODING_CHUNKED),
                new ResponseTest(aggregatedResponse(INTERNAL_SERVER_ERROR), CONNECT, defaults(), HAVE_CONTENT_LENGTH),
                new ResponseTest(aggregatedResponseAsStreaming(OK), GET, transform(), HAVE_TRANSFER_ENCODING_CHUNKED),
                new ResponseTest(streamingResponse(OK), GET, defaults(), HAVE_TRANSFER_ENCODING_CHUNKED),
                new ResponseTest(streamingResponse(OK), GET, withoutPayload(), HAVE_CONTENT_LENGTH_ZERO),

                new BlockingResponseTest(aggregatedResponse(OK), GET, defaults(), HAVE_CONTENT_LENGTH),
                new BlockingResponseTest(aggregatedResponseAsStreaming(OK), GET, defaults(), HAVE_CONTENT_LENGTH),
                new BlockingResponseTest(streamingResponse(OK), GET, defaults(), HAVE_TRANSFER_ENCODING_CHUNKED),
                new BlockingResponseTest(aggregatedResponse(OK), GET, trailers(), HAVE_TRANSFER_ENCODING_CHUNKED),
                new BlockingResponseTest(streamingResponse(OK), GET, withoutPayload(), HAVE_TRANSFER_ENCODING_CHUNKED)
        );
    }

    private static Supplier<HttpRequestMetaData> aggregatedRequest(final HttpRequestMethod requestMethod) {
        return describe(() -> newAggregatedRequest(requestMethod), "Aggregated" + requestMethod + "Request");
    }

    private static Supplier<HttpRequestMetaData> aggregatedRequestAsStreaming(final HttpRequestMethod requestMethod) {
        return describe(() -> newAggregatedRequest(requestMethod).toStreamingRequest(),
                "Aggregated" + requestMethod + "RequestAsStreaming");
    }

    private static Supplier<HttpRequestMetaData> streamingRequest(final HttpRequestMethod requestMethod) {
        return describe(() -> newStreamingRequest(requestMethod), "Streaming" + requestMethod + "Request");
    }

    private static Supplier<HttpResponseMetaData> aggregatedResponse(final HttpResponseStatus status) {
        return describe(() -> newAggregatedResponse(status), "Aggregated" + status.code() + "Response");
    }

    private static Supplier<HttpResponseMetaData> aggregatedResponseAsStreaming(final HttpResponseStatus status) {
        return describe(() -> newAggregatedResponse(status).toStreamingResponse(), "Aggregated" + status.code() +
                "ResponseAsStreaming");
    }

    private static Supplier<HttpResponseMetaData> streamingResponse(final HttpResponseStatus status) {
        return describe(() -> newStreamingResponse(status), "Streaming" + status.code() + "Response");
    }

    private static <T> UnaryOperator<T> defaults() {
        return describe(identity(), "Defaults");
    }

    private static UnaryOperator<HttpMetaData> withoutPayload() {
        return describe(input -> {
            if (input instanceof HttpRequest) {
                return ((HttpRequest) input).payloadBody(DEFAULT_ALLOCATOR.newBuffer(0));
            } else if (input instanceof HttpResponse) {
                return ((HttpResponse) input).payloadBody(DEFAULT_ALLOCATOR.newBuffer(0));
            } else if (input instanceof StreamingHttpRequest) {
                return ((StreamingHttpRequest) input).payloadBody(empty());
            } else if (input instanceof StreamingHttpResponse) {
                return ((StreamingHttpResponse) input).payloadBody(empty());
            } else {
                fail("Unexpected metadata type: " + input.getClass());
                throw new IllegalStateException();
            }
        }, "WithoutPayload");
    }

    private static UnaryOperator<HttpMetaData> transferEncodingGzip() {
        return describe(input -> {
            input.headers().set(TRANSFER_ENCODING, "gzip");
            return input;
        }, "ExistingTransferEncodingGzip");
    }

    private static UnaryOperator<HttpMetaData> transferEncodingGzipAndChunked() {
        return describe(input -> {
            input.headers().set(TRANSFER_ENCODING, "gzip");
            input.headers().add(TRANSFER_ENCODING, "chunked");
            return input;
        }, "ExistingTransferEncodingGzipAndChunked");
    }

    private static UnaryOperator<HttpMetaData> transferEncodingChunked() {
        return describe(input -> {
            input.headers().set(TRANSFER_ENCODING, CHUNKED);
            return input;
        }, "ExistingTransferEncodingChunked");
    }

    private static UnaryOperator<HttpMetaData> contentLength() {
        return describe(input -> {
            input.headers().set(CONTENT_LENGTH, Integer.toString(EXISTING_CONTENT_LENGTH));
            if (input instanceof HttpRequest) {
                return ((HttpRequest) input).payloadBody(EXISTING_CONTENT, textSerializerUtf8());
            } else if (input instanceof HttpResponse) {
                return ((HttpResponse) input).payloadBody(EXISTING_CONTENT, textSerializerUtf8());
            } else {
                fail("Unexpected metadata type: " + input.getClass());
                throw new IllegalStateException();
            }
        }, "ExistingContentLength");
    }

    private static UnaryOperator<HttpMetaData> trailers() {
        return describe(input -> {
            if (input instanceof HttpRequest) {
                ((HttpRequest) input).trailers().set("foo", "bar");
            } else if (input instanceof HttpResponse) {
                ((HttpResponse) input).trailers().set("foo", "bar");
            } else {
                fail("Unexpected metadata type: " + input.getClass());
            }
            return input;
        }, "Trailers");
    }

    private static UnaryOperator<HttpMetaData> transform() {
        return describe(input -> {
            if (input instanceof StreamingHttpRequest) {
                return ((StreamingHttpRequest) input).transform(new StatelessTrailersTransformer<>());
            } else if (input instanceof StreamingHttpResponse) {
                return ((StreamingHttpResponse) input).transform(new StatelessTrailersTransformer<>());
            } else {
                fail("Unexpected metadata type: " + input.getClass());
                throw new IllegalStateException();
            }
        }, "Transform");
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void integrationTest(TestType testType) throws Exception {
        setUp(testType);
        testDefinition.runTest(streamingHttpConnection());
    }

    @Override
    Single<ServerContext> listen(final HttpServerBuilder builder) {
        return testDefinition.listen(builder);
    }

    private static HttpRequest newAggregatedRequest(final HttpRequestMethod requestMethod) {
        return awaitSingleIndefinitelyNonNull(newRequest(requestMethod, "/", HTTP_1_1,
                headersFactory.newHeaders(), DEFAULT_ALLOCATOR, headersFactory).toRequest())
                .payloadBody(PAYLOAD, textSerializerUtf8());
    }

    private static StreamingHttpRequest newStreamingRequest(final HttpRequestMethod requestMethod) {
        return newRequest(requestMethod, "/", HTTP_1_1, headersFactory.newHeaders(),
                DEFAULT_ALLOCATOR, headersFactory).payloadBody(from(PAYLOAD), appSerializerUtf8FixLen());
    }

    private static HttpResponse newAggregatedResponse(final HttpResponseStatus status) {
        return awaitSingleIndefinitelyNonNull(newResponse(status, HTTP_1_1, headersFactory.newHeaders(),
                DEFAULT_ALLOCATOR, headersFactory).toResponse()).payloadBody(PAYLOAD, textSerializerUtf8());
    }

    private static StreamingHttpResponse newStreamingResponse(final HttpResponseStatus status) {
        return newResponse(status, HTTP_1_1, headersFactory.newHeaders(),
                DEFAULT_ALLOCATOR, headersFactory)
                .payloadBody(from(PAYLOAD), appSerializerUtf8FixLen());
    }

    private static <T> UnaryOperator<T> describe(UnaryOperator<T> operator, String description) {
        return new UnaryOperator<T>() {

            @Override
            public T apply(final T input) {
                return operator.apply(input);
            }

            @Override
            public String toString() {
                return description;
            }
        };
    }

    private static <T> Supplier<T> describe(Supplier<T> supplier, String description) {
        return new Supplier<T>() {

            @Override
            public T get() {
                return supplier.get();
            }

            @Override
            public String toString() {
                return description;
            }
        };
    }

    @Nullable
    static String assertTransferEncoding(final HttpHeaders headers) {
        if (headers.contains(CONTENT_LENGTH)) {
            return "content-length present";
        }
        if (!isTransferEncodingChunked(headers)) {
            return "No transfer-encoding: chunked";
        }
        return null;
    }

    @Nullable
    static String assertContentLength(final HttpHeaders headers, final int contentLength) {
        if (isTransferEncodingChunked(headers)) {
            return "transfer-encoding present";
        }
        if (!headers.contains(CONTENT_LENGTH)) {
            return "No content-length";
        }
        if (!headers.contains(CONTENT_LENGTH, Integer.toString(contentLength))) {
            return "Incorrect content-length (expected " + contentLength + " was " + headers.get(CONTENT_LENGTH) + ")";
        }
        return null;
    }

    @Nullable
    static String assertNeither(final HttpHeaders headers) {
        if (isTransferEncodingChunked(headers)) {
            return "transfer-encoding present";
        }
        if (headers.contains(CONTENT_LENGTH)) {
            return "content-length present";
        }
        return null;
    }

    private abstract static class TestType {
        final UnaryOperator<HttpMetaData> modifier;
        final Expectation expectation;

        TestType(final UnaryOperator<HttpMetaData> modifier, final Expectation expectation) {
            this.modifier = modifier;
            this.expectation = expectation;
        }

        abstract Single<ServerContext> listen(HttpServerBuilder builder);

        abstract void runTest(StreamingHttpConnection connection) throws Exception;
    }

    private static class RequestTest extends TestType {
        final Supplier<HttpRequestMetaData> requestSupplier;

        RequestTest(final Supplier<HttpRequestMetaData> requestSupplier,
                    final UnaryOperator<HttpMetaData> modifier,
                    final Expectation expectation) {
            super(modifier, expectation);
            this.requestSupplier = requestSupplier;
        }

        @Override
        public String toString() {
            return requestSupplier + "With" + modifier + expectation;
        }

        @Override
        Single<ServerContext> listen(final HttpServerBuilder builder) {
            // service needs to check the request
            return builder.listenStreaming((ctx, request, rf) -> {
                final HttpHeaders headers = request.headers();
                final String failure = expectation.assertHeaders(headers);
                if (failure != null) {
                    return succeeded(rf.internalServerError().payloadBody(
                            from(failure),
                            appSerializerUtf8FixLen()));
                }
                return succeeded(rf.noContent());
            });
        }

        @Override
        void runTest(final StreamingHttpConnection connection) throws Exception {
            // test needs to create the request
            final HttpMetaData metadata = modifier.apply(requestSupplier.get());
            final StreamingHttpRequest request = metadata instanceof StreamingHttpRequest ?
                    (StreamingHttpRequest) metadata : ((HttpRequest) metadata).toStreamingRequest();
            final StreamingHttpResponse response = connection.request(request).toFuture().get();
            assertThat(response.toResponse().toFuture().get().payloadBody().toString(UTF_8),
                    response.status(), is(NO_CONTENT));
        }
    }

    private static final class BlockingRequestTest extends RequestTest {

        private BlockingRequestTest(final Supplier<HttpRequestMetaData> requestSupplier,
                                    final UnaryOperator<HttpMetaData> modifier,
                                    final Expectation expectation) {
            super(requestSupplier, modifier, expectation);
        }

        @Override
        public String toString() {
            return "Blocking" + super.toString();
        }

        @Override
        void runTest(final StreamingHttpConnection connection) throws Exception {
            final BlockingStreamingHttpConnection blockingConnection = connection.asBlockingStreamingConnection();
            // test needs to create the request
            final HttpMetaData metadata = modifier.apply(requestSupplier.get());
            final BlockingStreamingHttpRequest request = metadata instanceof StreamingHttpRequest ?
                    ((StreamingHttpRequest) metadata).toBlockingStreamingRequest() :
                    ((HttpRequest) metadata).toBlockingStreamingRequest();
            final BlockingStreamingHttpResponse response = blockingConnection.request(request);
            assertThat(response.toResponse().toFuture().get().payloadBody().toString(UTF_8),
                    response.status(), is(NO_CONTENT));
        }
    }

    private static class ResponseTest extends TestType {
        final Supplier<HttpResponseMetaData> responseSupplier;
        final HttpRequestMethod requestMethod;

        ResponseTest(final Supplier<HttpResponseMetaData> responseSupplier,
                     final HttpRequestMethod requestMethod,
                     final UnaryOperator<HttpMetaData> modifier,
                     final Expectation expectation) {
            super(modifier, expectation);
            this.responseSupplier = responseSupplier;
            this.requestMethod = requestMethod;
        }

        @Override
        public String toString() {
            return responseSupplier + "To" + requestMethod + "With" + modifier + expectation;
        }

        @Override
        Single<ServerContext> listen(final HttpServerBuilder builder) {
            // service needs to generate the response
            return builder.listenStreaming((ctx, request, rf) -> {
                final HttpMetaData metadata = modifier.apply(responseSupplier.get());
                return succeeded((metadata instanceof StreamingHttpResponse) ?
                        (StreamingHttpResponse) metadata : ((HttpResponse) metadata).toStreamingResponse());
            });
        }

        @Override
        void runTest(final StreamingHttpConnection connection) throws Exception {
            // test needs to check the response
            final StreamingHttpResponse response = connection.request(
                    newStreamingRequest(requestMethod).payloadBody(empty())).toFuture().get();
            final HttpHeaders headers = response.headers();
            assertNull(expectation.assertHeaders(headers));
            response.payloadBody().toFuture().get(); // drain the payload
        }
    }

    private static final class BlockingResponseTest extends ResponseTest {

        private BlockingResponseTest(final Supplier<HttpResponseMetaData> responseSupplier,
                                     final HttpRequestMethod requestMethod,
                                     final UnaryOperator<HttpMetaData> modifier,
                                     final Expectation expectation) {
            super(responseSupplier, requestMethod, modifier, expectation);
        }

        @Override
        public String toString() {
            return "Blocking" + super.toString();
        }

        @Override
        Single<ServerContext> listen(final HttpServerBuilder builder) {
            // service needs to generate the response
            final HttpMetaData metadata = modifier.apply(responseSupplier.get());
            BlockingStreamingHttpResponse streamingResponse = (metadata instanceof StreamingHttpResponse) ?
                    ((StreamingHttpResponse) metadata).toBlockingStreamingResponse() :
                    ((HttpResponse) metadata).toBlockingStreamingResponse();

            if (metadata instanceof HttpResponse || HttpApiConversions.isSafeToAggregate(metadata)) {
                return builder.listenBlocking((ctx, request, rf) -> (metadata instanceof StreamingHttpResponse) ?
                        ((StreamingHttpResponse) metadata).toResponse().toFuture().get() : ((HttpResponse) metadata));
            } else {
                return builder.listenBlockingStreaming((ctx, request, response) -> {
                    response.status(streamingResponse.status());
                    try (HttpPayloadWriter<Buffer> payloadWriter = response.sendMetaData()) {
                        for (Buffer buffer : streamingResponse.payloadBody()) {
                            payloadWriter.write(buffer);
                        }
                    }
                });
            }
        }
    }

    enum Expectation {
        HAVE_CONTENT_LENGTH {
            @Override
            public String toString() {
                return "ShouldHaveContentLength";
            }

            @Override
            String assertHeaders(final HttpHeaders headers) {
                return assertContentLength(headers, PAYLOAD_LENGTH);
            }
        },
        HAVE_CONTENT_LENGTH_ZERO {
            @Override
            public String toString() {
                return "ShouldHaveContentLength";
            }

            @Override
            String assertHeaders(final HttpHeaders headers) {
                return assertContentLength(headers, 0);
            }
        },
        HAVE_EXISTING_CONTENT_LENGTH {
            @Override
            public String toString() {
                return "ShouldHaveContentLength";
            }

            @Override
            String assertHeaders(final HttpHeaders headers) {
                return assertContentLength(headers, EXISTING_CONTENT_LENGTH);
            }
        },
        HAVE_TRANSFER_ENCODING_CHUNKED {
            @Override
            public String toString() {
                return "ShouldHaveTransferEncodingChunked";
            }

            @Override
            String assertHeaders(final HttpHeaders headers) {
                return assertTransferEncoding(headers);
            }
        },
        HAVE_NEITHER {
            @Override
            public String toString() {
                return "ShouldHaveNeither";
            }

            @Override
            String assertHeaders(final HttpHeaders headers) {
                return assertNeither(headers);
            }
        };

        @Nullable
        abstract String assertHeaders(HttpHeaders headers);
    }
}
