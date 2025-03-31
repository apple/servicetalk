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
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.transport.api.ServerContext;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNone;
import static java.lang.Integer.toHexString;
import static java.lang.System.identityHashCode;
import static java.lang.Thread.currentThread;

class StreamingHttpServiceAsyncContextTest extends AbstractHttpServiceAsyncContextTest {

    @Override
    protected boolean isBlocking() {
        return false;
    }

    @Override
    protected ServerContext serverWithEmptyAsyncContextService(HttpServerBuilder serverBuilder,
                                                               boolean useImmediate) throws Exception {
        if (useImmediate) {
            serverBuilder.executionStrategy(offloadNone());
        }
        return serverBuilder.listenStreamingAndAwait(newEmptyAsyncContextService());
    }

    private StreamingHttpService newEmptyAsyncContextService() {
        return (ctx, request, factory) -> {
            // We intentionally do not consume request.messageBody() to evaluate behavior with auto-draining.
            // A use-case with consumed request.messageBody() is evaluated by aggregated API.

            StreamingHttpResponse response;
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
            return succeeded(response.payloadBody(from(
                    alloc.fromUtf8(toHexString(identityHashCode(AsyncContext.context()))))));
        };
    }

    @Override
    protected ServerContext serverWithService(HttpServerBuilder serverBuilder, boolean useImmediate,
                                              boolean asyncService) throws Exception {
        if (useImmediate) {
            serverBuilder.executionStrategy(offloadNone());
        }
        return serverBuilder.listenStreamingAndAwait(service(useImmediate, asyncService));
    }

    private StreamingHttpService service(final boolean useImmediate, final boolean asyncService) {
        return new StreamingHttpService() {
            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        final StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {
                // We intentionally do not consume request.messageBody() to evaluate behavior with auto-draining.
                // A use-case with consumed request.messageBody() is evaluated by aggregated API.

                return asyncService ? defer(() -> doHandle(responseFactory).shareContextOnSubscribe()) :
                        doHandle(responseFactory);
            }

            private Single<StreamingHttpResponse> doHandle(StreamingHttpResponseFactory factory) {
                boolean isIoThread = currentThread().getName().startsWith(IO_THREAD_PREFIX);
                if ((useImmediate && !isIoThread) || (!useImmediate && isIoThread)) {
                    // verify that if we expect to be offloaded, that we actually are
                    return succeeded(factory.badGateway());
                }

                CharSequence requestId = AsyncContext.get(K1);
                if (requestId != null) {
                    return succeeded(factory.ok().setHeader(REQUEST_ID_HEADER, requestId));
                } else {
                    return succeeded(factory.internalServerError().setHeader(REQUEST_ID_HEADER, "null"));
                }
            }
        };
    }
}
