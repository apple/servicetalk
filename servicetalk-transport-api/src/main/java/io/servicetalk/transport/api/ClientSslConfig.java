/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.transport.api;

import java.util.List;
import javax.annotation.Nullable;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

/**
 * Specifies the configuration for client side TLS/SSL.
 * @see ClientSslConfigBuilder
 */
public interface ClientSslConfig extends SslConfig {
    /**
     * Get the algorithm to use for hostname verification to verify the
     * <a href="https://tools.ietf.org/search/rfc2818#section-3.1">server identity</a>.
     *
     * @return The algorithm to use when verifying the host name.
     * See <a href="https://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#jssenames">
     * Endpoint Identification Algorithm Name</a>.
     * @see SSLParameters#setEndpointIdentificationAlgorithm(String)
     */
    @Nullable
    String hostnameVerificationAlgorithm();

    /**
     * Get the non-authoritative name of the peer.
     * @return the non-authoritative name of the peer, or {@code null} if unavailable (which may disable
     * {@link #hostnameVerificationAlgorithm() hostname verification} and
     * <a href="https://tools.ietf.org/html/rfc5077">session resumption</a>).
     * @see SSLEngine#getPeerHost()
     */
    @Nullable
    String peerHost();

    /**
     * Get the non-authoritative port of the peer.
     * @return the non-authoritative port of the peer, or {@code -1} if unavailable (which may prevent
     * <a href="https://tools.ietf.org/html/rfc5077">session resumption</a>).
     * @see SSLEngine#getPeerPort()
     */
    int peerPort();

    /**
     * Get the <a href="https://tools.ietf.org/html/rfc6066#section-3">SNI</a> host name.
     *
     * @return The <a href="https://tools.ietf.org/html/rfc6066#section-3">SNI</a> host name, or {@code null} to not
     * enable SNI.
     * @see SSLParameters#setServerNames(List) 
     */
    @Nullable
    String sniHostname();
}
