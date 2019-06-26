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
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

import static io.servicetalk.transport.api.SslConfig.ClientAuth.NONE;
import static java.util.Objects.requireNonNull;

class BaseClientSslConfigBuilder<B extends BaseClientSslConfigBuilder, F> extends BaseSslConfigBuilder<B, F> {

    private static final String DEFAULT_HOSTNAME_VERIFICATION_ALGORITHM = "HTTPS";

    @Nullable
    private String hostNameVerificationAlgorithm = DEFAULT_HOSTNAME_VERIFICATION_ALGORITHM;
    @Nullable
    private String hostNameVerificationHost;
    /**
     * Only valid if {@link #hostNameVerificationHost} is valid;
     */
    private int hostNameVerificationPort = -1;
    @Nullable
    private String sniHostname;

    private Supplier<InputStream> keyCertChainSupplier = nullSupplier();
    private Supplier<InputStream> keySupplier = nullSupplier();
    @Nullable
    private KeyManagerFactory keyManagerFactory;
    @Nullable
    private String keyPassword;

    BaseClientSslConfigBuilder(final Supplier<F> finisher, final Consumer<SslConfig> configConsumer) {
        super(finisher, configConsumer);
    }

    /**
     * Determines what algorithm to use for hostname verification.
     *
     * @param hostNameVerificationAlgorithm The algorithm to use when verifying the host name.
     * @return the algorithm to use for hostname verification.
     * @see SSLParameters#setEndpointIdentificationAlgorithm(String)
     */
    public B hostNameVerificationAlgorithm(String hostNameVerificationAlgorithm) {
        this.hostNameVerificationAlgorithm = requireNonNull(hostNameVerificationAlgorithm);
        return castAsB();
    }

    /**
     * Set the host name used to verify the <a href="https://tools.ietf.org/search/rfc2818#section-3.1">server
     * identity</a>.
     *
     * @param hostNameVerificationHost the host name used to verify the
     * <a href="https://tools.ietf.org/search/rfc2818#section-3.1">server identity</a>.
     * @return {@code this}
     * @see SslConfig#hostnameVerificationHost()
     */
    public B hostNameVerificationHost(@Nullable String hostNameVerificationHost) {
        this.hostNameVerificationHost = hostNameVerificationHost;
        return castAsB();
    }

    /**
     * The port which maybe used to verify the
     * <a href="https://tools.ietf.org/search/rfc2818#section-3.1">server identity</a>. If the port is used or not
     * determines on the {@link SSLEngine} implementation and protocol.
     *
     * @param hostNameVerificationPort The port which maybe used to verify the
     * <a href="https://tools.ietf.org/search/rfc2818#section-3.1">server identity</a>.
     * @return {@code this}
     * @see SslConfig#hostnameVerificationPort()
     */
    public B hostNameVerificationPort(int hostNameVerificationPort) {
        this.hostNameVerificationPort = hostNameVerificationPort;
        return castAsB();
    }

    /**
     * Set the <a href="https://tools.ietf.org/html/rfc6066#section-3">SNI</a> host name.
     *
     * @param sniHostname The <a href="https://tools.ietf.org/html/rfc6066#section-3">SNI</a> host name.
     * @return {@code this}
     */
    public B sniHostname(@Nullable String sniHostname) {
        this.sniHostname = sniHostname;
        return castAsB();
    }

    /**
     * Disable sending <a href="https://tools.ietf.org/html/rfc6066#section-3">SNI</a>.
     *
     * @return {@code this}
     */
    public B disableSni() {
        sniHostname = null;
        return castAsB();
    }

    /**
     * Disable verification of the <a href="https://tools.ietf.org/search/rfc2818#section-3.1">server identity</a>.
     * <p>
     * Disable at your own risk! Disabling this will leave you vulnerable to MITM attacks.
     * <p>
     *
     * @return {@code this}
     */
    public B disableHostnameVerification() {
        hostNameVerificationHost = null;
        hostNameVerificationPort = -1;
        return castAsB();
    }

    /**
     * Identifying certificate for this host. {@code keyManagerFactory} may
     * be {@code null}, which disables mutual authentication.
     * The {@link KeyManagerFactory} which take preference over any configured {@link Supplier}.
     *
     * @param keyManagerFactory an {@link KeyManagerFactory}.
     * @return self.
     */
    public B keyManager(KeyManagerFactory keyManagerFactory) {
        this.keyCertChainSupplier = nullSupplier();
        this.keySupplier = nullSupplier();
        this.keyPassword = null;
        this.keyManagerFactory = keyManagerFactory;
        return castAsB();
    }

    /**
     * Identifying certificate for this host. {@code keyCertChainInputStream} and {@code keyInputStream} may
     * be {@code null}, which disables mutual authentication.
     *
     * @param keyCertChainSupplier an {@link Supplier} that will provide an input stream for a X.509 certificate chain
     * in PEM format.
     * <p>
     * The responsibility to call {@link InputStream#close()} is transferred to callers of the {@link Supplier}.
     * If this is not the desired behavior then wrap the {@link InputStream} and override {@link InputStream#close()}.
     * @param keySupplier an {@link Supplier} that will provide an input stream for a KCS#8 private key in PEM format.
     * <p>
     * The responsibility to call {@link InputStream#close()} is transferred to callers of the {@link Supplier}.
     * If this is not the desired behavior then wrap the {@link InputStream} and override {@link InputStream#close()}.
     * @return self.
     */
    public B keyManager(Supplier<InputStream> keyCertChainSupplier, Supplier<InputStream> keySupplier) {
        return keyManager(keyCertChainSupplier, keySupplier, null);
    }

    /**
     * Identifying certificate for this host. {@code keyCertChainInputStream} and {@code keyInputStream} may
     * be {@code null}, which disables mutual authentication.
     *
     * @param keyCertChainSupplier an {@link Supplier} that will provide an input stream for a X.509 certificate chain
     * in PEM format.
     * <p>
     * The responsibility to call {@link InputStream#close()} is transferred to callers of the {@link Supplier}.
     * If this is not the desired behavior then wrap the {@link InputStream} and override {@link InputStream#close()}.
     * @param keySupplier an {@link Supplier} that will provide an input stream for a KCS#8 private key in PEM format.
     * <p>
     * The responsibility to call {@link InputStream#close()} is transferred to callers of the {@link Supplier}.
     * If this is not the desired behavior then wrap the {@link InputStream} and override {@link InputStream#close()}.
     * @param keyPassword the password of the {@code keyInputStream}, or {@code null} if it's not
     * password-protected
     * @return self.
     */
    public B keyManager(Supplier<InputStream> keyCertChainSupplier, Supplier<InputStream> keySupplier,
                        @Nullable String keyPassword) {
        keyManagerFactory = null;
        this.keyCertChainSupplier = keyCertChainSupplier;
        this.keySupplier = keySupplier;
        this.keyPassword = keyPassword;
        return castAsB();
    }

    @Override
    SslConfig buildInternal() {
        return new SslConfigImpl(false, trustManagerFactory, trustCertChainSupplier, keyManagerFactory,
                keyCertChainSupplier, keySupplier, keyPassword, ciphers, sessionCacheSize, sessionTimeout, NONE,
                apn, provider, protocols, hostNameVerificationAlgorithm, hostNameVerificationHost,
                hostNameVerificationPort, sniHostname);
    }
}
