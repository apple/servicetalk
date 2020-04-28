/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.tcp.netty.internal;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.transport.api.ConnectionAcceptor;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.netty.internal.ChannelInitializer;
import io.servicetalk.transport.netty.internal.NettyConnection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

import static io.servicetalk.concurrent.api.BlockingTestUtils.awaitIndefinitelyNonNull;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.failed;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(Parameterized.class)
public class TcpServerBinderConnectionAcceptorTest extends AbstractTcpServerTest {

    enum FilterMode {
        ACCEPT_ALL(true, false, (executor, context) -> completed()),
        DELAY_ACCEPT_ALL(true, false, (executor, context) -> executor.timer(100, MILLISECONDS).concat(completed())),
        REJECT_ALL(false, false, (executor, context) -> failed(DELIBERATE_EXCEPTION)),
        DELAY_REJECT_ALL(false, false, (executor, context) ->
                executor.timer(100, MILLISECONDS).concat(failed(DELIBERATE_EXCEPTION))),
        THROW_EXCEPTION(false, false, (executor, context) -> {
            throw DELIBERATE_EXCEPTION;
        }),
        DELAY_SINGLE_ERROR(false, false, (executor, context) ->
                executor.timer(100, MILLISECONDS).concat(failed(DELIBERATE_EXCEPTION))),
        SINGLE_ERROR(false, false, (executor, context) -> failed(new DeliberateException())),
        INITIALIZER_THROW(false, true, (executor, context) -> completed()),
        DELAY_INITIALIZER_THROW(false, true, (executor, context) ->
                executor.timer(100, MILLISECONDS).concat(completed())),
        ACCEPT_ALL_CONSTANT(true, false, (executor, context) -> completed()) {
            @Override
            ConnectionAcceptor getContextFilter(final Executor executor) {
                return ConnectionAcceptor.ACCEPT_ALL;
            }
        };

        private final boolean expectAccept;
        private final boolean initializerThrow;
        private final BiFunction<Executor, ConnectionContext, Completable>
                contextFilterFunction;

        FilterMode(boolean expectAccept, boolean initializerThrow, BiFunction<Executor, ConnectionContext,
                Completable> contextFilterFunction) {
            this.expectAccept = expectAccept;
            this.initializerThrow = initializerThrow;
            this.contextFilterFunction = contextFilterFunction;
        }

        ConnectionAcceptor getContextFilter(Executor executor) {
            return (context) -> contextFilterFunction.apply(executor, context);
        }
    }

    private final FilterMode filterMode;
    private volatile boolean acceptedConnection;
    @Nullable
    private volatile SSLSession sslSession;

    public TcpServerBinderConnectionAcceptorTest(final boolean enableSsl, final FilterMode filterMode) {
        this.filterMode = filterMode;
        sslEnabled(enableSsl);
        service(conn -> {
            acceptedConnection = true;
            return conn.write(conn.read());
        });
        if (enableSsl) {
            connectionAcceptor(ctx -> {
                // Asserting that the SSL Session has been set by the time the filter is called must be done from the
                // test thread, in order to fail the test with a useful message.
                sslSession = ctx.sslSession();
                return filterMode.getContextFilter(SERVER_CTX.executor()).accept(ctx);
            });
        } else {
            connectionAcceptor(filterMode.getContextFilter(SERVER_CTX.executor()));
        }
    }

    @Override
    TcpServer createServer() {
        if (filterMode.initializerThrow) {
            return new TcpServer() {
                @Override
                ChannelInitializer getChannelInitializer(Function<NettyConnection<Buffer, Buffer>, Completable> service,
                                                         ExecutionContext executionContext) {
                    return channel -> {
                        throw DELIBERATE_EXCEPTION;
                    };
                }
            };
        }
        return super.createServer();
    }

    @Parameters(name = "ssl={0} {1}")
    public static Object[] getContextFilters() {
        int filterModes = FilterMode.values().length;
        Object[] parameters = new Object[filterModes * 2];
        for (int i = 0; i < filterModes; ++i) {
            parameters[i] = new Object[]{false, FilterMode.values()[i]};
            parameters[i + filterModes] = new Object[]{true, FilterMode.values()[i]};
        }
        return parameters;
    }

    @Test
    public void testAcceptConnection() {
        // Write something, then try to read something and wait for a result.
        // We do this to ensure that the server has had a chance to execute code if the connection was accepted.
        // This is necessary for the delayed tests to see the correct state of the acceptedConnection flag.
        try {
            NettyConnection<Buffer, Buffer> connection = client.connectBlocking(CLIENT_CTX, serverAddress);
            final Buffer buffer = connection.executionContext().bufferAllocator().fromAscii("Hello");
            connection.write(Publisher.from(buffer)).toFuture().get();
            Single<Buffer> read = connection.read().firstOrElse(() -> null);
            Buffer responseBuffer = awaitIndefinitelyNonNull(read);
            assertEquals("Did not receive response payload echoing request",
                    "Hello", responseBuffer.toString(US_ASCII));
        } catch (ExecutionException | InterruptedException e) {
            // If we expect the connection to be rejected, then an exception here is ok.
            // We want to continue after the exception, to assert that the server did not accept the connection.
            if (filterMode.expectAccept) {
                throw new RuntimeException("Unexpected exception while reading/writing request/response", e);
            }
        }

        assertEquals("Filter did not " + (filterMode.expectAccept ? "accept" : "reject") + " connection",
                filterMode.expectAccept, acceptedConnection);

        // If the initializer throws, the filter will not execute, so we can't check the SSL Session.
        if (isSslEnabled() && !filterMode.initializerThrow) {
            assertNotNull("SslSession was not set by the time filter executed", sslSession);
        }
    }
}
