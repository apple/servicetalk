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

import static java.util.Objects.requireNonNull;

/**
 * A default immutable implementation of {@link HostAndPort}.
 */
final class DefaultHostAndPort implements HostAndPort {
    private final String hostName;
    private final int port;

    /**
     * Create a new instance.
     * @param hostName the host name.
     * @param port the port.
     */
    DefaultHostAndPort(String hostName, int port) {
        this.hostName = requireNonNull(hostName);
        this.port = port;
    }

    @Override
    public String hostName() {
        return hostName;
    }

    @Override
    public int port() {
        return port;
    }

    @Override
    public String toString() {
        return hostName + ':' + port;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DefaultHostAndPort)) {
            return false;
        }
        DefaultHostAndPort rhs = (DefaultHostAndPort) o;
        return port == rhs.port() && hostName.equalsIgnoreCase(rhs.hostName());
    }

    @Override
    public int hashCode() {
        return 31 * (31 + port) + hostName.hashCode();
    }
}
