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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.netty.BufferAllocators;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.Processors;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.transport.netty.internal.FlushStrategies;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;

import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.internal.TestTimeoutConstants.CI;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpRequestMethod.POST;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED_SERVER;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

class FlushStrategyForClientApiTest extends AbstractNettyHttpServerTest {

    private final CountDownLatch requestLatch = new CountDownLatch(1);
    private final BlockingQueue<Buffer> payloadBuffersReceived = new ArrayBlockingQueue<>(2);
    private volatile StreamingHttpRequest request;

    @BeforeEach
    void setUp() {
        setUp(CACHED, CACHED_SERVER);
    }

    @Override
    void service(final StreamingHttpService service) {
        super.service((ctx, request, responseFactory) -> {
            FlushStrategyForClientApiTest.this.request = request;
            requestLatch.countDown();
            request.payloadBody().forEach(buffer -> {
                try {
                    payloadBuffersReceived.put(buffer);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
            return Single.succeeded(responseFactory.ok());
        });
    }

    @Test
    void aggregatedApiShouldFlushOnEnd() throws Exception {
        final StreamingHttpConnection connection = streamingHttpConnection();

        // The payload never completes, so since the aggregated API should use `flushOnEnd`, it should never flush.
        final Single<StreamingHttpResponse> responseSingle = connection.request(
                connection.asConnection().newRequest(POST, "/")
                        .addHeader(TRANSFER_ENCODING, CHUNKED)
                        .toStreamingRequest().payloadBody(Publisher.never()));

        try {
            responseSingle.toFuture().get(CI ? 900 : 100, MILLISECONDS);
            fail("Expected timeout");
        } catch (TimeoutException e) {
            // After the timeout, we've given the client some time to write and send the metadata, if it was going to.
            assertNull(request);
        }
    }

    @Test
    void aggregatedApiShouldNotOverrideExplicit() throws Exception {
        final StreamingHttpConnection connection = streamingHttpConnection();

        ((NettyConnectionContext) connection.connectionContext()).updateFlushStrategy(
                (prev, isOriginal) -> FlushStrategies.flushOnEach());

        final Single<StreamingHttpResponse> responseSingle = connection.request(
                connection.asConnection().newRequest(POST, "/")
                        .addHeader(TRANSFER_ENCODING, CHUNKED)
                        .toStreamingRequest().payloadBody(Publisher.never()));

        responseSingle.toFuture(); // Subscribe, to initiate the request, but we don't care about the response.

        requestLatch.await(); // Wait for the server to receive the response, meaning the client wrote and flushed.
    }

    @Test
    void streamingApiShouldFlushOnEach() throws Exception {
        final StreamingHttpConnection connection = streamingHttpConnection();
        final SingleSource.Processor<Buffer, Buffer> payloadItemProcessor = Processors.newSingleProcessor();
        final Publisher<Buffer> payload = fromSource(payloadItemProcessor).toPublisher();
        final Single<StreamingHttpResponse> responseSingle = connection.request(
                connection.newRequest(POST, "/").payloadBody(payload));

        responseSingle.toFuture(); // Subscribe, to initiate the request, but we don't care about the response.

        requestLatch.await(); // Wait for the server to receive the response, meaning the client wrote and flushed.
        MatcherAssert.assertThat(payloadBuffersReceived.size(), is(0));

        final Buffer payloadItem = BufferAllocators.DEFAULT_ALLOCATOR.fromAscii("Hello");
        payloadItemProcessor.onSuccess(payloadItem);
        Buffer receivedBuffer = payloadBuffersReceived.take(); // Wait for the server to receive the payload
        MatcherAssert.assertThat(receivedBuffer, is(payloadItem));
    }
}
