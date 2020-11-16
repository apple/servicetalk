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
package io.servicetalk.http.netty;

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.ServerContext;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.http.api.StreamingHttpRequests.newTransportRequest;
import static io.servicetalk.http.netty.HttpServers.forPort;
import static java.util.Objects.requireNonNull;
import static org.junit.Assert.assertEquals;

public class AggregatedRequestsFilterTest {

    private static final StreamingHttpClientFilterFactory BODY_TRANSFORMER_FILTER =
            new StreamingHttpClientFilterFactory() {
        @Override
        public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
            return new StreamingHttpClientFilter(client) {
                @Override
                protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                final HttpExecutionStrategy strategy,
                                                                final StreamingHttpRequest request) {
                    BufferAllocator allocator = delegate.executionContext().bufferAllocator();
                    request.transformPayloadBody(b -> b.concat(succeeded(allocator.fromAscii("red-pill"))));
                    return super.request(delegate, strategy, request);
                }
            };
        }
    };

    private static final StreamingHttpClientFilterFactory RAW_TRANSFORMER_FILTER =
            new StreamingHttpClientFilterFactory() {
                @Override
                public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
                    return new StreamingHttpClientFilter(client) {
                        @Override
                        protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                        final HttpExecutionStrategy strategy,
                                                                        final StreamingHttpRequest request) {
                            BufferAllocator allocator = delegate.executionContext().bufferAllocator();
                            HttpHeadersFactory headersFactory = new DefaultHttpHeadersFactory(false, false);

                            request.transformRawPayloadBody(b -> from(allocator.fromAscii("red-pill"),
                                    headersFactory.newTrailers().add("fooA", "barA")));
                            return super.request(delegate, strategy, request);
                        }
                    };
                }
            };

    private ServerContext bodyOnlyServerCtx;
    private ServerContext bodyWithTrailersServerCtx;
    private HttpClient clientWithBodyTransformer;
    private HttpClient clientWithRawTransformer;

    @Before
    public void setup() throws Exception {
        bodyOnlyServerCtx = forPort(0).listenStreamingAndAwait((ctx, request, responseFactory) ->
            succeeded(responseFactory.ok().payloadBody(from("reply"), textSerializer())));

        bodyWithTrailersServerCtx = forPort(0).listenStreamingAndAwait((ctx, request, responseFactory) -> {
            try {
                HttpRequest aggr = request.toRequest().toFuture().get();
                if (aggr.trailers().get("fooA") == null) {
                    return failed(new IllegalStateException("Expected trailers"));
                }
            } catch (Exception e) {
                return Single.failed(e);
            }
            return succeeded(responseFactory.ok().payloadBody(from("reply"), textSerializer()));
        });

        clientWithBodyTransformer = HttpClients
                .forResolvedAddress(bodyOnlyServerCtx.listenAddress())
                .appendClientFilter(BODY_TRANSFORMER_FILTER)
                .build();

        clientWithRawTransformer = HttpClients
                .forResolvedAddress(bodyWithTrailersServerCtx.listenAddress())
                .appendClientFilter(RAW_TRANSFORMER_FILTER)
                .build();
    }

    @After
    public void tearDown() throws Exception {
        clientWithBodyTransformer.close();
        bodyOnlyServerCtx.close();
        bodyWithTrailersServerCtx.close();
    }

    @Test
    public void clientWithStreamingRequest() {
        final StreamingHttpClient streamingClient = clientWithBodyTransformer.asStreamingClient();
        final StreamingHttpRequest request = streamingClient.get("/")
                .payloadBody(from("A", "B"), textSerializer());

        assertValidResponse(clientWithBodyTransformer, request);
    }

    @Test
    public void clientWithStreamingRequestAndTrailers() {
        BufferAllocator allocator = clientWithBodyTransformer.executionContext().bufferAllocator();
        HttpHeadersFactory headersFactory = new DefaultHttpHeadersFactory(false, false);
        final StreamingHttpRequest request = newTransportRequest(GET, "/", HTTP_1_1,
                headersFactory.newHeaders(), allocator, from(EMPTY_BUFFER, EMPTY_BUFFER, headersFactory.newTrailers()),
                headersFactory);

        assertValidResponse(clientWithBodyTransformer, request);
    }

    @Test
    public void clientWithAggregatedStreamingRequestAndTrailers() {
        BufferAllocator allocator = clientWithBodyTransformer.executionContext().bufferAllocator();
        HttpHeadersFactory headersFactory = new DefaultHttpHeadersFactory(false, false);
        final StreamingHttpRequest request = newTransportRequest(GET, "/", HTTP_1_1,
                headersFactory.newHeaders().add(TRANSFER_ENCODING, CHUNKED), allocator,
                from(EMPTY_BUFFER, EMPTY_BUFFER, headersFactory.newTrailers().add("foo", "bar")),
                headersFactory);

        assertValidResponse(clientWithBodyTransformer, aggregate(request));
    }

    @Test
    public void clientWithAggregatedStreamingRequest() {
        final StreamingHttpClient streamingClient = clientWithBodyTransformer.asStreamingClient();
        final StreamingHttpRequest request = streamingClient.get("/")
                .payloadBody(from("A", "B"), textSerializer());

        assertValidResponse(clientWithBodyTransformer, aggregate(request));
    }

    @Test
    public void clientWithAggregatedStreamingRequestAndRawTransformerFilter() {
        final StreamingHttpClient streamingClient = clientWithRawTransformer.asStreamingClient();
        final StreamingHttpRequest request = streamingClient.get("/")
                .payloadBody(from("A", "B"), textSerializer());

        assertValidResponse(clientWithRawTransformer, aggregate(request));
    }

    private HttpRequest aggregate(StreamingHttpRequest request) {
        return awaitSingleIndefinitelyNonNull(request.toRequest());
    }

    private void assertValidResponse(HttpClient client, HttpRequest request) {
        try {
            // Two requests to make sure the HttpEncoder is left in the right state (eg. not awaiting for trailers)
            assertEquals(OK, client.request(request).toFuture().get().status());
            assertEquals(OK, client.request(request).toFuture().get().status());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private void assertValidResponse(HttpClient client, StreamingHttpRequest request) {
        try {
            // Two requests to make sure the HttpEncoder is left in the right state (eg. not awaiting for trailers)
            assertEquals(OK, client.asStreamingClient().request(request).toFuture().get().status());
            assertEquals(OK, client.asStreamingClient().request(request).toFuture().get().status());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    static <T> T awaitSingleIndefinitelyNonNull(Single<T> single) {
        try {
            return requireNonNull(single.toFuture().get());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
