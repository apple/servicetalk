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

import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.IoThreadFactory;
import io.servicetalk.transport.api.ServerContext;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.transport.netty.NettyIoExecutors.createIoExecutor;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @param <T> Type of the keys used in thread recorder map.
 */
final class InvokingThreadsRecorder<T> implements AutoCloseable {
    static final String IO_EXECUTOR_NAME_PREFIX = "io-executor";

    @Nullable
    private final HttpExecutionStrategy strategy;
    @Nullable
    private ServerContext context;
    @Nullable
    private StreamingHttpClient client;
    private final ConcurrentMap<T, Thread> invokingThreads = new ConcurrentHashMap<>();
    private final IoExecutor ioExecutor = createIoExecutor(IO_EXECUTOR_NAME_PREFIX);

    InvokingThreadsRecorder(@Nullable HttpExecutionStrategy strategy) {
        this.strategy = strategy;
    }

    static <T> InvokingThreadsRecorder<T> noStrategy() {
        return new InvokingThreadsRecorder<>(null);
    }

    static <T> InvokingThreadsRecorder<T> userStrategy(@Nullable HttpExecutionStrategy strategy) {
        return new InvokingThreadsRecorder<>(strategy);
    }

    void init(BiFunction<IoExecutor, HttpServerBuilder, Single<ServerContext>> serverStarter,
              BiConsumer<IoExecutor, SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress>> clientUpdater) {
        try {
            HttpServerBuilder serverBuilder = HttpServers.forAddress(localAddress(0));
            context = serverStarter.apply(ioExecutor, serverBuilder).toFuture().get();
        } catch (Exception e) {
            fail("Exception in initialization", e);
        }
        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> clientBuilder =
                HttpClients.forSingleAddress(serverHostAndPort(context));
        clientUpdater.accept(ioExecutor, clientBuilder);
        client = clientBuilder.buildStreaming();
    }

    @Override
    public void close() throws Exception {
        CompositeCloseable compositeCloseable = newCompositeCloseable();
        if (client != null) {
            compositeCloseable.append(client);
        }
        if (context != null) {
            compositeCloseable.append(context);
        }
        compositeCloseable.append(ioExecutor);

        compositeCloseable.close();
    }

    void assertNoOffload(final T offloadPoint) {
        assertThat("Expected IoThread at point: " + offloadPoint,
                IoThreadFactory.IoThread.isIoThread(invokingThread(offloadPoint)));
    }

    void assertOffload(final T offloadPoint) {
        assertThat("Expected non-IoThread at point: " + offloadPoint,
                !IoThreadFactory.IoThread.isIoThread(invokingThread(offloadPoint)));
    }

    Thread invokingThread(final T offloadPoint) {
        return invokingThreads.get(offloadPoint);
    }

    void verifyOffloadCount() {
        assertThat("Unexpected offload points recorded. " + invokingThreads, invokingThreads.size(), is(3));
    }

    StreamingHttpClient client() {
        assert client != null;
        return client;
    }

    @Nullable
    HttpExecutionStrategy executionStrategy() {
        return strategy;
    }

    void recordThread(final T offloadPoint) {
        invokingThreads.put(offloadPoint, Thread.currentThread());
    }
}
