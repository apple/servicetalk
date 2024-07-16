/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.examples.http.files;

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.netty.HttpServers;
import io.servicetalk.transport.api.IoExecutor;

import java.io.InputStream;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Publisher.fromInputStream;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN_UTF_8;

/**
 * A simple server that serves content from a file via {@link InputStream}. Note reading from file into application
 * memory maybe blocking and this example uses the default execution strategy which consumes from the
 * {@link InputStream} on a non-{@link IoExecutor} thread to avoid blocking EventLoop threads.
 */
public final class FilesServer {
    public static void main(String[] args) throws Exception {
        HttpServers.forPort(8080)
            .listenStreamingAndAwait((ctx, request, responseFactory) -> {
                // InputStream lifetime ownership is transferred to ServiceTalk (e.g. it will call close) because
                // we create a new InputStream per request and always pass it to ServiceTalk as the response payload
                // body (if not null).
                final InputStream responseStream = FilesServer.class.getClassLoader()
                        .getResourceAsStream("response_payload.txt");
                final BufferAllocator allocator = ctx.executionContext().bufferAllocator();
                final StreamingHttpResponse response = responseStream == null ?
                            responseFactory.notFound().payloadBody(
                                    from(allocator.fromAscii("file not found, please rebuild the project"))) :
                            responseFactory.ok().payloadBody(fromInputStream(responseStream, allocator::wrap));
                response.headers().set(CONTENT_TYPE, TEXT_PLAIN_UTF_8);
                return succeeded(response);
            }).awaitShutdown();
    }
}
