/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.ConnectionObserver.SecurityHandshakeObserver;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.ClientSecurityConfig;
import io.servicetalk.transport.netty.internal.ServerSecurityConfig;

import org.mockito.Mockito;
import org.mockito.verification.VerificationWithTimeout;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AbstractTransportObserverTest extends AbstractTcpServerTest {

    protected final TransportObserver clientTransportObserver;
    protected final ConnectionObserver clientConnectionObserver;
    protected final SecurityHandshakeObserver clientSecurityHandshakeObserver;

    protected final TransportObserver serverTransportObserver;
    protected final ConnectionObserver serverConnectionObserver;
    protected final SecurityHandshakeObserver serverSecurityHandshakeObserver;

    protected AbstractTransportObserverTest() {
        clientTransportObserver = mock(TransportObserver.class, "clientTransportObserver");
        clientConnectionObserver = mock(ConnectionObserver.class, "clientConnectionObserver");
        clientSecurityHandshakeObserver = mock(SecurityHandshakeObserver.class, "clientSecurityHandshakeObserver");
        when(clientTransportObserver.onNewConnection()).thenReturn(clientConnectionObserver);
        when(clientConnectionObserver.onSecurityHandshake()).thenReturn(clientSecurityHandshakeObserver);

        serverTransportObserver = mock(TransportObserver.class, "serverTransportObserver");
        serverConnectionObserver = mock(ConnectionObserver.class, "serverConnectionObserver");
        serverSecurityHandshakeObserver = mock(SecurityHandshakeObserver.class, "serverSecurityHandshakeObserver");
        when(serverTransportObserver.onNewConnection()).thenReturn(serverConnectionObserver);
        when(serverConnectionObserver.onSecurityHandshake()).thenReturn(serverSecurityHandshakeObserver);
    }

    @Override
    TcpClientConfig getTcpClientConfig() {
        final TcpClientConfig config = super.getTcpClientConfig();
        config.transportObserver(clientTransportObserver);
        return config;
    }

    static ClientSecurityConfig defaultClientSecurityConfig() {
        ClientSecurityConfig config = new ClientSecurityConfig("foo", -1);
        config.disableHostnameVerification();
        config.trustManager(DefaultTestCerts::loadMutualAuthCaPem);
        return config;
    }

    @Override
    TcpServerConfig getTcpServerConfig() {
        final TcpServerConfig config = super.getTcpServerConfig();
        config.transportObserver(serverTransportObserver);
        return config;
    }

    static ServerSecurityConfig defaultServerSecurityConfig() {
        ServerSecurityConfig config = new ServerSecurityConfig();
        config.keyManager(DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey);
        return config;
    }

    /**
     * Because the client is just a trigger for server-side events we need to await for invocations to verify them.
     */
    static VerificationWithTimeout await() {
        return Mockito.timeout(Long.MAX_VALUE);
    }
}
