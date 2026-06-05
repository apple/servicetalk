/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.transport.netty.internal;

import io.servicetalk.transport.api.ConnectionInfo;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.ConnectionObserver.SecurityHandshakeObserver;
import io.servicetalk.transport.api.SslConfig;
import io.servicetalk.transport.netty.internal.ConnectionObserverInitializer.ConnectionObserverHandler;

import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the constructor-time observer reporting behavior of {@link ConnectionObserverHandler} under TCP fast-open.
 */
final class ConnectionObserverInitializerTest {

    @Test
    void fastOpenWithApplicationTlsReportsApplicationHandshake() {
        // TcpFastOpenTest covers fast-open + direct TLS at the request level but doesn't verify which observer
        // callback fires; this locks in onSecurityHandshake (not onProxySecurityHandshake).
        final ConnectionObserver observer = mock(ConnectionObserver.class);
        final SslConfig sslConfig = mock(SslConfig.class);
        when(observer.onSecurityHandshake(sslConfig)).thenReturn(mock(SecurityHandshakeObserver.class));

        new ConnectionObserverHandler(observer, ch -> mock(ConnectionInfo.class),
                true /* fastOpen */, sslConfig, null);

        verify(observer).onSecurityHandshake(sslConfig);
        verify(observer, never()).onProxySecurityHandshake(any(SslConfig.class));
    }

    @Test
    void fastOpenWithProxyTlsReportsProxyHandshake() {
        // HttpsProxyTest.testFastOpenWithProxyTlsReportsProxySecurityHandshake verifies onProxySecurityHandshake
        // fires eventually, but it can't tell whether it fired at construction time (correct, ClientHello in the
        // SYN) or later from channelActive (the pre-fix bug). This pins the constructor-time call — the proxy
        // ClientHello piggybacks the SYN; the application handshake doesn't start until after CONNECT.
        final ConnectionObserver observer = mock(ConnectionObserver.class);
        final SslConfig proxySslConfig = mock(SslConfig.class);
        when(observer.onProxySecurityHandshake(proxySslConfig)).thenReturn(mock(SecurityHandshakeObserver.class));

        new ConnectionObserverHandler(observer, ch -> mock(ConnectionInfo.class),
                true /* fastOpen */, null /* sslConfig deferred */, proxySslConfig);

        verify(observer).onProxySecurityHandshake(proxySslConfig);
        verify(observer, never()).onSecurityHandshake(any(SslConfig.class));
    }
}
