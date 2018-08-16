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
package io.servicetalk.http.netty;

import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscoverer.Event;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.transport.api.HostAndPort;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.net.InetSocketAddress;

import static io.servicetalk.concurrent.internal.Await.awaitIndefinitelyNonNull;
import static io.servicetalk.http.netty.GlobalDnsServiceDiscoverer.globalDnsServiceDiscoverer;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class GlobalDnsServiceDiscovererTest {

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    @Test
    public void testGlobalDnsServiceDiscoverer() throws Exception {
        ServiceDiscoverer<HostAndPort, InetSocketAddress> dns = globalDnsServiceDiscoverer();

        Single<Event<InetSocketAddress>> servicetalkIo = dns.discover(HostAndPort.of("localhost", 80)).first();
        Event<InetSocketAddress> event = awaitIndefinitelyNonNull(servicetalkIo);

        assertThat(event.getAddress().getHostName(), equalTo("localhost"));
        assertThat(event.getAddress().getHostString(), equalTo("localhost"));
        assertThat(event.getAddress().getAddress().getAddress(), equalTo(new byte[]{127, 0, 0, 1}));
    }
}
