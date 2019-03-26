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

import io.servicetalk.buffer.api.CompositeBuffer;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.PlatformDependent;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.AddressUtils;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.http.api.HttpHeaderNames.TRAILER;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.http.api.StreamingHttpResponses.newResponseWithTrailers;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

public class ConsumeRequestPayloadOnResponsePathTest {

    private static final String EXPECTED_REQUEST_PAYLOAD = "ExpectedRequestPayload";
    private static final String X_TOTAL_LENGTH = "X-Total-Length";

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private final CountDownLatch waitServer = new CountDownLatch(1);
    private final AtomicReference<Throwable> errorRef = new AtomicReference<>();
    private final CompositeBuffer receivedPayload = DEFAULT_ALLOCATOR.newCompositeBuffer();

    @Test
    public void testConsumeRequestPayloadBeforeResponseMetaDataSent() throws Exception {
        test((responseSingle, request) -> consumePayloadBody(request).concat(responseSingle));
    }

    @Test
    public void testConsumeRequestPayloadAfterResponseMetaDataSent() throws Exception {
        test((responseSingle, request) -> responseSingle.concat(consumePayloadBody(request)));
    }

    @Test
    public void testConsumeRequestPayloadBeforeResponsePayloadSent() throws Exception {
        test((responseSingle, request) -> responseSingle.map(response ->
                response.transformRawPayloadBody(payloadBody -> consumePayloadBody(request).concat(payloadBody))));
    }

    @Test
    public void testConsumeRequestPayloadAfterResponsePayloadSent() throws Exception {
        test((responseSingle, request) -> responseSingle.map(response ->
                response.transformRawPayloadBody(payloadBody -> payloadBody.concat(consumePayloadBody(request)))));
    }

    @Test
    public void testConsumeRequestPayloadBeforeTrailersSent() throws Exception {
        test((responseSingle, request) -> responseSingle.map(response ->
                response.transformRaw(() -> null, (payloadChunk, __) -> payloadChunk, (__, trailers) -> {
                    try {
                        consumePayloadBody(request).toFuture().get();
                    } catch (Exception e) {
                        PlatformDependent.throwException(e);
                    }
                    return trailers;
                })));
    }

    @Test
    public void testConsumeRequestPayloadAfterTrailersSent() throws Exception {
        test((responseSingle, request) -> responseSingle.map(response ->
                // It doesn't use the BufferAllocator from HttpServiceContext to simplify the test and avoid using
                // TriFunction. It doesn't change the behavior of this test.
                newResponseWithTrailers(response.status(), response.version(), response.headers(), DEFAULT_ALLOCATOR,
                        response.payloadBodyAndTrailers().concat(consumePayloadBody(request)))));
    }

    @Test
    public void testSendResponseMetaDataAndConsumeRequestPayload() throws Exception {
        // TODO: replace flatMap when Single.merge(Completable) is available
        test((responseSingle, request) -> responseSingle.flatMap(response ->
                consumePayloadBody(request).concat(success(response))));
    }

    @Test
    public void testConsumeRequestPayloadAndSendResponseMetaData() throws Exception {
        test((responseSingle, request) -> consumePayloadBody(request)
                // TODO: remove toPublisher() when Completable.merge(Single) is available
                .merge(responseSingle.toPublisher()).firstOrError());
    }

    @Test
    public void testConsumeRequestPayloadAndResponsePayloadSent() throws Exception {
        test((responseSingle, request) -> responseSingle.map(response ->
                response.transformRawPayloadBody(payloadBody -> consumePayloadBody(request).merge(payloadBody))));
    }

    // TODO: add testResponsePayloadSentAndConsumeRequestPayload when Publisher.merge(Completable) is available
    // TODO: add testTrailersSentAndConsumeRequestPayload when Publisher.merge(Completable) is available

    private Completable consumePayloadBody(final StreamingHttpRequest request) {
        return request.payloadBody().doBeforeOnNext(receivedPayload::addBuffer)
                .ignoreElements()
                .doBeforeOnError(errorRef::set)
                .doAfterFinally(waitServer::countDown);
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
                            .payloadBody(from("Response\n", "Payload\n", "Body\n"), textSerializer())
                            .transform(AtomicInteger::new, (chunk, total) -> {
                                total.addAndGet(chunk.readableBytes());
                                return chunk;
                            }, (total, trailers) -> trailers.add(X_TOTAL_LENGTH, String.valueOf(total.get())));

                    return success(response);
                })) {

            HttpResponse response;
            try (BlockingHttpClient client = HttpClients.forSingleAddress(AddressUtils.serverHostAndPort(serverContext))
                    .buildBlocking()) {
                response = client.request(client.post("/").payloadBody(EXPECTED_REQUEST_PAYLOAD, textSerializer()));
            }

            waitServer.await();
            assertThat(response.status(), is(OK));
            assertThat("Request payload body might be consumed by someone else", errorRef.get(), is(nullValue()));
            assertThat(receivedPayload.toString(UTF_8), is(EXPECTED_REQUEST_PAYLOAD));
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
