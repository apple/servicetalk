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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.BlockingStreamingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.FilterableStreamingHttpLoadBalancedConnection;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpLoadBalancerFactory;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.loadbalancer.RoundRobinLoadBalancerFactory;
import io.servicetalk.transport.api.ExecutionStrategy;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.IoThreadFactory;
import io.servicetalk.transport.api.ServerContext;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpExecutionStrategies.anyStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadAll;
import static io.servicetalk.http.netty.ClientEffectiveStrategyTest.ClientOffloadPoint.RequestPayloadSubscription;
import static io.servicetalk.http.netty.ClientEffectiveStrategyTest.ClientOffloadPoint.ResponseData;
import static io.servicetalk.http.netty.ClientEffectiveStrategyTest.ClientOffloadPoint.ResponseMeta;
import static io.servicetalk.test.resources.TestUtils.assertNoAsyncErrors;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@Execution(ExecutionMode.CONCURRENT)
class ClientEffectiveStrategyTest {
    private static final String GREETING = "Hello";

    private static final HttpExecutionStrategy[] BUILDER_STRATEGIES = {
            null, // unspecified
            noOffloadsStrategy(),
            anyStrategy(),
            defaultStrategy(),
            HttpExecutionStrategies.customStrategyBuilder().offloadSend().build(),
            offloadAll(),
    };

    private static final HttpExecutionStrategy[] FILTER_STRATEGIES = {
            null, // absent
            noOffloadsStrategy(), // treated as "anyStrategy"
            anyStrategy(),
            defaultStrategy(), // treated as "anyStrategy"
            HttpExecutionStrategies.customStrategyBuilder().offloadSend().build(),
            offloadAll(),
    };

    private static final HttpExecutionStrategy[] LB_STRATEGIES = {
            null, // absent
            noOffloadsStrategy(), // treated as "anyStrategy"
            anyStrategy(),
            defaultStrategy(), // treated as "anyStrategy"
            HttpExecutionStrategies.customStrategyBuilder().offloadSend().build(),
            offloadAll(),
    };

    private static final HttpExecutionStrategy[] CF_STRATEGIES = {
            null, // absent
            noOffloadsStrategy(), // treated as "anyStrategy"
            anyStrategy(),
            defaultStrategy(), // treated as "anyStrategy"
            HttpExecutionStrategies.customStrategyBuilder().offloadSend().build(),
            offloadAll(),
    };

    static final ServerContext context;

    static {
        try {
            HttpServerBuilder serverBuilder = HttpServers.forAddress(localAddress(0));
            context = serverBuilder.listenBlocking((ctx, request, responseFactory) ->
                    responseFactory.ok().payloadBody(ctx.executionContext().bufferAllocator()
                            .fromAscii(GREETING))).toFuture().get();
        } catch (Throwable all) {
            throw new AssertionError("Failed to initialize server", all);
        }
    }

    @AfterAll
    static void shutdown() throws Exception {
        context.closeGracefully();
    }

    @SuppressWarnings("unused")
    static Stream<Arguments> casesSupplier() {
        List<Arguments> arguments = new ArrayList<>();
        for (ClientType clientType : ClientType.values()) {
            for (HttpExecutionStrategy builderStrategy : BUILDER_STRATEGIES) {
                for (HttpExecutionStrategy filterStrategy : FILTER_STRATEGIES) {
                    for (HttpExecutionStrategy lbStrategy : LB_STRATEGIES) {
                        for (HttpExecutionStrategy cfStrategy : CF_STRATEGIES) {
                            arguments.add(Arguments.of(clientType,
                                    builderStrategy, filterStrategy, lbStrategy, cfStrategy));
                        }
                    }
                }
            }
        }
        return arguments.stream();
    }

