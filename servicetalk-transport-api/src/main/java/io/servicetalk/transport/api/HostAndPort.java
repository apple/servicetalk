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

import java.net.InetSocketAddress;

/**
 * A tuple of {@code <host name, port>}.
 */
public interface HostAndPort {
    /**
     * Returns the host name.
     *
     * @return The hostname
     */
    String hostName();

    /**
     * Returns the port.
     *
     * @return The port
     */
    int port();

    /**
     * Returns a {@link HostAndPort} object for the specified values.
     *
     * @param host host name
     * @param port port
     * @return the {@link HostAndPort}
     */
    static HostAndPort of(String host, int port) {
        return new DefaultHostAndPort(host, port);
    }

    /**
     * Create a new {@link HostAndPort} from a {@link InetSocketAddress}.
     * <p>
     * Note that creation of a {@link InetSocketAddress} may use the JDK's blocking DNS resolution. Take care to only
     * create these objects if you intend to use the JDK's blocking DNS resolution, and you are safe to block.
     * @param address The {@link InetSocketAddress} to convert.
     * @return the {@link HostAndPort}.
     */
    static HostAndPort of(InetSocketAddress address) {
        return new DefaultHostAndPort(address.getHostString(), address.getPort());
    }
}
