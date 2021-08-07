/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.DelegatingConnectionFactory;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.test.StepVerifiers;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestFactory;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.netty.Http2Exception.H2StreamResetException;
import io.servicetalk.transport.api.TransportObserver;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_2_0;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializers.appSerializerUtf8FixLen;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED_SERVER;
import static io.servicetalk.http.netty.HttpProtocol.HTTP_2;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_ECHO;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class H2ResponseCancelTest extends AbstractNettyHttpServerTest {

    private static final String PARAM = "param";

    private final CountDownLatch firstRequestReceivedLatch = new CountDownLatch(1);
    private final CountDownLatch secondRequestReceivedLatch = new CountDownLatch(1);

    private final CountDownLatch firstResponseLatch = new CountDownLatch(1);
    private final CountDownLatch secondResponseLatch = new CountDownLatch(1);

    private final AtomicInteger newConnectionsCounter = new AtomicInteger();

    H2ResponseCancelTest() {
        protocol(HTTP_2.config);
        serviceFilterFactory(service -> new StreamingHttpServiceFilter(service) {
            @Override
            public Single<StreamingHttpResponse> handle(HttpServiceContext ctx, StreamingHttpRequest request,
                                                        StreamingHttpResponseFactory responseFactory) {
                try {
                    if ("first".equals(request.queryParameter(PARAM))) {
                        firstRequestReceivedLatch.countDown();
                        firstResponseLatch.await();
                    }
                    if ("second".equals(request.queryParameter(PARAM))) {
                        secondRequestReceivedLatch.countDown();
                        secondResponseLatch.await();
                    }
                    if ("close".equals(request.queryParameter(PARAM))) {
                        ctx.closeAsync().toFuture().get();
                    }
                } catch (Exception e) {
                    return failed(e);
                }
                return delegate().handle(ctx, request, responseFactory);
            }
        });
        connectionFactoryFilter(factory -> new DelegatingConnectionFactory<InetSocketAddress,
                FilterableStreamingHttpConnection>(factory) {
            @Override
            public Single<FilterableStreamingHttpConnection> newConnection(InetSocketAddress inetSocketAddress,
                                                                           @Nullable TransportObserver observer) {
                return defer(() -> {
                    newConnectionsCounter.incrementAndGet();
                    return delegate().newConnection(inetSocketAddress, observer);
                });
            }
        });
        setUp(CACHED, CACHED_SERVER);
    }

    @Test
    void testClient() throws Exception {
        // Release existing connection to avoid creating unexpected number of new h2 connections because of randomness
        // in RRLB (it may randomly pick the reserved connection until all attempts are taken).
        ((ReservedStreamingHttpConnection) streamingHttpConnection()).releaseAsync().toFuture().get();

        int connectionsBefore = newConnectionsCounter.get();
        requestCancellationResetsStreamButNotParentConnection(streamingHttpClient());
        int connectionsAfter = newConnectionsCounter.get();
        assertThat("Client unexpectedly created more connections instead of reusing the existing one",
                connectionsAfter, is(connectionsBefore));
    }

    @Test
    void testConnection() throws Exception {
        StreamingHttpConnection connection = streamingHttpConnection();
        AtomicBoolean connectionClosed = new AtomicBoolean();
        connection.onClose().whenFinally(() -> connectionClosed.set(true)).subscribe();

        requestCancellationResetsStreamButNotParentConnection(connection);
        assertThat("Connection closed unexpectedly", connectionClosed.get(), is(false));
    }

    @Test
    void testServerClosesStreamNotConnection() throws Exception {
        StreamingHttpConnection connection = streamingHttpConnection();
        AtomicBoolean connectionClosed = new AtomicBoolean();
        connection.onClose().whenFinally(() -> connectionClosed.set(true)).subscribe();

        ExecutionException e = assertThrows(ExecutionException.class,
                () -> makeRequest(newRequest(connection, "close")));
        assertThat(e.getCause(), instanceOf(H2StreamResetException.class));

        // Mak sure we can use the same connection for future requests:
        assertSerializedResponse(makeRequest(newRequest(connection, "ok")), HTTP_2_0, OK, "ok");
        assertThat("Connection closed unexpectedly", connectionClosed.get(), is(false));
    }

    private void requestCancellationResetsStreamButNotParentConnection(StreamingHttpRequester requester)
            throws Exception {

        BlockingQueue<StreamingHttpResponse> responses = new LinkedBlockingDeque<>();
        requester.request(defaultStrategy(), newRequest(requester, "first")).subscribe(responses::add);
        firstRequestReceivedLatch.await();

        AtomicReference<Cancellable> cancellable = new AtomicReference<>();
        StepVerifiers.create(requester.request(defaultStrategy(), newRequest(requester, "second"))
                        .whenOnSuccess(responses::add)) // Add response to the queue to verify that we never receive it
                .expectCancellableConsumed(cancellable::set)
                .then(() -> {
                    try {
                        secondRequestReceivedLatch.await();
                        cancellable.get().cancel(); // If I use thenCancel() the current then(Runnable) does not run
                    } catch (InterruptedException e) {
                        // ignore
                    }
                })
                // FIXME: use thenCancel() after await() instead of cancelling from inside then(...) + expectError()
                // https://github.com/apple/servicetalk/issues/1492
                .expectError(IllegalStateException.class)   // should never happen
                .verify();

        assertThat("Unexpected responses", responses, is(empty()));
        firstResponseLatch.countDown();

        assertSerializedResponse(responses.take(), HTTP_2_0, OK, "first");
        // Make sure we can use the same connection for future requests:
        assertSerializedResponse(makeRequest(newRequest(requester, "third")), HTTP_2_0, OK, "third");
    }

    private static StreamingHttpRequest newRequest(StreamingHttpRequestFactory requestFactory, String param) {
        return requestFactory.post(SVC_ECHO)
                .addQueryParameter(PARAM, param)
                .payloadBody(from(param), appSerializerUtf8FixLen());
    }
}
