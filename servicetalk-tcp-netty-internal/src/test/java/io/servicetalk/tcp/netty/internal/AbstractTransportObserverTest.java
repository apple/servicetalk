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
import io.servicetalk.transport.api.ConnectionInfo;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.ConnectionObserver.NonMultiplexedObserver;
import io.servicetalk.transport.api.ConnectionObserver.ReadObserver;
import io.servicetalk.transport.api.ConnectionObserver.SecurityHandshakeObserver;
import io.servicetalk.transport.api.ConnectionObserver.WriteObserver;
import io.servicetalk.transport.api.SecurityConfigurator.SslProvider;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.ClientSecurityConfig;
import io.servicetalk.transport.netty.internal.ServerSecurityConfig;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AbstractTransportObserverTest extends AbstractTcpServerTest {

    protected final TransportObserver clientTransportObserver;
    protected final ConnectionObserver clientConnectionObserver;
    protected final SecurityHandshakeObserver clientSecurityHandshakeObserver;
    protected final NonMultiplexedObserver clientNonMultiplexedObserver;
    protected final ReadObserver clientReadObserver;
    protected final WriteObserver clientWriteObserver;

    protected final TransportObserver serverTransportObserver;
    protected final ConnectionObserver serverConnectionObserver;
    protected final SecurityHandshakeObserver serverSecurityHandshakeObserver;
    protected final NonMultiplexedObserver serverNonMultiplexedObserver;
    protected final ReadObserver serverReadObserver;
    protected final WriteObserver serverWriteObserver;

    protected AbstractTransportObserverTest() {
        clientTransportObserver = mock(TransportObserver.class, "clientTransportObserver");
        clientConnectionObserver = mock(ConnectionObserver.class, "clientConnectionObserver");
        clientSecurityHandshakeObserver = mock(SecurityHandshakeObserver.class, "clientSecurityHandshakeObserver");
        clientNonMultiplexedObserver = mock(NonMultiplexedObserver.class, "clientNonMultiplexedObserver");
        clientReadObserver = mock(ReadObserver.class, "clientReadObserver");
        clientWriteObserver = mock(WriteObserver.class, "clientWriteObserver");
        when(clientTransportObserver.onNewConnection()).thenReturn(clientConnectionObserver);
        when(clientConnectionObserver.onSecurityHandshake()).thenReturn(clientSecurityHandshakeObserver);
        when(clientConnectionObserver.established(any(ConnectionInfo.class))).thenReturn(clientNonMultiplexedObserver);
        when(clientNonMultiplexedObserver.onNewRead()).thenReturn(clientReadObserver);
        when(clientNonMultiplexedObserver.onNewWrite()).thenReturn(clientWriteObserver);

        serverTransportObserver = mock(TransportObserver.class, "serverTransportObserver");
        serverConnectionObserver = mock(ConnectionObserver.class, "serverConnectionObserver");
        serverSecurityHandshakeObserver = mock(SecurityHandshakeObserver.class, "serverSecurityHandshakeObserver");
        serverNonMultiplexedObserver = mock(NonMultiplexedObserver.class, "serverNonMultiplexedObserver");
        serverReadObserver = mock(ReadObserver.class, "serverReadObserver");
        serverWriteObserver = mock(WriteObserver.class, "serverWriteObserver");
        when(serverTransportObserver.onNewConnection()).thenReturn(serverConnectionObserver);
        when(serverConnectionObserver.onSecurityHandshake()).thenReturn(serverSecurityHandshakeObserver);
        when(serverConnectionObserver.established(any(ConnectionInfo.class))).thenReturn(serverNonMultiplexedObserver);
        when(serverNonMultiplexedObserver.onNewRead()).thenReturn(serverReadObserver);
        when(serverNonMultiplexedObserver.onNewWrite()).thenReturn(serverWriteObserver);
    }

    @Override
    TcpClientConfig getTcpClientConfig() {
        final TcpClientConfig config = super.getTcpClientConfig();
        config.transportObserver(clientTransportObserver);
        return config;
    }

    static ClientSecurityConfig defaultClientSecurityConfig(SslProvider provider) {
        ClientSecurityConfig config = new ClientSecurityConfig("foo", -1);
        config.disableHostnameVerification();
        config.trustManager(DefaultTestCerts::loadMutualAuthCaPem);
        config.provider(provider);
        return config;
    }

    @Override
    TcpServerConfig getTcpServerConfig() {
        final TcpServerConfig config = super.getTcpServerConfig();
        config.transportObserver(serverTransportObserver);
        return config;
    }

    static ServerSecurityConfig defaultServerSecurityConfig(SslProvider provider) {
        ServerSecurityConfig config = new ServerSecurityConfig();
        config.keyManager(DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey);
        config.provider(provider);
        return config;
    }

    static void verifyWriteObserver(NonMultiplexedObserver nonMultiplexedObserver, WriteObserver writeObserver,
                                    boolean completeExpected) {
        verify(nonMultiplexedObserver).onNewWrite();
        verify(writeObserver).requestedToWrite(anyLong());
        verify(writeObserver).itemReceived();
        verify(writeObserver).onFlushRequest();
        verify(writeObserver).itemWritten();
        if (completeExpected) {
            verify(writeObserver).writeComplete();
        }
    }

    static void verifyReadObserver(NonMultiplexedObserver nonMultiplexedObserver, ReadObserver readObserver) {
        verify(nonMultiplexedObserver).onNewRead();
        verify(readObserver).requestedToRead(anyLong());
        verify(readObserver, atLeastOnce()).itemRead();
    }
}
