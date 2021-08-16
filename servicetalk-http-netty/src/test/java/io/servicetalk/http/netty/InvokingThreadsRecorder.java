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
import io.servicetalk.concurrent.api.DefaultThreadFactory;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.ClientInvoker;
import io.servicetalk.http.api.DelegatingHttpExecutionStrategy;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.IoExecutor;
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
import static java.lang.Thread.NORM_PRIORITY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

/**
 * @param <T> Type of the keys used in thread recorder map.
 */
final class InvokingThreadsRecorder<T> {
    static final String IO_EXECUTOR_NAME_PREFIX = "io-executor";

    @Nullable
    private final HttpExecutionStrategy strategy;
    @Nullable
    private ServerContext context;
    @Nullable
    private StreamingHttpClient client;
    @Nullable
    private ConcurrentMap<T, Thread> invokingThreads;
    @Nullable
    private IoExecutor ioExecutor;

    InvokingThreadsRecorder(@Nullable HttpExecutionStrategy strategy) {
        this.strategy = strategy;
    }

    static <T> InvokingThreadsRecorder<T> noStrategy() {
        return new InvokingThreadsRecorder<>(null);
    }

    static <T> InvokingThreadsRecorder<T> userStrategyNoVerify(HttpExecutionStrategy strategy) {
        return new InvokingThreadsRecorder<>(strategy);
    }

    static <T> InvokingThreadsRecorder<T> userStrategy(HttpExecutionStrategy strategy) {
        return new InvokingThreadsRecorder<>(new InstrumentedStrategy(strategy));
    }

    void init(BiFunction<IoExecutor, HttpServerBuilder, Single<ServerContext>> serverStarter,
              BiConsumer<IoExecutor, SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress>> clientUpdater) {
        invokingThreads = new ConcurrentHashMap<>();
        ioExecutor = createIoExecutor(new DefaultThreadFactory(IO_EXECUTOR_NAME_PREFIX, true, NORM_PRIORITY));
        try {
            HttpServerBuilder serverBuilder = HttpServers.forAddress(localAddress(0));
            context = serverStarter.apply(ioExecutor, serverBuilder).toFuture().get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> clientBuilder =
                HttpClients.forSingleAddress(serverHostAndPort(context));
        clientUpdater.accept(ioExecutor, clientBuilder);
        client = clientBuilder.buildStreaming();
    }

    void dispose() throws Exception {
        CompositeCloseable compositeCloseable = newCompositeCloseable();
        if (client != null) {
            compositeCloseable.append(client);
        }
        if (context != null) {
            compositeCloseable.append(context);
        }
        if (ioExecutor != null) {
            compositeCloseable.append(ioExecutor);
        }
        compositeCloseable.close();
    }

    void assertNoOffload(final T offloadPoint) {
        assert invokingThreads != null;
        assertThat("Unexpected thread for point: " + offloadPoint, invokingThreads.get(offloadPoint).getName(),
                startsWith(IO_EXECUTOR_NAME_PREFIX));
    }

    void assertOffload(final T offloadPoint) {
        assert invokingThreads != null;
        assertThat("Unexpected thread for point: " + offloadPoint, invokingThreads.get(offloadPoint).getName(),
                not(startsWith(IO_EXECUTOR_NAME_PREFIX)));
    }

    void assertOffload(final T offloadPoint, final String executorNamePrefix) {
        assert invokingThreads != null;
        assertThat("Unexpected thread for point: " + offloadPoint, invokingThreads.get(offloadPoint).getName(),
                both(not(startsWith(IO_EXECUTOR_NAME_PREFIX))).and(startsWith(executorNamePrefix)));
    }

    Thread invokingThread(final T offloadPoint) {
        assert invokingThreads != null;
        return invokingThreads.get(offloadPoint);
    }

    void verifyOffloadCount() {
        assert invokingThreads != null;
        assertThat("Unexpected offload points recorded. " + invokingThreads, invokingThreads.size(), is(3));
    }

    void assertStrategyUsedForClient() {
        assertThat("No user specified strategy found.", strategy, is(notNullValue()));
        assertThat("Unknown user specified strategy.", strategy, instanceOf(InstrumentedStrategy.class));
        InstrumentedStrategy instrumentedStrategy = (InstrumentedStrategy) strategy;
        assertThat("User specified strategy not used.", instrumentedStrategy.isUsedForClientOffloading());
    }

    void assertStrategyUsedForServer() {
        assertThat("No user specified strategy found.", strategy, is(notNullValue()));
        assertThat("Unknown user specified strategy.", strategy, instanceOf(InstrumentedStrategy.class));
        InstrumentedStrategy instrumentedStrategy = (InstrumentedStrategy) strategy;
        assertThat("User specified strategy not used.", instrumentedStrategy.isUsedForServerOffloading());
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
        assert invokingThreads != null;
        invokingThreads.put(offloadPoint, Thread.currentThread());
    }

    private static final class InstrumentedStrategy extends DelegatingHttpExecutionStrategy {

        private volatile boolean usedForClientOffloading;
        private volatile boolean usedForServerOffloading;

        InstrumentedStrategy(HttpExecutionStrategy delegate) {
            super(delegate);
        }

        @Override
        public <FS> Single<StreamingHttpResponse> invokeClient(
                final Executor fallback, final Publisher<Object> flattenedRequest, final FS flushStrategy,
                final ClientInvoker<FS> client) {
            usedForClientOffloading = true;
            return super.invokeClient(fallback, flattenedRequest, flushStrategy, client);
        }

        @Override
        public StreamingHttpService offloadService(final Executor fallback, final StreamingHttpService handler) {
            usedForServerOffloading = true;
            return super.offloadService(fallback, handler);
        }

        boolean isUsedForClientOffloading() {
            return usedForClientOffloading;
        }

        boolean isUsedForServerOffloading() {
            return usedForServerOffloading;
        }

        @Override
        public HttpExecutionStrategy merge(final HttpExecutionStrategy other) {
            return this;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            if (!super.equals(o)) {
                return false;
            }

            final InstrumentedStrategy that = (InstrumentedStrategy) o;

            if (usedForClientOffloading != that.usedForClientOffloading) {
                return false;
            }
            return usedForServerOffloading == that.usedForServerOffloading;
        }

        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + (usedForClientOffloading ? 1 : 0);
            result = 31 * result + (usedForServerOffloading ? 1 : 0);
            return result;
        }
    }
}
