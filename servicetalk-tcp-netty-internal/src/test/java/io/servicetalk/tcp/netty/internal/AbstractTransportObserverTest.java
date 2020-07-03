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

import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.TransportObserver;

import org.mockito.Mockito;
import org.mockito.verification.VerificationWithTimeout;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AbstractTransportObserverTest extends AbstractTcpServerTest {

    protected final TransportObserver serverTransportObserver;
    protected final ConnectionObserver serverConnectionObserver;

    public AbstractTransportObserverTest() {
        serverTransportObserver = mock(TransportObserver.class);
        serverConnectionObserver = mock(ConnectionObserver.class);
        when(serverTransportObserver.onNewConnection()).thenReturn(serverConnectionObserver);
    }

    @Override
    final TcpServerConfig getTcpServerConfig() {
        final TcpServerConfig config = super.getTcpServerConfig();
        config.transportObserver(serverTransportObserver);
        return config;
    }

    /**
     * Because the client is just a trigger for server-side events we need to await for invocations to verify them.
     */
    protected static VerificationWithTimeout await() {
        return Mockito.timeout(Long.MAX_VALUE);
    }
}
