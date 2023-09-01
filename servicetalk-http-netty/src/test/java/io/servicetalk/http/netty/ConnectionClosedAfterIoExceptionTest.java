/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.netty.RetryingHttpRequesterFilter.BackOffPolicy;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.failed;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/**
 * Verifies that connection is marked as "closing" after it receives
 * {@link ChannelInboundHandler#exceptionCaught(ChannelHandlerContext, Throwable)} event.
 */
class ConnectionClosedAfterIoExceptionTest {

    @RegisterExtension
    static final ExecutionContextExtension SERVER_CTX =
            ExecutionContextExtension.cached("server-io", "server-executor")
                    .setClassLevel(true);
    @RegisterExtension
    static final ExecutionContextExtension CLIENT_CTX =
            ExecutionContextExtension.cached("client-io", "client-executor")
                    .setClassLevel(true);

    @ParameterizedTest(name = "{displayName} [{index}]: protocol={0}")
    @EnumSource(HttpProtocol.class)
    void test(HttpProtocol protocol) throws Exception {
        AtomicReference<FilterableStreamingHttpConnection> firstConnection = new AtomicReference<>();
        BlockingQueue<Throwable> errors = new LinkedBlockingQueue<>();
        AtomicBoolean firstAttempt = new AtomicBoolean(true);
        try (ServerContext serverContext = BuilderUtils.newServerBuilder(SERVER_CTX, protocol)
                // Fail only the first connect attempt
                .appendEarlyConnectionAcceptor(conn -> firstAttempt.compareAndSet(true, false) ?
                        failed(DELIBERATE_EXCEPTION) : completed())
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok());
             BlockingHttpClient client = BuilderUtils.newClientBuilder(serverContext, CLIENT_CTX, protocol)
                     .appendConnectionFactoryFilter(original -> new DelegatingConnectionFactory<InetSocketAddress,
                             FilterableStreamingHttpConnection>(original) {
                         @Override
                         public Single<FilterableStreamingHttpConnection> newConnection(InetSocketAddress address,
                                 @Nullable ContextMap context, @Nullable TransportObserver observer) {
                             return delegate().newConnection(address, context, observer)
                                     .whenOnSuccess(connection -> firstConnection.compareAndSet(null, connection));
                         }
                     })
                     .appendClientFilter(new RetryingHttpRequesterFilter.Builder()
                             .retryRetryableExceptions((metaData, t) -> {
                                 errors.add((Throwable) t);
                                 return BackOffPolicy.ofImmediateBounded();
                             })
                             .retryOther((metaData, t) -> {
                                 errors.add(t);
                                 return BackOffPolicy.ofImmediateBounded();
                             })
                             .build())
                     .buildBlocking()) {

            Assertions.assertDoesNotThrow(() -> {
                HttpResponse response = client.request(client.get("/"));
                assertThat(response.status(), is(OK));
            });

            assertThat("Unexpected number of errors, likely retried more than expected", errors, hasSize(1));
            assertThat("Did not propagate original IoException", errors.poll(),
                    anyOf(instanceOf(IOException.class), not(instanceOf(ClosedChannelException.class))));

            // Make sure that the first connection was properly closed:
            final FilterableStreamingHttpConnection connection = firstConnection.get();
            connection.onClose().toFuture().get();
            connection.connectionContext().onClose().toFuture().get();
        }
    }
}
