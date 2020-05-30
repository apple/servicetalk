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

import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import static io.servicetalk.transport.netty.internal.BuilderUtils.toInetSocketAddress;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

public class BuilderUtilsTest {

    @Test
    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    public void toInetSocketAddressFromIPv4() {
        InetSocketAddress address = toInetSocketAddress(HostAndPort.of("192.168.1.1", 8080));
        assertThat(address.isUnresolved(), is(false));
        assertThat(address.getHostString(), equalTo("192.168.1.1"));
        assertThat(address.getPort(), is(8080));
    }

    @Test
    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    public void toInetSocketAddressFromIPv6() {
        InetSocketAddress address = toInetSocketAddress(HostAndPort.of("2001:db8:0:0:0:0:0:abcd", 8080));
        assertThat(address.isUnresolved(), is(false));
        assertThat(address.getHostString(), equalTo("2001:db8:0:0:0:0:0:abcd"));
        assertThat(address.getPort(), is(8080));
    }

    @Test(expected = UnknownHostException.class)
    public void toInetSocketAddressFromUnresolved() {
        toInetSocketAddress(HostAndPort.of("unresolved-hostname", 8080));
    }
}
