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

import io.servicetalk.concurrent.internal.DefaultThreadFactory;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.IoThreadFactory;
import io.servicetalk.transport.netty.internal.ExecutionContextRule;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitelyNonNull;
import static io.servicetalk.transport.netty.NettyIoExecutors.createIoExecutor;
import static java.lang.Thread.NORM_PRIORITY;
import static java.net.InetAddress.getLoopbackAddress;

public abstract class AbstractNettyHttpServerTest {

    @Rule
    public final ServiceTalkTestTimeout timeout = new ServiceTalkTestTimeout();

    @ClassRule
    public static final ExecutionContextRule CTX = new ExecutionContextRule(() -> DEFAULT_ALLOCATOR,
            () -> createIoExecutor(new IoThreadFactory("server-io-executor")),
            () -> newCachedThreadExecutor(new DefaultThreadFactory("server-executor", true, NORM_PRIORITY)));

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractNettyHttpServerTest.class);
    private static final InetAddress LOOPBACK_ADDRESS = getLoopbackAddress();

    private ServerContext serverContext;
    private InetSocketAddress socketAddress;

    @Before
    public void startServer() throws Exception {
        final InetSocketAddress bindAddress = new InetSocketAddress(LOOPBACK_ADDRESS, 0);
        final TestService service = new TestService();

        // A small SNDBUF is needed to test that the server defers closing the connection until writes are complete.
        // However, if it is too small, tests that expect certain chunks of data will see those chunks broken up
        // differently.
        serverContext = awaitIndefinitelyNonNull(
                new DefaultHttpServerStarter()
                        .setSocketOption(StandardSocketOptions.SO_SNDBUF, 100)
                        .start(CTX, bindAddress, service)
                        .doBeforeSuccess(ctx -> LOGGER.debug("Server started on {}.", ctx.getListenAddress()))
                        .doBeforeError(throwable -> LOGGER.debug("Failed starting server on {}.", bindAddress)));

        this.socketAddress = new InetSocketAddress(LOOPBACK_ADDRESS,
                ((InetSocketAddress) serverContext.getListenAddress()).getPort());
    }

    @After
    public void stopServer() throws Exception {
        awaitIndefinitely(serverContext.closeAsync());
    }

    InetSocketAddress getServerSocketAddress() {
        return socketAddress;
    }
}
