/*
 * Copyright © 2018, 2020 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.ExecutionContextRule;

import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.Objects.requireNonNull;
import static org.junit.Assert.assertEquals;

public class HttpAuthConnectionFactoryClientTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    @ClassRule
    public static final ExecutionContextRule CTX = ExecutionContextRule.immediate();

    @Nullable
    private StreamingHttpClient client;
    @Nullable
    private ServerContext serverContext;

    @After
    public void teardown() throws Exception {
        if (client != null) {
            client.closeAsync().toFuture().get();
        }
        if (serverContext != null) {
            serverContext.closeAsync().toFuture().get();
        }
    }

    @Test
    public void simulateAuth() throws Exception {
        serverContext = HttpServers.forAddress(localAddress(0))
                .ioExecutor(CTX.ioExecutor())
                .executionStrategy(noOffloadsStrategy())
                .listenStreamingAndAwait((ctx, request, factory) -> succeeded(newTestResponse(factory)));

        client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                .appendConnectionFactoryFilter(TestHttpAuthConnectionFactory::new)
                .ioExecutor(CTX.ioExecutor())
                .executionStrategy(noOffloadsStrategy())
                .buildStreaming();

        StreamingHttpResponse response = client.request(newTestRequest(client, "/foo")).toFuture().get();
        assertEquals(OK, response.status());
    }

    private static final class TestHttpAuthConnectionFactory<ResolvedAddress> implements
                              ConnectionFactory<ResolvedAddress, FilterableStreamingHttpConnection> {
        private final ConnectionFactory<ResolvedAddress,
                ? extends FilterableStreamingHttpConnection> delegate;

        TestHttpAuthConnectionFactory(final ConnectionFactory<ResolvedAddress,
                ? extends FilterableStreamingHttpConnection> delegate) {
            this.delegate = requireNonNull(delegate);
        }

        @Override
        public Single<FilterableStreamingHttpConnection> newConnection(
                final ResolvedAddress resolvedAddress, @Nullable final TransportObserver observer) {
            return delegate.newConnection(resolvedAddress, observer).flatMap(cnx ->
                    cnx.request(defaultStrategy(), newTestRequest(cnx, "/auth"))
                            .recoverWith(cause -> {
                                cnx.closeAsync().subscribe();
                                return failed(new IllegalStateException("failed auth"));
                            })
                            .flatMap(response -> {
                                if (OK.equals(response.status())) {
                                    // In this test we have not enabled pipelining so we drain this response before
                                    // indicating the connection is usable.
                                    return response.payloadBodyAndTrailers().ignoreElements().concat(succeeded(cnx));
                                }
                                cnx.closeAsync().subscribe();
                                return failed(new IllegalStateException("failed auth"));
                            })
            );
        }

        @Override
        public Completable onClose() {
            return delegate.onClose();
        }

        @Override
        public Completable closeAsync() {
            return delegate.closeAsync();
        }

        @Override
        public Completable closeAsyncGracefully() {
            return delegate.closeAsyncGracefully();
        }
    }

    private static StreamingHttpRequest newTestRequest(StreamingHttpRequestFactory factory, String requestTarget) {
        StreamingHttpRequest req = factory.get(requestTarget);
        req.headers().set(CONTENT_LENGTH, ZERO);
        return req;
    }

    private static StreamingHttpResponse newTestResponse(StreamingHttpResponseFactory factory) {
        StreamingHttpResponse resp = factory.ok();
        resp.headers().set(CONTENT_LENGTH, ZERO);
        return resp;
    }
}
