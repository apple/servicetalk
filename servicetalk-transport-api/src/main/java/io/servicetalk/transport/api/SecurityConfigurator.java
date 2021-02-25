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
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;

/**
 * An abstraction to configure SSL/TLS.
 * @deprecated Use {@link DefaultServerSslConfigBuilder} or {@link DefaultClientSslConfigBuilder}.
 */
@Deprecated
public interface SecurityConfigurator {

    /**
     * The provider to use for {@link SSLEngine}.
     * @deprecated Use {@link io.servicetalk.transport.api.SslProvider}.
     */
    @Deprecated
    enum SslProvider {
        /**
         * Use the stock JDK implementation.
         */
        JDK,
        /**
         * Use the openssl implementation.
         */
        OPENSSL,
        /**
         * Auto detect which implementation to use.
         */
        AUTO
    }

    /**
     * Trusted certificates for verifying the remote endpoint's certificate. The input stream should
     * contain an {@code X.509} certificate chain in {@code PEM} format.
     *
     * @param trustCertChainSupplier a supplier for the certificate chain input stream.
     * <p>
     * The responsibility to call {@link InputStream#close()} is transferred to callers of the returned
     * {@link Supplier}. If this is not the desired behavior then wrap the {@link InputStream} and override
     * {@link InputStream#close()}.
     * @return {@code this}.
     */
    SecurityConfigurator trustManager(Supplier<InputStream> trustCertChainSupplier);

    /**
     * Trust manager for verifying the remote endpoint's certificate.
     * The {@link TrustManagerFactory} which take preference over any configured {@link Supplier}.
     *
     * @param trustManagerFactory the {@link TrustManagerFactory} to use.
     * @return {@code this}.
     */
    SecurityConfigurator trustManager(TrustManagerFactory trustManagerFactory);

    /**
     * The SSL protocols to enable, in the order of preference.
     *
     * @param protocols the protocols to use.
     * @return {@code this}.
     * @see SSLEngine#setEnabledProtocols(String[])
     */
    SecurityConfigurator protocols(String... protocols);

    /**
     * The cipher suites to enable, in the order of preference.
     *
     * @param ciphers the ciphers to use.
     * @return {@code this}.
     */
    SecurityConfigurator ciphers(Iterable<String> ciphers);

    /**
     * Set the size of the cache used for storing SSL session objects.
     *
     * @param sessionCacheSize the cache size.
     * @return {@code this}.
     */
    SecurityConfigurator sessionCacheSize(long sessionCacheSize);

    /**
     * Set the timeout for the cached SSL session objects, in seconds.
     *
     * @param sessionTimeout the session timeout.
     * @return {@code this}.
     */
    SecurityConfigurator sessionTimeout(long sessionTimeout);

    /**
     * Sets the {@link SslProvider} to use.
     *
     * @param provider the provider.
     * @return {@code this}.
     */
    SecurityConfigurator provider(SslProvider provider);
}
