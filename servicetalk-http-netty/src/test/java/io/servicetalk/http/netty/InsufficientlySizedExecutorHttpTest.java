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

import io.servicetalk.client.api.ConnectionRejectedException;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.transport.api.ServerContext;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Executors.from;
import static io.servicetalk.concurrent.api.Executors.newFixedSizeExecutor;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.http.api.HttpExecutionStrategies.customStrategyBuilder;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.api.HttpResponseStatuses.SERVICE_UNAVAILABLE;
import static io.servicetalk.http.netty.HttpClients.forSingleAddress;
import static io.servicetalk.http.netty.HttpServers.forPort;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.rules.ExpectedException.none;

@RunWith(Parameterized.class)
public class InsufficientlySizedExecutorHttpTest {

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExpectedException expectedException = none();

    private final int capacity;
    private final boolean threadBased;
    private Executor executor;
    @Nullable
    private StreamingHttpClient client;
    @Nullable
    private ServerContext server;

    public InsufficientlySizedExecutorHttpTest(final int capacity, final boolean threadBased) {
        this.capacity = capacity;
        this.threadBased = threadBased;
    }

    @Parameterized.Parameters(name = "{index} - capacity: {0} thread based: {1}")
    public static Collection<Object[]> executors() {
        List<Object[]> params = new ArrayList<>();
        params.add(newParam(0, true));
        params.add(newParam(0, false));
        params.add(newParam(1, true));
        params.add(newParam(1, false));
        return params;
    }

    @Test
    public void insufficientClientCapacityStreaming() throws Exception {
        initClientAndServer(true);
        assert client != null;
        if (threadBased && capacity <= 1 || !threadBased && capacity == 0) {
            expectedException.expect(instanceOf(ExecutionException.class));
            expectedException.expectCause(anyOf(instanceOf(RejectedExecutionException.class),
                    // If we do not have enough threads to offload onClose then we will close the connection immediately
                    // upon creation which will cause LoadBalancer selector to reject a new connection.
                    instanceOf(ConnectionRejectedException.class)));
        }
        StreamingHttpResponse response = client.request(client.get("/")).toFuture().get();
        // As server isn't under provisioned, if we get a response, it should be OK.
        assertThat("Unexpected response code.", response.status().code(), is(OK.code()));
    }

    @Test
    public void insufficientServerCapacityStreaming() throws Exception {
        initClientAndServer(false);
        assert client != null;
        // For task based, we use a queue for the executor
        int expectedResponseCode = !threadBased && capacity > 0 ? OK.code() : SERVICE_UNAVAILABLE.code();
        if (capacity == 0) {
            // If there are no threads, we can not start processing.
            // If there is a single thread, it is used by the connection to listen for close events.
            expectedException.expect(instanceOf(ExecutionException.class));
            expectedException.expectCause(anyOf(instanceOf(ClosedChannelException.class),
                    instanceOf(IOException.class)));
        }
        StreamingHttpResponse response = client.request(client.get("/")).toFuture().get();
        assertThat("Unexpected response code.", response.status().code(), is(expectedResponseCode));
    }

    private void initClientAndServer(boolean clientUnderProvisioned) throws Exception {
        InetSocketAddress addr;
        executor = getExecutorForCapacity(capacity, !threadBased);
        final HttpExecutionStrategies.Builder strategyBuilder = customStrategyBuilder().offloadAll().executor(executor);
        final HttpExecutionStrategy strategy = threadBased ? strategyBuilder.offloadWithThreadAffinity().build() :
                strategyBuilder.build();
        if (clientUnderProvisioned) {
            server = forPort(0).listenStreamingAndAwait(
                    (ctx, request, responseFactory) -> success(responseFactory.ok()));
            addr = (InetSocketAddress) server.listenAddress();
            client = forSingleAddress(addr.getHostName(), addr.getPort()).executionStrategy(strategy)
                    .buildStreaming();
        } else {
            server = forPort(0).listenStreamingAndAwait(new StreamingHttpService() {
                @Override
                public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                            final StreamingHttpRequest request,
                                                            final StreamingHttpResponseFactory responseFactory) {
                    return success(responseFactory.ok());
                }

                @Override
                public HttpExecutionStrategy executionStrategy() {
                    return strategy;
                }
            });
            addr = (InetSocketAddress) server.listenAddress();
            client = forSingleAddress(addr.getHostName(), addr.getPort()).buildStreaming();
        }
    }

    @After
    public void tearDown() {
        CompositeCloseable closeable = newCompositeCloseable();
        if (client != null) {
            closeable.append(client);
        }
        if (server != null) {
            closeable.append(server);
        }
        closeable.append(executor);
    }

    private static Object[] newParam(final int capacity, final boolean threadBased) {
        return new Object[]{capacity, threadBased};
    }

    @Nonnull
    private static Executor getExecutorForCapacity(final int capacity, final boolean useQueue) {
        return capacity == 0 ? from(task -> {
            throw new RejectedExecutionException();
        }) : useQueue ? from(java.util.concurrent.Executors.newFixedThreadPool(capacity)) :
                newFixedSizeExecutor(capacity);
    }
}
