/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.BlockingStreamingHttpClient;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.ReservedBlockingHttpConnection;
import io.servicetalk.http.api.ReservedBlockingStreamingHttpConnection;
import io.servicetalk.http.api.ReservedHttpConnection;
import io.servicetalk.http.api.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpExecutionStrategies.customStrategyBuilder;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.netty.HttpClients.forSingleAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.hamcrest.MatcherAssert.assertThat;

class ExecutionStrategyInContextTest {

    @Nullable
    private ServerContext context;
    @Nullable
    private AutoCloseable clientAsCloseable;
    @Nullable
    private HttpExecutionStrategy expectedServerStrategy;
    @Nullable
    private HttpExecutionStrategy expectedClientStrategy;
    private final AtomicReference<HttpExecutionStrategy> serviceStrategyRef = new AtomicReference<>();

    @AfterEach
    void tearDown() throws Exception {
        if (clientAsCloseable != null) {
            clientAsCloseable.close();
        }
        if (context != null) {
            context.closeAsync().toFuture().get();
        }
    }

    @ParameterizedTest(name = "customStrategy={0}")
    @ValueSource(booleans = {false, true})
    void testStreaming(boolean customStrategy) throws Exception {
        StreamingHttpClient client = initClientAndServer(builder ->
                builder.listenStreaming((ctx, request, responseFactory) -> {
                    serviceStrategyRef.set(ctx.executionContext().executionStrategy());
                    return succeeded(responseFactory.ok());
                }), customStrategy).buildStreaming();
        clientAsCloseable = client;
        if (!customStrategy) {
            assert expectedClientStrategy == null;
            expectedClientStrategy = defaultStrategy();
            assert expectedServerStrategy == null;
            expectedServerStrategy = defaultStrategy();
        }
        HttpExecutionStrategy clientStrat = client.executionContext().executionStrategy();
        assertThat("Unexpected client strategy.", clientStrat, equalStrategies(expectedClientStrategy));
        client.request(client.get("/")).toFuture().get();
        assertThat("Unexpected service strategy", serviceStrategyRef.get(),
                equalStrategies(expectedServerStrategy));
        ReservedStreamingHttpConnection conn = client.reserveConnection(client.get("/")).toFuture().get();
        assertThat("Unexpected connection strategy (from execution context).",
                conn.executionContext().executionStrategy(), equalStrategies(expectedClientStrategy));
        assertThat("Unexpected connection strategy (from execution context).",
                conn.connectionContext().executionContext().executionStrategy(),
                equalStrategies(expectedClientStrategy));
    }

    @ParameterizedTest(name = "customStrategy={0}")
    @ValueSource(booleans = {false, true})
    void testAsync(boolean customStrategy) throws Exception {
        HttpClient client = initClientAndServer(builder ->
                builder.listen((ctx, request, responseFactory) -> {
                    serviceStrategyRef.set(ctx.executionContext().executionStrategy());
                    return succeeded(responseFactory.ok());
                }), customStrategy).build();
        clientAsCloseable = client;
        if (!customStrategy) {
            assert expectedClientStrategy == null;
            expectedClientStrategy = customStrategyBuilder().offloadReceiveData().build();
            assert expectedServerStrategy == null;
            expectedServerStrategy = customStrategyBuilder().offloadReceiveData().offloadSend().build();
        }
        HttpExecutionStrategy clientStrat = client.executionContext().executionStrategy();
        assertThat("Unexpected client strategy.", clientStrat, equalStrategies(expectedClientStrategy));
        client.request(client.get("/")).toFuture().get();
        assertThat("Unexpected service strategy", serviceStrategyRef.get(),
                equalStrategies(expectedServerStrategy));
        ReservedHttpConnection conn = client.reserveConnection(client.get("/")).toFuture().get();
        assertThat("Unexpected connection strategy (from execution context).",
                conn.executionContext().executionStrategy(), equalStrategies(expectedClientStrategy));
        assertThat("Unexpected connection strategy (from execution context).",
                conn.connectionContext().executionContext().executionStrategy(),
                equalStrategies(expectedClientStrategy));
    }

