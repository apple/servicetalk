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
package io.servicetalk.http.api;

import io.servicetalk.transport.api.ClientSslConfig;
import io.servicetalk.transport.api.ClientSslConfigBuilder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ProxyConfigBuilderTest {

    @Test
    void rejectsProxyAlpnWithH2() {
        final ClientSslConfig proxySsl = new ClientSslConfigBuilder()
                .alpnProtocols("h2")
                .build();
        final ProxyConfigBuilder<String> builder = new ProxyConfigBuilder<>("proxy:8080");
        assertThrows(IllegalArgumentException.class, () -> builder.sslConfig(proxySsl));
    }

    @Test
    void rejectsProxyAlpnContainingH2AlongsideHttp11() {
        final ClientSslConfig proxySsl = new ClientSslConfigBuilder()
                .alpnProtocols("h2", "http/1.1")
                .build();
        final ProxyConfigBuilder<String> builder = new ProxyConfigBuilder<>("proxy:8080");
        assertThrows(IllegalArgumentException.class, () -> builder.sslConfig(proxySsl));
    }

    @Test
    void allowsProxyAlpnExactlyHttp11() {
        final ClientSslConfig proxySsl = new ClientSslConfigBuilder()
                .alpnProtocols("http/1.1")
                .build();
        final ProxyConfigBuilder<String> builder = new ProxyConfigBuilder<>("proxy:8080");
        assertDoesNotThrow(() -> builder.sslConfig(proxySsl));
    }

    @Test
    void allowsProxyAlpnUnset() {
        final ClientSslConfig proxySsl = new ClientSslConfigBuilder().build();
        final ProxyConfigBuilder<String> builder = new ProxyConfigBuilder<>("proxy:8080");
        assertDoesNotThrow(() -> builder.sslConfig(proxySsl));
    }
}
