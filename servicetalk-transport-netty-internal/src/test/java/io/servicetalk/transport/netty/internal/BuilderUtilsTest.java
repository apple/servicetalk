/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.transport.api.HostAndPort;

import io.netty.util.NetUtil;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import static io.servicetalk.transport.netty.internal.BuilderUtils.toResolvedInetSocketAddress;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BuilderUtilsTest {

    @Test
    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    void toResolvedInetSocketAddressFromIPv4() {
        final String localhostIp4Address = NetUtil.LOCALHOST4.getHostAddress();
        InetSocketAddress address = toResolvedInetSocketAddress(HostAndPort.of(localhostIp4Address, 8080));
        assertThat(address.isUnresolved(), is(false));
        assertThat(address.getHostString(), equalTo(localhostIp4Address));
        assertThat(address.getPort(), is(8080));
    }

    @Test
    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    void toResolvedInetSocketAddressFromIPv6() {
        final String localhostIp6Address = NetUtil.LOCALHOST6.getHostAddress();
        InetSocketAddress address = toResolvedInetSocketAddress(HostAndPort.of(localhostIp6Address, 8080));
        assertThat(address.isUnresolved(), is(false));
        assertThat(address.getHostString(), equalTo(localhostIp6Address));
        assertThat(address.getPort(), is(8080));
    }

    @Test
    void toResolvedInetSocketAddressFromUnresolved() {
        Throwable t = assertThrows(IllegalArgumentException.class,
                () -> toResolvedInetSocketAddress(HostAndPort.of("unresolved-hostname", 8080)));
        assertThat(t.getCause(), instanceOf(UnknownHostException.class));
    }
}
