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

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.http.api.BlockingHttpService;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.transport.api.ServerContext;

import static java.lang.Thread.currentThread;

class BlockingHttpServiceAsyncContextTest extends AbstractHttpServiceAsyncContextTest {

    @Override
    protected ServerContext serverWithEmptyAsyncContextService(HttpServerBuilder serverBuilder,
                                                               boolean useImmediate) throws Exception {
        // Ignore "useImmediate"
        return serverBuilder.listenBlockingAndAwait(newEmptyAsyncContextService());
    }

    private static BlockingHttpService newEmptyAsyncContextService() {
        return (ctx, request, factory) -> {
            if (!AsyncContext.isEmpty()) {
                BufferAllocator alloc = ctx.executionContext().bufferAllocator();
                return factory.internalServerError()
                        .payloadBody(alloc.fromAscii(AsyncContext.context().toString()));
            }
            CharSequence requestId = request.headers().getAndRemove(REQUEST_ID_HEADER);
            if (requestId != null) {
                AsyncContext.put(K1, requestId);
                return factory.ok().setHeader(REQUEST_ID_HEADER, requestId);
            } else {
                return factory.badRequest();
            }
        };
    }

    @Override
    protected ServerContext serverWithService(HttpServerBuilder serverBuilder,
                                              boolean useImmediate, boolean asyncService) throws Exception {
        // Ignore "useImmediate" and "asyncService"
        return serverBuilder.listenBlockingAndAwait(service());
    }

    private static BlockingHttpService service() {
        return (ctx, request, responseFactory) -> {
            CharSequence requestId = AsyncContext.get(K1);

            if (currentThread().getName().startsWith(IO_THREAD_PREFIX)) {
                // verify that we are not offloaded
                return responseFactory.badGateway();
            }

            if (requestId != null) {
                return responseFactory.ok().setHeader(REQUEST_ID_HEADER, requestId);
            } else {
                return responseFactory.internalServerError()
                        .setHeader(REQUEST_ID_HEADER, "null");
            }
        };
    }
}
