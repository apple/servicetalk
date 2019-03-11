/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

/**
 * Configuration for SSL/TLS.
 */
public interface SslConfig {

    /**
     * Indicates the state of the {@link SSLEngine} with respect to client authentication.
     * This configuration item really only applies when building the server-side {@link SslConfig}.
     */
    enum ClientAuth {
        /**
         * Indicates that the {@link SSLEngine} will not request client authentication.
         */
        NONE,

        /**
         * Indicates that the {@link SSLEngine} will request client authentication.
         */
        OPTIONAL,

        /**
         * Indicates that the {@link SSLEngine} will *require* client authentication.
         */
        REQUIRE
    }

    /**
     * The provider to use for {@link SSLEngine}.
     */
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
     * Return {@code true} if config is for server.
     *
     * @return {@code true} if config is for server.
     */
    boolean isServer();

    /**
     * Return the {@link TrustManagerFactory} to use or {@code null} if the default should be used.
     *
     * @return the factory to use.
     */
    @Nullable
    TrustManagerFactory trustManagerFactory();

    /**
     * Return the {@link KeyManagerFactory} to use or {@code null} if none should be used.
     *
     * @return the factory to use.
     */
    @Nullable
    KeyManagerFactory keyManagerFactory();

    /**
     * Return the password to use or {@code null} if none.
     *
     * @return the password to use or {@code null} if none.
     */
    @Nullable
    String keyPassword();

    /**
     * Return the ciphers or {@code null} if the default should be used.
     *
     * @return the ciphers.
     */
    @Nullable
    Iterable<String> ciphers();

    /**
     * Get the size of the cache used for storing SSL session objects.
     *
     * @return cache size.
     */
    long sessionCacheSize();

    /**
     * Get the timeout for the cached SSL session objects, in seconds.
     *
     * @return the timeout.
     */
    long sessionTimeout();

    /**
     * Return the configured {@link ClientAuth}.
     *
     * @return auth.
     */
    ClientAuth clientAuth();

    /**
     * Return the supplier for the trust cert chain.
     *
     * @return supplier.
     */
    Supplier<InputStream> trustCertChainSupplier();

    /**
     * Return the supplier for the key cert chain.
     * <p>
     * The responsibility to call {@link InputStream#close()} is transferred to callers of the returned
     * {@link Supplier}. If this is not the desired behavior then wrap the {@link InputStream} and override
     * {@link InputStream#close()}.
     *
     * @return supplier.
     */
    Supplier<InputStream> keyCertChainSupplier();

    /**
     * Return the supplier for private key.
     * <p>
     * The responsibility to call {@link InputStream#close()} is transferred to callers of the returned
     * {@link Supplier}. If this is not the desired behavior then wrap the {@link InputStream} and override
     * {@link InputStream#close()}.
     *
     * @return supplier.
     */
    Supplier<InputStream> keySupplier();

    /**
     * Return the config to use.
     *
     * @return config.
     */
    ApplicationProtocolConfig apn();

    /**
     * Return the provider to use.
     *
     * @return the provider.
     */
    SslProvider provider();

    /**
     * Returns the protocols to enable, in the order of preference. {@code null} to use default protocols.
     *
     * @return protocols the protocols to use.
     */
    @Nullable
    List<String> protocols();

    /**
     * Determines what algorithm to use for hostname verification using the
     * <a href="https://tools.ietf.org/search/rfc2818#section-3.1">server identity</a>.
     * @return the algorithm to use for hostname verification.
     * @see SSLParameters#setEndpointIdentificationAlgorithm(String)
     */
    @Nullable
    String hostnameVerificationAlgorithm();

    /**
     * The host name used to verify the <a href="https://tools.ietf.org/search/rfc2818#section-3.1">server identity</a>.
     * @return The host name used to verify the
     * <a href="https://tools.ietf.org/search/rfc2818#section-3.1">server identity</a>.
     */
    @Nullable
    String hostnameVerificationHost();

    /**
     * The port which maybe used to verify the
     * <a href="https://tools.ietf.org/search/rfc2818#section-3.1">server identity</a>. If the port is used or not
     * determines on the {@link SSLEngine} implementation and protocol.
     * @return The port which maybe used to verify the
     * <a href="https://tools.ietf.org/search/rfc2818#section-3.1">server identity</a>.
     */
    int hostnameVerificationPort();
}
