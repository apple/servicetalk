/*
 * Copyright © 2025 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ExecutorExtension;
import io.servicetalk.concurrent.api.Processors;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.SourceAdapters;
import io.servicetalk.concurrent.internal.RejectedSubscribeException;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.BlockingHttpRequester;
import io.servicetalk.http.api.BlockingStreamingHttpClient;
import io.servicetalk.http.api.BlockingStreamingHttpRequest;
import io.servicetalk.http.api.BlockingStreamingHttpRequester;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpRequester;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.ReservedBlockingHttpConnection;
import io.servicetalk.http.api.ReservedBlockingStreamingHttpConnection;
import io.servicetalk.http.api.ReservedHttpConnection;
import io.servicetalk.http.api.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.concurrent.api.ExecutorExtension.withCachedExecutor;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpResponseStatus.NO_CONTENT;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.assertResponse;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConcurrentHttpRequestTest {

    @RegisterExtension
    static final ExecutorExtension<Executor> executorExtension = withCachedExecutor().setClassLevel(true);

    private final CountDownLatch receivedFirstRequest = new CountDownLatch(1);
    private final AtomicInteger receivedRequests = new AtomicInteger();
    private final CompletableSource.Processor responseProcessor = Processors.newCompletableProcessor();
    private final ServerContext serverCtx;

    ConcurrentHttpRequestTest() throws Exception {
        serverCtx = HttpServers.forAddress(localAddress(0))
                .listenAndAwait((ctx, request, responseFactory) -> {
                    receivedFirstRequest.countDown();
                    if (receivedRequests.incrementAndGet() == 1) {
                        return SourceAdapters.fromSource(responseProcessor)
                                .concat(Single.succeeded(responseFactory.ok()));
                    }
                    return Single.succeeded(responseFactory.noContent());
                });
    }

    @AfterEach
    void tearDown() throws Exception {
        serverCtx.close();
    }

    @ParameterizedTest(name = "{displayName} [{index}] multiAddressClient={0}")
    @ValueSource(booleans = {false, true})
    void asyncStreamingClient(boolean multiAddressClient) throws Exception {
        try (StreamingHttpClient client = newClient(multiAddressClient)) {
            asyncStreamingRequester(client, multiAddressClient);
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] multiAddressClient={0}")
    @ValueSource(booleans = {false, true})
    void asyncStreamingConnection(boolean multiAddressClient) throws Exception {
        try (StreamingHttpClient client = newClient(multiAddressClient)) {
            try (ReservedStreamingHttpConnection connection = client.reserveConnection(
                    client.get(requestTarget(multiAddressClient))).toFuture().get()) {

                asyncStreamingRequester(connection, multiAddressClient);
            }
        }
    }

    void asyncStreamingRequester(StreamingHttpRequester requester, boolean multiAddressClient) throws Exception {
        StreamingHttpRequest request = requester.get(requestTarget(multiAddressClient));
        Single<StreamingHttpResponse> firstSingle = requester.request(request);
        Future<StreamingHttpResponse> first = firstSingle.toFuture();
        receivedFirstRequest.await();
        Future<StreamingHttpResponse> firstConcurrent = firstSingle.toFuture();
        Future<StreamingHttpResponse> secondConcurrent = requester.request(request).toFuture();

        assertRejected(firstConcurrent);
        assertRejected(secondConcurrent);
        responseProcessor.onComplete();
        assertResponse(first.get(), HTTP_1_1, OK, 0);

        assertSequential(multiAddressClient, request, firstSingle);
        assertSequential(multiAddressClient, request, requester.request(request));

        assertThat(receivedRequests.get(), is(3));
    }

    @ParameterizedTest(name = "{displayName} [{index}] multiAddressClient={0}")
    @ValueSource(booleans = {false, true})
    void asyncAggregatedClient(boolean multiAddressClient) throws Exception {
        try (HttpClient client = newClient(multiAddressClient).asClient()) {
            asyncAggregatedRequester(client, multiAddressClient);
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] multiAddressClient={0}")
    @ValueSource(booleans = {false, true})
    void asyncAggregatedConnection(boolean multiAddressClient) throws Exception {
        try (HttpClient client = newClient(multiAddressClient).asClient()) {
            try (ReservedHttpConnection connection = client.reserveConnection(
                    client.get(requestTarget(multiAddressClient))).toFuture().get()) {

                asyncAggregatedRequester(connection, multiAddressClient);
            }
        }
    }

    void asyncAggregatedRequester(HttpRequester requester, boolean multiAddressClient) throws Exception {
        HttpRequest request = requester.get(requestTarget(multiAddressClient));
        Single<HttpResponse> firstSingle = requester.request(request);
        Future<HttpResponse> first = firstSingle.toFuture();
        receivedFirstRequest.await();
        Future<HttpResponse> firstConcurrent = firstSingle.toFuture();
        Future<HttpResponse> secondConcurrent = requester.request(request).toFuture();

        assertRejected(firstConcurrent);
        assertRejected(secondConcurrent);
        responseProcessor.onComplete();
        assertAggregatedResponse(first.get(), OK);

        assertSequential(multiAddressClient, request, firstSingle);
        assertSequential(multiAddressClient, request, requester.request(request));

        assertThat(receivedRequests.get(), is(3));
    }

    @ParameterizedTest(name = "{displayName} [{index}] multiAddressClient={0}")
    @ValueSource(booleans = {false, true})
    void blockingStreamingClient(boolean multiAddressClient) throws Exception {
        try (BlockingStreamingHttpClient client = newClient(multiAddressClient).asBlockingStreamingClient()) {
            blockingStreamingRequester(client, multiAddressClient);
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] multiAddressClient={0}")
    @ValueSource(booleans = {false, true})
    void blockingStreamingConnection(boolean multiAddressClient) throws Exception {
        try (BlockingStreamingHttpClient client = newClient(multiAddressClient).asBlockingStreamingClient()) {
            try (ReservedBlockingStreamingHttpConnection connection = client.reserveConnection(
                    client.get(requestTarget(multiAddressClient)))) {

                blockingStreamingRequester(connection, multiAddressClient);
            }
        }
    }

    void blockingStreamingRequester(BlockingStreamingHttpRequester requester,
                                    boolean multiAddressClient) throws Exception {
        BlockingStreamingHttpRequest request = requester.get(requestTarget(multiAddressClient));
        Single<StreamingHttpResponse> firstSingle =
                executorExtension.executor().submit(() -> requester.request(request).toStreamingResponse());
        Future<StreamingHttpResponse> first = firstSingle.toFuture();
        receivedFirstRequest.await();
        Future<StreamingHttpResponse> firstConcurrent = firstSingle.toFuture();
        Future<StreamingHttpResponse> secondConcurrent =
                executorExtension.executor().submit(() -> requester.request(request).toStreamingResponse()).toFuture();

        assertRejected(firstConcurrent);
        assertRejected(secondConcurrent);
        responseProcessor.onComplete();
        assertResponse(first.get(), HTTP_1_1, OK, 0);

        assertSequential(multiAddressClient, request, firstSingle);
        assertSequential(multiAddressClient, request,
                executorExtension.executor().submit(() -> requester.request(request).toStreamingResponse()));

        assertThat(receivedRequests.get(), is(3));
    }

    @ParameterizedTest(name = "{displayName} [{index}] multiAddressClient={0}")
    @ValueSource(booleans = {false, true})
    void blockingAggregatedClient(boolean multiAddressClient) throws Exception {
        try (BlockingHttpClient client = newClient(multiAddressClient).asBlockingClient()) {
            blockingAggregatedRequester(client, multiAddressClient);
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] multiAddressClient={0}")
    @ValueSource(booleans = {false, true})
    void blockingAggregatedConnection(boolean multiAddressClient) throws Exception {
        try (BlockingHttpClient client = newClient(multiAddressClient).asBlockingClient()) {
            try (ReservedBlockingHttpConnection connection = client.reserveConnection(
                    client.get(requestTarget(multiAddressClient)))) {

                blockingAggregatedRequester(connection, multiAddressClient);
            }
        }
    }

    void blockingAggregatedRequester(BlockingHttpRequester requester, boolean multiAddressClient) throws Exception {
        HttpRequest request = requester.get(requestTarget(multiAddressClient));
        Single<HttpResponse> firstSingle = executorExtension.executor().submit(() -> requester.request(request));
        Future<HttpResponse> first = firstSingle.toFuture();
        receivedFirstRequest.await();
        Future<HttpResponse> firstConcurrent = firstSingle.toFuture();
        Future<HttpResponse> secondConcurrent = executorExtension.executor().submit(() -> requester.request(request))
                .toFuture();

        assertRejected(firstConcurrent);
        assertRejected(secondConcurrent);
        responseProcessor.onComplete();
        assertAggregatedResponse(first.get(), OK);

        assertSequential(multiAddressClient, request, firstSingle);
        assertSequential(multiAddressClient, request,
                executorExtension.executor().submit(() -> requester.request(request)));

        assertThat(receivedRequests.get(), is(3));
    }

    private StreamingHttpClient newClient(boolean multiAddressClient) {
        return multiAddressClient ?
                HttpClients.forMultiAddressUrl(getClass().getSimpleName()).buildStreaming() :
                HttpClients.forSingleAddress(serverHostAndPort(serverCtx)).buildStreaming();
    }

    private String requestTarget(boolean multiAddressClient) {
        return multiAddressClient ? "http://" + serverHostAndPort(serverCtx) + "/" : "/";
    }

    private static void assertRejected(Future<? extends HttpResponseMetaData> future) {
        ExecutionException e = assertThrows(ExecutionException.class, future::get);
        assertThat(e.getCause(), is(instanceOf(RejectedSubscribeException.class)));
    }

    private void assertSequential(boolean multiAddressClient, HttpRequestMetaData request,
                                  Single<? extends HttpResponseMetaData> responseSingle) throws Exception {
        // We need to reset requestTarget for MultiAddress client because it changed from absolute to relative format.
        request.requestTarget(requestTarget(multiAddressClient));
        Future<? extends HttpResponseMetaData> future = responseSingle.toFuture();
        HttpResponseMetaData response = future.get();
        if (response instanceof StreamingHttpResponse) {
            assertResponse((StreamingHttpResponse) response, HTTP_1_1, NO_CONTENT, 0);
        } else if (response instanceof HttpResponse) {
            assertAggregatedResponse((HttpResponse) response, NO_CONTENT);
        } else {
            throw new AssertionError("Unexpected response type: " + response.getClass());
        }
    }

    private static void assertAggregatedResponse(HttpResponse response, HttpResponseStatus status) {
        assertResponse(response, HTTP_1_1, status);
        assertThat(response.payloadBody(), is(EMPTY_BUFFER));
    }
}
