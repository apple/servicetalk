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
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StatelessTrailersTransformer;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpServiceFilter;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.buffer.api.Matchers.contentEqualTo;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED_SERVER;
import static io.servicetalk.http.netty.HttpProtocol.HTTP_1;
import static io.servicetalk.http.netty.HttpProtocol.HTTP_2;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_ECHO;
import static java.lang.String.valueOf;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

@RunWith(Parameterized.class)
public class ContentLengthAndTrailersTest extends AbstractNettyHttpServerTest {

    private static final CharSequence CLIENT_CONTENT_LENGTH = newAsciiString("client-content-length");
    private static final CharSequence CLIENT_TRANSFER_ENCODING = newAsciiString("client-transfer-encoding");
    private static final CharSequence TRAILER_NAME = newAsciiString("trailer-name");
    private static final CharSequence TRAILER_VALUE = newAsciiString("trailer-value");
    private static final String CONTENT = "content";

    private final HttpProtocol protocol;

    public ContentLengthAndTrailersTest(HttpProtocol protocol) {
        super(CACHED, CACHED_SERVER);
        this.protocol = protocol;
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
    }

    @Parameterized.Parameters(name = "protocol={0}")
    public static HttpProtocol[] data() {
        return HttpProtocol.values();
    }

    @Test
    public void contentLengthAddedAutomaticallyByAggregatedApiConversion() throws Exception {
        test(r -> r.toRequest().toFuture().get().toStreamingRequest(), r -> r, true, false, false);
    }

    @Test
    public void contentLengthAddedManually() throws Exception {
        test(r -> r.setHeader(CONTENT_LENGTH, valueOf(CONTENT.length())), r -> r, true, false, false);
    }

    @Test
    public void transferEncodingAddedAutomatically() throws Exception {
        test(r -> r, r -> r, false, true, false);
    }

    @Test
    public void transferEncodingAddedManually() throws Exception {
        test(r -> r.setHeader(TRANSFER_ENCODING, CHUNKED), r -> r, false, true, false);
    }

    @Test
    public void trailersAddedForAggregatedRequest() throws Exception {
        test(r -> r.toRequest().toFuture().get()
                .addTrailer(TRAILER_NAME, TRAILER_VALUE)
                .toStreamingRequest(),
                // HTTP/2 may have content-length and trailers at the same time
                r -> r, protocol == HTTP_2, protocol == HTTP_1, true);
    }

    @Test
    public void trailersAddedForStreamingRequest() throws Exception {
        test(r -> r.transform(new StatelessTrailersTransformer<Buffer>() {

                    @Override
                    protected HttpHeaders payloadComplete(final HttpHeaders trailers) {
                        return trailers.add(TRAILER_NAME, TRAILER_VALUE);
                    }
                }), r -> r, false, true, true);
    }

    @Test
    public void trailersAndContentLengthAddedForAggregatedRequest() throws Exception {
        test(r -> r.toRequest().toFuture().get()
                        .setHeader(CONTENT_LENGTH, valueOf(CONTENT.length()))
                        .addTrailer(TRAILER_NAME, TRAILER_VALUE)
                        .toStreamingRequest(),
                // HTTP/2 may have content-length and trailers at the same time
                r -> r, true, false, protocol == HTTP_2);
    }

    @Test
    public void trailersAndContentLengthAddedForStreamingRequest() throws Exception {
        test(r -> r.setHeader(CONTENT_LENGTH, valueOf(CONTENT.length()))
                        .transform(new StatelessTrailersTransformer<Buffer>() {

                            @Override
                            protected HttpHeaders payloadComplete(final HttpHeaders trailers) {
                                return trailers.add(TRAILER_NAME, TRAILER_VALUE);
                            }
                        }),
                // HTTP/2 may have content-length and trailers at the same time
                r -> r, true, false, protocol == HTTP_2);
    }

