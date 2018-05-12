/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.examples.http.aggregation;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.HttpConnection;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.netty.DefaultHttpConnectionBuilder;
import io.servicetalk.transport.api.DefaultExecutionContext;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.netty.NettyIoExecutors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpPayloadChunks.aggregateChunks;
import static io.servicetalk.http.api.HttpPayloadChunks.newPayloadChunk;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.http.api.HttpRequests.newRequest;
import static java.nio.charset.StandardCharsets.US_ASCII;

public final class AggregatingPayloadClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(AggregatingPayloadClient.class);

    public static void main(String[] args) throws Exception {
        // Shared IoExecutor for the application.
        IoExecutor ioExecutor = NettyIoExecutors.createIoExecutor();
        final ExecutionContext executionContext = new DefaultExecutionContext(DEFAULT_ALLOCATOR, ioExecutor, immediate());
        try {
            InetSocketAddress serverAddress = new InetSocketAddress("127.0.0.1", 8081);

            DefaultHttpConnectionBuilder<InetSocketAddress> connectionBuilder = new DefaultHttpConnectionBuilder<>();
            HttpConnection<HttpPayloadChunk, HttpPayloadChunk> connection =
                    awaitIndefinitely(connectionBuilder.build(executionContext, serverAddress));
            assert connection != null;

            Buffer data = executionContext.getBufferAllocator().fromAscii("lorem ipsum dolor sit amet \n");
            Publisher<HttpPayloadChunk> requestpayload = success(newPayloadChunk(data)).repeat(count -> count <= 10);

            HttpRequest<HttpPayloadChunk> request = newRequest(GET, "/sayHello", requestpayload);
            // This is required at the moment since HttpClient does not add a transfer-encoding header.
            request.getHeaders().add(TRANSFER_ENCODING, CHUNKED);
            connection.request(request)
                    .flatMap(resp -> {
                        LOGGER.info("got response \n{}", resp.toString((name, value) -> value));
                        return aggregateChunks(resp.getPayloadBody(), executionContext.getBufferAllocator());
                    })
                    .concatWith(connection.closeAsync()) // close connection after the response is completed.
                    .subscribe(chunk -> LOGGER.info("Response content: \n{}", chunk.getContent().toString(US_ASCII)));

            // await connection close which is done above after the response is processed.
            awaitIndefinitely(connection.onClose());
        } finally {
            awaitIndefinitely(ioExecutor.closeAsync());
        }
    }
}
