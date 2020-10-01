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
package io.servicetalk.dns.discovery.netty;

import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Publisher;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collection;

/**
 * A client for the <a href="https://tools.ietf.org/html/rfc1035">DNS</a> protocol.
 */
interface DnsClient extends ListenableAsyncCloseable {
    /**
     * Query for <a href="https://tools.ietf.org/html/rfc1035">host addresses</a> (e.g. A or AAAA records).
     * @param hostName <a href="https://tools.ietf.org/html/rfc1035">host name</a> to lookup.
     * @return A {@link Publisher} which provides notification when resolved addresses for the {@code hostName}
     * change.
     */
    Publisher<Collection<ServiceDiscovererEvent<InetAddress>>> dnsQuery(String hostName);

    /**
     * Query for <a href="https://tools.ietf.org/html/rfc2782">SRV Resource Records</a> corresponding to
     * {@code serviceName}. For each SRV answer capture the <strong>Port</strong> and resolve the
     * <strong>Target</strong>.
     * @param serviceName The domain name of the service to lookup.
     * @return A {@link Publisher} which provides notification when resolved addresses for the {@code serviceName}
     * change.
     */
    Publisher<Collection<ServiceDiscovererEvent<InetSocketAddress>>> dnsSrvQuery(String serviceName);
}
