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

import java.io.InputStream;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

import static io.servicetalk.transport.api.SslClientAuthMode.NONE;
import static java.util.Objects.requireNonNull;

/**
 * Default builder for {@link ServerSslConfig} objects.
 */
public final class ServerSslConfigBuilder extends AbstractSslConfigBuilder<ServerSslConfigBuilder> {
    private SslClientAuthMode clientAuthMode = NONE;

    /**
     * Create a new instance using the {@link KeyManagerFactory} for SSL/TLS handshakes.
     *
     * @param kmf the {@link KeyManagerFactory} to use for the SSL/TLS handshakes.
     */
    public ServerSslConfigBuilder(KeyManagerFactory kmf) {
        keyManager(kmf);
    }

    /**
     * Create a new instance from a {@link InputStream} which provides {@code X.509} certificate chain in {@code PEM}
     * format and a {@code PKCS#8} private key in {@code PEM} format.
     * @param keyCertChainSupplier the {@code X.509} certificate chain in {@code PEM} format.
     * <p>
     * Each invocation of the {@link Supplier} should provide an independent instance of {@link InputStream} and the
     * caller is responsible for invoking {@link InputStream#close()}.
     * @param keySupplier a {@link InputStream} which provides a {@code PKCS#8} private key in PEM format associated
     * with.
     * <p>
     * Each invocation of the {@link Supplier} should provide an independent instance of {@link InputStream} and the
     * caller is responsible for invoking {@link InputStream#close()}.
     */
    public ServerSslConfigBuilder(Supplier<InputStream> keyCertChainSupplier,
                                  Supplier<InputStream> keySupplier) {
        keyManager(keyCertChainSupplier, keySupplier);
    }

    /**
     * Create a new instance from a {@link InputStream} which provides {@code X.509} certificate chain in {@code PEM}
     * format and a {@code PKCS#8} private key in {@code PEM} format.
     * @param keyCertChainSupplier the {@code X.509} certificate chain in {@code PEM} format.
     * <p>
     * Each invocation of the {@link Supplier} should provide an independent instance of {@link InputStream} and the
     * caller is responsible for invoking {@link InputStream#close()}.
     * @param keySupplier a {@link InputStream} which provides a {@code PKCS#8} private key in PEM format associated
     * with.
     * <p>
     * Each invocation of the {@link Supplier} should provide an independent instance of {@link InputStream} and the
     * caller is responsible for invoking {@link InputStream#close()}.
     * @param keyPassword the password required to access the key material from {@code keySupplier}.
     */
    public ServerSslConfigBuilder(Supplier<InputStream> keyCertChainSupplier, Supplier<InputStream> keySupplier,
                                  @Nullable String keyPassword) {
        keyManager(keyCertChainSupplier, keySupplier, keyPassword);
    }

    @Override
    public ServerSslConfigBuilder trustManager(TrustManagerFactory tmf) {
        return super.trustManager(tmf);
    }

    @Override
    public ServerSslConfigBuilder trustManager(Supplier<InputStream> trustCertChainSupplier) {
        return super.trustManager(trustCertChainSupplier);
    }

    /**
     * Set the {@link SslClientAuthMode} which determines how client authentication should be done.
     * @param clientAuthMode the {@link SslClientAuthMode} which determines how client authentication should be done.
     * @return {@code this}.
     */
    public ServerSslConfigBuilder clientAuthMode(SslClientAuthMode clientAuthMode) {
        this.clientAuthMode = requireNonNull(clientAuthMode);
        return this;
    }

    /**
     * Build a new {@link ServerSslConfig}.
     * @return a new {@link ServerSslConfig}.
     */
    public ServerSslConfig build() {
        return new DefaultServerSslConfig(clientAuthMode, trustManager(), trustCertChainSupplier(), keyManager(),
                keyCertChainSupplier(), keySupplier(), keyPassword(), sslProtocols(), alpnProtocols(), ciphers(),
                sessionCacheSize(), sessionTimeout(), provider());
    }

    @Override
    protected ServerSslConfigBuilder thisT() {
        return this;
    }

    private static final class DefaultServerSslConfig extends AbstractSslConfig implements ServerSslConfig {
        private final SslClientAuthMode clientAuthMode;

        DefaultServerSslConfig(SslClientAuthMode clientAuthMode,
                               @Nullable final TrustManagerFactory trustManagerFactory,
                               @Nullable final Supplier<InputStream> trustCertChainSupplier,
                               @Nullable final KeyManagerFactory keyManagerFactory,
                               @Nullable final Supplier<InputStream> keyCertChainSupplier,
                               @Nullable final Supplier<InputStream> keySupplier, @Nullable final String keyPassword,
                               @Nullable final List<String> sslProtocols, @Nullable final List<String> alpnProtocols,
                               @Nullable final List<String> ciphers, final long sessionCacheSize,
                               final long sessionTimeout, @Nullable final SslProvider provider) {
            super(trustManagerFactory, trustCertChainSupplier, keyManagerFactory, keyCertChainSupplier, keySupplier,
                    keyPassword, sslProtocols, alpnProtocols, ciphers, sessionCacheSize, sessionTimeout, provider);
            this.clientAuthMode = clientAuthMode;
        }

        @Override
        public SslClientAuthMode clientAuthMode() {
            return clientAuthMode;
        }
    }
}
