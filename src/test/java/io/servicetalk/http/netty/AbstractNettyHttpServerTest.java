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

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.internal.DefaultThreadFactory;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.IoThreadFactory;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitelyNonNull;
import static io.servicetalk.transport.netty.NettyIoExecutors.createIoExecutor;
import static java.lang.Thread.NORM_PRIORITY;
import static java.net.InetAddress.getLoopbackAddress;

public abstract class AbstractNettyHttpServerTest {

    @Rule
    public final ServiceTalkTestTimeout timeout = new ServiceTalkTestTimeout();

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractNettyHttpServerTest.class);
    private static final InetAddress LOOPBACK_ADDRESS = getLoopbackAddress();

    private static IoExecutor ioExecutor;

    private ServerContext serverContext;
    private InetSocketAddress socketAddress;

    @BeforeClass
    public static void createServerIoExecutor() {
        ioExecutor = createIoExecutor(2, new IoThreadFactory("server-io-executor"));
    }

    @Before
    public void startServer() throws Exception {
        final InetSocketAddress bindAddress = new InetSocketAddress(LOOPBACK_ADDRESS, 0);

        final Executor executor = newCachedThreadExecutor(new DefaultThreadFactory("server-executor", true,
                NORM_PRIORITY));
        final TestService service = new TestService();

        serverContext = awaitIndefinitelyNonNull(
                new DefaultHttpServerStarter(ioExecutor)
                        .start(bindAddress, executor, service)
                        .doBeforeSuccess(ctx -> LOGGER.debug("Server started on {}.", ctx.getListenAddress()))
                        .doBeforeError(throwable -> LOGGER.debug("Failed starting server on {}.", bindAddress)));

        this.socketAddress = new InetSocketAddress(LOOPBACK_ADDRESS,
                ((InetSocketAddress) serverContext.getListenAddress()).getPort());
    }

    @After
    public void stopServer() throws Exception {
        awaitIndefinitely(serverContext.closeAsync());
    }

    @AfterClass
    public static void shutdownServerIoExecutor() throws Exception {
        awaitIndefinitely(ioExecutor.closeAsync());
    }

    InetSocketAddress getServerSocketAddress() {
        return socketAddress;
    }
}