    @ParameterizedTest(name = "API={0} builder={1} filter={2} LB={3} CF={4}")
    @MethodSource("casesSupplier")
    void clientStrategy(ClientType clientType,
                  @Nullable final HttpExecutionStrategy builderStrategy,
                  @Nullable final HttpExecutionStrategy filterStrategy,
                  @Nullable final HttpExecutionStrategy lbStrategy,
                  @Nullable final HttpExecutionStrategy cfStrategy) throws Exception {
        HttpExecutionStrategy effectiveStrategy = null == builderStrategy ?
                defaultStrategy() : builderStrategy;
        effectiveStrategy = null == filterStrategy || anyStrategy() == filterStrategy ||
                defaultStrategy() == filterStrategy || noOffloadsStrategy() == filterStrategy ?
                effectiveStrategy : effectiveStrategy.merge(filterStrategy);
        effectiveStrategy = null == lbStrategy || anyStrategy() == lbStrategy ||
                defaultStrategy() == lbStrategy || noOffloadsStrategy() == lbStrategy ?
                effectiveStrategy : effectiveStrategy.merge(lbStrategy);
        effectiveStrategy = null == cfStrategy || anyStrategy() == cfStrategy ||
                defaultStrategy() == cfStrategy || noOffloadsStrategy() == cfStrategy ?
                effectiveStrategy : effectiveStrategy.merge(cfStrategy);

        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> clientBuilder =
                HttpClients.forSingleAddress(serverHostAndPort(context));

        if (builderStrategy != null) {
            clientBuilder.executionStrategy(builderStrategy);
        }

        ClientInvokingThreadRecorder invokingThreadsRecorder =
                new ClientInvokingThreadRecorder(clientType, effectiveStrategy);

        clientBuilder.appendClientFilter(invokingThreadsRecorder);
        if (null != filterStrategy) {
            clientBuilder.appendClientFilter(new StreamingHttpClientFilterFactory() {
                @Override
                public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
                    return new StreamingHttpClientFilter(client) {
                    };
                }

                @Override
                public HttpExecutionStrategy requiredOffloads() {
                    return filterStrategy;
                }
            });
        }
        if (null != lbStrategy) {
            HttpLoadBalancerFactory<InetSocketAddress> lfFactory =
                    DefaultHttpLoadBalancerFactory.Builder.from(new LoadBalancerFactoryImpl() {
                        @Override
                        public ExecutionStrategy requiredOffloads() {
                            return lbStrategy;
                        }
                    }).build();
            clientBuilder.loadBalancerFactory(lfFactory);
        }
        if (null != cfStrategy) {
            clientBuilder.appendConnectionFilter(new StreamingHttpConnectionFilterFactory() {
                @Override
                public StreamingHttpConnectionFilter create(
                        final FilterableStreamingHttpConnection connection) {
                    return new StreamingHttpConnectionFilter(connection) {
                    };
                }

                @Override
                public HttpExecutionStrategy requiredOffloads() {
                    return cfStrategy;
                }
            });
        }

