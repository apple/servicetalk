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
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServerContext;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

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
import static io.servicetalk.transport.netty.NettyIoExecutors.createIoExecutor;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.lang.Thread.NORM_PRIORITY;
import static java.lang.Thread.currentThread;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

@RunWith(Parameterized.class)
public class HttpClientOverrideOffloadingTest {
    private static final String IO_EXECUTOR_THREAD_NAME_PREFIX = "http-client-io-executor-";

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private final IoExecutor ioExecutor;
    private final Executor executor;
    private final Predicate<Thread> isInvalidThread;
    private final HttpExecutionStrategy overridingStrategy;
    private final ServerContext server;
    private final HttpClient client;

    public HttpClientOverrideOffloadingTest(@SuppressWarnings("unused") final String description,
                                            final Predicate<Thread> isInvalidThread,
                                            @Nullable final HttpExecutionStrategy overridingStrategy,
                                            @Nullable final HttpExecutionStrategy defaultStrategy) throws Exception {
        ioExecutor = createIoExecutor(new DefaultThreadFactory(IO_EXECUTOR_THREAD_NAME_PREFIX, true,
                NORM_PRIORITY));
        executor = newCachedThreadExecutor();
        this.isInvalidThread = isInvalidThread;
        this.overridingStrategy = overridingStrategy == null ? defaultStrategy(executor) : overridingStrategy;
        server = HttpServers.forAddress(localAddress())
                .listenStreamingAndAwait((ctx, request, responseFactory) -> success(responseFactory.ok()));
        client = HttpClients.forSingleAddress(serverHostAndPort(server))
                .ioExecutor(ioExecutor)
                .executionStrategy(defaultStrategy == null ? defaultStrategy(executor) : defaultStrategy)
                .build();
    }

    @Parameterized.Parameters(name = "{index} - {0}")
    public static Collection<Object[]> params() {
        List<Object[]> params = new ArrayList<>();
        params.add(newParam("Override no offload", th -> !isInClientEventLoop(th), noOffloadsStrategy(), null));
        params.add(newParam("Default no offload", HttpClientOverrideOffloadingTest::isInClientEventLoop,
                null, noOffloadsStrategy()));
        params.add(newParam("Both offloads", HttpClientOverrideOffloadingTest::isInClientEventLoop, null, null));
        return params;
    }

    private static Object[] newParam(String description, Predicate<Thread> isInvalidThread,
                                     @Nullable HttpExecutionStrategy overridingStrategy,
                                     @Nullable HttpExecutionStrategy defaultStrategy) {
        return new Object[]{description, isInvalidThread, overridingStrategy, defaultStrategy};
    }

    @After
    public void tearDown() throws Exception {
        newCompositeCloseable().appendAll(client, server, ioExecutor, executor).closeAsync().toFuture().get();
    }

    @Test
    public void reserveRespectsDisable() throws Exception {
        ConcurrentLinkedQueue<AssertionError> errors = new ConcurrentLinkedQueue<>();
        client.reserveConnection(overridingStrategy, client.get("/")).doBeforeSuccess(__ -> {
            if (isInvalidThread()) {
                errors.add(new AssertionError("Invalid thread found providing the connection. Thread: "
                        + currentThread()));
            }
        }).toFuture().get().closeAsync().toFuture().get();
        assertThat("Unexpected errors: " + errors, errors, hasSize(0));
    }

    @Test
    public void requestRespectsDisable() throws Exception {
        ConcurrentLinkedQueue<AssertionError> errors = new ConcurrentLinkedQueue<>();
        client.request(overridingStrategy, client.get("/"))
                .doBeforeSuccess(__ -> {
                    if (isInvalidThread()) {
                        errors.add(new AssertionError("Invalid thread called response. " +
                                "Thread: " + currentThread()));
                    }
                })
                .toFuture().get();

        assertThat("Unexpected errors: " + errors, errors, hasSize(0));
    }

    private boolean isInvalidThread() {
        return isInvalidThread.test(currentThread());
    }

    private static boolean isInClientEventLoop(Thread thread) {
        return thread.getName().startsWith(IO_EXECUTOR_THREAD_NAME_PREFIX);
    }
}
