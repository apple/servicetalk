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
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.TrailersTransformer;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.AddressUtils;
import io.servicetalk.utils.internal.PlatformDependent;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpHeaderNames.TRAILER;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializers.appSerializerUtf8FixLen;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static io.servicetalk.http.api.StreamingHttpResponses.newTransportResponse;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConsumeRequestPayloadOnResponsePathTest {

    private static final String EXPECTED_REQUEST_PAYLOAD = "ExpectedRequestPayload";
    private static final String X_TOTAL_LENGTH = "X-Total-Length";

    private final CountDownLatch serverLatch = new CountDownLatch(1);
    private final AtomicReference<Throwable> errorRef = new AtomicReference<>();
    private final StringBuffer receivedPayload = new StringBuffer();

    @Test
    void testConsumeRequestPayloadBeforeResponseMetaDataSent() throws Exception {
        test((responseSingle, request) -> consumePayloadBody(request).concat(responseSingle));
    }

    @Test
    void testConsumeRequestPayloadAfterResponseMetaDataSent() throws Exception {
        test((responseSingle, request) -> responseSingle.concat(consumePayloadBody(request)));
    }

    @Test
    void testConsumeRequestPayloadBeforeResponsePayloadSent() throws Exception {
        test((responseSingle, request) -> responseSingle.map(response ->
                response.transformMessageBody(payloadBody -> consumePayloadBody(request).concat(payloadBody))));
    }

    @Test
    void testConsumeRequestPayloadAfterResponsePayloadSent() throws Exception {
        test((responseSingle, request) -> responseSingle.map(response ->
                response.transformMessageBody(payloadBody -> payloadBody.concat(consumePayloadBody(request)))));
    }

    @Test
    void testConsumeRequestPayloadBeforeTrailersSent() throws Exception {
        test((responseSingle, request) -> responseSingle.map(response ->
                response.transform(new SuccessOnlyTrailersTransformer<>(trailers -> {
                    try {
                        consumePayloadBody(request).toFuture().get();
                    } catch (Exception e) {
                        PlatformDependent.throwException(e);
                    }
                    return trailers;
                }))));
    }

    @Test
    void testConsumeRequestPayloadAfterTrailersSent() throws Exception {
        test((responseSingle, request) -> responseSingle.map(response ->
                // It doesn't use the BufferAllocator from HttpServiceContext to simplify the test and avoid using
                // TriFunction. It doesn't change the behavior of this test.
                newTransportResponse(response.status(), response.version(), response.headers(), DEFAULT_ALLOCATOR,
                        response.messageBody().concat(consumePayloadBody(request)), false,
                        DefaultHttpHeadersFactory.INSTANCE)));
    }

    @Test
    void testSendResponseMetaDataAndConsumeRequestPayload() throws Exception {
        // TODO: replace flatMap when Single.merge(Completable) is available
        test((responseSingle, request) -> responseSingle.flatMap(response ->
                consumePayloadBody(request).concat(succeeded(response))));
    }

    @Test
    void testConsumeRequestPayloadAndSendResponseMetaData() throws Exception {
        test((responseSingle, request) -> consumePayloadBody(request)
                // TODO: remove toPublisher() when Completable.merge(Single) is available
                .merge(responseSingle.toPublisher()).firstOrError());
    }

    @Test
    void testConsumeRequestPayloadAndResponsePayloadSent() throws Exception {
        test((responseSingle, request) -> responseSingle.map(response ->
                response.transformMessageBody(payloadBody -> consumePayloadBody(request).merge(payloadBody))));
    }

    // TODO: add testResponsePayloadSentAndConsumeRequestPayload when Publisher.merge(Completable) is available
    // TODO: add testTrailersSentAndConsumeRequestPayload when Publisher.merge(Completable) is available

    private Completable consumePayloadBody(final StreamingHttpRequest request) {
        return request.payloadBody().beforeOnNext(buffer -> receivedPayload.append(buffer.toString(UTF_8)))
                .ignoreElements()
                .beforeOnError(errorRef::set)
                .afterFinally(serverLatch::countDown);
    }

    private void test(final BiFunction<Single<StreamingHttpResponse>, StreamingHttpRequest,
            Single<StreamingHttpResponse>> consumeRequestPayload) throws Exception {

        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .appendServiceFilter(service -> new StreamingHttpServiceFilter(service) {
                    @Override
                    public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                                final StreamingHttpRequest request,
                                                                final StreamingHttpResponseFactory responseFactory) {
                        return consumeRequestPayload.apply(delegate().handle(ctx, request, responseFactory), request);
                    }
                })
                .listenStreamingAndAwait((ctx, request, responseFactory) -> {
                    final StreamingHttpResponse response = responseFactory.ok()
                            .addHeader(TRAILER, X_TOTAL_LENGTH)
                            .payloadBody(from("Response\n", "Payload\n", "Body\n"), appSerializerUtf8FixLen())
                            .transform(new TrailersTransformer<AtomicInteger, Buffer>() {
                                @Override
                                public AtomicInteger newState() {
                                    return new AtomicInteger();
                                }

                                @Override
                                public Buffer accept(final AtomicInteger total, final Buffer chunk) {
                                    total.addAndGet(chunk.readableBytes());
                                    return chunk;
                                }

                                @Override
                                public HttpHeaders payloadComplete(final AtomicInteger total,
                                                                   final HttpHeaders trailers) {
                                    trailers.add(X_TOTAL_LENGTH, String.valueOf(total.get()));
                                    return trailers;
                                }

                                @Override
                                public HttpHeaders catchPayloadFailure(final AtomicInteger __, final Throwable ___,
                                                                       final HttpHeaders trailers) {
                                    return trailers;
                                }
                            });

                    return succeeded(response);
                })) {

            HttpResponse response;
            try (BlockingHttpClient client = HttpClients.forSingleAddress(AddressUtils.serverHostAndPort(serverContext))
                    .buildBlocking()) {
                response = client.request(client.post("/").payloadBody(EXPECTED_REQUEST_PAYLOAD, textSerializerUtf8()));
                serverLatch.await();
            }

            assertThat(response.status(), is(OK));
            assertThat("Request payload body might be consumed by someone else", errorRef.get(), is(nullValue()));
            assertThat(receivedPayload.toString(), is(EXPECTED_REQUEST_PAYLOAD));
            assertThat(response.headers().contains(TRAILER, X_TOTAL_LENGTH), is(true));
            assertThat(response.trailers().contains(X_TOTAL_LENGTH), is(true));
            CharSequence trailerLength = response.trailers().get(X_TOTAL_LENGTH);
            assertNotNull(trailerLength);
            assertThat("Unexpected response payload: '" + response.payloadBody().toString(UTF_8) + "'",
                    trailerLength.toString(),
                    is(Integer.toString(response.payloadBody().readableBytes())));
        }
    }
}