    @Test
    public void trailersAndTransferEncodingAddedForAggregatedRequest() throws Exception {
        test(r -> r.toRequest().toFuture().get()
                        .setHeader(TRANSFER_ENCODING, CHUNKED)
                        .addTrailer(TRAILER_NAME, TRAILER_VALUE)
                        .toStreamingRequest(),
                r -> r, false, true, true);
    }

    @Test
    public void trailersAndTransferEncodingAddedForStreamingRequest() throws Exception {
        test(r -> r.setHeader(TRANSFER_ENCODING, CHUNKED)
                        .transform(new StatelessTrailersTransformer<Buffer>() {

                            @Override
                            protected HttpHeaders payloadComplete(final HttpHeaders trailers) {
                                return trailers.add(TRAILER_NAME, TRAILER_VALUE);
                            }
                        }),
                r -> r, false, true, true);
    }

    @Test
    public void trailersContentLengthAndTransferEncodingAddedForAggregatedRequest() throws Exception {
        test(r -> r.toRequest().toFuture().get()
                        .setHeader(CONTENT_LENGTH, valueOf(CONTENT.length()))
                        .setHeader(TRANSFER_ENCODING, CHUNKED)
                        .addTrailer(TRAILER_NAME, TRAILER_VALUE)
                        .toStreamingRequest(),
                // HTTP/2 may have content-length and trailers at the same time
                r -> r, protocol == HTTP_2, protocol == HTTP_1, true);
    }

    @Test
    public void trailersContentLengthAndTransferEncodingAddedForStreamingRequest() throws Exception {
        test(r -> r.setHeader(CONTENT_LENGTH, valueOf(CONTENT.length()))
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

    @Test
    public void responseTrailersObservedWhenNoTrailers() throws Exception {
        // Use transform to simulate access to trailers
        test(r -> r, r -> r.transform(new StatelessTrailersTransformer<>()), false, true, false);
    }

    @Test
    public void responseTrailersObserved() throws Exception {
        test(r -> r.transform(new StatelessTrailersTransformer<Buffer>() {

            @Override
            protected HttpHeaders payloadComplete(final HttpHeaders trailers) {
                return trailers.add(TRAILER_NAME, TRAILER_VALUE);
            }
        }),
        // Use transform to simulate access to trailers
        r -> r.transform(new StatelessTrailersTransformer<>()), false, true, true);
    }

    private void test(Transformer<StreamingHttpRequest> requestTransformer,
                      Transformer<StreamingHttpResponse> responseTransformer,
                      boolean hasContentLength, boolean chunked, boolean hasTrailers) throws Exception {

        StreamingHttpRequest request = requestTransformer.transform(streamingHttpConnection().post(SVC_ECHO)
                .payloadBody(from(CONTENT), textSerializer()));
        HttpResponse response = responseTransformer.transform(makeRequest(request)).toResponse().toFuture().get();
        assertResponse(response, protocol.version, OK);
        assertThat(response.payloadBody().toString(US_ASCII), equalTo(CONTENT));

        HttpHeaders headers = response.headers();
        assertThat("Unexpected content-length on the response", mergeValues(headers.values(CONTENT_LENGTH)),
                hasContentLength ? contentEqualTo(valueOf(CONTENT.length())) : contentEqualTo(""));
        assertThat("Unexpected transfer-encoding on the response", mergeValues(headers.values(TRANSFER_ENCODING)),
                chunked ? contentEqualTo(CHUNKED) : contentEqualTo(""));

        assertThat("Unexpected content-length on the request", headers.get(CLIENT_CONTENT_LENGTH),
                hasContentLength ? contentEqualTo(valueOf(CONTENT.length())) : nullValue());
        assertThat("Unexpected transfer-encoding on the request", headers.get(CLIENT_TRANSFER_ENCODING),
                chunked ? contentEqualTo(CHUNKED) : nullValue());
        assertThat("Unexpected trailers on the request", response.trailers().get(TRAILER_NAME),
                hasTrailers ? contentEqualTo(TRAILER_VALUE) : nullValue());
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

    private interface Transformer<T extends HttpMetaData> {
        T transform(T request) throws Exception;
    }
}
