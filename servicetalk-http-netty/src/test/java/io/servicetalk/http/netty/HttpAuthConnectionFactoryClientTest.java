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

import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.ExecutionContextRule;

import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;

import static io.servicetalk.concurrent.api.Single.error;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.netty.HttpServers.newHttpServerBuilder;
import static java.util.Objects.requireNonNull;
import static org.junit.Assert.assertEquals;

public class HttpAuthConnectionFactoryClientTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    @ClassRule
    public static final ExecutionContextRule CTX = ExecutionContextRule.immediate();

    private StreamingHttpClient client;
    private ServerContext serverContext;

    @After
    public void teardown() throws ExecutionException, InterruptedException {
        if (client != null) {
            awaitIndefinitely(client.closeAsync());
        }
        if (serverContext != null) {
            awaitIndefinitely(serverContext.closeAsync());
        }
    }

    @Test
    public void simulateAuth() throws Exception {
        serverContext = newHttpServerBuilder(0)
                .executionContext(CTX)
                .listenStreamingAndAwait(
                        new StreamingHttpService() {
                            @Override
                            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                                        final StreamingHttpRequest request,
                                                                        final StreamingHttpResponseFactory factory) {
                                return success(newTestResponse(factory));
                            }
                        });
        client = HttpClients.forSingleAddress("localhost",
                ((InetSocketAddress) serverContext.getListenAddress()).getPort())
                .executionContext(CTX)
                .buildStreaming();

        StreamingHttpResponse response = awaitIndefinitely(client.request(newTestRequest(client, "/foo")));
        assertEquals(OK, response.status());
    }

    private static final class TestHttpAuthConnectionFactory<ResolvedAddress> implements
                              ConnectionFactory<ResolvedAddress, StreamingHttpConnection> {
        private final ConnectionFactory<ResolvedAddress,
                ? extends StreamingHttpConnection> delegate;

        TestHttpAuthConnectionFactory(final ConnectionFactory<ResolvedAddress,
                ? extends StreamingHttpConnection> delegate) {
            this.delegate = requireNonNull(delegate);
        }

        @Override
        public Single<StreamingHttpConnection> newConnection(
                final ResolvedAddress resolvedAddress) {
            return delegate.newConnection(resolvedAddress).flatMap(cnx ->
                    cnx.request(newTestRequest(cnx, "/auth"))
                            .onErrorResume(cause -> {
                                cnx.closeAsync().subscribe();
                                return error(new IllegalStateException("failed auth"));
                            })
                            .flatMap(response -> {
                                if (response.status().equals(OK)) {
                                    // In this test we have not enabled pipelining so we drain this response before
                                    // indicating the connection is usable.
                                    return response.payloadBody().ignoreElements().andThen(success(cnx));
                                }
                                cnx.closeAsync().subscribe();
                                return error(new IllegalStateException("failed auth"));
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
