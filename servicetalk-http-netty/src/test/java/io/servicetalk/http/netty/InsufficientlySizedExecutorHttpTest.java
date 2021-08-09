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

import io.servicetalk.client.api.ConnectionRejectedException;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Executors.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpExecutionStrategies.customStrategyBuilder;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpResponseStatus.SERVICE_UNAVAILABLE;
import static io.servicetalk.http.netty.HttpClients.forSingleAddress;
import static io.servicetalk.http.netty.HttpServers.forAddress;
import static io.servicetalk.transport.api.ConnectionAcceptorFactory.identity;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InsufficientlySizedExecutorHttpTest {
    @Nullable
    private Executor executor;
    @Nullable
    private StreamingHttpClient client;
    @Nullable
    private ServerContext server;

    @ParameterizedTest(name = "{displayName} {index} - capacity: {0}")
    @ValueSource(ints = {0, 1})
    void insufficientClientCapacityStreaming(final int capacity) throws Exception {
        initWhenClientUnderProvisioned(capacity);
        assertNotNull(client);

        if (capacity == 0) {
            ExecutionException e = assertThrows(ExecutionException.class,
                    () -> client.request(client.get("/")).toFuture().get());
            assertThat(e.getCause(), anyOf(instanceOf(RejectedExecutionException.class),
                    // If we do not have enough threads to offload onClose then we will close the connection immediately
                    // upon creation which will cause LoadBalancer selector to reject a new connection.
                    instanceOf(ConnectionRejectedException.class)));
        } else {
            StreamingHttpResponse response = client.request(client.get("/")).toFuture().get();
            // As server isn't under provisioned, if we get a response, it should be OK.
            assertThat("Unexpected response code.", response.status(), is(OK));
        }
    }

    @ParameterizedTest(name = "{displayName} {index} - capacity: {0}")
    @ValueSource(ints = {0, 1})
    void insufficientServerCapacityStreaming(final int capacity) throws Exception {
        initWhenServerUnderProvisioned(capacity, false);
        insufficientServerCapacityStreaming0(capacity);
    }

    @Disabled("https://github.com/apple/servicetalk/issues/336")
    @ParameterizedTest(name = "{displayName} {index} - capacity: {0}")
    @ValueSource(ints = {0, 1})
    void insufficientServerCapacityStreamingWithConnectionAcceptor(final int capacity)
            throws Exception {
        initWhenServerUnderProvisioned(capacity, true);
        insufficientServerCapacityStreaming0(capacity);
    }

    private void insufficientServerCapacityStreaming0(final int capacity) throws Exception {
        assertNotNull(client);
        final HttpResponseStatus expectedResponseStatus = capacity > 0 ? OK : SERVICE_UNAVAILABLE;
        StreamingHttpResponse response = client.request(client.get("/")).toFuture().get();
        assertThat("Unexpected response code.", response.status(), is(expectedResponseStatus));
    }

    private void initWhenClientUnderProvisioned(final int capacity) throws Exception {
        executor = getExecutorForCapacity(capacity);
        server = forAddress(localAddress(0))
                .listenStreamingAndAwait((ctx, request, responseFactory) -> succeeded(responseFactory.ok()));
        client = forSingleAddress(serverHostAndPort(server))
                .executionStrategy(newStrategy())
                .buildStreaming();
    }

    private void initWhenServerUnderProvisioned(final int capacity,
                                                boolean addConnectionAcceptor)
        throws Exception {
        executor = getExecutorForCapacity(capacity);
        final HttpExecutionStrategy strategy = newStrategy();
        HttpServerBuilder serverBuilder = forAddress(localAddress(0));
        if (addConnectionAcceptor) {
            serverBuilder.appendConnectionAcceptorFilter(identity());
        }
        server = serverBuilder.executionStrategy(strategy)
                .listenStreamingAndAwait((ctx, request, respFactory) -> succeeded(respFactory.ok()));
        client = forSingleAddress(serverHostAndPort(server)).buildStreaming();
    }

    private HttpExecutionStrategy newStrategy() {
        assertNotNull(executor);
        final HttpExecutionStrategies.Builder strategyBuilder = customStrategyBuilder().offloadAll().executor(executor);
        return strategyBuilder.build();
    }

    @AfterEach
    void tearDown() throws Exception {
        CompositeCloseable closeable = newCompositeCloseable();
        if (client != null) {
            closeable.append(client);
        }
        if (server != null) {
            closeable.append(server);
        }
        if (null != executor) {
            closeable.append(executor);
        }
        closeable.close();
    }

    @Nonnull
    private static Executor getExecutorForCapacity(final int capacity) {
        return capacity == 0 ?
                from(task -> {
                    throw new RejectedExecutionException();
                }) :
                from(java.util.concurrent.Executors.newFixedThreadPool(capacity));
    }
}
