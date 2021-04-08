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

import io.servicetalk.transport.api.ServerSecurityConfigurator.ClientAuth;
import io.servicetalk.transport.api.ServerSslConfig;

import static io.servicetalk.transport.api.ServerSecurityConfigurator.ClientAuth.NONE;

/**
 * Read-only security config for servers.
 * @deprecated Use {@link ServerSslConfig}.
 */
@Deprecated
public class ReadOnlyServerSecurityConfig extends ReadOnlySecurityConfig {

    protected ClientAuth clientAuth = NONE;

    /**
     * Creates new instance.
     */
    protected ReadOnlyServerSecurityConfig() {
    }

    /**
     * Copy constructor.
     *
     * @param from {@link ReadOnlyServerSecurityConfig} to copy.
     */
    protected ReadOnlyServerSecurityConfig(final ReadOnlyServerSecurityConfig from) {
        super(from);
        clientAuth = from.clientAuth;
    }

    /**
     * Returns the {@link ClientAuth} mode.
     * @return The {@link ClientAuth} mode.
     */
    public ClientAuth clientAuth() {
        return clientAuth;
    }
}
