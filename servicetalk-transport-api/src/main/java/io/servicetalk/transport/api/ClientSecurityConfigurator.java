/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import java.io.InputStream;
import java.util.function.Supplier;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

/**
 * A {@link SecurityConfigurator} contract for clients.
 */
public interface ClientSecurityConfigurator extends SecurityConfigurator {
    @Override
    ClientSecurityConfigurator trustManager(Supplier<InputStream> trustCertChainSupplier);

    @Override
    ClientSecurityConfigurator trustManager(TrustManagerFactory trustManagerFactory);

    @Override
    ClientSecurityConfigurator keyManager(KeyManagerFactory keyManagerFactory);

    @Override
    ClientSecurityConfigurator keyManager(Supplier<InputStream> keyCertChainSupplier,
                                          Supplier<InputStream> keySupplier);

    @Override
    ClientSecurityConfigurator keyManager(Supplier<InputStream> keyCertChainSupplier, Supplier<InputStream> keySupplier,
                                          String keyPassword);

    @Override
    ClientSecurityConfigurator protocols(String... protocols);

    @Override
    ClientSecurityConfigurator ciphers(Iterable<String> ciphers);

    @Override
    ClientSecurityConfigurator sessionCacheSize(long sessionCacheSize);

    @Override
    ClientSecurityConfigurator sessionTimeout(long sessionTimeout);

    @Override
    ClientSecurityConfigurator provider(SslProvider provider);

    /**
     * Determines what algorithm to use for hostname verification.
     *
     * @param hostNameVerificationAlgorithm The algorithm to use when verifying the host name.
     * See <a href="https://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#jssenames">
     * Endpoint Identification Algorithm Name</a>.
     * @return {@code this}.
     * @see SSLParameters#setEndpointIdentificationAlgorithm(String)
     */
    ClientSecurityConfigurator hostnameVerificationAlgorithm(String hostNameVerificationAlgorithm);

    /**
     * Disable verification of the <a href="https://tools.ietf.org/search/rfc2818#section-3.1">server identity</a>.
     *
     * @return {@code this}.
     */
    ClientSecurityConfigurator disableHostnameVerification();

    /**
     * Set the non-authoritative name of the peer, will be used for host name verification (if enabled).
     * @param peerHost the non-authoritative name of the peer, will be used for host name verification (if enabled).
     * @return {@code this}.
     */
    ClientSecurityConfigurator peerHost(String peerHost);

    /**
     * Set the non-authoritative port of the peer.
     * @param peerPort the non-authoritative port of the peer.
     * @return {@code this}.
     */
    ClientSecurityConfigurator peerPort(int peerPort);

    /**
     * Set the <a href="https://tools.ietf.org/html/rfc6066#section-3">SNI</a> host name.
     *
     * @param sniHostname The <a href="https://tools.ietf.org/html/rfc6066#section-3">SNI</a> host name.
     * @return {@code this}.
     */
    ClientSecurityConfigurator sniHostname(String sniHostname);
}
