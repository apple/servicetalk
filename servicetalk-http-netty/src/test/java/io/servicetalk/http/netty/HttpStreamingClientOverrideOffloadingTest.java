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
package io.servicetalk.http.netty;

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.transport.netty.NettyIoExecutors.createIoExecutor;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.lang.Thread.currentThread;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

class HttpStreamingClientOverrideOffloadingTest {
    private static final String IO_EXECUTOR_THREAD_NAME_PREFIX = "http-client-io-executor";

    private final IoExecutor ioExecutor = createIoExecutor(IO_EXECUTOR_THREAD_NAME_PREFIX);
    private final Executor executor = newCachedThreadExecutor();
    private Predicate<Thread> isInvalidThread;
    private HttpExecutionStrategy overridingStrategy;
    private ServerContext server;
    private StreamingHttpClient client;

    private void setUp(final Params params)
            throws Exception {
        this.isInvalidThread = params.isInvalidThread;
        this.overridingStrategy = params.overridingStrategy == null ?
                defaultStrategy() : params.overridingStrategy;
        server = HttpServers.forAddress(localAddress(0))
                .listenStreamingAndAwait((ctx, request, responseFactory) -> succeeded(responseFactory.ok()));
        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> clientBuilder =
                HttpClients.forSingleAddress(serverHostAndPort(server))
                .ioExecutor(ioExecutor)
                .executionStrategy(params.defaultStrategy == null ?
                        defaultStrategy() : params.defaultStrategy);
        if (null == params.defaultStrategy) {
            clientBuilder.executor(executor);
        }

        client = clientBuilder.buildStreaming();
    }

    enum Params {
        OVERRIDE_NO_OFFLOAD(th -> !isInClientEventLoop(th), noOffloadsStrategy(), null),
        DEFAULT_NO_OFFLOAD(HttpStreamingClientOverrideOffloadingTest::isInClientEventLoop, null,
                noOffloadsStrategy()),
        BOTH_OFFLOADS(HttpStreamingClientOverrideOffloadingTest::isInClientEventLoop, null, null);

        final Predicate<Thread> isInvalidThread;
        @Nullable
        final HttpExecutionStrategy overridingStrategy;
        @Nullable
        final HttpExecutionStrategy defaultStrategy;

        Params(final Predicate<Thread> isInvalidThread,
               @Nullable final HttpExecutionStrategy overridingStrategy,
               @Nullable final HttpExecutionStrategy defaultStrategy) {
            this.isInvalidThread = isInvalidThread;
            this.overridingStrategy = overridingStrategy;
            this.defaultStrategy = defaultStrategy;
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        newCompositeCloseable().appendAll(client, server, ioExecutor, executor).closeAsync().toFuture().get();
    }

    @ParameterizedTest
    @EnumSource(Params.class)
    void reserveRespectsDisable(final Params params) throws Exception {
        setUp(params);
        client.reserveConnection(this.overridingStrategy, client.get("/")).beforeOnSuccess(__ -> {
            if (isInvalidThread()) {
                throw new AssertionError("Invalid thread found providing the connection. Thread: "
                        + currentThread());
            }
        }).toFuture().get().closeAsync().toFuture().get();
    }

    @ParameterizedTest
    @EnumSource(Params.class)
    void requestRespectsDisable(final Params params) throws Exception {
        setUp(params);
        ConcurrentLinkedQueue<AssertionError> errors = new ConcurrentLinkedQueue<>();
        StreamingHttpRequest req = client.get("/").transformPayloadBody(p -> p.beforeRequest(__ -> {
            if (isInvalidThread()) {
                errors.add(new AssertionError("Invalid thread called request-n. Thread: "
                        + currentThread()));
            }
        }));
        client.request(this.overridingStrategy, req)
                .beforeOnSuccess(__ -> {
                    if (isInvalidThread()) {
                        errors.add(new AssertionError("Invalid thread called response metadata. " +
                                "Thread: " + currentThread()));
                    }
                })
                .flatMapPublisher(StreamingHttpResponse::payloadBody)
                .beforeOnNext(__ -> {
                    if (isInvalidThread()) {
                        errors.add(new AssertionError("Invalid thread called response payload onNext. " +
                                "Thread: " + currentThread()));
                    }
                })
                .beforeOnComplete(() -> {
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
