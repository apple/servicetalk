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
package io.servicetalk.http.netty;

import io.servicetalk.concurrent.api.DefaultThreadFactory;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServerContext;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.http.netty.HttpServers.forPort;
import static io.servicetalk.transport.netty.NettyIoExecutors.createIoExecutor;
import static java.lang.Thread.NORM_PRIORITY;
import static java.lang.Thread.currentThread;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

@RunWith(Parameterized.class)
public class HttpStreamingClientOverrideOffloadingTest {
    private static final String IO_EXECUTOR_THREAD_NAME_PREFIX = "http-client-io-executor-";

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private final IoExecutor ioExecutor;
    private final Executor executor;
    private final Predicate<Thread> isInvalidThread;
    private final HttpExecutionStrategy overridingStrategy;
    private final ServerContext server;
    private final StreamingHttpClient client;

    public HttpStreamingClientOverrideOffloadingTest(@SuppressWarnings("unused") final String description,
                                                     final Predicate<Thread> isInvalidThread,
                                                     @Nullable final HttpExecutionStrategy overridingStrategy,
                                                     @Nullable final HttpExecutionStrategy defaultStrategy) throws Exception {
        this.ioExecutor = createIoExecutor(new DefaultThreadFactory(IO_EXECUTOR_THREAD_NAME_PREFIX, true,
                NORM_PRIORITY));
        this.executor = newCachedThreadExecutor();
        this.isInvalidThread = isInvalidThread;
        this.overridingStrategy = overridingStrategy == null ? defaultStrategy(executor) : overridingStrategy;
        server = forPort(0).listenStreamingAndAwait((ctx, request, responseFactory) -> success(responseFactory.ok()));
        InetSocketAddress socketAddress = (InetSocketAddress) server.listenAddress();
        client = HttpClients.forSingleAddress(socketAddress.getHostName(), socketAddress.getPort())
                .ioExecutor(ioExecutor)
                .executionStrategy(defaultStrategy == null ? defaultStrategy(executor) : defaultStrategy)
                .buildStreaming();
    }

    @Parameterized.Parameters(name = "{index} - {0}")
    public static Collection<Object[]> params() {
        List<Object[]> params = new ArrayList<>();
        params.add(newParam("Override no offload", th -> !isInClientEventLoop(th), noOffloadsStrategy(), null));
        params.add(newParam("Default no offload", HttpStreamingClientOverrideOffloadingTest::isInClientEventLoop,
                null, noOffloadsStrategy()));
        params.add(newParam("Both offloads", HttpStreamingClientOverrideOffloadingTest::isInClientEventLoop, null, null));
        return params;
    }

    private static Object[] newParam(String description, Predicate<Thread> isInvalidThread,
                                     @Nullable HttpExecutionStrategy overridingStrategy,
                                     @Nullable HttpExecutionStrategy defaultStrategy) {
        Object[] param = new Object[4];
        param[0] = description;
        param[1] = isInvalidThread;
        param[2] = overridingStrategy;
        param[3] = defaultStrategy;
        return param;
    }

    @After
    public void tearDown() throws Exception {
        newCompositeCloseable().appendAll(client, server, ioExecutor, executor).closeAsync().toFuture().get();
    }

    @Test
    public void reserveRespectsDisable() throws Exception {
        client.reserveConnection(overridingStrategy, client.get("/")).doBeforeSuccess(__ -> {
            if (isInvalidThread()) {
                throw new AssertionError("Invalid thread found providing the connection. Thread: "
                        + currentThread());
            }
        }).toFuture().get().closeAsync().toFuture().get();
    }

    @Test
    public void requestRespectsDisable() throws Exception {
        ConcurrentLinkedQueue<AssertionError> errors = new ConcurrentLinkedQueue<>();
        StreamingHttpRequest req = client.get("/").transformPayloadBody(p -> p.doBeforeRequest(__ -> {
            if (isInvalidThread()) {
                errors.add(new AssertionError("Invalid thread called request-n. Thread: "
                        + currentThread()));
            }
        }));
        client.request(overridingStrategy, req)
                .doBeforeSuccess(__ -> {
                    if (isInvalidThread()) {
                        errors.add(new AssertionError("Invalid thread called response metadata. " +
                                "Thread: " + currentThread()));
                    }
                })
                .flatMapPublisher(StreamingHttpResponse::payloadBody)
                .doBeforeNext(__ -> {
                    if (isInvalidThread()) {
                        errors.add(new AssertionError("Invalid thread called response payload onNext. " +
                                "Thread: " + currentThread()));
                    }
                })
                .doBeforeComplete(() -> {
                    if (isInvalidThread()) {
                        errors.add(new AssertionError("Invalid thread called response payload onComplete. " +
                                "Thread: " + currentThread()));
                    }
                }).toFuture().get();

        assertThat("Unexpected errors: " + errors, errors, hasSize(0));
    }

    private boolean isInvalidThread() {
        return isInvalidThread.test(currentThread());
    }

    private static boolean isInClientEventLoop(Thread thread) {
        return thread.getName().startsWith(IO_EXECUTOR_THREAD_NAME_PREFIX);
    }
}
