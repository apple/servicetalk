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
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.net.ssl.TrustManagerFactory;

import static java.util.Objects.requireNonNull;

abstract class BaseSslConfigBuilder<B extends BaseSslConfigBuilder, F> {

    private final Supplier<F> finisher;
    private final Consumer<SslConfig> configConsumer;

    @SuppressWarnings("rawtypes")
    static final Supplier NULL_SUPPLIER = () -> null;

    @Nullable
    TrustManagerFactory trustManagerFactory;
    @Nullable
    Iterable<String> ciphers;
    long sessionCacheSize;
    long sessionTimeout;
    Supplier<InputStream> trustCertChainSupplier = nullSupplier();
    ApplicationProtocolConfig apn = ApplicationProtocolConfig.DISABLED;
    SslConfig.SslProvider provider = SslConfig.SslProvider.AUTO;
    @Nullable
    List<String> protocols;

    BaseSslConfigBuilder(final Supplier<F> finisher, final Consumer<SslConfig> configConsumer) {
        this.finisher = finisher;
        this.configConsumer = configConsumer;
    }

    /**
     * Trusted certificates for verifying the remote endpoint's certificate. The input stream should
     * contain an X.509 certificate chain in PEM format. {@code null} uses the system default.
     *
     * @param trustCertChainSupplier a supplier for the certificate chain input stream.
     * <p>
     * The responsibility to call {@link InputStream#close()} is transferred to callers of the returned
     * {@link Supplier}. If this is not the desired behavior then wrap the {@link InputStream} and override
     * {@link InputStream#close()}.
     * @return self.
     */
    public B trustManager(Supplier<InputStream> trustCertChainSupplier) {
        this.trustCertChainSupplier = trustCertChainSupplier;
        trustManagerFactory = null;
        return castAsB();
    }

    /**
     * Trusted manager for verifying the remote endpoint's certificate. {@code null} uses the system default.
     * The {@link TrustManagerFactory} which take preference over any configured {@link Supplier}.
     *
     * @param trustManagerFactory the {@link TrustManagerFactory} to use.
     * @return self.
     */
    public B trustManager(TrustManagerFactory trustManagerFactory) {
        trustCertChainSupplier = nullSupplier();
        this.trustManagerFactory = trustManagerFactory;
        return castAsB();
    }

    /**
     * The protocols to enable, in the order of preference. {@code null} to use default protocols.
     *
     * @param protocols the protocols to use.
     * @return self.
     */
    public B protocols(@Nullable String... protocols) {
        if (protocols == null) {
            this.protocols = null;
        } else if (protocols.length == 0) {
            throw new IllegalArgumentException("protocols must contain at least one element");
        } else {
            this.protocols = Arrays.asList(protocols);
        }
        return castAsB();
    }

    /**
     * Application protocol negotiation configuration.
     *
     * @param apn the configuration to use.
     * @return self.
     */
    public B applicationProtocolConfig(ApplicationProtocolConfig apn) {
        this.apn = apn;
        return castAsB();
    }

    /**
     * The cipher suites to enable, in the order of preference. {@code null} to use default
     * cipher suites.
     *
     * @param ciphers the ciphers to use.
     * @return self.
     */
    public B ciphers(@Nullable Iterable<String> ciphers) {
        this.ciphers = ciphers;
        return castAsB();
    }

    /**
     * Set the size of the cache used for storing SSL session objects. {@code 0} to use the
     * default value.
     *
     * @param sessionCacheSize the cache size.
     * @return self.
     */
    public B sessionCacheSize(long sessionCacheSize) {
        this.sessionCacheSize = sessionCacheSize;
        return castAsB();
    }

    /**
     * Set the timeout for the cached SSL session objects, in seconds. {@code 0} to use the
     * default value.
     *
     * @param sessionTimeout the session timeout.
     * @return self.
     */
    public B sessionTimeout(long sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
        return castAsB();
    }

    /**
     * Sets the {@link SslConfig.SslProvider} to use.
     *
     * @param provider the provider.
     * @return self.
     */
    public B provider(SslConfig.SslProvider provider) {
        this.provider = requireNonNull(provider);
        return castAsB();
    }

    final F finishInternal() {
        configConsumer.accept(buildInternal());
        return finisher.get();
    }

    abstract SslConfig buildInternal();

    @SuppressWarnings("unchecked")
    B castAsB() {
        return (B) this;
    }

    @SuppressWarnings("unchecked")
    static <T> Supplier<T> nullSupplier() {
        return NULL_SUPPLIER;
    }
}
