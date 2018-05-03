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

import io.servicetalk.http.api.BlockingHttpConnection;
import io.servicetalk.http.api.BlockingHttpRequests;
import io.servicetalk.http.api.BlockingHttpResponse;
import io.servicetalk.http.api.HttpConnection;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.netty.DefaultHttpConnectionBuilder;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.netty.NettyIoExecutors;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.stream.StreamSupport;

import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.http.api.BlockingHttpRequests.newRequest;
import static io.servicetalk.http.api.HttpRequestMethods.GET;

public final class HelloWorldBlockingClient {

    public static void main(String[] args) throws Exception {
        // Shared IoExecutor for the application.
        IoExecutor ioExecutor = NettyIoExecutors.createExecutor();
        InetSocketAddress serverAddress = new InetSocketAddress(8080);

        DefaultHttpConnectionBuilder<InetSocketAddress> connectionBuilder = new DefaultHttpConnectionBuilder<>();
        HttpConnection<HttpPayloadChunk, HttpPayloadChunk> connection =
                awaitIndefinitely(connectionBuilder.build(ioExecutor, newCachedThreadExecutor(), serverAddress));
        assert connection != null;

        BlockingHttpConnection<HttpPayloadChunk, HttpPayloadChunk> conn = connection.asBlockingConnection();

        BlockingHttpResponse<HttpPayloadChunk> response =
                conn.request(newRequest(GET, "/sayHello", connection.getExecutionContext().getExecutor()));
        System.out.println(response.toString((name, value) -> value));
        for (HttpPayloadChunk chunk : response.getPayloadBody()) {
            System.out.println(chunk.getContent().toString(Charset.defaultCharset()));
        }
        conn.close();
        awaitIndefinitely(ioExecutor.closeAsync());
    }
}
