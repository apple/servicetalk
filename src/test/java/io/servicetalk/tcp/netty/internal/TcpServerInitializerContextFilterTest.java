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
package io.servicetalk.tcp.netty.internal;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.DeliberateException;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ContextFilter;
import io.servicetalk.transport.netty.internal.ChannelInitializer;
import io.servicetalk.transport.netty.internal.Connection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import java.util.function.Function;

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.api.Single.error;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitelyNonNull;
import static io.servicetalk.transport.api.FlushStrategy.flushOnEach;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

@RunWith(Parameterized.class)
public class TcpServerInitializerContextFilterTest extends AbstractTcpServerTest {

    enum FilterMode {
        ACCEPT_ALL(true, false, (executor, context) -> success(true)),
        DELAY_ACCEPT_ALL(true, false, (executor, context) -> executor.schedule(100, MILLISECONDS).andThen(success(true))),
        REJECT_ALL(false, false, (executor, context) -> success(false)),
        DELAY_REJECT_ALL(false, false, (executor, context) -> executor.schedule(100, MILLISECONDS).andThen(success(false))),
        THROW_EXCEPTION(false, false, (executor, context) -> {
            throw DELIBERATE_EXCEPTION;
        }),
        SINGLE_ERROR(false, false, (executor, context) -> executor.schedule(100, MILLISECONDS).andThen(error(new DeliberateException()))),
        DELAY_SINGLE_ERROR(false, false, (executor, context) -> error(new DeliberateException())),
        INITIALIZER_THROW(false, true, (executor, context) -> success(true)),
        DELAY_INITIALIZER_THROW(false, true, (executor, context) -> executor.schedule(100, MILLISECONDS).andThen(success(true)));

        private final boolean expectAccept;
        private final boolean initializerThrow;
        private final BiFunction<Executor, ConnectionContext, Single<Boolean>>
                contextFilterFunction;

        FilterMode(boolean expectAccept, boolean initializerThrow, BiFunction<Executor, ConnectionContext,
                Single<Boolean>> contextFilterFunction) {
            this.expectAccept = expectAccept;
            this.initializerThrow = initializerThrow;
            this.contextFilterFunction = contextFilterFunction;
        }

        ContextFilter getContextFilter(Executor executor) {
            return (context) -> contextFilterFunction.apply(executor, context);
        }
    }

    private final FilterMode filterMode;
    private volatile boolean acceptedConnection;

    public TcpServerInitializerContextFilterTest(final FilterMode filterMode) {
        this.filterMode = filterMode;
    }

    @Override
    TcpServer createServer() {
        setContextFilter(filterMode.getContextFilter(getExecutor()));
        setService(conn -> {
            acceptedConnection = true;
            return conn.write(conn.read(), flushOnEach());
        });

        if (filterMode.initializerThrow) {
            return new TcpServer(serverIoExecutor) {
                @Override
                ChannelInitializer getChannelInitializer(
                        final Function<Connection<Buffer, Buffer>, Completable> service, final Executor executor) {
                    return (channel, context) -> {
                        throw DELIBERATE_EXCEPTION;
                    };
                }
            };
        }
        return super.createServer();
    }

    @Parameters(name = "{0}")
    public static FilterMode[] getContextFilters() {
        return FilterMode.values();
    }

    @Test
    public void testAcceptConnection() throws Exception {
        Connection<Buffer, Buffer> connection = client.connectBlocking(serverPort);
        final Buffer buffer = connection.getBufferAllocator().fromAscii("Hello");

        // Write something, then try to read something and wait for a result.
        // We do this to ensure that the server has had a chance to execute code if the connection was accepted.
        // This is necessary for the delayed tests to see the correct state of the acceptedConnection flag.
        try {
            awaitIndefinitely(connection.writeAndFlush(buffer));
            Single<Buffer> read = connection.read().first();
            Buffer responseBuffer = awaitIndefinitelyNonNull(read);
            assertEquals("Did not receive response payload echoing request",
                    "Hello", responseBuffer.toString(US_ASCII));
        } catch (ExecutionException | InterruptedException e) {
            // If we expect the connection to be rejected, then an exception here is ok.
            // We want to continue after the exception, to assert that the server did not accept the connection.
            assertFalse("Unexpected exception while reading/writing request/response",
                    filterMode.expectAccept);
        }

        assertEquals("Filter did not " + (filterMode.expectAccept ? "accept" : "reject") + " connection",
                filterMode.expectAccept, acceptedConnection);
    }
}
