/*
 * Copyright © 2019-2025 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.transport.api.ServerContext;

import org.hamcrest.Matchers;

import static java.lang.Integer.toHexString;
import static java.lang.System.identityHashCode;
import static java.lang.Thread.currentThread;
import static org.hamcrest.MatcherAssert.assertThat;

class BlockingHttpServiceAsyncContextTest extends AbstractHttpServiceAsyncContextTest {

    @Override
    protected boolean isBlocking() {
        return true;
    }

    @Override
    protected ServerContext serverWithEmptyAsyncContextService(HttpServerBuilder serverBuilder,
                                                               boolean useImmediate) throws Exception {
        assertThat(useImmediate, Matchers.is(false));
        return serverBuilder.listenBlockingAndAwait(newEmptyAsyncContextService());
    }

    private static BlockingHttpService newEmptyAsyncContextService() {
        return (ctx, request, factory) -> {
            HttpResponse response;
            if (!AsyncContext.isEmpty()) {
                response = factory.internalServerError();
            } else {
                CharSequence requestId = request.headers().getAndRemove(REQUEST_ID_HEADER);
                if (requestId != null) {
                    AsyncContext.put(K1, requestId);
                    response = factory.ok().setHeader(REQUEST_ID_HEADER, requestId);
                } else {
                    response = factory.badRequest();
                }
            }
            BufferAllocator alloc = ctx.executionContext().bufferAllocator();
            return response.payloadBody(alloc.fromUtf8(toHexString(identityHashCode(AsyncContext.context()))));
        };
    }

    @Override
    protected ServerContext serverWithService(HttpServerBuilder serverBuilder,
                                              boolean useImmediate, boolean asyncService) throws Exception {
        assertThat(useImmediate, Matchers.is(false));
        assertThat(asyncService, Matchers.is(false));
        return serverBuilder.listenBlockingAndAwait(service());
    }

    private static BlockingHttpService service() {
        return (ctx, request, responseFactory) -> {
            if (currentThread().getName().startsWith(IO_THREAD_PREFIX)) {
                // verify that we are not offloaded
                return responseFactory.badGateway();
            }

            CharSequence requestId = AsyncContext.get(K1);
            if (requestId != null) {
                return responseFactory.ok().setHeader(REQUEST_ID_HEADER, requestId);
            } else {
                return responseFactory.internalServerError().setHeader(REQUEST_ID_HEADER, "null");
            }
        };
    }
}
