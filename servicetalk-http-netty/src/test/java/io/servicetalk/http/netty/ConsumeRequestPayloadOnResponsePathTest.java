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
import io.servicetalk.buffer.netty.BufferAllocators;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.AddressUtils;

import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

public class ConsumeRequestPayloadOnResponsePathTest {

    private static final String EXPECTED_REQUEST_PAYLOAD = "ExpectedRequestPayload";

    @Rule
    public final ServiceTalkTestTimeout timeout = new ServiceTalkTestTimeout();

    private final CountDownLatch waitServer = new CountDownLatch(1);
    private final AtomicReference<Throwable> errorRef = new AtomicReference<>();
    private final CompositeBuffer receivedPayload = BufferAllocators.DEFAULT_ALLOCATOR.newCompositeBuffer();

    @Test
    public void testConsumeRequestPayloadBeforeResponseMetaDataSent() throws Exception {
        test((responseSingle, request) -> consumePayloadBody(request).concatWith(responseSingle));
    }

    @Test
    public void testConsumeRequestPayloadBeforeResponsePayloadSent() throws Exception {
        test((responseSingle, request) -> responseSingle.flatMap(response -> success(
                response.transformRawPayloadBody(payloadBody -> consumePayloadBody(request).concatWith(payloadBody)))));
    }

    @Test
    public void testConsumeRequestPayloadAfterResponseMetaDataSent() throws Exception {
        test((responseSingle, request) -> responseSingle.concatWith(consumePayloadBody(request)));
    }

    @Test
    public void testConsumeRequestPayloadAfterResponsePayloadSent() throws Exception {
        test((responseSingle, request) -> responseSingle.flatMap(response -> success(
                response.transformRawPayloadBody(payloadBody -> payloadBody.concatWith(consumePayloadBody(request))))));
    }

    @Test
    public void testSendResponseMetaDataAndConsumeRequestPayload() throws Exception {
        // TODO: replace flatMap when Single.merge(Completable) is available
        test((responseSingle, request) -> responseSingle.flatMap(response ->
                consumePayloadBody(request).concatWith(success(response))));
    }

    @Test
    public void testConsumeRequestPayloadAndSendResponseMetaData() throws Exception {
        test((responseSingle, request) -> consumePayloadBody(request)
                // TODO: remove toPublisher() when Completable.merge(Single) is available
                .merge(responseSingle.toPublisher()).toSingleOrError());
    }

    @Test
    public void testConsumeRequestPayloadAndResponsePayloadSent() throws Exception {
        test((responseSingle, request) -> responseSingle.flatMap(response -> success(
                response.transformRawPayloadBody(payloadBody -> consumePayloadBody(request).merge(payloadBody)))));
    }

    // TODO: add testResponsePayloadSentAndConsumeRequestPayload when Publisher.merge(Completable) is available

    private Completable consumePayloadBody(final StreamingHttpRequest request) {
        return request.payloadBody().doBeforeNext(receivedPayload::addBuffer)
                .ignoreElements()
                .doBeforeError(errorRef::set)
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
                .listenStreamingAndAwait((ctx, request, responseFactory) -> success(responseFactory.ok()
                        .payloadBody(from("Response\n", "Payload\n", "Body\n"), textSerializer())))) {

            try (BlockingHttpClient client = HttpClients.forSingleAddress(AddressUtils.serverHostAndPort(serverContext))
                    .buildBlocking()) {
                client.request(client.post("/").payloadBody(EXPECTED_REQUEST_PAYLOAD, textSerializer()));
            }

            waitServer.await();
            assertThat("Request payload body might be consumed by someone else", errorRef.get(), is(nullValue()));
            assertThat(receivedPayload.toString(UTF_8), is(EXPECTED_REQUEST_PAYLOAD));
        }
    }
}
