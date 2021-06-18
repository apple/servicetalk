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
package io.servicetalk.tcp.netty.internal;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.ConnectionAcceptor;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfigBuilder;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.AddressUtils;
import io.servicetalk.transport.netty.internal.ExecutionContextRule;
import io.servicetalk.transport.netty.internal.NettyConnection;
import io.servicetalk.transport.netty.internal.NoopTransportObserver;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.Timeout;

import java.net.InetSocketAddress;
import java.util.function.Function;

import static io.servicetalk.logging.api.LogLevel.TRACE;
import static io.servicetalk.test.resources.DefaultTestCerts.serverPemHostname;
import static io.servicetalk.transport.api.ConnectionAcceptor.ACCEPT_ALL;
import static io.servicetalk.transport.netty.internal.ExecutionContextRule.cached;

public abstract class AbstractTcpServerTest {

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    @ClassRule
    public static final ExecutionContextRule SERVER_CTX = cached("server-io", "server-executor");
    @ClassRule
    public static final ExecutionContextRule CLIENT_CTX = cached("client-io", "client-executor");

    private ConnectionAcceptor connectionAcceptor = ACCEPT_ALL;
    private Function<NettyConnection<Buffer, Buffer>, Completable> service =
            conn -> conn.write(conn.read());
    private boolean sslEnabled;

    protected ServerContext serverContext;
    protected InetSocketAddress serverAddress;
    protected TcpClient client;
    protected TcpServer server;

    void connectionAcceptor(final ConnectionAcceptor connectionAcceptor) {
        this.connectionAcceptor = connectionAcceptor;
    }

    void service(final Function<NettyConnection<Buffer, Buffer>, Completable> service) {
        this.service = service;
    }

    void sslEnabled(final boolean sslEnabled) {
        this.sslEnabled = sslEnabled;
    }

    boolean isSslEnabled() {
        return sslEnabled;
    }

    @Before
    public void startServer() throws Exception {
        server = createServer();
        serverContext = server.bind(SERVER_CTX, 0, connectionAcceptor, service, SERVER_CTX.executionStrategy());
        serverAddress = (InetSocketAddress) serverContext.listenAddress();
        client = createClient();
    }

    // Visible for overriding.
    TcpClient createClient() {
        return new TcpClient(getTcpClientConfig(), getClientTransportObserver());
    }

    // Visible for overriding.
    TcpClientConfig getTcpClientConfig() {
        TcpClientConfig tcpClientConfig = new TcpClientConfig();
        if (sslEnabled) {
            HostAndPort serverHostAndPort = AddressUtils.serverHostAndPort(serverContext);
            tcpClientConfig.sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                    .peerHost(serverPemHostname())
                    .peerPort(serverHostAndPort.port())
                    .build());
        }
        tcpClientConfig.enableWireLogging("servicetalk-tests-wire-logger", TRACE, () -> true);
        return tcpClientConfig;
    }

    // Visible for overriding.
    TransportObserver getClientTransportObserver() {
        return NoopTransportObserver.INSTANCE;
    }

    // Visible for overriding.
    TcpServer createServer() {
        return new TcpServer(getTcpServerConfig());
    }

    // Visible for overriding.
    TcpServerConfig getTcpServerConfig() {
        TcpServerConfig tcpServerConfig = new TcpServerConfig();
        if (sslEnabled) {
            tcpServerConfig.sslConfig(new ServerSslConfigBuilder(DefaultTestCerts::loadServerPem,
                    DefaultTestCerts::loadServerKey).build());
        }
        tcpServerConfig.enableWireLogging("servicetalk-tests-wire-logger", TRACE, () -> true);
        return tcpServerConfig;
    }

    @After
    public void stopServer() throws Exception {
        serverContext.close();
    }
}
