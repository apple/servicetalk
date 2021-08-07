/*
 * Copyright © 2018-2019, 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.AsyncContextMap;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.api.Test;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.http.api.HttpSerializers.appSerializerUtf8FixLen;
import static java.lang.Thread.currentThread;

class StreamingHttpServiceAsyncContextTest extends AbstractHttpServiceAsyncContextTest {

    @Test
    void newRequestsGetFreshContextImmediate() throws Exception {
        newRequestsGetFreshContext(true);
    }

    @Test
    void contextPreservedOverFilterBoundariesOffloadedAsyncService() throws Exception {
        contextPreservedOverFilterBoundaries(false, false, true);
    }

    @Test
    void contextPreservedOverFilterBoundariesOffloadedAsyncFilterAsyncService() throws Exception {
        contextPreservedOverFilterBoundaries(false, true, true);
    }

    @Test
    void contextPreservedOverFilterBoundariesNoOffload() throws Exception {
        contextPreservedOverFilterBoundaries(true, false, false);
    }

    @Test
    void contextPreservedOverFilterBoundariesNoOffloadAsyncService() throws Exception {
        contextPreservedOverFilterBoundaries(true, false, true);
    }

    @Test
    void contextPreservedOverFilterBoundariesNoOffloadAsyncFilter() throws Exception {
        contextPreservedOverFilterBoundaries(true, true, false);
    }

    @Test
    void contextPreservedOverFilterBoundariesNoOffloadAsyncFilterAsyncService() throws Exception {
        contextPreservedOverFilterBoundaries(true, true, true);
    }

    @Test
    void connectionAcceptorContextDoesNotLeakImmediate() throws Exception {
        connectionAcceptorContextDoesNotLeak(true);
    }

    @Override
    protected ServerContext serverWithEmptyAsyncContextService(HttpServerBuilder serverBuilder,
                                                     boolean useImmediate) throws Exception {
        if (useImmediate) {
            serverBuilder.executionStrategy(noOffloadsStrategy());
        }
        return serverBuilder.listenStreamingAndAwait(newEmptyAsyncContextService());
    }

    private static StreamingHttpService newEmptyAsyncContextService() {
        return (ctx, request, factory) -> {
            request.messageBody().ignoreElements().subscribe();

            AsyncContextMap current = AsyncContext.current();
            if (!current.isEmpty()) {
                return succeeded(factory.internalServerError().payloadBody(from(current.toString()),
                        appSerializerUtf8FixLen()));
            }
            CharSequence requestId = request.headers().getAndRemove(REQUEST_ID_HEADER);
            if (requestId != null) {
                current.put(K1, requestId);
                return succeeded(factory.ok()
                        .setHeader(REQUEST_ID_HEADER, requestId));
            } else {
                return succeeded(factory.badRequest());
            }
        };
    }

    @Override
    protected ServerContext serverWithService(HttpServerBuilder serverBuilder,
                                    boolean useImmediate, boolean asyncService) throws Exception {
        if (useImmediate) {
            serverBuilder.executionStrategy(noOffloadsStrategy());
        }
        return serverBuilder.listenStreamingAndAwait(service(useImmediate, asyncService));
    }

    private static StreamingHttpService service(final boolean useImmediate, final boolean asyncService) {
        return new StreamingHttpService() {
            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        final StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {
                return asyncService ? defer(() -> doHandle(request, responseFactory).subscribeShareContext()) :
                        doHandle(request, responseFactory);
            }

            private Single<StreamingHttpResponse> doHandle(final StreamingHttpRequest request,
                                                           final StreamingHttpResponseFactory factory) {
                CharSequence requestId = AsyncContext.get(K1);
                // The test doesn't wait until the request body is consumed and only cares when the request is received
                // from the client. So we force the server to consume the entire request here which will make sure the
                // AsyncContext is as expected while processing the request data in the filter.
                return request.messageBody().ignoreElements()
                        .concat(defer(() -> {
                            if (useImmediate && !currentThread().getName().startsWith(IO_THREAD_PREFIX)) {
                                // verify that if we expect to be offloaded, that we actually are
                                return succeeded(factory.internalServerError());
                            }
                            CharSequence requestId2 = AsyncContext.get(K1);
                            if (requestId2 == requestId && requestId2 != null) {
                                StreamingHttpResponse response = factory.ok();
                                response.headers().set(REQUEST_ID_HEADER, requestId);
                                return succeeded(response);
                            } else {
                                StreamingHttpResponse response = factory.internalServerError();
                                response.headers().set(REQUEST_ID_HEADER, String.valueOf(requestId));
                                response.headers().set(REQUEST_ID_HEADER + "2", String.valueOf(requestId2));
                                return succeeded(response);
                            }
                        }));
            }
        };
    }
}
