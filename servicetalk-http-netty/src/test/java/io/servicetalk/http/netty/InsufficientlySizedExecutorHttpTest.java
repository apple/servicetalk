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
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
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

import static io.servicetalk.concurrent.api.Executors.from;
import static io.servicetalk.concurrent.api.Executors.newFixedSizeExecutor;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
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
    private final Executor executor;
    @Nullable
    private StreamingHttpClient client;
    @Nullable
    private ServerContext server;

    public InsufficientlySizedExecutorHttpTest(final int capacity, final Executor executor) {
        this.capacity = capacity;
        this.executor = executor;
    }

    @Parameterized.Parameters(name = "{index} - capacity: {0} ")
    public static Collection<Object[]> executors() {
        List<Object[]> executors = new ArrayList<>();
        executors.add(newParam(0));
        executors.add(newParam(1));
        return executors;
    }

    @Test
    public void insufficientClientCapacityStreaming() throws Exception {
        initClientAndServer(true);
        assert client != null;
        expectedException.expect(instanceOf(ExecutionException.class));
        expectedException.expectCause(anyOf(instanceOf(RejectedExecutionException.class)
                // If we do not have enough threads to offload onClose then we will close the connection immediately
                // upon creation which will cause LoadBalancer selector to reject a new connection.
                , instanceOf(ConnectionRejectedException.class));
        client.request(client.get("/")).toFuture().get();
    }

    @Test
    public void insufficientServerCapacityStreaming() throws Exception {
        initClientAndServer(false);
        assert client != null;
        if (capacity <= 1) {
            // If there are no threads, we can not start processing.
            // If there is a single thread, it is used by the connection to listen for close events.
            expectedException.expect(instanceOf(ExecutionException.class));
            expectedException.expectCause(anyOf(instanceOf(ClosedChannelException.class),
                    instanceOf(IOException.class)));
        }
        StreamingHttpResponse response = client.request(client.get("/")).toFuture().get();
        assertThat("Unexpected response code.", response.status().code(), is(SERVICE_UNAVAILABLE.code()));
    }

    private void initClientAndServer(boolean clientUnderProvisioned) throws Exception {
        InetSocketAddress addr;
        final HttpExecutionStrategy strategy = defaultStrategy(executor);
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
    public void tearDown() throws Exception {
        if (client != null) {
            client.closeAsync().toFuture().get();
        }
        if (server != null) {
            server.closeAsync().toFuture().get();
        }
        executor.closeAsync().toFuture().get();
    }

    private static Object[] newParam(final int capacity) {
        return new Object[]{capacity, getExecutorForCapacity(capacity)};
    }

    @Nonnull
    private static Executor getExecutorForCapacity(final int clientCapacity) {
        return clientCapacity == 0 ? from(task -> {
                throw new RejectedExecutionException();
        }) : newFixedSizeExecutor(clientCapacity);
    }
}
