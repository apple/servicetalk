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
package io.servicetalk.tcp.netty.internal;

import io.servicetalk.transport.api.ClientSslConfig;
import io.servicetalk.transport.api.ClientSslConfigBuilder;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class TcpClientConfigProxyAlpnTest {

    @Test
    void rejectsProxyAlpnWithH2() {
        final ClientSslConfig proxySsl = new ClientSslConfigBuilder()
                .alpnProtocols("h2")
                .build();
        final TcpClientConfig cfg = new TcpClientConfig();
        assertThrows(IllegalArgumentException.class, () -> cfg.proxySslConfig(proxySsl));
    }

    @Test
    void rejectsProxyAlpnContainingH2AlongsideHttp11() {
        final ClientSslConfig proxySsl = new ClientSslConfigBuilder()
                .alpnProtocols("h2", "http/1.1")
                .build();
        final TcpClientConfig cfg = new TcpClientConfig();
        assertThrows(IllegalArgumentException.class, () -> cfg.proxySslConfig(proxySsl));
    }

    @Test
    void allowsProxyAlpnExactlyHttp11() {
        final ClientSslConfig proxySsl = new ClientSslConfigBuilder()
                .alpnProtocols("http/1.1")
                .build();
        final TcpClientConfig cfg = new TcpClientConfig();
        assertDoesNotThrow(() -> cfg.proxySslConfig(proxySsl));
    }

    @Test
    void allowsProxyAlpnUnset() {
        // No ALPN advertised on the proxy stage — proxy negotiates HTTP/1.1 by default; safe.
        final ClientSslConfig proxySsl = new ClientSslConfigBuilder().build();
        final TcpClientConfig cfg = new TcpClientConfig();
        assertDoesNotThrow(() -> cfg.proxySslConfig(proxySsl));
    }

    @Test
    void proxySslWithoutOriginSslProducesValidReadOnlyConfig() {
        // The TCP config layer accepts proxy SSL without origin SSL. The combination is rejected
        // higher up at the HTTP-client layer (HttpClientConfig.asReadOnly) — see
        // HttpsProxyTest / HttpClientConfigTest for that check. This test just locks in that the TCP
        // layer itself doesn't reject the combination, so the rejection lives in one place.
        final TcpClientConfig cfg = new TcpClientConfig();
        cfg.proxySslConfig(new ClientSslConfigBuilder().build());
        final ReadOnlyTcpClientConfig ro = cfg.asReadOnly();
        assertThat(ro.proxySslContext(), is(notNullValue()));
        assertThat(ro.proxySslConfig(), is(notNullValue()));
        assertThat(ro.sslContext(), is(nullValue()));
        assertThat(ro.sslConfig(), is(nullValue()));
    }
}
