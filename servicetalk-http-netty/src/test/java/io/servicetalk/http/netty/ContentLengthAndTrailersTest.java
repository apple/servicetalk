/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpMetaData;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StatelessTrailersTransformer;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.TrailersTransformer;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.buffer.api.Matchers.contentEqualTo;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializers.appSerializerUtf8FixLen;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED_SERVER;
import static io.servicetalk.http.netty.HttpProtocol.HTTP_1;
import static io.servicetalk.http.netty.HttpProtocol.HTTP_2;
import static java.lang.String.valueOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

class ContentLengthAndTrailersTest extends AbstractNettyHttpServerTest {

    private static final CharSequence CLIENT_CONTENT_LENGTH = newAsciiString("client-content-length");
    private static final CharSequence CLIENT_TRANSFER_ENCODING = newAsciiString("client-transfer-encoding");
    private static final CharSequence TRAILER_NAME = newAsciiString("trailer-name");
    private static final CharSequence TRAILER_VALUE = newAsciiString("trailer-value");

    private HttpProtocol protocol;
    private String content;

    private void setUp(HttpProtocol protocol, String content) {
        this.protocol = protocol;
        this.content = content;
        protocol(protocol.config);
        serviceFilterFactory(service -> new StreamingHttpServiceFilter(service) {
            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        final StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {
                // Use transform to simulate access to request trailers
                return delegate().handle(ctx, request.transform(new StatelessTrailersTransformer<>()), responseFactory)
                        .map(response -> {
                            final HttpHeaders headers = request.headers();
                            if (headers.contains(CONTENT_LENGTH)) {
                                response.setHeader(CLIENT_CONTENT_LENGTH, mergeValues(headers.values(CONTENT_LENGTH)));
                            }
                            if (headers.contains(TRANSFER_ENCODING)) {
                                response.setHeader(CLIENT_TRANSFER_ENCODING,
                                        mergeValues(headers.values(TRANSFER_ENCODING)));
                            }
                            return response;
                        });
            }
        });
        setUp(CACHED, CACHED_SERVER);
    }

    @SuppressWarnings("unused")
    private static List<Arguments> data() {
        List<Arguments> list = new ArrayList<>();
        for (HttpProtocol protocol : HttpProtocol.values()) {
            list.add(Arguments.of(protocol, ""));
            list.add(Arguments.of(protocol, "content"));
        }
        return list;
    }

    @Override
    protected void service(final StreamingHttpService __) {
        // Replace the original service with custom impl.
        // We use a custom "echo" service instead of TestServiceStreaming#SVC_ECHO because we want to modify PayloadInfo
        // flags only when payload body or trailers are present in the request:
        super.service((ctx, request, factory) -> request.toRequest().map(req -> {
            final StreamingHttpResponse response = factory.ok().version(req.version());
            if (req.payloadBody().readableBytes() > 0) {
                response.payloadBody(from(req.payloadBody()));
            }
            if (!req.trailers().isEmpty()) {
                response.transform(new StatelessTrailersTransformer<Buffer>() {
                    @Override
                    protected HttpHeaders payloadComplete(final HttpHeaders trailers) {
                        return trailers.add(req.trailers());
                    }
                });
            }
            final CharSequence contentLength = req.headers().get(CONTENT_LENGTH);
            if (contentLength != null) {
                response.addHeader(CONTENT_LENGTH, contentLength);
            }
            final CharSequence contentType = req.headers().get(CONTENT_TYPE);
            if (contentType != null) {
                response.addHeader(CONTENT_TYPE, contentType);
            }
            final CharSequence transferEncoding = req.headers().get(TRANSFER_ENCODING);
            if (transferEncoding != null) {
                response.addHeader(TRANSFER_ENCODING, transferEncoding);
            }
            return response;
        }));
    }

    @ParameterizedTest(name = "protocol={0}")
    @MethodSource("data")
    void contentLengthAddedAutomaticallyByAggregatedApiConversion(HttpProtocol protocol,
                                                                  String content) throws Exception {
        setUp(protocol, content);
        test(r -> r.toRequest().toFuture().get().toStreamingRequest(), r -> r, true, false, false);
    }

