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
package io.servicetalk.examples.http.helloworld;

import io.servicetalk.buffer.netty.BufferAllocators;
import io.servicetalk.http.api.HttpConnection;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.netty.DefaultHttpConnectionBuilder;
import io.servicetalk.transport.api.DefaultExecutionContext;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.netty.NettyIoExecutors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.http.api.HttpRequests.newRequest;
import static java.nio.charset.StandardCharsets.US_ASCII;

public final class HelloWorldClient {
    private static final Logger LOGGER = LogManager.getLogger(HelloWorldClient.class);

    public static void main(String[] args) throws Exception {
        // Shared IoExecutor for the application.
        IoExecutor ioExecutor = NettyIoExecutors.createExecutor();
        try {
            InetSocketAddress serverAddress = new InetSocketAddress(8080);

            DefaultHttpConnectionBuilder<InetSocketAddress> connectionBuilder = new DefaultHttpConnectionBuilder<>();
            ExecutionContext executionContext =
                    new DefaultExecutionContext(DEFAULT_ALLOCATOR, ioExecutor, newCachedThreadExecutor());
            HttpConnection<HttpPayloadChunk, HttpPayloadChunk> connection =
                    awaitIndefinitely(connectionBuilder.build(executionContext, serverAddress));
            assert connection != null;


            connection.request(newRequest(GET, "/sayHello", connection.getExecutionContext().getExecutor()))
                    .flatMapPublisher(resp -> {
                        LOGGER.info("got response {}", resp.toString((name, value) -> value));
                        return resp.getPayloadBody().map(chunk -> chunk.getContent().toString(US_ASCII));
                    })
                    .concatWith(connection.closeAsync()) // close connection after the response is completed.
                    .forEach(stringPayloadChunk -> LOGGER.info("converted string chunk '{}'", stringPayloadChunk));

            // await connection close which is done above after the response is processed.
            awaitIndefinitely(connection.onClose());
        } finally {
            awaitIndefinitely(ioExecutor.closeAsync());
        }
    }
}
