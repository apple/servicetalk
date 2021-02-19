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
package io.servicetalk.transport.netty.internal;

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * Read-only security config for clients.
 */
public class ReadOnlyClientSecurityConfig extends ReadOnlySecurityConfig {
    /**
     * See <a href="https://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#jssenames">
     * Endpoint Identification Algorithm Name</a>.
     */
    @Nullable
    protected String hostnameVerificationAlgorithm = "HTTPS";
    protected String peerHost;
    protected int peerPort;
    @Nullable
    protected String sniHostname;

    /**
     * Creates new instance.
     *
     * @param peerHost the non-authoritative name of the peer, will be used for host name verification (if enabled).
     * @param peerPort the non-authoritative port of the peer.
     */
    protected ReadOnlyClientSecurityConfig(final String peerHost, final int peerPort) {
        this.peerHost = requireNonNull(peerHost);
        this.peerPort = peerPort;
    }

    /**
     * Copy constructor.
     *
     * @param from {@link ReadOnlyClientSecurityConfig} to copy.
     */
    protected ReadOnlyClientSecurityConfig(final ReadOnlyClientSecurityConfig from) {
        super(from);
        hostnameVerificationAlgorithm = from.hostnameVerificationAlgorithm;
        peerHost = from.peerHost;
        peerPort = from.peerPort;
        sniHostname = from.sniHostname;
    }

    /**
     * Returns the host name verification algorithm.
     *
     * @return The host name verification algorithm.
     */
    @Nullable
    public String hostnameVerificationAlgorithm() {
        return hostnameVerificationAlgorithm;
    }

    /**
     * Get the non-authoritative name of the peer, will be used for host name verification (if enabled).
     * @return the non-authoritative name of the peer, will be used for host name verification (if enabled).
     */
    public String peerHost() {
        return peerHost;
    }

    /**
     * Get the non-authoritative port of the peer.
     * @return the non-authoritative port of the peer.
     */
    public int peerPort() {
        return peerPort;
    }

    /**
     * Returns the SNI host name.
     *
     * @return The SNI host name.
     */
    @Nullable
    public String sniHostname() {
        return sniHostname;
    }
}
