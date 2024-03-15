/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.transport.api.ServerSslConfig;
import io.servicetalk.transport.netty.internal.NoopTransportObserver;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

final class TcpServerConfigTest {

    @Test
    void testDefaultsAreApplied() {
        assertDefaults(new TcpServerConfig());
    }

    @Test
    void testSslResetWithoutSni() {
        TcpServerConfig serverConfig = new TcpServerConfig();
        serverConfig.sslConfig(mock(ServerSslConfig.class), true);
        assertTrue(serverConfig.acceptInsecureConnections());
        assertNotNull(serverConfig.sslConfig());

        serverConfig.sslConfig(null);
        assertDefaults(serverConfig);
    }

    @Test
    void testSslResetWithSni() {
        TcpServerConfig serverConfig = new TcpServerConfig();
        ServerSslConfig mockConfig = mock(ServerSslConfig.class);
        serverConfig.sslConfig(mockConfig, Collections.emptyMap(), 1, Duration.ofSeconds(1), true);
        assertTrue(serverConfig.acceptInsecureConnections());
        assertEquals(mockConfig, serverConfig.sslConfig());
        assertEquals(Collections.emptyMap(), serverConfig.sniConfig());
        assertEquals(1, serverConfig.sniMaxClientHelloLength());
        assertEquals(Duration.ofSeconds(1), serverConfig.sniClientHelloTimeout());

        serverConfig.sslConfig(null);
        assertDefaults(serverConfig);
    }

    private static void assertDefaults(final TcpServerConfig serverConfig) {
        assertNull(serverConfig.sslConfig());
        assertNull(serverConfig.sniConfig());
        assertNull(serverConfig.listenOptions());
        assertFalse(serverConfig.acceptInsecureConnections());
        assertEquals(TcpServerConfig.MAX_CLIENT_HELLO_LENGTH, serverConfig.sniMaxClientHelloLength());
        assertEquals(TcpServerConfig.DEFAULT_CLIENT_HELLO_TIMEOUT, serverConfig.sniClientHelloTimeout());
        assertEquals(NoopTransportObserver.INSTANCE, serverConfig.transportObserver());
    }
}
