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
import io.servicetalk.transport.api.ClientSslConfig;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.ConnectionInfo;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.ConnectionObserver.DataObserver;
import io.servicetalk.transport.api.ConnectionObserver.ReadObserver;
import io.servicetalk.transport.api.ConnectionObserver.SecurityHandshakeObserver;
import io.servicetalk.transport.api.ConnectionObserver.WriteObserver;
import io.servicetalk.transport.api.ServerSslConfig;
import io.servicetalk.transport.api.ServerSslConfigBuilder;
import io.servicetalk.transport.api.SslProvider;
import io.servicetalk.transport.api.TransportObserver;

import org.mockito.Mockito;
import org.mockito.verification.VerificationWithTimeout;

import java.io.InputStream;
import java.util.function.Supplier;

import static io.servicetalk.test.resources.DefaultTestCerts.serverPemHostname;
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
    protected final DataObserver clientDataObserver;
    protected final ReadObserver clientReadObserver;
    protected final WriteObserver clientWriteObserver;

    protected final TransportObserver serverTransportObserver;
    protected final ConnectionObserver serverConnectionObserver;
    protected final SecurityHandshakeObserver serverSecurityHandshakeObserver;
    protected final DataObserver serverDataObserver;
    protected final ReadObserver serverReadObserver;
    protected final WriteObserver serverWriteObserver;

    protected AbstractTransportObserverTest() {
        clientTransportObserver = mock(TransportObserver.class, "clientTransportObserver");
        clientConnectionObserver = mock(ConnectionObserver.class, "clientConnectionObserver");
        clientSecurityHandshakeObserver = mock(SecurityHandshakeObserver.class, "clientSecurityHandshakeObserver");
        clientDataObserver = mock(DataObserver.class, "clientDataObserver");
        clientReadObserver = mock(ReadObserver.class, "clientReadObserver");
        clientWriteObserver = mock(WriteObserver.class, "clientWriteObserver");
        when(clientTransportObserver.onNewConnection(any(), any())).thenReturn(clientConnectionObserver);
        when(clientConnectionObserver.onSecurityHandshake()).thenReturn(clientSecurityHandshakeObserver);
        when(clientConnectionObserver.connectionEstablished(any(ConnectionInfo.class))).thenReturn(clientDataObserver);
        when(clientDataObserver.onNewRead()).thenReturn(clientReadObserver);
        when(clientDataObserver.onNewWrite()).thenReturn(clientWriteObserver);

        serverTransportObserver = mock(TransportObserver.class, "serverTransportObserver");
        serverConnectionObserver = mock(ConnectionObserver.class, "serverConnectionObserver");
        serverSecurityHandshakeObserver = mock(SecurityHandshakeObserver.class, "serverSecurityHandshakeObserver");
        serverDataObserver = mock(DataObserver.class, "serverDataObserver");
        serverReadObserver = mock(ReadObserver.class, "serverReadObserver");
        serverWriteObserver = mock(WriteObserver.class, "serverWriteObserver");
        when(serverTransportObserver.onNewConnection(any(), any())).thenReturn(serverConnectionObserver);
        when(serverConnectionObserver.onSecurityHandshake()).thenReturn(serverSecurityHandshakeObserver);
        when(serverConnectionObserver.connectionEstablished(any(ConnectionInfo.class))).thenReturn(serverDataObserver);
        when(serverDataObserver.onNewRead()).thenReturn(serverReadObserver);
        when(serverDataObserver.onNewWrite()).thenReturn(serverWriteObserver);
    }

    @Override
    TransportObserver getClientTransportObserver() {
        return clientTransportObserver;
    }

    static ClientSslConfigBuilder defaultClientSslBuilder(SslProvider provider) {
        return defaultClientSslBuilder(provider, DefaultTestCerts::loadServerCAPem);
    }

    static ClientSslConfigBuilder defaultClientSslBuilder(SslProvider provider,
                                                          Supplier<InputStream> trustCertSupplier) {
        return new ClientSslConfigBuilder(trustCertSupplier)
                .peerHost(serverPemHostname())
                .peerPort(-1)
                .provider(provider)
                .sslProtocols("TLSv1.2");
    }

    static ClientSslConfig defaultClientSslConfig(SslProvider provider) {
        return defaultClientSslBuilder(provider).build();
    }

    @Override
    TcpServerConfig getTcpServerConfig() {
        final TcpServerConfig config = super.getTcpServerConfig();
        config.transportObserver(serverTransportObserver);
        return config;
    }

    static ServerSslConfigBuilder defaultServerSslBuilder(SslProvider provider) {
        return new ServerSslConfigBuilder(DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey)
                .provider(provider)
                .sslProtocols("TLSv1.2");
    }

    static ServerSslConfig defaultServerSslConfig(SslProvider provider) {
        return defaultServerSslBuilder(provider).build();
    }

    static void verifyWriteObserver(DataObserver dataObserver, WriteObserver writeObserver,
                                    boolean completeExpected) {
        verify(dataObserver).onNewWrite();
        verify(writeObserver, atLeastOnce()).requestedToWrite(anyLong());
        verify(writeObserver).itemReceived();
        verify(writeObserver).onFlushRequest();
        verify(writeObserver).itemWritten();
        if (completeExpected) {
            verify(writeObserver).writeComplete();
        }
    }

    static void verifyReadObserver(DataObserver dataObserver, ReadObserver readObserver) {
        verify(dataObserver).onNewRead();
        verify(readObserver).requestedToRead(anyLong());
        verify(readObserver, atLeastOnce()).itemRead();
    }

    static VerificationWithTimeout await() {
        return Mockito.timeout(Long.MAX_VALUE);
    }
}
