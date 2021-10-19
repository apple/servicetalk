/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.router.predicate;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.CompletableSource.Processor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.http.netty.HttpServers;
import io.servicetalk.http.router.predicate.dsl.RouteContinuation;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.IoThreadFactory;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Processors.newCompletableProcessor;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.http.api.HttpExecutionStrategies.customStrategyBuilder;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.http.api.HttpSerializers.appSerializerUtf8FixLen;
import static io.servicetalk.test.resources.TestUtils.assertNoAsyncErrors;
import static io.servicetalk.transport.netty.NettyIoExecutors.createIoExecutor;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.lang.Thread.currentThread;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class HttpServerOverrideOffloadingTest {
    private static final String IO_EXECUTOR_THREAD_NAME_PREFIX = "http-server-io-executor";
    private static final HttpExecutionStrategy[] SERVER_STRATEGIES = new HttpExecutionStrategy[] {
            defaultStrategy(), noOffloadsStrategy() };

    private static final HttpExecutionStrategy[] ROUTE_STRATEGIES = new HttpExecutionStrategy[] {
            null, defaultStrategy(), noOffloadsStrategy(), customStrategyBuilder().offloadSend().build() };

    private static final IoExecutor IO_EXECUTOR = createIoExecutor(IO_EXECUTOR_THREAD_NAME_PREFIX);
    private OffloadingTesterService routeService;
    private ServerContext server;

    @SuppressWarnings("unused")
    static Stream<Arguments> cases() {
        return Arrays.stream(SERVER_STRATEGIES)
                .flatMap(serverOffloads -> Arrays.stream(ROUTE_STRATEGIES)
                        .map(strategy -> Arguments.of(serverOffloads, strategy)));
    }

    HttpClient setup(HttpExecutionStrategy serverStrategy,
                     @Nullable HttpExecutionStrategy routeStrategy) throws Exception {
        routeService = new OffloadingTesterService(routeStrategy);
        RouteContinuation route = new HttpPredicateRouterBuilder()
                .whenPathStartsWith("/service");
        if (null != routeStrategy) {
            route = route.executionStrategy(routeService.usingStrategy());
        }
        StreamingHttpService router = route.thenRouteTo(routeService).buildStreaming();

        server = HttpServers.forAddress(localAddress(0))
                .ioExecutor(IO_EXECUTOR)
                .executionStrategy(serverStrategy)
                .listenStreamingAndAwait(router);
        return HttpClients.forSingleAddress(serverHostAndPort(server)).build();
    }

    @AfterEach
    void tearDown() throws Exception {
        newCompositeCloseable().appendAll(server).closeAsyncGracefully().toFuture().get();
    }

    @ParameterizedTest(name = "serverStrategy={0} routeStrategy={1}")
    @MethodSource("cases")
    void test(HttpExecutionStrategy serverStrategy, @Nullable HttpExecutionStrategy routeStrategy) throws Exception {
        try (HttpClient client = setup(serverStrategy, routeStrategy)) {
            Buffer payload = client.executionContext().bufferAllocator().fromAscii("hello");
            client.request(client.post("/service").payloadBody(payload)).toFuture().get();
        }
        assertThat("Route unexpected invocation count.", routeService.invoked.get(), is(1));
        assertNoAsyncErrors("Route, unexpected errors: " + routeService.errors, routeService.errors);
    }

    private static final class OffloadingTesterService implements StreamingHttpService {

        private final @Nullable HttpExecutionStrategy usingStrategy;
        private final AtomicInteger invoked = new AtomicInteger();
        private final Queue<Throwable> errors = new ConcurrentLinkedQueue<>();

        private OffloadingTesterService(@Nullable final HttpExecutionStrategy usingStrategy) {
            this.usingStrategy = usingStrategy;
        }

        HttpExecutionStrategy usingStrategy() {
            return usingStrategy;
        }

        @Override
        public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx, final StreamingHttpRequest request,
                                                    final StreamingHttpResponseFactory responseFactory) {
            boolean offloading = ctx.executionContext().executionStrategy() != noOffloadsStrategy() ||
                    defaultStrategy() != usingStrategy;
            boolean expectReadMetaOffload = offloading &&
                    (ctx.executionContext().executionStrategy().isMetadataReceiveOffloaded() ||
                    (null != usingStrategy && usingStrategy.isMetadataReceiveOffloaded()));
            boolean expectReadDataOffload = offloading &&
                    (ctx.executionContext().executionStrategy().isDataReceiveOffloaded() ||
                    (null != usingStrategy && usingStrategy.isDataReceiveOffloaded()));
            boolean expectSendOffload = offloading &&
                    (ctx.executionContext().executionStrategy().isSendOffloaded() ||
                    (null != usingStrategy && usingStrategy.isSendOffloaded()));
            boolean handleOnIoThread = IoThreadFactory.IoThread.currentThreadIsIoThread();
            invoked.incrementAndGet();
            Processor cp = newCompletableProcessor();
            if ((!offloading && !handleOnIoThread) || (expectReadMetaOffload && handleOnIoThread)) {
                errors.add(new AssertionError("Invalid thread called the service. Thread: " +
                        currentThread()));
            }
            toSource(request.payloadBody().beforeOnNext(__ -> {
                boolean onIoThread = IoThreadFactory.IoThread.currentThreadIsIoThread();
                if ((!offloading && !onIoThread) || (expectReadDataOffload && onIoThread)) {
                    errors.add(new AssertionError("Invalid thread calling response payload onNext." +
                            "Thread: " + currentThread()));
                }
            }).beforeOnComplete(() -> {
                boolean onIoThread = IoThreadFactory.IoThread.currentThreadIsIoThread();
                if ((!offloading && !onIoThread) || (expectReadDataOffload && onIoThread)) {
                    errors.add(new AssertionError("Invalid thread calling response payload onComplete." +
                            "Thread: " + currentThread()));
                }
            }).ignoreElements()).subscribe(cp);
            return succeeded(responseFactory.ok().payloadBody(from("Hello"), appSerializerUtf8FixLen())
                    .transformPayloadBody(p -> p.beforeRequest(__ -> {
                        boolean onIoThread = IoThreadFactory.IoThread.currentThreadIsIoThread();
                        if ((!offloading && !onIoThread) || (expectSendOffload && onIoThread)) {
                            errors.add(new AssertionError("Invalid thread calling response payload " +
                                    "request-n. Thread: " + currentThread()));
                        }
                    })));
        }
    }
}
