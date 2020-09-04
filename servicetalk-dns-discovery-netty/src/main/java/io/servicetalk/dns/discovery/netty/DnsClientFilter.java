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
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Filter to modify behavior of a {@link DnsClient}.
 */
class DnsClientFilter implements DnsClient {
    private final DnsClient client;

    /**
     * Create a new instance.
     *
     * @param client the {@link DnsClient} to delegate to.
     */
    DnsClientFilter(final DnsClient client) {
        this.client = requireNonNull(client);
    }

    @Override
    public Publisher<List<ServiceDiscovererEvent<InetAddress>>> dnsQuery(final String hostName) {
        return client.dnsQuery(hostName);
    }

    @Override
    public Publisher<List<ServiceDiscovererEvent<InetSocketAddress>>> dnsSrvQuery(final String serviceName) {
        return client.dnsSrvQuery(serviceName);
    }

    @Override
    public Completable onClose() {
        return client.onClose();
    }

    @Override
    public Completable closeAsync() {
        return client.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return client.closeAsyncGracefully();
    }
}
