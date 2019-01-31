/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.CompletableProcessor;
import io.servicetalk.concurrent.api.DefaultThreadFactory;
import io.servicetalk.concurrent.api.Executor;
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
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServerContext;

import org.junit.After;
import org.junit.Test;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.transport.netty.NettyIoExecutors.createIoExecutor;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.lang.Thread.NORM_PRIORITY;
import static java.lang.Thread.currentThread;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

public class HttpServerOverrideOffloadingTest {
    private static final String IO_EXECUTOR_THREAD_NAME_PREFIX = "http-server-io-executor-";

    private final IoExecutor ioExecutor;
    private final Executor executor;
    private final OffloadingTesterService service1;
    private final OffloadingTesterService service2;
    private final HttpClient client;
    private final ServerContext server;

    public HttpServerOverrideOffloadingTest() throws Exception {
        ioExecutor = createIoExecutor(new DefaultThreadFactory(IO_EXECUTOR_THREAD_NAME_PREFIX, true,
                NORM_PRIORITY));
        executor = newCachedThreadExecutor();
        service1 = new OffloadingTesterService(noOffloadsStrategy(), th -> !isInServerEventLoop(th));
        service2 = new OffloadingTesterService(defaultStrategy(executor),
                HttpServerOverrideOffloadingTest::isInServerEventLoop);
        server = HttpServers.forAddress(localAddress())
                .ioExecutor(ioExecutor)
                .listenAndAwait(new HttpPredicateRouterBuilder()
                        .executionStrategy(noOffloadsStrategy())
                        .whenPathStartsWith("/service1").thenRouteTo(service1)
                        .whenPathStartsWith("/service2").thenRouteTo(service2).build());
        client = HttpClients.forSingleAddress(serverHostAndPort(server)).build();
    }

    @After
    public void tearDown() throws Exception {
        newCompositeCloseable().appendAll(client, server, ioExecutor, executor).closeAsync().toFuture().get();
    }

    private static boolean isInServerEventLoop(Thread thread) {
        return thread.getName().startsWith(IO_EXECUTOR_THREAD_NAME_PREFIX);
    }

    @Test
    public void offloadDifferentRoutes() throws Exception {
        client.request(client.get("/service1")).toFuture().get();
        assertThat("Service-1 unexpected invocation count.", service1.invoked.get(), is(1));
        assertThat("Service-1, unexpected errors: " + service1.errors, service1.errors, hasSize(0));
        client.request(client.get("/service2")).toFuture().get();
        assertThat("Service-2 unexpected invocation count.", service2.invoked.get(), is(1));
        assertThat("Service-2, unexpected errors: " + service2.errors, service2.errors, hasSize(0));
    }

    private static final class OffloadingTesterService extends StreamingHttpService {

        private final AtomicInteger invoked = new AtomicInteger();
        private final HttpExecutionStrategy strategy;
        private final Predicate<Thread> isInvalidThread;
        private final ConcurrentLinkedQueue<AssertionError> errors;

        private OffloadingTesterService(final HttpExecutionStrategy strategy, final Predicate<Thread> isInvalidThread) {
            this.strategy = strategy;
            this.isInvalidThread = isInvalidThread;
            errors = new ConcurrentLinkedQueue<>();
        }

        @Override
        public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx, final StreamingHttpRequest request,
                                                    final StreamingHttpResponseFactory responseFactory) {
            invoked.incrementAndGet();
            CompletableProcessor cp = new CompletableProcessor();
            if (isInvalidThread.test(currentThread())) {
                errors.add(new AssertionError("Invalid thread called the service. Thread: " +
                        currentThread()));
            }
            request.payloadBody().doBeforeNext(__ -> {
                if (isInvalidThread.test(currentThread())) {
                    errors.add(new AssertionError("Invalid thread calling response payload onNext." +
                            "Thread: " + currentThread()));
                }
            }).doBeforeComplete(() -> {
                if (isInvalidThread.test(currentThread())) {
                    errors.add(new AssertionError("Invalid thread calling response payload onComplete." +
                            "Thread: " + currentThread()));
                }
            }).ignoreElements().subscribe(cp);
            return success(responseFactory.ok().payloadBody(just("Hello"), textSerializer())
                    .transformPayloadBody(p -> p.doBeforeRequest(__ -> {
                        if (isInvalidThread.test(currentThread())) {
                            errors.add(new AssertionError("Invalid thread calling response payload " +
                                    "request-n. Thread: " + currentThread()));
                        }
                    })));
        }

        @Override
        public HttpExecutionStrategy executionStrategy() {
            return strategy;
        }
    }
}
