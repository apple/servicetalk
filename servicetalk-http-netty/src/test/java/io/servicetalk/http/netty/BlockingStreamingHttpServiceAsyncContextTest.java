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
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.http.api.BlockingStreamingHttpService;
import io.servicetalk.http.api.HttpPayloadWriter;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.transport.api.ServerContext;

import static io.servicetalk.http.api.HttpResponseStatus.BAD_GATEWAY;
import static io.servicetalk.http.api.HttpResponseStatus.BAD_REQUEST;
import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static java.lang.Integer.toHexString;
import static java.lang.System.identityHashCode;
import static java.lang.Thread.currentThread;

class BlockingStreamingHttpServiceAsyncContextTest extends AbstractHttpServiceAsyncContextTest {

    @Override
    protected ServerContext serverWithEmptyAsyncContextService(HttpServerBuilder serverBuilder,
                                                               boolean useImmediate) throws Exception {
        // Ignore "useImmediate"
        return serverBuilder.listenBlockingStreamingAndAwait(newEmptyAsyncContextService());
    }

    private static BlockingStreamingHttpService newEmptyAsyncContextService() {
        return (ctx, request, response) -> {
            // We intentionally do not consume request.messageBody() to evaluate behavior with auto-draining.
            // A use-case with consumed request.messageBody() is evaluated by aggregated API.

            if (!AsyncContext.isEmpty()) {
                response.status(INTERNAL_SERVER_ERROR);
            } else {
                CharSequence requestId = request.headers().getAndRemove(REQUEST_ID_HEADER);
                if (requestId != null) {
                    AsyncContext.put(K1, requestId);
                    response.setHeader(REQUEST_ID_HEADER, requestId);
                } else {
                    response.status(BAD_REQUEST);
                }
            }

            BufferAllocator alloc = ctx.executionContext().bufferAllocator();
            try (HttpPayloadWriter<Buffer> writer = response.sendMetaData()) {
                writer.write(alloc.fromUtf8(toHexString(identityHashCode(AsyncContext.context()))));
            }
        };
    }

    @Override
    protected ServerContext serverWithService(HttpServerBuilder serverBuilder,
                                              boolean useImmediate, boolean asyncService) throws Exception {
        // Ignore "useImmediate" and "asyncService"
        return serverBuilder.listenBlockingStreamingAndAwait(service());
    }

    private static BlockingStreamingHttpService service() {
        return (ctx, request, response) -> {
            // We intentionally do not consume request.messageBody() to evaluate behavior with auto-draining.
            // A use-case with consumed request.messageBody() is evaluated by aggregated API.

            if (currentThread().getName().startsWith(IO_THREAD_PREFIX)) {
                // verify that we are not offloaded
                response.status(BAD_GATEWAY).sendMetaData().close();
                return;
            }

            CharSequence requestId = AsyncContext.get(K1);
            if (requestId != null) {
                response.setHeader(REQUEST_ID_HEADER, requestId);
            } else {
                response.status(INTERNAL_SERVER_ERROR).setHeader(REQUEST_ID_HEADER, "null");
            }
            response.sendMetaData().close();
        };
    }
}
