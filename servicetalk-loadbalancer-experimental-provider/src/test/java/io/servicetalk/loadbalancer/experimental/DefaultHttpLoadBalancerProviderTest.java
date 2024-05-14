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
package io.servicetalk.loadbalancer.experimental;

import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.transport.api.HostAndPort;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.net.InetSocketAddress;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;

@Execution(ExecutionMode.SAME_THREAD)
class DefaultHttpLoadBalancerProviderTest {

    private static final String PROPERTY_NAME = "io.servicetalk.loadbalancer.experimental.clientsEnabledFor";

    @AfterEach
    void cleanup() {
        System.clearProperty(PROPERTY_NAME);
    }

    @Test
    void defaultsToNoHost() throws Exception {
        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                HttpClients.forSingleAddress("testhost", 80);
        assertThat(builder, not(instanceOf(DefaultHttpLoadBalancerProvider.LoadBalancerIgnoringBuilder.class)));
    }

    @Test
    void isNotUsedForUnmatchedHost() throws Exception {
        System.setProperty(PROPERTY_NAME, "notthisone");
        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                HttpClients.forSingleAddress("testhost", 80);
        assertThat(builder, not(instanceOf(DefaultHttpLoadBalancerProvider.LoadBalancerIgnoringBuilder.class)));
    }

    @Test
    void isNotUsedForUnmatchedHostInList() throws Exception {
        System.setProperty(PROPERTY_NAME, "notthisone,foo");
        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                HttpClients.forSingleAddress("testhost", 80);
        assertThat(builder, not(instanceOf(DefaultHttpLoadBalancerProvider.LoadBalancerIgnoringBuilder.class)));
    }

    @Test
    void isUsedForMatchedHost() throws Exception {
        System.setProperty(PROPERTY_NAME, "testhost");
        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                HttpClients.forSingleAddress("testhost", 80);
        assertThat(builder, instanceOf(DefaultHttpLoadBalancerProvider.LoadBalancerIgnoringBuilder.class));
    }

    @Test
    void isUsedForMatchedHostInList() throws Exception {
        System.setProperty(PROPERTY_NAME, "testhost,foo");
        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                HttpClients.forSingleAddress("testhost", 80);
        assertThat(builder, instanceOf(DefaultHttpLoadBalancerProvider.LoadBalancerIgnoringBuilder.class));
    }

    @Test
    void isUsedForAnyHost() throws Exception {
        System.setProperty(PROPERTY_NAME, "*");
        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                HttpClients.forSingleAddress("anyhostname", 80);
        assertThat(builder, instanceOf(DefaultHttpLoadBalancerProvider.LoadBalancerIgnoringBuilder.class));
    }
}