        // Exercise the client
        try (StreamingHttpClient client = clientBuilder.buildStreaming()) {
            String responseBody = getResponse(clientType, client);
            assertThat(responseBody, is(GREETING));
            invokingThreadsRecorder.verifyOffloads();
        }
    }

    private String getResponse(ClientType clientType, StreamingHttpClient client) throws Exception {
        Iterable<Buffer> buffers;
        switch (clientType) {
            case Blocking:
                BlockingHttpClient blockingClient = client.asBlockingClient();
                buffers = Collections.singleton(blockingClient.request(blockingClient.get("/")).payloadBody());
                break;
            case BlockingStreaming:
                BlockingStreamingHttpClient blockingStreamingClient = client.asBlockingStreamingClient();
                buffers = blockingStreamingClient.request(blockingStreamingClient.get("/")).payloadBody();
                break;
            case AsyncStreaming:
                buffers = client.request(client.get("/")).flatMapPublisher(StreamingHttpResponse::payloadBody)
                        .toFuture().get();
                break;
            case Async:
                HttpClient httpClient = client.asClient();
                buffers = Collections.singleton(
                        httpClient.request(httpClient.get("/")).toFuture().get().payloadBody());
                break;
            default:
                fail("Unexpected client type " + clientType);
                /* NOTREACHED */
                return "failed";
        }
        return buffersToString(buffers, StandardCharsets.US_ASCII);
    }

    private static String buffersToString(Iterable<? extends Buffer> buffers, Charset charset) {
        CharsetDecoder decoder = charset.newDecoder();
        Iterator<? extends Buffer> eachBuffer = buffers.iterator();
        StringBuilder stringBuilder = new StringBuilder();
        CharBuffer charBuffer = CharBuffer.allocate(256);
            while (eachBuffer.hasNext()) {
                ByteBuffer byteBuffer = eachBuffer.next().toNioBuffer();
                CoderResult coderResult;
                do {
                    coderResult = decoder.decode(byteBuffer, charBuffer, !eachBuffer.hasNext());
                    if (coderResult.isError()) {
                        try {
                            coderResult.throwException();
                        } catch (CharacterCodingException conversionError) {
                            throw new IllegalArgumentException("Unconvertable input", conversionError);
                        }
                    }
                    charBuffer.flip();
                    stringBuilder.append(charBuffer);
                    charBuffer.clear();
                } while (CoderResult.OVERFLOW == coderResult);
            }
        return stringBuilder.toString();
    }

    private static final class ClientInvokingThreadRecorder implements StreamingHttpClientFilterFactory {

        private final EnumSet<ClientOffloadPoint> offloadPoints;
        private final ConcurrentMap<ClientOffloadPoint, Throwable> invokingThreads = new ConcurrentHashMap<>();
        private final Queue<Throwable> errors = new LinkedBlockingQueue<>();

        ClientInvokingThreadRecorder(ClientType clientType, HttpExecutionStrategy effectiveStrategy) {
            if (defaultStrategy() == effectiveStrategy) {
                offloadPoints = Offloads.DEFAULT.expected(clientType);
            } else {
                offloadPoints = EnumSet.noneOf(ClientOffloadPoint.class);
                // adjust expected offloads for specific execution strategy
                if (effectiveStrategy.isSendOffloaded()) {
                    offloadPoints.add(RequestPayloadSubscription);
                }
                if (effectiveStrategy.isMetadataReceiveOffloaded()) {
                    offloadPoints.add(ResponseMeta);
                }
                if (effectiveStrategy.isDataReceiveOffloaded()) {
                    offloadPoints.add(ResponseData);
                }
            }

            System.out.printf("API=%s effectiveStrategy=%s offloads=%s%n",
                    clientType, effectiveStrategy, offloadPoints);
        }

        @Override
        public HttpExecutionStrategy requiredOffloads() {
            // No influence since we do not block.
            return HttpExecutionStrategies.anyStrategy();
        }

        @Override
        public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
            return new StreamingHttpClientFilter(client) {

                @Override
                protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                final StreamingHttpRequest request) {
                    return delegate.request(request.transformPayloadBody(payload ->
                                    payload.beforeRequest(__ -> recordThread(RequestPayloadSubscription))))
                            .beforeOnSuccess(__ -> recordThread(ResponseMeta))
                            .map(resp -> resp.transformPayloadBody(payload ->
                                    payload.beforeOnNext(__ -> recordThread(ResponseData))));
                }
            };
        }

        void recordThread(final ClientOffloadPoint offloadPoint) {
            invokingThreads.compute(offloadPoint, (ClientOffloadPoint offload, Throwable recorded) -> {
                Thread current = Thread.currentThread();
                boolean ioThread = IoThreadFactory.IoThread.isIoThread(current);
                if (offloadPoints.contains(offloadPoint)) {
                    if (ioThread) {
                        errors.add(new AssertionError("Expected offloaded thread at " + offloadPoint));
                    }
                } else {
                    if (!ioThread) {
                        errors.add(new AssertionError("Expected ioThread at " + offloadPoint));
                    }
                }
                return new Throwable("stack crawl : " + Thread.currentThread());
            });
        }

        public void verifyOffloads() {
            assertNoAsyncErrors(errors);
            assertThat("Unexpected offload points recorded. " + invokingThreads,
                    invokingThreads.size(), Matchers.is(ClientOffloadPoint.values().length));
        }
    }

    private static class LoadBalancerFactoryImpl
            implements LoadBalancerFactory<InetSocketAddress, FilterableStreamingHttpLoadBalancedConnection> {
        @Override
        public <T extends FilterableStreamingHttpLoadBalancedConnection> LoadBalancer<T>
        newLoadBalancer(final Publisher<? extends ServiceDiscovererEvent<InetSocketAddress>> eventPublisher,
                        final ConnectionFactory<InetSocketAddress, T> connectionFactory) {
            return new RoundRobinLoadBalancerFactory
                    .Builder<InetSocketAddress, FilterableStreamingHttpLoadBalancedConnection>().build()
                    .newLoadBalancer(eventPublisher, connectionFactory);
        }

        @Override
        public ExecutionStrategy requiredOffloads() {
            return ExecutionStrategy.anyStrategy();
        }
    }

    private enum Offloads {
        NONE() {
            @Override
            EnumSet<ClientOffloadPoint> expected(ClientType clientType) {
                return EnumSet.noneOf(ClientOffloadPoint.class);
            }
        },
        DEFAULT() {
            @Override
            EnumSet<ClientOffloadPoint> expected(ClientType clientType) {
                switch (clientType) {
                    case Blocking:
                        return EnumSet.noneOf(ClientOffloadPoint.class);
                    case BlockingStreaming:
                        return EnumSet.of(RequestPayloadSubscription);
                    case Async:
                        return EnumSet.of(ResponseData);
                    case AsyncStreaming:
                        return EnumSet.allOf(ClientOffloadPoint.class);
                    default:
                        throw new IllegalStateException("unexpected case " + clientType);
                }
            }
        },
        ALL() {
            @Override
            EnumSet<ClientOffloadPoint> expected(ClientType clientType) {
                return EnumSet.allOf(ClientOffloadPoint.class);
            }
        };

        abstract EnumSet<ClientOffloadPoint> expected(ClientType clientType);
    }

    private enum ClientType {
        AsyncStreaming,
        BlockingStreaming,
        Blocking,
        Async
    }

    enum ClientOffloadPoint {
        RequestPayloadSubscription,
        ResponseMeta,
        ResponseData
    }
}
