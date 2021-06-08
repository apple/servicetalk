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

import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.http.api.BlockingStreamingHttpRequest;
import io.servicetalk.http.api.BlockingStreamingHttpServerResponse;
import io.servicetalk.http.api.BlockingStreamingHttpService;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.transport.api.ServerContext;

import static io.servicetalk.http.api.HttpResponseStatus.BAD_REQUEST;
import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
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
            request.payloadBody().forEach(__ -> { });

            if (!AsyncContext.isEmpty()) {
                response.status(INTERNAL_SERVER_ERROR).sendMetaData().close();
                return;
            }
            CharSequence requestId = request.headers().getAndRemove(REQUEST_ID_HEADER);
            if (requestId != null) {
                AsyncContext.put(K1, requestId);
                response.setHeader(REQUEST_ID_HEADER, requestId);
            } else {
                response.status(BAD_REQUEST);
            }
            response.sendMetaData().close();
        };
    }

    @Override
    protected ServerContext serverWithService(HttpServerBuilder serverBuilder,
                                              boolean useImmediate, boolean asyncService) throws Exception {
        // Ignore "useImmediate" and "asyncService"
        return serverBuilder.listenBlockingStreamingAndAwait(service());
    }

    private static BlockingStreamingHttpService service() {
        return new BlockingStreamingHttpService() {
            @Override
            public void handle(final HttpServiceContext ctx,
                               final BlockingStreamingHttpRequest request,
                               final BlockingStreamingHttpServerResponse response) throws Exception {
                doHandle(request, response);
            }

            private void doHandle(final BlockingStreamingHttpRequest request,
                                  final BlockingStreamingHttpServerResponse response) throws Exception {
                CharSequence requestId = AsyncContext.get(K1);
                // The test forces the server to consume the entire request here which will make sure the
                // AsyncContext is as expected while processing the request data in the filter.
                request.payloadBody().forEach(__ -> { });

                if (currentThread().getName().startsWith(IO_THREAD_PREFIX)) {
                    // verify that we actually are offloaded
                    response.status(INTERNAL_SERVER_ERROR).sendMetaData().close();
                    return;
                }

                CharSequence requestId2 = AsyncContext.get(K1);
                if (requestId2 == requestId && requestId2 != null) {
                    response.setHeader(REQUEST_ID_HEADER, requestId);
                } else {
                    response.status(INTERNAL_SERVER_ERROR)
                            .setHeader(REQUEST_ID_HEADER, String.valueOf(requestId))
                            .setHeader(REQUEST_ID_HEADER + "2", String.valueOf(requestId2));
                }
                response.sendMetaData().close();
            }
        };
    }
}
