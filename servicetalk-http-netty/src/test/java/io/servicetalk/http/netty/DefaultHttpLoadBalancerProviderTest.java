/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.loadbalancer.experimental.DefaultHttpLoadBalancerProvider;
import io.servicetalk.transport.api.HostAndPort;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.net.InetSocketAddress;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;

@Execution(ExecutionMode.SAME_THREAD)
class DefaultHttpLoadBalancerProviderTest {

    private static final String PROPERTY_NAME = "io.servicetalk.loadbalancer.experimental.clientsEnabledFor";

    @AfterEach
    void cleanup() {
        System.clearProperty(PROPERTY_NAME);
    }

    @Test
    void defaultsToNoHost() {
        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                HttpClients.forSingleAddress("testhost", 80);
        assertThat(builder, instanceOf(DefaultSingleAddressHttpClientBuilder.class));
    }

    @Test
    void isNotUsedForUnmatchedHost() {
        System.setProperty(PROPERTY_NAME, "notthisone");
        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                HttpClients.forSingleAddress("testhost", 80);
        assertThat(builder, instanceOf(DefaultSingleAddressHttpClientBuilder.class));
    }

    @Test
    void isNotUsedForUnmatchedHostInList() {
        System.setProperty(PROPERTY_NAME, "notthisone,foo");
        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                HttpClients.forSingleAddress("testhost", 80);
        assertThat(builder, instanceOf(DefaultSingleAddressHttpClientBuilder.class));
    }

    @Test
    void isUsedForMatchedHost() {
        System.setProperty(PROPERTY_NAME, "testhost");
        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                HttpClients.forSingleAddress("testhost", 80);
        assertThat(builder, instanceOf(DefaultHttpLoadBalancerProvider.LoadBalancerIgnoringBuilder.class));
    }

    @Test
    void isUsedForMatchedHostInList() {
        System.setProperty(PROPERTY_NAME, "testhost,foo");
        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                HttpClients.forSingleAddress("testhost", 80);
        assertThat(builder, instanceOf(DefaultHttpLoadBalancerProvider.LoadBalancerIgnoringBuilder.class));
    }

    @Test
    void isUsedForAnyHost() {
        System.setProperty(PROPERTY_NAME, "*");
        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                HttpClients.forSingleAddress("anyhostname", 80);
        assertThat(builder, instanceOf(DefaultHttpLoadBalancerProvider.LoadBalancerIgnoringBuilder.class));
    }
}
