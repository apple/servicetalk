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
 * @deprecated Use {@link ClientSslConfigBuilder}.
 */
@Deprecated
public interface ClientSecurityConfigurator extends SecurityConfigurator {
    @Override
    ClientSecurityConfigurator trustManager(Supplier<InputStream> trustCertChainSupplier);

    @Override
    ClientSecurityConfigurator trustManager(TrustManagerFactory trustManagerFactory);

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
     * Supported algorithm names</a>.
     * @return {@code this}.
     * @see SSLParameters#setEndpointIdentificationAlgorithm(String)
     */
    ClientSecurityConfigurator hostnameVerificationAlgorithm(String hostNameVerificationAlgorithm);

    /**
     * Determines what algorithm to use for hostname verification.
     *
     * @param hostNameVerificationAlgorithm The algorithm to use when verifying the host name.
     * See <a href="https://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#jssenames">
     * Supported algorithm names</a>.
     * @param hostNameVerificationHost the host name used to verify the
     * <a href="https://tools.ietf.org/search/rfc2818#section-3.1">server identity</a>.
     * @return {@code this}.
     * @see SSLParameters#setEndpointIdentificationAlgorithm(String)
     */
    ClientSecurityConfigurator hostnameVerification(String hostNameVerificationAlgorithm,
                                                    String hostNameVerificationHost);

    /**
     * Determines what algorithm to use for hostname verification.
     *
     * @param hostNameVerificationAlgorithm The algorithm to use when verifying the host name.
     * See <a href="https://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#jssenames">
     * Supported algorithm names</a>.
     * @param hostNameVerificationHost the host name used to verify the
     * <a href="https://tools.ietf.org/search/rfc2818#section-3.1">server identity</a>.
     * @param hostNameVerificationPort The port which maybe used to verify the
     * <a href="https://tools.ietf.org/search/rfc2818#section-3.1">server identity</a>.
     * @return {@code this}.
     * @see SSLParameters#setEndpointIdentificationAlgorithm(String)
     */
    ClientSecurityConfigurator hostnameVerification(String hostNameVerificationAlgorithm,
                                                    String hostNameVerificationHost, int hostNameVerificationPort);

    /**
     * Set the host name used to verify the <a href="https://tools.ietf.org/search/rfc2818#section-3.1">server
     * identity</a>.
     *
     * @param hostNameVerificationHost the host name used to verify the
     * <a href="https://tools.ietf.org/search/rfc2818#section-3.1">server identity</a>.
     * @return {@code this}.
     */
    ClientSecurityConfigurator hostnameVerification(String hostNameVerificationHost);

    /**
     * Set the host name and port used to verify the <a href="https://tools.ietf.org/search/rfc2818#section-3.1">server
     * identity</a>.
     *
     * @param hostNameVerificationHost the host name used to verify the
     * <a href="https://tools.ietf.org/search/rfc2818#section-3.1">server identity</a>.
     * @param hostNameVerificationPort The port which maybe used to verify the
     * <a href="https://tools.ietf.org/search/rfc2818#section-3.1">server identity</a>.
     * @return {@code this}.
     * @see SSLParameters#setEndpointIdentificationAlgorithm(String)
     */
    ClientSecurityConfigurator hostnameVerification(String hostNameVerificationHost, int hostNameVerificationPort);

    /**
     * Set the <a href="https://tools.ietf.org/html/rfc6066#section-3">SNI</a> host name.
     *
     * @param sniHostname The <a href="https://tools.ietf.org/html/rfc6066#section-3">SNI</a> host name.
     * @return {@code this}.
     */
    ClientSecurityConfigurator sniHostname(String sniHostname);

    /**
     * Disable verification of the <a href="https://tools.ietf.org/search/rfc2818#section-3.1">server identity</a>.
     *
     * @return {@code this}.
     */
    ClientSecurityConfigurator disableHostnameVerification();

    /**
     * Identifying certificate for this host. {@code keyManagerFactory} may be {@code null}, which disables mutual
     * authentication. The {@link KeyManagerFactory} which take preference over any configured {@link Supplier}.
     *
     * @param keyManagerFactory an {@link KeyManagerFactory}.
     * @return {@code this}.
     */
    ClientSecurityConfigurator keyManager(KeyManagerFactory keyManagerFactory);

    /**
     * Identifying certificate for this host. {@code keyCertChainInputStream} and {@code keyInputStream} may
     * be {@code null}, which disables mutual authentication.
     *
     * @param keyCertChainSupplier a {@link Supplier} that will provide an input stream for a {@code X.509} certificate
     * chain in {@code PEM} format.
     * <p>
     * The responsibility to call {@link InputStream#close()} is transferred to callers of the {@link Supplier}.
     * If this is not the desired behavior then wrap the {@link InputStream} and override {@link InputStream#close()}.
     * @param keySupplier an {@link Supplier} that will provide an input stream for a KCS#8 private key in PEM format.
     * <p>
     * The responsibility to call {@link InputStream#close()} is transferred to callers of the {@link Supplier}.
     * If this is not the desired behavior then wrap the {@link InputStream} and override {@link InputStream#close()}.
     * @return {@code this}.
     */
    ClientSecurityConfigurator keyManager(Supplier<InputStream> keyCertChainSupplier,
                                          Supplier<InputStream> keySupplier);

    /**
     * Identifying certificate for this host. {@code keyCertChainInputStream} and {@code keyInputStream} may
     * be {@code null}, which disables mutual authentication.
     *
     * @param keyCertChainSupplier an {@link Supplier} that will provide an input stream for a {@code X.509} certificate
     * chain in {@code PEM} format.
     * <p>
     * The responsibility to call {@link InputStream#close()} is transferred to callers of the {@link Supplier}.
     * If this is not the desired behavior then wrap the {@link InputStream} and override {@link InputStream#close()}.
     * @param keySupplier an {@link Supplier} that will provide an input stream for a KCS#8 private key in PEM format.
     * <p>
     * The responsibility to call {@link InputStream#close()} is transferred to callers of the {@link Supplier}.
     * If this is not the desired behavior then wrap the {@link InputStream} and override {@link InputStream#close()}.
     * @param keyPassword the password of the {@code keyInputStream}.
     * @return {@code this}.
     */
    ClientSecurityConfigurator keyManager(Supplier<InputStream> keyCertChainSupplier, Supplier<InputStream> keySupplier,
                                          String keyPassword);
}
