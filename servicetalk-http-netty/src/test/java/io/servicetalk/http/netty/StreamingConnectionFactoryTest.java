/*
 * Copyright © 2018-2023 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.netty;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.stream.Stream;

import static io.servicetalk.http.netty.StreamingConnectionFactory.toHostAndIpBundle;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamingConnectionFactoryTest {
    static Stream<InetAddress> addresses() throws Exception {
        Stream.Builder<InetAddress> builder = Stream.builder();
        builder.add(InetAddress.getLoopbackAddress());
        builder.add(InetAddress.getLocalHost());
        builder.add(InetAddress.getByAddress(new byte[4]));
        builder.add(InetAddress.getByAddress(new byte[16]));
        builder.add(InetAddress.getByAddress(null, new byte[16]));
        builder.add(Inet6Address.getByAddress(null, new byte[16], 0));
        builder.add(Inet6Address.getByAddress("localhost", new byte[16], 0));
        builder.add(Inet6Address.getByAddress("local_host", new byte[16], 0));

        Enumeration<NetworkInterface> nifs = NetworkInterface.getNetworkInterfaces();
        addNetworkInterfaceAddresses(nifs, builder);
        return builder.build();
    }

    private static void addNetworkInterfaceAddresses(
            Enumeration<NetworkInterface> nifs, Stream.Builder<InetAddress> builder) {
        while (nifs.hasMoreElements()) {
            NetworkInterface nif = nifs.nextElement();
            Enumeration<InetAddress> inetAddresses = nif.getInetAddresses();
            while (inetAddresses.hasMoreElements()) {
                builder.add(inetAddresses.nextElement());
            }
            addNetworkInterfaceAddresses(nif.getSubInterfaces(), builder);
        }
    }

    @ParameterizedTest
    @MethodSource("addresses")
    void mustConvertInetAddressesToValidHostnames(InetAddress address) throws Exception {
        String hostAddress = StreamingConnectionFactory.toHostAddress(address);
        assertTrue(StreamingConnectionFactory.isValidSniHostname(hostAddress), hostAddress);
    }

    @Test
    void loopbackAddressIsNotBundled() {
        InetAddress loopbackAddress = InetAddress.getLoopbackAddress();
        assertThat(toHostAndIpBundle("localhost", loopbackAddress), is(equalTo("localhost")));
        assertThat(toHostAndIpBundle("servicetalk.io", loopbackAddress), is(equalTo("servicetalk.io")));
    }
}
