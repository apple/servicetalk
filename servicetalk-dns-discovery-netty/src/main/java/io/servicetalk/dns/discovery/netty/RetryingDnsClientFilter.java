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
import io.servicetalk.concurrent.api.BiIntFunction;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import static java.util.Objects.requireNonNull;

/**
 * Retry {@link DnsClient} operations upon failure.
 */
final class RetryingDnsClientFilter extends DnsClientFilter {
    private final BiIntFunction<Throwable, Completable> retryStrategy;

    /**
     * Create a new instance.
     *
     * @param client the {@link DnsClient} to delegate to.
     * @param retryStrategy decorates query methods with {@link Publisher#retryWhen(BiIntFunction)} to determine when
     * retry is appropriate.
     */
    RetryingDnsClientFilter(final DnsClient client,
                            final BiIntFunction<Throwable, Completable> retryStrategy) {
        super(client);
        this.retryStrategy = requireNonNull(retryStrategy);
    }

    @Override
    public Publisher<ServiceDiscovererEvent<InetAddress>> dnsQuery(final String hostName) {
        return super.dnsQuery(hostName).retryWhen(retryStrategy);
    }

    @Override
    public Publisher<ServiceDiscovererEvent<InetSocketAddress>> dnsSrvQuery(final String serviceName) {
        return super.dnsSrvQuery(serviceName).retryWhen(retryStrategy);
    }
}
