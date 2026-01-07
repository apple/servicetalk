/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.BlockingHttpService;
import io.servicetalk.http.api.BlockingStreamingHttpRequest;
import io.servicetalk.http.api.BlockingStreamingHttpServerResponse;
import io.servicetalk.http.api.BlockingStreamingHttpService;
import io.servicetalk.http.api.HttpApiConversions;
import io.servicetalk.http.api.HttpPayloadWriter;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseFactory;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpServerContext;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.netty.AsyncContextHttpFilterVerifier.AsyncContextAssertionFilter;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.BiFunction;

import static io.servicetalk.http.api.HttpApiConversions.toBlockingHttpService;
import static io.servicetalk.http.api.HttpApiConversions.toBlockingStreamingHttpService;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.AsyncContextHttpFilterVerifier.K1;
import static io.servicetalk.http.netty.AsyncContextHttpFilterVerifier.K2;
import static io.servicetalk.http.netty.AsyncContextHttpFilterVerifier.K3;
import static io.servicetalk.http.netty.AsyncContextHttpFilterVerifier.V1;
import static io.servicetalk.http.netty.AsyncContextHttpFilterVerifier.V2;
import static io.servicetalk.http.netty.AsyncContextHttpFilterVerifier.V3;
import static io.servicetalk.http.netty.AsyncContextHttpFilterVerifier.asyncContextRequestHandler;
import static io.servicetalk.http.netty.BuilderUtils.newClientBuilder;
import static io.servicetalk.http.netty.BuilderUtils.newServerBuilder;
import static io.servicetalk.test.resources.TestUtils.assertNoAsyncErrors;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

class ServiceApiConversionsAsyncContextTest {

    @RegisterExtension
    static final ExecutionContextExtension SERVER_CTX =
            ExecutionContextExtension.cached("server-io", "server-executor")
                    .setClassLevel(true);
    @RegisterExtension
    static final ExecutionContextExtension CLIENT_CTX =
            ExecutionContextExtension.cached("client-io", "client-executor")
                    .setClassLevel(true);

    @Test
    void testStreaming() throws Exception {
        runTest((builder, errors) -> builder
                .appendServiceFilter(new AsyncContextAssertionFilter(errors, true, true, true, false, false))
                .listenStreaming(asyncContextRequestHandler(errors)));
    }

    @Test
    void testOriginalBlockingHttpService() throws Exception {
        runTest((builder, errors) -> builder
                .appendServiceFilter(new AsyncContextAssertionFilter(errors, false, true, false, true, false))
                .listenBlocking(new BlockingHttpService() {
                    @Override
                    public HttpResponse handle(final HttpServiceContext ctx, final HttpRequest request,
                                               final HttpResponseFactory responseFactory) {
                        AsyncContext.put(K1, V1);
                        AsyncContext.put(K2, V2);
                        return responseFactory.ok().payloadBody(request.payloadBody());
                    }
                }));
    }

    @Test
    void testOriginalHttpService() throws Exception {
        runTest((builder, errors) -> builder
                .appendServiceFilter(new AsyncContextAssertionFilter(errors, false, true, false, true, false))
                .listen(new HttpService() {
                    @Override
                    public Single<HttpResponse> handle(final HttpServiceContext ctx, final HttpRequest request,
                                                       final HttpResponseFactory responseFactory) {
                        AsyncContext.put(K1, V1);
                        return Single.succeeded(responseFactory.ok().payloadBody(request.payloadBody()))
                                .beforeOnSuccess(ignore -> AsyncContext.put(K2, V2));
                    }
                }));
    }

    @Test
    void testOriginalBlockingStreamingHttpService() throws Exception {
        runTest((builder, errors) -> builder
                // We expect that the service will make a copy of the AsyncContext when it subscribes to payload body
                // because if we share, then behavior of any modifications inside for-each loop are not guaranteed to be
                // visible inside request.transformMessageBody because of the intermediate queueing of data.
                .appendServiceFilter(new AsyncContextAssertionFilter(errors, true, true, true, false, true))
                .listenBlockingStreaming(new BlockingStreamingHttpService() {
                    @Override
                    public void handle(final HttpServiceContext ctx, final BlockingStreamingHttpRequest request,
                                       final BlockingStreamingHttpServerResponse response) throws Exception {
                        AsyncContext.put(K1, V1);
                        List<Buffer> received = new ArrayList<>();
                        for (Buffer buffer : request.payloadBody()) {
                            received.add(buffer);
                        }
                        AsyncContext.put(K2, V2);
                        try (HttpPayloadWriter<Buffer> writer = response.sendMetaData()) {
                            for (Buffer buffer : received) {
                                writer.write(buffer);
                            }
                            AsyncContext.put(K3, V3);
                        }
                    }
                }));
    }

    @Test
    void testToBlockingHttpService() throws Exception {
        runTest((builder, errors) -> builder
                .appendServiceFilter(new AsyncContextAssertionFilter(errors, false, true, true, true, false))
                .listenBlocking(toBlockingHttpService(asyncContextRequestHandler(errors))));
    }

    @Test
    void testToBlockingStreamingHttpService() throws Exception {
        runTest((builder, errors) -> builder
                .appendServiceFilter(new AsyncContextAssertionFilter(errors, true, true, true, false, false))
                .listenBlockingStreaming(toBlockingStreamingHttpService(asyncContextRequestHandler(errors))));
    }

    @Test
    void testToHttpService() throws Exception {
        runTest((builder, errors) -> builder
                .appendServiceFilter(new AsyncContextAssertionFilter(errors, false, true, true, true, false))
                .listen(HttpApiConversions.toHttpService(asyncContextRequestHandler(errors))));
    }

    private void runTest(BiFunction<HttpServerBuilder, Queue<Throwable>, Single<HttpServerContext>> serverModifier)
            throws Exception {

        final BlockingQueue<Throwable> errors = new LinkedBlockingDeque<>();
        final String content = "Hello World";

        try (HttpServerContext serverContext = serverModifier.apply(newServerBuilder(SERVER_CTX), errors)
                .toFuture().get();
             BlockingHttpClient client = newClientBuilder(serverContext, CLIENT_CTX).buildBlocking()) {

            HttpResponse resp = client.request(client.post("/test")
                    .payloadBody(client.executionContext().bufferAllocator().fromAscii(content)));
            assertThat(resp.status(), is(OK));
            assertThat(resp.payloadBody().toString(US_ASCII), is(equalTo(content)));
        }
        assertNoAsyncErrors(errors);
    }
}