    @ParameterizedTest(name = "protocol={0}")
    @MethodSource("data")
    void contentLengthAddedManually(HttpProtocol protocol, String content) throws Exception {
        setUp(protocol, content);
        test(r -> r.setHeader(CONTENT_LENGTH, valueOf(addFixedLengthFramingOverhead(content.length()))), r -> r, true,
                false, false);
    }

    @ParameterizedTest(name = "protocol={0}")
    @MethodSource("data")
    void transferEncodingAddedAutomatically(HttpProtocol protocol,
                                            String content) throws Exception {
        setUp(protocol, content);
        test(r -> r, r -> r, content.isEmpty(), !content.isEmpty(), false);
    }

    @ParameterizedTest(name = "protocol={0}")
    @MethodSource("data")
    void transferEncodingAddedManually(HttpProtocol protocol, String content) throws Exception {
        setUp(protocol, content);
        // HTTP/2 can write a request without payload body as a single frame,
        // server adds "content-length: 0" when it reads those requests
        boolean hasContentLength = protocol == HTTP_2 && content.isEmpty();
        test(r -> r.setHeader(TRANSFER_ENCODING, CHUNKED), r -> r, hasContentLength, !hasContentLength, false);
    }

    @ParameterizedTest(name = "protocol={0}")
    @MethodSource("data")
    void trailersAddedForAggregatedRequest(HttpProtocol protocol, String content) throws Exception {
        setUp(protocol, content);
        test(r -> r.toRequest().toFuture().get()
                .addTrailer(TRAILER_NAME, TRAILER_VALUE)
                .toStreamingRequest(),
                // HTTP/2 may have content-length and trailers at the same time
                r -> r, protocol == HTTP_2, protocol == HTTP_1, true);
    }

    @ParameterizedTest(name = "protocol={0}")
    @MethodSource("data")
    void trailersAddedForStreamingRequest(HttpProtocol protocol, String content) throws Exception {
        setUp(protocol, content);
        test(r -> r.transform(new StatelessTrailersTransformer<Buffer>() {

                    @Override
                    protected HttpHeaders payloadComplete(final HttpHeaders trailers) {
                        return trailers.add(TRAILER_NAME, TRAILER_VALUE);
                    }
                }), r -> r,
                // HTTP/2 may have content-length and trailers at the same time, but it can set CL only if the content
                // is empty. Otherwise, it cannot compute CL when the streaming API is used
                content.isEmpty() && protocol == HTTP_2, !content.isEmpty() || protocol == HTTP_1, true);
    }

    @ParameterizedTest(name = "protocol={0}")
    @MethodSource("data")
    void trailersAndContentLengthAddedForAggregatedRequest(HttpProtocol protocol,
                                                           String content) throws Exception {
        setUp(protocol, content);
        test(r -> r.toRequest().toFuture().get()
                        .setHeader(CONTENT_LENGTH, valueOf(addFixedLengthFramingOverhead(content.length())))
                        .addTrailer(TRAILER_NAME, TRAILER_VALUE)
                        .toStreamingRequest(),
                // HTTP/2 may have content-length and trailers at the same time
                r -> r, protocol == HTTP_2, protocol == HTTP_1, true);
    }

    @ParameterizedTest(name = "protocol={0}")
    @MethodSource("data")
    void trailersAndContentLengthAddedForStreamingRequest(HttpProtocol protocol,
                                                          String content) throws Exception {
        setUp(protocol, content);
        test(r -> r.setHeader(CONTENT_LENGTH, valueOf(addFixedLengthFramingOverhead(content.length())))
                        .transform(new StatelessTrailersTransformer<Buffer>() {

                            @Override
                            protected HttpHeaders payloadComplete(final HttpHeaders trailers) {
                                return trailers.add(TRAILER_NAME, TRAILER_VALUE);
                            }
                        }),
                // HTTP/2 may have content-length and trailers at the same time
                r -> r, protocol == HTTP_2, protocol == HTTP_1, true);
    }

