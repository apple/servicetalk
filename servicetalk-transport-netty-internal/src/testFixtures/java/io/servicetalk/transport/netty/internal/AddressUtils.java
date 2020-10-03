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

import io.servicetalk.transport.api.DomainSocketAddress;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import static io.netty.util.NetUtil.isValidIpV6Address;
import static java.net.InetAddress.getLoopbackAddress;
import static org.junit.Assert.assertTrue;

/**
 * A utility class to work with addresses.
 */
public final class AddressUtils {

    private AddressUtils() {
        // No instances
    }

    /**
     * Creates an {@link InetSocketAddress} with {@link InetAddress#getLoopbackAddress() loopback address} and specified
     * port number.
     *
     * @param port the port number
     * @return an {@link InetSocketAddress} with {@link InetAddress#getLoopbackAddress() loopback address} and specified
     * port number
     */
    public static InetSocketAddress localAddress(final int port) {
        return new InetSocketAddress(getLoopbackAddress(), port);
    }

    /**
     * Returns a {@link HostAndPort} representation of server's listening address.
     *
     * @param ctx The {@link ServerContext} of the server
     * @return a {@link HostAndPort} representation of server's listening address.
     */
    public static HostAndPort serverHostAndPort(final ServerContext ctx) {
        return HostAndPort.of((InetSocketAddress) ctx.listenAddress());
    }

    /**
     * Returns a {code HOST} header value based of the information in {@link HostAndPort}.
     *
     * @param hostAndPort to convert to the {@code HOST} header
     * @return a {@code HOST} header value
     */
    public static String hostHeader(final HostAndPort hostAndPort) {
        return isValidIpV6Address(hostAndPort.hostName()) ?
                "[" + hostAndPort.hostName() + "]:" + hostAndPort.port() : hostAndPort.toString();
    }

    /**
     * Creates a new {@link DomainSocketAddress}.
     *
     * @return a new {@link DomainSocketAddress}
     * @throws IOException if a temporary file cannot be created for {@link DomainSocketAddress}
     */
    public static DomainSocketAddress newSocketAddress() throws IOException {
        File file = File.createTempFile("STUDS", ".uds");
        assertTrue(file.delete());
        return new DomainSocketAddress(file);
    }
}
