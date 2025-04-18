/*
 * Copyright © 2025 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.DelegatingConnectionFactory;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpLifecycleObserver;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.transport.api.ConnectionInfo;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.ExecutionStrategy;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.NoopTransportObserver;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConnectionObserverEventOrderTest {

    private final BlockingQueue<String> eventQueue = new LinkedBlockingQueue<>();

    @Test
    void repro() throws Exception {
        final ServerContext serverContext = HttpServers.forPort(0)
                .listenBlockingAndAwait((ctx, request, responseFactory) ->
                        responseFactory.ok().payloadBody("Test server response", textSerializerUtf8()));

        final HostAndPort hostAndPort = serverHostAndPort(serverContext);
        final String hostName = hostAndPort.hostName();
        final int port = hostAndPort.port();

        assertThrows(Exception.class, () -> {
            try (BlockingHttpClient client = HttpClients.forSingleAddress(hostName, port)
                    .appendConnectionFactoryFilter(new TransportObserverInjectorFilter<>())
                    .appendClientFilter(new HttpLifecycleObserverRequesterFilter(new HttpLifecycleObserverImpl()))
                    .appendClientFilter(new RetryingHttpRequesterFilter.Builder()
                            .retryRetryableExceptions((req, ex) ->
                                    RetryingHttpRequesterFilter.BackOffPolicy.ofNoRetries())
                            .build())
                    .buildBlocking()) {
                serverContext.close(); // causes connection establishment to fail.
                HttpResponse response = client.request(client.get("/sayHello"));
                response.payloadBody();
            }
        });

        List<String> expectedEvents = Arrays.asList("onNewExchange", "onRequest", "onNewConnection",
                "connectionClosed", "onResponseError", "onExchangeFinally");
        for (String expected : expectedEvents) {
            assertEquals(expected, eventQueue.take());
        }
    }

    private final class HttpLifecycleObserverImpl implements HttpLifecycleObserver {
        @Override
        public HttpExchangeObserver onNewExchange() {
            addEvent();
            return new HttpExchangeObserver() {
                @Override
                public void onConnectionSelected(ConnectionInfo info) {
                    addEvent();
                }

                @Override
                public HttpRequestObserver onRequest(HttpRequestMetaData requestMetaData) {
                    addEvent();
                    return NoopHttpLifecycleObserver.NoopHttpRequestObserver.INSTANCE;
                }

                @Override
                public HttpResponseObserver onResponse(HttpResponseMetaData responseMetaData) {
                    addEvent();
                    return NoopHttpLifecycleObserver.NoopHttpResponseObserver.INSTANCE;
                }

                @Override
                public void onResponseError(Throwable cause) {
                    addEvent();
                }

                @Override
                public void onResponseCancel() {
                    addEvent();
                }

                @Override
                public void onExchangeFinally() {
                    addEvent();
                }
            };
        }
    }

    private final class TransportObserverInjectorFilter<R>
            implements ConnectionFactoryFilter<R, FilterableStreamingHttpConnection> {

        @Override
        public ConnectionFactory<R, FilterableStreamingHttpConnection> create(
                ConnectionFactory<R, FilterableStreamingHttpConnection> original) {
            return new TransportObserverInjector<>(original);
        }

        @Override
        public ExecutionStrategy requiredOffloads() {
            return ExecutionStrategy.offloadNone();
        }
    }

    private final class TransportObserverInjector<R> extends
            DelegatingConnectionFactory<R, FilterableStreamingHttpConnection> {

        TransportObserverInjector(final ConnectionFactory<R, FilterableStreamingHttpConnection> delegate) {
            super(delegate);
        }

        @Override
        public Single<FilterableStreamingHttpConnection> newConnection(final R resolvedAddress,
                                                                       @Nullable final ContextMap context,
                                                                       @Nullable final TransportObserver observer) {
            return delegate().newConnection(resolvedAddress, context, new TestTransportObserver());
        }
    }

    private final class TestTransportObserver implements TransportObserver {

        @Override
        public ConnectionObserver onNewConnection(@Nullable Object localAddress, Object remoteAddress) {
            addEvent();
            return new ConnectionObserverImpl();
        }

        private final class ConnectionObserverImpl implements ConnectionObserver {
            @Override
            public void onDataRead(int size) {
                addEvent();
            }

            @Override
            public void onDataWrite(int size) {
                addEvent();
            }

            @Override
            public void onFlush() {
                addEvent();
            }

            @Override
            public DataObserver connectionEstablished(ConnectionInfo info) {
                addEvent();
                return NoopTransportObserver.NoopDataObserver.INSTANCE;
            }

            @Override
            public MultiplexedObserver multiplexedConnectionEstablished(ConnectionInfo info) {
                addEvent();
                return NoopTransportObserver.NoopMultiplexedObserver.INSTANCE;
            }

            @Override
            public void connectionClosed(Throwable error) {
                addEvent();
            }

            @Override
            public void connectionClosed() {
                addEvent();
            }
        }
    }

    private void addEvent() {
        eventQueue.add(new Exception().getStackTrace()[2].getMethodName());
    }
}