    @ParameterizedTest(name = "customStrategy={0}")
    @ValueSource(booleans = {false, true})
    void testBlocking(boolean customStrategy) throws Exception {
        BlockingHttpClient client = initClientAndServer(builder ->
                builder.listenBlocking((ctx, request, responseFactory) -> {
                    serviceStrategyRef.set(ctx.executionContext().executionStrategy());
                    return responseFactory.ok();
                }), customStrategy).buildBlocking();
        clientAsCloseable = client;
        if (!customStrategy) {
            assert expectedClientStrategy == null;
            expectedClientStrategy = customStrategyBuilder().offloadNone().build();
            assert expectedServerStrategy == null;
            expectedServerStrategy = customStrategyBuilder().offloadReceiveData().build();
        }
        HttpExecutionStrategy clientStrat = client.executionContext().executionStrategy();
        assertThat("Unexpected client strategy.", clientStrat, equalStrategies(expectedClientStrategy));
        client.request(client.get("/"));
        assertThat("Unexpected service strategy", serviceStrategyRef.get(),
                equalStrategies(expectedServerStrategy));
        ReservedBlockingHttpConnection conn = client.reserveConnection(client.get("/"));
        assertThat("Unexpected connection strategy (from execution context).",
                conn.executionContext().executionStrategy(), equalStrategies(expectedClientStrategy));
        assertThat("Unexpected connection strategy (from execution context).",
                conn.connectionContext().executionContext().executionStrategy(),
                equalStrategies(expectedClientStrategy));
    }

    @ParameterizedTest(name = "customStrategy={0}")
    @ValueSource(booleans = {false, true})
    void testBlockingStreaming(boolean customStrategy) throws Exception {
        BlockingStreamingHttpClient client = initClientAndServer(builder -> {
            if (customStrategy) {
                // Ensure we don't deadlock by not offloading receive meta
                expectedServerStrategy = customStrategyBuilder().offloadReceiveMetadata().build();
                builder.executionStrategy(expectedServerStrategy);
            }
            return builder.listenBlockingStreaming((ctx, request, response) -> {
                serviceStrategyRef.set(ctx.executionContext().executionStrategy());
                response.sendMetaData().close();
            });
        }, customStrategy).buildBlockingStreaming();
        clientAsCloseable = client;
        if (!customStrategy) {
            assert expectedClientStrategy == null;
            expectedClientStrategy = customStrategyBuilder().offloadSend().build();
            assert expectedServerStrategy == null;
            expectedServerStrategy = customStrategyBuilder().offloadReceiveMetadata().build();
        }
        HttpExecutionStrategy clientStrat = client.executionContext().executionStrategy();
        assertThat("Unexpected client strategy.", clientStrat, equalStrategies(expectedClientStrategy));
        client.request(client.get("/"));
        assertThat("Unexpected service strategy", serviceStrategyRef.get(),
                equalStrategies(expectedServerStrategy));
        ReservedBlockingStreamingHttpConnection conn = client.reserveConnection(client.get("/"));
        assertThat("Unexpected connection strategy (from execution context).",
                conn.executionContext().executionStrategy(), equalStrategies(expectedClientStrategy));
        assertThat("Unexpected connection strategy (from execution context).",
                conn.connectionContext().executionContext().executionStrategy(),
                equalStrategies(expectedClientStrategy));
    }

    private SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> initClientAndServer(
        Function<HttpServerBuilder, Single<ServerContext>> serverStarter, boolean customStrategy)
            throws Exception {
        HttpServerBuilder serverBuilder = HttpServers.forAddress(localAddress(0));
        if (customStrategy) {
            expectedServerStrategy = customStrategyBuilder().offloadAll().build();
            serverBuilder.executionStrategy(expectedServerStrategy);
        }
        context = serverStarter.apply(serverBuilder).toFuture().get();
        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> clientBuilder =
                forSingleAddress(serverHostAndPort(context));
        if (customStrategy) {
            expectedClientStrategy = customStrategyBuilder().offloadAll().build();
            clientBuilder.executionStrategy(expectedClientStrategy);
        }
        return clientBuilder;
    }

    static Matcher<HttpExecutionStrategy> equalStrategies(@Nullable HttpExecutionStrategy expected) {
        return new TypeSafeMatcher<HttpExecutionStrategy>() {

            @Override
            public void describeMismatchSafely(@Nullable HttpExecutionStrategy item, Description mismatchDescription) {
                mismatchDescription
                        .appendText("was strategy ")
                        .appendValue(item);
            }

            @Override
            protected boolean matchesSafely(final @Nullable HttpExecutionStrategy item) {
                return Objects.equals(expected, item);
            }

            @Override
            public void describeTo(final Description description) {
                description.appendText("a strategy of ")
                        .appendValue(expected);
            }
        };
    }
}
