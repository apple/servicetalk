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
package io.servicetalk.http.netty;

import io.servicetalk.http.api.ProxyConfigBuilder;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.HostAndPort;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies that "proxy SSL + no origin SSL" is rejected at HTTP-client build time. With origin SSL unset
 * the client would otherwise route through forward-proxy mode, where the proxy decrypts the TLS-encrypted
 * client hop and forwards plaintext to the origin — the path from proxy to origin is no longer under client
 * control. Rejecting the combination explicitly removes a security foot-gun; a dedicated API for the
 * "TLS to forward proxy" deployment can be designed separately.
 */
class ProxySslWithoutOriginSslRejectionTest {

    @Test
    void buildClientWithProxySslButNoOriginSslThrows() {
        final IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> HttpClients.forSingleAddress(HostAndPort.of("example.com", 443))
                        .proxyConfig(new ProxyConfigBuilder<>(HostAndPort.of("proxy.example.com", 8443))
                                .sslConfig(new ClientSslConfigBuilder().build())
                                .build())
                        // NB: no .sslConfig(...) on the client builder — this is the rejected case.
                        .buildBlocking());
        assertThat(ex.getMessage(), containsString("Proxy SSL is configured but origin SSL is not"));
    }
}