    @ParameterizedTest(name = "protocol={0}")
    @MethodSource("data")
    void trailersAndTransferEncodingAddedForAggregatedRequest(HttpProtocol protocol,
                                                              String content) throws Exception {
        setUp(protocol, content);
        test(r -> r.toRequest().toFuture().get()
                        .setHeader(TRANSFER_ENCODING, CHUNKED)
                        .addTrailer(TRAILER_NAME, TRAILER_VALUE)
                        .toStreamingRequest(),
                r -> r, false, true, true);
    }

    @ParameterizedTest(name = "protocol={0}")
    @MethodSource("data")
    void trailersAndTransferEncodingAddedForStreamingRequest(HttpProtocol protocol,
                                                             String content) throws Exception {
        setUp(protocol, content);
        test(r -> r.setHeader(TRANSFER_ENCODING, CHUNKED)
                        .transform(new StatelessTrailersTransformer<Buffer>() {

                            @Override
                            protected HttpHeaders payloadComplete(final HttpHeaders trailers) {
                                return trailers.add(TRAILER_NAME, TRAILER_VALUE);
                            }
                        }),
                r -> r, false, true, true);
    }

    @ParameterizedTest(name = "protocol={0}")
    @MethodSource("data")
    void trailersContentLengthAndTransferEncodingAddedForAggregatedRequest(HttpProtocol protocol,
                                                                           String content) throws Exception {
        setUp(protocol, content);
        test(r -> r.toRequest().toFuture().get()
                        .setHeader(CONTENT_LENGTH, valueOf(addFixedLengthFramingOverhead(content.length())))
                        .setHeader(TRANSFER_ENCODING, CHUNKED)
                        .addTrailer(TRAILER_NAME, TRAILER_VALUE)
                        .toStreamingRequest(),
                // HTTP/2 may have content-length and trailers at the same time
                r -> r, protocol == HTTP_2, protocol == HTTP_1, true);
    }

    @ParameterizedTest(name = "protocol={0}")
    @MethodSource("data")
    void trailersContentLengthAndTransferEncodingAddedForStreamingRequest(HttpProtocol protocol,
                                                                          String content) throws Exception {
        setUp(protocol, content);
        test(r -> r.setHeader(CONTENT_LENGTH, valueOf(addFixedLengthFramingOverhead(content.length())))
                        .setHeader(TRANSFER_ENCODING, CHUNKED)
                        .transform(new StatelessTrailersTransformer<Buffer>() {

                            @Override
                            protected HttpHeaders payloadComplete(final HttpHeaders trailers) {
                                return trailers.add(TRAILER_NAME, TRAILER_VALUE);
                            }
                        }),
                // HTTP/2 may have content-length and trailers at the same time
                r -> r, protocol == HTTP_2, protocol == HTTP_1, true);
    }

    @ParameterizedTest(name = "protocol={0}")
    @MethodSource("data")
    void responseTrailersObservedWhenNoTrailers(HttpProtocol protocol, String content) throws Exception {
        setUp(protocol, content);
        // Use transform to simulate access to trailers
        test(r -> r, r -> r.transform(new StatelessTrailersTransformer<>()),
                content.isEmpty(), !content.isEmpty(), false);
    }

    @ParameterizedTest(name = "protocol={0}")
    @MethodSource("data")
    void responseTrailersObserved(HttpProtocol protocol, String content) throws Exception {
        setUp(protocol, content);
        test(r -> r.transform(new StatelessTrailersTransformer<Buffer>() {

            @Override
            protected HttpHeaders payloadComplete(final HttpHeaders trailers) {
                return trailers.add(TRAILER_NAME, TRAILER_VALUE);
            }
        }),
        // Use transform to simulate access to trailers
        r -> r.transform(new StatelessTrailersTransformer<>()),
                // HTTP/2 may have content-length and trailers at the same time, but it can set CL only if the content
                // is empty. Otherwise, it cannot compute CL when the streaming API is used
                content.isEmpty() && protocol == HTTP_2, !content.isEmpty() || protocol == HTTP_1, true);
    }

