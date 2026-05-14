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

import io.servicetalk.tcp.netty.internal.ReadOnlyTcpClientConfig;
import io.servicetalk.tcp.netty.internal.TcpClientConfig;
import io.servicetalk.transport.api.ClientSslConfig;
import io.servicetalk.transport.api.ClientSslConfigBuilder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.stream.Stream;

import static io.servicetalk.http.netty.StreamingConnectionFactory.toHostAndIpBundle;
import static io.servicetalk.http.netty.StreamingConnectionFactory.withSslConfigPeerHost;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
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

    @Test
    void ipAddressesAreNotBundled() {
        InetAddress loopbackAddress = InetAddress.getLoopbackAddress();
        assertThat(toHostAndIpBundle("127.0.0.1", loopbackAddress), is(equalTo("127.0.0.1")));
        assertThat(toHostAndIpBundle("192.168.1.1", loopbackAddress), is(equalTo("192.168.1.1")));
        assertThat(toHostAndIpBundle("::1", loopbackAddress), is(equalTo("::1")));
        assertThat(toHostAndIpBundle("2001:db8:0:1::1/64", loopbackAddress), is(equalTo("2001:db8:0:1::1/64")));
    }

    @Test
    void trailingDotPeerHostIsSmuggledViaSniAndPeerHostIsMangled() throws Exception {
        ClientSslConfig result = withSslConfig(
                new ClientSslConfigBuilder().peerHost("example.com.").build(),
                inetSocketAddress(new byte[] {1, 2, 3, 4}, 443));

        // SNIHostName rejects trailing-dot FQDNs, so we strip it before smuggling.
        assertThat(result.sniHostname(), is(equalTo("example.com")));
        assertThat(result.peerHost(), is(equalTo("example.com-1.2.3.4")));
        assertThat(result.hostnameVerificationAlgorithm(), equalTo("HTTPS"));
    }

    @Test
    void regularPeerHostIsSmuggledViaSniAndPeerHostIsMangled() throws Exception {
        ClientSslConfig result = withSslConfig(
                new ClientSslConfigBuilder().peerHost("example.com").build(),
                inetSocketAddress(new byte[] {1, 2, 3, 4}, 443));

        assertThat(result.sniHostname(), is(equalTo("example.com")));
        assertThat(result.peerHost(), is(equalTo("example.com-1.2.3.4")));
    }

    @Test
    void sniInvalidPeerHostWithVerificationPreservesPeerHost() throws Exception {
        // Underscore makes the name invalid for SNI, so we cannot smuggle it. With verification enabled, mangling
        // peerHost would silently break verification — so we sacrifice the cache-key widening instead.
        ClientSslConfig result = withSslConfig(
                new ClientSslConfigBuilder().peerHost("under_score.example").build(),
                inetSocketAddress(new byte[] {1, 2, 3, 4}, 443));

        assertThat(result.sniHostname(), is(nullValue()));
        assertThat(result.peerHost(), is(equalTo("under_score.example")));
        assertThat(result.hostnameVerificationAlgorithm(), equalTo("HTTPS"));
    }

    @Test
    void sniInvalidPeerHostWithoutVerificationIsMangled() throws Exception {
        ClientSslConfig result = withSslConfig(
                new ClientSslConfigBuilder()
                        .peerHost("under_score.example")
                        .hostnameVerificationAlgorithm("")
                        .build(),
                inetSocketAddress(new byte[] {1, 2, 3, 4}, 443));

        assertThat(result.sniHostname(), is(nullValue()));
        assertThat(result.peerHost(), is(equalTo("under_score.example-1.2.3.4")));
        assertThat(result.hostnameVerificationAlgorithm(), is(equalTo("")));
    }

    @Test
    void noPeerHostNoSniResetsVerificationAlgorithmToEmpty() throws Exception {
        // Algorithm is reset to "" so Netty 4.2's "HTTPS" default doesn't try to verify against the IP literal.
        ClientSslConfig result = withSslConfig(
                new ClientSslConfigBuilder().build(),
                inetSocketAddress(new byte[] {1, 2, 3, 4}, 443));

        assertThat(result.sniHostname(), is(nullValue()));
        assertThat(result.peerHost(), is(equalTo("1.2.3.4")));
        assertThat(result.hostnameVerificationAlgorithm(), is(equalTo("")));
    }

    private static ClientSslConfig withSslConfig(ClientSslConfig sslConfig, InetSocketAddress resolved) {
        TcpClientConfig tcpConfig = new TcpClientConfig();
        tcpConfig.sslConfig(sslConfig);
        ReadOnlyTcpClientConfig result = withSslConfigPeerHost(resolved, tcpConfig.asReadOnly());
        return result.sslConfig();
    }

    private static InetSocketAddress inetSocketAddress(byte[] addr, int port) throws Exception {
        return new InetSocketAddress(InetAddress.getByAddress(addr), port);
    }
}
