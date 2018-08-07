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
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpConnection;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.transport.api.ContextFilter;
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
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitelyNonNull;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.http.api.HttpRequests.newRequest;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.api.HttpResponses.newResponse;
import static io.servicetalk.http.api.HttpService.fromAsync;
import static java.util.Objects.requireNonNull;
import static org.junit.Assert.assertEquals;

public class HttpAuthConnectionFactoryClientTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    @ClassRule
    public static final ExecutionContextRule CTX = ExecutionContextRule.immediate();

    private HttpClient client;
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
        serverContext = awaitIndefinitelyNonNull(new DefaultHttpServerStarter()
                .start(CTX, new InetSocketAddress(0), ContextFilter.ACCEPT_ALL,
                        fromAsync((ctx, req) -> success(newTestResponse()))));
        client = DefaultHttpClientBuilder.forSingleAddress(
                "localhost", ((InetSocketAddress) serverContext.getListenAddress()).getPort()).build(CTX);

        HttpResponse<HttpPayloadChunk> response = awaitIndefinitely(client.request(newTestRequest("/foo")));
        assertEquals(OK, response.getStatus());
    }

    private static final class TestHttpAuthConnectionFactory<ResolvedAddress> implements
                              ConnectionFactory<ResolvedAddress, HttpConnection> {
        private final ConnectionFactory<ResolvedAddress,
                ? extends HttpConnection> delegate;

        TestHttpAuthConnectionFactory(final ConnectionFactory<ResolvedAddress,
                ? extends HttpConnection> delegate) {
            this.delegate = requireNonNull(delegate);
        }

        @Override
        public Single<HttpConnection> newConnection(
                final ResolvedAddress resolvedAddress) {
            return delegate.newConnection(resolvedAddress).flatMap(cnx ->
                    cnx.request(newTestRequest("/auth"))
                            .onErrorResume(cause -> {
                                cnx.closeAsync().subscribe();
                                return error(new IllegalStateException("failed auth"));
                            })
                            .flatMap(response -> {
                                if (response.getStatus().equals(OK)) {
                                    // In this test we have not enabled pipelining so we drain this response before
                                    // indicating the connection is usable.
                                    return response.getPayloadBody().ignoreElements().andThen(success(cnx));
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

    private static HttpRequest<HttpPayloadChunk> newTestRequest(String requestTarget) {
        HttpRequest<HttpPayloadChunk> req = newRequest(GET, requestTarget);
        req.getHeaders().set(CONTENT_LENGTH, ZERO);
        return req;
    }

    private static HttpResponse<HttpPayloadChunk> newTestResponse() {
        HttpResponse<HttpPayloadChunk> resp = newResponse(OK);
        resp.getHeaders().set(CONTENT_LENGTH, ZERO);
        return resp;
    }
}
