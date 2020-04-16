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
import java.net.UnknownHostException;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithConstantBackoffAndJitter;
import static java.lang.Integer.MAX_VALUE;
import static java.time.Duration.ofSeconds;
import static java.util.Objects.requireNonNull;

/**
 * Retry {@link DnsClient} operations upon failure.
 */
final class RetryingDnsClientFilter implements DnsClientFilterFactory {
    private final BiIntFunction<Throwable, Completable> retryStrategy;

    /**
     * Create a new instance.
     */
    RetryingDnsClientFilter() {
        this(retryWithConstantBackoffAndJitter(MAX_VALUE, t -> t instanceof UnknownHostException, ofSeconds(60),
                immediate()));
    }

    /**
     * Create a new instance.
     *
     * @param retryStrategy decorates query methods with {@link Publisher#retryWhen(BiIntFunction)} to determine when
     * retry is appropriate.
     */
    RetryingDnsClientFilter(final BiIntFunction<Throwable, Completable> retryStrategy) {
        this.retryStrategy = requireNonNull(retryStrategy);
    }

    @Override
    public DnsClientFilter create(final DnsClient dnsClient) {
        return new DnsClientFilter(dnsClient) {
            @Override
            public Publisher<ServiceDiscovererEvent<InetAddress>> dnsQuery(final String hostName) {
                return super.dnsQuery(hostName).retryWhen(retryStrategy);
            }

            @Override
            public Publisher<ServiceDiscovererEvent<InetSocketAddress>> dnsSrvQuery(final String serviceName) {
                return super.dnsSrvQuery(serviceName).retryWhen(retryStrategy);
            }
        };
    }
}