    private void test(Transformer<StreamingHttpRequest> requestTransformer,
                      Transformer<StreamingHttpResponse> responseTransformer,
                      boolean hasContentLength, boolean chunked, boolean hasTrailers) throws Exception {

        StreamingHttpRequest preRequest = streamingHttpConnection().post("/");
        if (!content.isEmpty()) {
            preRequest.payloadBody(from(content), appSerializerUtf8FixLen());
        }
        StreamingHttpRequest request = requestTransformer.transform(preRequest);
        StreamingHttpResponse response = responseTransformer.transform(makeRequest(request));
        assertResponse(response, protocol.version, OK);
        HttpHeaders headers = response.headers();
        assertThat("Unexpected content-length on the response", mergeValues(headers.values(CONTENT_LENGTH)),
                contentEqualTo(hasContentLength ? valueOf(addFixedLengthFramingOverhead(content.length())) : ""));
        assertThat("Unexpected transfer-encoding on the response", mergeValues(headers.values(TRANSFER_ENCODING)),
                contentEqualTo(chunked ? CHUNKED : ""));

        assertThat("Unexpected content-length on the request", headers.get(CLIENT_CONTENT_LENGTH),
                hasContentLength ? contentEqualTo(valueOf(addFixedLengthFramingOverhead(content.length()))) :
                        nullValue());
        assertThat("Unexpected transfer-encoding on the request", headers.get(CLIENT_TRANSFER_ENCODING),
                chunked ? contentEqualTo(CHUNKED) : nullValue());

        if (content.isEmpty()) {
            response.transform(new TrailersTransformer<Object, Buffer>() {
                @Nullable
                @Override
                public Integer newState() {
                    return null;
                }

                @Override
                public Buffer accept(@Nullable final Object o, final Buffer buffer) {
                    assertThat(buffer.readableBytes(), equalTo(0));
                    return buffer;
                }

                @Override
                public HttpHeaders payloadComplete(@Nullable final Object o, final HttpHeaders trailers) {
                    assertThat("Unexpected trailers on the request", trailers.get(TRAILER_NAME),
                            hasTrailers ? contentEqualTo(TRAILER_VALUE) : nullValue());
                    return trailers;
                }

                @Override
                public HttpHeaders catchPayloadFailure(@Nullable final Object o, final Throwable cause,
                                                       final HttpHeaders trailers) throws Throwable {
                    throw cause;
                }
            }).messageBody().ignoreElements().toFuture().get();
        } else {
            response.transform(new TrailersTransformer<StringBuilder, String>() {
                @Override
                public StringBuilder newState() {
                    return new StringBuilder();
                }

                @Override
                public String accept(final StringBuilder o, final String s) {
                    o.append(s);
                    return s;
                }

                @Override
                public HttpHeaders payloadComplete(final StringBuilder o, final HttpHeaders trailers) {
                    assertThat(o.toString(), equalTo(content));
                    assertThat("Unexpected trailers on the request", trailers.get(TRAILER_NAME),
                            hasTrailers ? contentEqualTo(TRAILER_VALUE) : nullValue());
                    return trailers;
                }

                @Override
                public HttpHeaders catchPayloadFailure(@Nullable final StringBuilder o, final Throwable cause,
                                                       final HttpHeaders trailers) throws Throwable {
                    throw cause;
                }
            }, appSerializerUtf8FixLen()).messageBody().ignoreElements().toFuture().get();
        }
    }

    private static CharSequence mergeValues(Iterable<? extends CharSequence> values) {
        StringBuilder sb = new StringBuilder();
        for (CharSequence value : values) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(value);
        }
        return sb;
    }

    static int addFixedLengthFramingOverhead(int length) {
        return length == 0 ? 0 : length + Integer.BYTES;
    }

    private interface Transformer<T extends HttpMetaData> {
        T transform(T request) throws Exception;
    }
}
