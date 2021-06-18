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

import io.servicetalk.transport.api.ClientSslConfig;

import javax.annotation.Nullable;

/**
 * Read-only security config for clients.
 * @deprecated Use {@link ClientSslConfig}.
 */
@Deprecated
public class ReadOnlyClientSecurityConfig extends ReadOnlySecurityConfig {
    @Nullable
    protected String hostnameVerificationAlgorithm = "HTTPS";
    @Nullable
    protected String hostNameVerificationHost;
    /**
     * Only valid if {@link #hostNameVerificationHost} is valid.
     */
    protected int hostNameVerificationPort;
    @Nullable
    protected String sniHostname;

    protected ReadOnlyClientSecurityConfig() {
    }

    /**
     * Copy constructor.
     *
     * @param from {@link ReadOnlyClientSecurityConfig} to copy.
     */
    protected ReadOnlyClientSecurityConfig(final ReadOnlyClientSecurityConfig from) {
        super(from);
        hostnameVerificationAlgorithm = from.hostnameVerificationAlgorithm;
        hostNameVerificationHost = from.hostNameVerificationHost;
        hostNameVerificationPort = from.hostNameVerificationPort;
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
     * Returns the host name verification host.
     *
     * @return The host name verification host.
     */
    @Nullable
    public String hostnameVerificationHost() {
        return hostNameVerificationHost;
    }

    /**
     * Returns the host name verification port.
     *
     * @return The host name verification port.
     */
    public int hostnameVerificationPort() {
        return hostNameVerificationPort;
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
