/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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

import org.junit.jupiter.api.Test;

import java.net.Inet6Address;
import java.net.InetSocketAddress;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
final class HostAndPortTest {
    @Test
    void hostConstructorNormalizesIpv6() {
        HostAndPort hp1 = HostAndPort.of("[::1]", 9999);
        HostAndPort hp2 = HostAndPort.of("::1", 9999);
        HostAndPort hp3 = HostAndPort.of("[0000:0000:0000:0000:0000:0000:0000:0001]", 9999);
        HostAndPort hp4 = HostAndPort.of("0000:0000:0000:0000:0000:0000:0000:0001", 9999);

        assertHpEqualTo(hp1, hp2);
        assertHpEqualTo(hp2, hp3);
        assertHpEqualTo(hp3, hp4);
    }

    @Test
    void IPv6LoopBack() {
        assertIP("[::1]:9999", "::1", 9999);
    }

    @Test
    void IPv6Compressed() {
        assertIP("[2001:1234::1b12:0:0:1a13]:0", "2001:1234::1b12:0:0:1a13", 0);
    }

    @Test
    void IPv6MappedIPv4() {
        assertIP("[::FFFF:129.144.52.38]:443", "::FFFF:129.144.52.38", 443);
    }

    @Test
    void IPv6WithScope() {
        assertIP("[::FFFF:129.144.52.38%2]:65535", "::FFFF:129.144.52.38%2", 65535);
    }

    @Test
    void IPv4() {
        assertIP("1.2.3.4:8080", "1.2.3.4", 8080);
    }

    @Test
    void IPv4MissingComponents() {
        assertThrows(IllegalArgumentException.class, () -> assertIP("1.2.3:80", "1.2.3", 80));
    }

    @Test
    void IPv4NoAddress() {
        assertThrows(IllegalArgumentException.class, () -> assertIP(":80", "", 80));
    }

    @Test
    void IPv4NoPort() {
        assertThrows(IllegalArgumentException.class, () -> assertIP("1.2.3.4", "1.2.3.4", 0));
    }

    @Test
    void IPv6NoPort() {
        assertThrows(IllegalArgumentException.class, () -> assertIP("[::1]", "[::1]", 0));
    }

    @Test
    void IPv6NoAddress() {
        assertThrows(IllegalArgumentException.class, () -> assertIP("[]:80", "[]", 80));
    }

    @Test
    void IPv6SingleCharAddress() {
        assertThrows(IllegalArgumentException.class, () -> assertIP("[a]:80", "[a]", 80));
    }

    @Test
    void IPv4NegativePort() {
        assertThrows(IllegalArgumentException.class, () -> assertIP("1.2.3.4:-22", "1.2.3.4", 0));
    }

    @Test
    void IPv6NegativePort() {
        assertThrows(IllegalArgumentException.class, () -> assertIP("[::1]-1", "[::1]", 0));
    }

    @Test
    void IPv4PlusPort() {
        assertThrows(IllegalArgumentException.class, () -> assertIP("1.2.3.4:+22", "1.2.3.4", 0));
    }

    @Test
    void IPv6PlusPort() {
        assertThrows(IllegalArgumentException.class, () -> assertIP("[::1]:+22", "[::1]", 0));
    }

    @Test
    void IPv4PortTooHigh() {
        assertThrows(IllegalArgumentException.class, () -> assertIP("1.2.3.4:65536", "1.2.3.4", 0));
    }

    @Test
    void IPv6PortTooHigh() {
        assertThrows(IllegalArgumentException.class, () -> assertIP("[::1]:65536", "[::1]", 0));
    }

    @Test
    void IPv4PortTooLow() {
        assertThrows(IllegalArgumentException.class, () -> assertIP("1.2.3.4:-1", "1.2.3.4", 0));
    }

    @Test
    void IPv6PortTooLow() {
        assertThrows(IllegalArgumentException.class, () -> assertIP("[::1]:-3", "[::1]", 0));
    }

    @Test
    void IPv4PortInvalidChar() {
        assertThrows(IllegalArgumentException.class, () -> assertIP("1.2.3.4:12x", "1.2.3.4", 0));
    }

    @Test
    void IPv6PortInvalidChar() {
        assertThrows(IllegalArgumentException.class, () -> assertIP("[::1]:x", "[::1]", 0));
    }

    @Test
    void IPv6HalfBracketFirst() {
        assertThrows(IllegalArgumentException.class, () -> assertIP("::1]:80", "[::1]", 80));
    }

    @Test
    void IPv6HalfBracketLast() {
        assertThrows(IllegalArgumentException.class, () -> assertIP("[::1:80", "[::1]", 80));
    }

    @Test
    void IPv6DoubleBracketFirst() {
        assertThrows(IllegalArgumentException.class, () -> assertIP("[[::1]:80", "[::1]", 80));
    }

