/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpMetaData;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpRequests;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.HttpResponses;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequests;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponses;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.transport.api.ServerContext;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
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
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED;
import static io.servicetalk.http.netty.ContentHeadersTest.Expectation.HAVE_CONTENT_LENGTH;
import static io.servicetalk.http.netty.ContentHeadersTest.Expectation.HAVE_NEITHER;
import static io.servicetalk.http.netty.ContentHeadersTest.Expectation.HAVE_TRANSFER_ENCODING_CHUNKED;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.function.UnaryOperator.identity;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public class ContentHeadersTest extends AbstractNettyHttpServerTest {

    private static final DefaultHttpHeadersFactory headersFactory = new DefaultHttpHeadersFactory(false, false);

    private final TestType testDefinition;

    public ContentHeadersTest(final TestType testDefinition) {
        super(CACHED, CACHED);
        this.testDefinition = testDefinition;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<TestType> parameters() {
        return Arrays.asList(
                // ----- Request -----
                new RequestTest(aggregatedRequest(GET), defaults(), HAVE_CONTENT_LENGTH),
                new RequestTest(aggregatedRequest(HEAD), defaults(), HAVE_CONTENT_LENGTH),
                new RequestTest(aggregatedRequest(POST), defaults(), HAVE_CONTENT_LENGTH),
                new RequestTest(aggregatedRequest(PUT), defaults(), HAVE_CONTENT_LENGTH),
                new RequestTest(aggregatedRequest(DELETE), defaults(), HAVE_CONTENT_LENGTH),
                new RequestTest(aggregatedRequest(CONNECT), defaults(), HAVE_CONTENT_LENGTH),
                new RequestTest(aggregatedRequest(OPTIONS), defaults(), HAVE_CONTENT_LENGTH),
                new RequestTest(aggregatedRequest(TRACE), defaults(), HAVE_NEITHER),
                new RequestTest(aggregatedRequest(PATCH), defaults(), HAVE_CONTENT_LENGTH),

                new RequestTest(aggregatedRequest(GET), withoutPayload(), HAVE_NEITHER),
                new RequestTest(aggregatedRequest(HEAD), withoutPayload(), HAVE_NEITHER),
                new RequestTest(aggregatedRequest(POST), withoutPayload(), HAVE_CONTENT_LENGTH),
                new RequestTest(aggregatedRequest(PUT), withoutPayload(), HAVE_CONTENT_LENGTH),
                new RequestTest(aggregatedRequest(DELETE), withoutPayload(), HAVE_NEITHER),
                new RequestTest(aggregatedRequest(CONNECT), withoutPayload(), HAVE_NEITHER),
                new RequestTest(aggregatedRequest(OPTIONS), withoutPayload(), HAVE_NEITHER),
                new RequestTest(aggregatedRequest(TRACE), withoutPayload(), HAVE_NEITHER),
                new RequestTest(aggregatedRequest(PATCH), withoutPayload(), HAVE_CONTENT_LENGTH),

                new RequestTest(aggregatedRequest(GET), transferEncodingGzip(), HAVE_CONTENT_LENGTH),
                new RequestTest(aggregatedRequest(GET), transferEncodingChunked(), HAVE_TRANSFER_ENCODING_CHUNKED),
                new RequestTest(aggregatedRequest(GET), transferEncodingGzipAndChunked(),
                        HAVE_TRANSFER_ENCODING_CHUNKED),
                new RequestTest(aggregatedRequest(GET), contentLength(), HAVE_CONTENT_LENGTH),
                new RequestTest(aggregatedRequest(GET), trailers(), HAVE_TRANSFER_ENCODING_CHUNKED),
                new RequestTest(aggregatedRequestAsStreaming(GET), transform(), HAVE_TRANSFER_ENCODING_CHUNKED),
                new RequestTest(aggregatedRequestAsStreaming(GET), transformRaw(), HAVE_TRANSFER_ENCODING_CHUNKED),
                new RequestTest(streamingRequest(GET), defaults(), HAVE_TRANSFER_ENCODING_CHUNKED),

                // ----- Response -----
                new ResponseTest(aggregatedResponse(OK), GET, defaults(), HAVE_CONTENT_LENGTH),
                new ResponseTest(aggregatedResponse(OK), HEAD, defaults(), HAVE_CONTENT_LENGTH),
                new ResponseTest(aggregatedResponse(OK), POST, defaults(), HAVE_CONTENT_LENGTH),
                new ResponseTest(aggregatedResponse(OK), PUT, defaults(), HAVE_CONTENT_LENGTH),
                new ResponseTest(aggregatedResponse(OK), DELETE, defaults(), HAVE_CONTENT_LENGTH),
                new ResponseTest(aggregatedResponse(OK), CONNECT, defaults(), HAVE_NEITHER),
                new ResponseTest(aggregatedResponse(OK), OPTIONS, defaults(), HAVE_CONTENT_LENGTH),
                new ResponseTest(aggregatedResponse(OK), TRACE, defaults(), HAVE_CONTENT_LENGTH),
                new ResponseTest(aggregatedResponse(OK), PATCH, defaults(), HAVE_CONTENT_LENGTH),

                new ResponseTest(aggregatedResponse(OK), GET, withoutPayload(), HAVE_CONTENT_LENGTH),
                new ResponseTest(aggregatedResponse(OK), HEAD, withoutPayload(), HAVE_CONTENT_LENGTH),
                new ResponseTest(aggregatedResponse(OK), POST, withoutPayload(), HAVE_CONTENT_LENGTH),
                new ResponseTest(aggregatedResponse(OK), PUT, withoutPayload(), HAVE_CONTENT_LENGTH),
                new ResponseTest(aggregatedResponse(OK), DELETE, withoutPayload(), HAVE_CONTENT_LENGTH),
                new ResponseTest(aggregatedResponse(OK), CONNECT, withoutPayload(), HAVE_NEITHER),
                new ResponseTest(aggregatedResponse(OK), OPTIONS, withoutPayload(), HAVE_CONTENT_LENGTH),
                new ResponseTest(aggregatedResponse(OK), TRACE, withoutPayload(), HAVE_CONTENT_LENGTH),
                new ResponseTest(aggregatedResponse(OK), PATCH, withoutPayload(), HAVE_CONTENT_LENGTH),

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
                new ResponseTest(aggregatedResponse(OK), GET, contentLength(), HAVE_CONTENT_LENGTH),
                new ResponseTest(aggregatedResponse(OK), GET, trailers(), HAVE_TRANSFER_ENCODING_CHUNKED),
                new ResponseTest(aggregatedResponse(INTERNAL_SERVER_ERROR), CONNECT, defaults(), HAVE_CONTENT_LENGTH),
                new ResponseTest(aggregatedResponseAsStreaming(OK), GET, transform(), HAVE_TRANSFER_ENCODING_CHUNKED),
                new ResponseTest(aggregatedResponseAsStreaming(OK), GET, transformRaw(),
                        HAVE_TRANSFER_ENCODING_CHUNKED),
                new ResponseTest(streamingResponse(OK), GET, defaults(), HAVE_TRANSFER_ENCODING_CHUNKED)
        );
    }

    private static Supplier<HttpRequestMetaData> aggregatedRequest(final HttpRequestMethod requestMethod) {
        return describe(() -> newAggregatedRequest(requestMethod), "aggregated" + requestMethod + "Request");
    }

    private static Supplier<HttpRequestMetaData> aggregatedRequestAsStreaming(final HttpRequestMethod requestMethod) {
        return describe(() -> newAggregatedRequest(requestMethod).toStreamingRequest(),
                "aggregated" + requestMethod + "RequestAsStreaming");
    }

    private static Supplier<HttpRequestMetaData> streamingRequest(final HttpRequestMethod requestMethod) {
        return describe(() -> newStreamingRequest(requestMethod), "streaming" + requestMethod + "Request");
    }

    private static Supplier<HttpResponseMetaData> aggregatedResponse(final HttpResponseStatus status) {
        return describe(() -> newAggregatedResponse(status), "aggregatedResponse");
    }

    private static Supplier<HttpResponseMetaData> aggregatedResponseAsStreaming(final HttpResponseStatus status) {
        return describe(() -> newAggregatedResponse(status).toStreamingResponse(), "aggregatedResponseAsStreaming");
    }

    private static Supplier<HttpResponseMetaData> streamingResponse(final HttpResponseStatus status) {
        return describe(() -> newStreamingResponse(status), "streamingResponse");
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
        }, "TransferEncodingGzip");
    }

    private static UnaryOperator<HttpMetaData> transferEncodingGzipAndChunked() {
        return describe(input -> {
            input.headers().set(TRANSFER_ENCODING, "gzip");
            input.headers().add(TRANSFER_ENCODING, "chunked");
            return input;
        }, "TransferEncodingGzipAndChunked");
    }

    private static UnaryOperator<HttpMetaData> transferEncodingChunked() {
        return describe(input -> {
            input.headers().set(TRANSFER_ENCODING, CHUNKED);
            return input;
        }, "TransferEncodingChunked");
    }

    private static UnaryOperator<HttpMetaData> contentLength() {
        return describe(input -> {
            input.headers().set(CONTENT_LENGTH, "100");
            return input;
        }, "ContentLength");
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
                return ((StreamingHttpRequest) input).transform(() -> null, (b, __) -> b, (__, t) -> t);
            } else if (input instanceof StreamingHttpResponse) {
                return ((StreamingHttpResponse) input).transform(() -> null, (b, __) -> b, (__, t) -> t);
            } else {
                fail("Unexpected metadata type: " + input.getClass());
                throw new IllegalStateException();
            }
        }, "Transform");
    }

    private static UnaryOperator<HttpMetaData> transformRaw() {
        return describe(input -> {
            if (input instanceof StreamingHttpRequest) {
                return ((StreamingHttpRequest) input).transformRaw(() -> null, (b, __) -> b, (__, t) -> t);
            } else if (input instanceof StreamingHttpResponse) {
                return ((StreamingHttpResponse) input).transformRaw(() -> null, (b, __) -> b, (__, t) -> t);
            } else {
                fail("Unexpected metadata type: " + input.getClass());
                throw new IllegalStateException();
            }
        }, "TransformRaw");
    }

    @Test
    public void integrationTest() throws Exception {
        if (testDefinition instanceof RequestTest) {
            RequestTest definition = (RequestTest) testDefinition;
            // test needs to create the request
            final HttpMetaData metadata = definition.modifier.apply(definition.requestSupplier.get());
            final StreamingHttpRequest request = metadata instanceof StreamingHttpRequest ?
                    (StreamingHttpRequest) metadata : ((HttpRequest) metadata).toStreamingRequest();
            final StreamingHttpResponse response = streamingHttpConnection().request(request).toFuture().get();
            assertThat(response.toResponse().toFuture().get().payloadBody().toString(UTF_8),
                    response.status(), is(NO_CONTENT));
        } else if (testDefinition instanceof ResponseTest) {
            ResponseTest definition = (ResponseTest) testDefinition;
            final Expectation expectation = definition.expectation;
            // test needs to check the response
            final StreamingHttpResponse response = streamingHttpConnection().request(
                    newStreamingRequest(definition.requestMethod)).toFuture().get();
            final HttpHeaders headers = response.headers();
            assertNull(checkHeaders(expectation, headers));
        }
    }

    @Override
    Single<ServerContext> listen(final HttpServerBuilder builder) {
        final StreamingHttpService service;
        if (testDefinition instanceof RequestTest) {
            // service needs to check the request
            RequestTest definition = (RequestTest) testDefinition;
            final Expectation expectation = definition.expectation;
            service = (ctx, request, rf) -> {
                final HttpHeaders headers = request.headers();
                final String failure = checkHeaders(expectation, headers);
                if (failure != null) {
                    return succeeded(rf.internalServerError().payloadBody(
                            from(failure),
                            textSerializer()));
                }
                return succeeded(rf.noContent());
            };
        } else if (testDefinition instanceof ResponseTest) {
            ResponseTest definition = (ResponseTest) testDefinition;
            // service needs to generate the response
            service = (ctx, request, rf) -> {
                final HttpMetaData metadata = definition.modifier.apply(definition.responseSupplier.get());
                return succeeded((metadata instanceof StreamingHttpResponse) ?
                        (StreamingHttpResponse) metadata : ((HttpResponse) metadata).toStreamingResponse());
            };
        } else {
            fail("Unknown definition type: " + testDefinition.getClass());
            throw new IllegalStateException();
        }
        return builder.listenStreaming(service);
    }

    @Nullable
    private static String checkHeaders(final Expectation expectation, final HttpHeaders headers) {
        final String failure;
        switch (expectation) {
            case HAVE_CONTENT_LENGTH:
                failure = assertContentLength(headers);
                break;
            case HAVE_TRANSFER_ENCODING_CHUNKED:
                failure = assertTransferEncoding(headers);
                break;
            case HAVE_NEITHER:
                failure = assertNeither(headers);
                break;
            default:
                failure = "Unknown enum value: " + expectation;
        }
        return failure;
    }

    private static HttpRequest newAggregatedRequest(final HttpRequestMethod requestMethod) {
        return HttpRequests.newRequest(requestMethod, "/", HTTP_1_1, headersFactory.newHeaders(),
                headersFactory.newEmptyTrailers(), DEFAULT_ALLOCATOR)
                .payloadBody("Hello", textSerializer());
    }

    private static StreamingHttpRequest newStreamingRequest(final HttpRequestMethod requestMethod) {
        return StreamingHttpRequests.newRequest(requestMethod, "/", HTTP_1_1, headersFactory.newHeaders(),
                headersFactory.newEmptyTrailers(), DEFAULT_ALLOCATOR)
                .payloadBody(from("Hello"), textSerializer());
    }

    private static HttpResponse newAggregatedResponse(final HttpResponseStatus status) {
        return HttpResponses.newResponse(status, HTTP_1_1, headersFactory.newHeaders(),
                headersFactory.newEmptyTrailers(), DEFAULT_ALLOCATOR)
                .payloadBody("Hello", textSerializer());
    }

    private static StreamingHttpResponse newStreamingResponse(final HttpResponseStatus status) {
        return StreamingHttpResponses.newResponse(status, HTTP_1_1, headersFactory.newHeaders(),
                headersFactory.newEmptyTrailers(), DEFAULT_ALLOCATOR)
                .payloadBody(from("Hello"), textSerializer());
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
        if (!headers.contains(TRANSFER_ENCODING, CHUNKED)) {
            return "No transfer-encoding: chunked";
        }
        return null;
    }

    @Nullable
    static String assertContentLength(final HttpHeaders headers) {
        if (headers.contains(TRANSFER_ENCODING, CHUNKED)) {
            return "transfer-encoding present";
        }
        if (!headers.contains(CONTENT_LENGTH)) {
            return "No content-length";
        }
        return null;
    }

    @Nullable
    static String assertNeither(final HttpHeaders headers) {
        if (headers.contains(TRANSFER_ENCODING, CHUNKED)) {
            return "transfer-encoding present";
        }
        if (headers.contains(CONTENT_LENGTH)) {
            return "content-length present";
        }
        return null;
    }

    private static class TestType {
        final UnaryOperator<HttpMetaData> modifier;
        final Expectation expectation;

        TestType(final UnaryOperator<HttpMetaData> modifier, final Expectation expectation) {
            this.modifier = modifier;
            this.expectation = expectation;
        }
    }

    private static final class RequestTest extends TestType {
        final Supplier<HttpRequestMetaData> requestSupplier;

        private RequestTest(final Supplier<HttpRequestMetaData> requestSupplier,
                            final UnaryOperator<HttpMetaData> modifier,
                            final Expectation expectation) {
            super(modifier, expectation);
            this.requestSupplier = requestSupplier;
        }

        @Override
        public String toString() {
            return requestSupplier.toString() + "With" + modifier + expectation;
        }
    }

    private static final class ResponseTest extends TestType {
        final Supplier<HttpResponseMetaData> responseSupplier;
        final HttpRequestMethod requestMethod;

        private ResponseTest(final Supplier<HttpResponseMetaData> responseSupplier,
                             final HttpRequestMethod requestMethod,
                             final UnaryOperator<HttpMetaData> modifier,
                             final Expectation expectation) {
            super(modifier, expectation);
            this.responseSupplier = responseSupplier;
            this.requestMethod = requestMethod;
        }

        @Override
        public String toString() {
            return responseSupplier.toString() + "To" + requestMethod + "With" + modifier + expectation;
        }
    }

    enum Expectation {
        HAVE_CONTENT_LENGTH {
            @Override
            public String toString() {
                return "ShouldHaveContentLength";
            }
        },
        HAVE_TRANSFER_ENCODING_CHUNKED {
            @Override
            public String toString() {
                return "ShouldHaveTransferEncodingChunked";
            }
        },
        HAVE_NEITHER {
            @Override
            public String toString() {
                return "ShouldHaveNeither";
            }
        };
    }
}
