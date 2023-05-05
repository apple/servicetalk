/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpServerContext;
import io.servicetalk.http.api.ReservedBlockingHttpConnection;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutor;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNone;
import static io.servicetalk.test.resources.TestUtils.assertNoAsyncErrors;
import static io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutors.toEventLoopAwareNettyIoExecutor;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;

class ConnectionContextIoExecutorTest {

    @RegisterExtension
    static final ExecutionContextExtension SERVER_CTX =
            ExecutionContextExtension.cached("server-io", "server-executor")
                    .setClassLevel(true);
    @RegisterExtension
    static final ExecutionContextExtension CLIENT_CTX =
            ExecutionContextExtension.cached("client-io", "client-executor")
                    .setClassLevel(true);

    @ParameterizedTest(name = "protocol={0}")
    @EnumSource(HttpProtocol.class)
    void test(HttpProtocol protocol) throws Exception {
        BlockingQueue<Throwable> asyncErrors = new LinkedBlockingQueue<>();
        try (HttpServerContext serverContext = BuilderUtils.newServerBuilder(SERVER_CTX, protocol)
                .executionStrategy(offloadNone())
                .listenBlockingAndAwait(((ctx, request, responseFactory) -> {
                    try {
                        EventLoopAwareNettyIoExecutor eventLoopExecutor =
                                toEventLoopAwareNettyIoExecutor(ctx.executionContext().ioExecutor());
                        assertThat("HttpServiceContext should not have the same IoExecutor as HttpServerContext",
                                eventLoopExecutor, is(not(sameInstance(SERVER_CTX.ioExecutor()))));
                        assertThat("HttpServiceContext should have a single-threaded IoExecutor",
                                eventLoopExecutor.next(), is(sameInstance(eventLoopExecutor)));
                        assertThat("HttpService should execute on IoExecutor",
                                eventLoopExecutor.isCurrentThreadEventLoop(), is(true));
                    } catch (Throwable t) {
                        asyncErrors.add(t);
                    }
                    return responseFactory.ok();
                }));
             BlockingHttpClient client = BuilderUtils.newClientBuilder(serverContext, CLIENT_CTX, protocol)
                     .executionStrategy(offloadNone())
                     .appendConnectionFilter(new StreamingHttpConnectionFilterFactory() {
                         @Override
                         public StreamingHttpConnectionFilter create(FilterableStreamingHttpConnection connection) {
                             return new StreamingHttpConnectionFilter(connection) {
                                 @Override
                                 public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
                                     assertThat(executionContext(),
                                             is(sameInstance(connectionContext().executionContext())));
                                     EventLoopAwareNettyIoExecutor eventLoopExecutor =
                                             assertSingleThreadedEventLoop(executionContext().ioExecutor());
                                     return delegate().request(request).whenOnSuccess(response ->
                                             assertThat("Response should execute on IoExecutor",
                                                     eventLoopExecutor.isCurrentThreadEventLoop(), is(true)));
                                 }
                             };
                         }

                         @Override
                         public HttpExecutionStrategy requiredOffloads() {
                             return offloadNone();
                         }
                     })
                     .buildBlocking();
             ReservedBlockingHttpConnection connection = client.reserveConnection(client.get("/"))) {

            assertThat(client.executionContext().ioExecutor(), is(sameInstance(CLIENT_CTX.ioExecutor())));

            assertThat(connection.executionContext(),
                    is(sameInstance(connection.connectionContext().executionContext())));
            assertSingleThreadedEventLoop(connection.executionContext().ioExecutor());
            assertNoAsyncErrors(asyncErrors);
        }
    }

    private static EventLoopAwareNettyIoExecutor assertSingleThreadedEventLoop(IoExecutor ioExecutor) {
        EventLoopAwareNettyIoExecutor eventLoopAwareNettyIoExecutor =
                toEventLoopAwareNettyIoExecutor(ioExecutor);
        assertThat(eventLoopAwareNettyIoExecutor, is(not(sameInstance(CLIENT_CTX.ioExecutor()))));
        assertThat("IoExecutor should be single-threaded",
                eventLoopAwareNettyIoExecutor.next(), is(sameInstance(eventLoopAwareNettyIoExecutor)));
        return eventLoopAwareNettyIoExecutor;
    }
}