    @Test
    void IPv6DoubleBracketLast() {
        assertThrows(IllegalArgumentException.class, () -> assertIP("[::1]]:80", "[::1]", 80));
    }

    @Test
    void IPv6ChineseChar() {
        assertThrows(IllegalArgumentException.class, () -> assertIP("[\u4F60\u597D]:8080", "", 8080));
    }

    @Test
    void IPv4ChineseChar() {
        assertThrows(IllegalArgumentException.class, () -> assertIP("\u4F60.2.3.4:8080", "", 8080));
    }

    @Test
    void IPv6UTF8() {
        assertThrows(IllegalArgumentException.class, () -> assertIP("[::❤]:8080", "", 8080));
    }

    @Test
    void IPv4UTF8() {
        assertThrows(IllegalArgumentException.class, () -> assertIP("1.❤.3.4:8080", "", 8080));
    }

    @Test
    void IPv6Latin1AsUTF8() {
        assertThrows(IllegalArgumentException.class, () -> assertIP("[::ö]:8080", "", 8080));
    }

    @Test
    void IPv4Latin1AsUTF8() {
        assertThrows(IllegalArgumentException.class, () -> assertIP("1.2.ö.4:8080", "", 8080));
    }

    @Test
    void IPv6CompressAndRemoveLeadingZeros() {
        assertIP("[0000:0000:0000:0000:0000:0000:0000:0001]:234", "::1", 234);
    }

    @Test
    void IPv6CompressAndRemoveLeadingZerosPreserveLastZero() {
        assertIP("[1000:0200:0030:0004:0000:0000:0050:0000]:234", "1000:200:30:4::50:0", 234);
    }

    @Test
    void IPv6CompressAndRemoveLeadingZeroTwo() {
        assertIP("[00:000:0030:0004:0001:2000:0050:0000]:234", "::30:4:1:2000:50:0", 234);
    }

    @Test
    void IPv6RemoveLeadingZerosEvenIfAlreadyCompressed() {
        // https://datatracker.ietf.org/doc/html/rfc5952#section-4.2.1
        assertIP("[2001:0db8::0001]:1234", "2001:db8::1", 1234);
    }

    @Test
    void IPv6NoCompressOneBitField() {
        // https://datatracker.ietf.org/doc/html/rfc5952#section-4.2.2
        assertIP("[2001:db8:0:1:1:1:1:1]:1234", "2001:db8:0:1:1:1:1:1", 1234);
        assertIP("[2001:db8:0000:1:1:1:1:1]:1234", "2001:db8:0:1:1:1:1:1", 1234);
        assertIP("[0000:0db8:0000:01:01:01:01:01]:1234", "0:db8:0:1:1:1:1:1", 1234);
    }

    @Test
    void IPv6CompressLongestStreak() {
        // https://datatracker.ietf.org/doc/html/rfc5952#section-4.2.3
        assertIP("[2001:0:0:1:0:0:0:1]:1234", "2001:0:0:1::1", 1234);
    }

    @Test
    void IPv6CompressFirstIfTie() {
        // https://datatracker.ietf.org/doc/html/rfc5952#section-4.2.3
        assertIP("[2001:db8:0:0:1:0:0:1]:1234", "2001:db8::1:0:0:1", 1234);
    }

    private static void assertIP(String ipPort, String expectedAddress, int expectedPort) {
        assertIP(HostAndPort.ofIpPort(ipPort), expectedAddress, expectedPort);

        for (int i = 1; i <= 3; ++i) {
            StringBuilder prefix = new StringBuilder(i);
            for (int x = 0; x < i; ++x) {
                prefix.append('a');
            }
            assertIP(HostAndPort.ofIpPort(prefix + ipPort, prefix.length()), expectedAddress, expectedPort);
        }
    }

    private static void assertIP(HostAndPort result, String expectedAddress, int expectedPort) {
        assertThat(result.hostName(), equalTo(expectedAddress));
        assertThat(result.port(), equalTo(expectedPort));
        InetSocketAddress socketAddress = new InetSocketAddress(expectedAddress, expectedPort);
        if (socketAddress.getAddress() instanceof Inet6Address || expectedAddress.startsWith("::FFFF:")) {
            assertThat(result.toString(), equalTo('[' + expectedAddress + "]:" + expectedPort));
        } else {
            assertThat(result.toString(), equalTo(expectedAddress + ':' + expectedPort));
        }
    }

    private static void assertHpEqualTo(HostAndPort hp1, HostAndPort hp2) {
        assertThat(hp1, equalTo(hp2));
        assertThat(hp1.hashCode(), equalTo(hp2.hashCode()));
        assertThat(hp1.toString(), equalTo(hp2.toString()));
    }
}
