/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.BiIntFunction;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;

final class DefaultDnsServiceDiscovererFilter extends ServiceDiscovererFilter<String, InetAddress, ServiceDiscovererEvent<InetAddress>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultDnsServiceDiscovererFilter.class);

    private final BiIntFunction<Throwable, Completable> retryStrategy;

    DefaultDnsServiceDiscovererFilter(final ServiceDiscoverer<String, InetAddress, ServiceDiscovererEvent<InetAddress>> delegate,
                                      final BiIntFunction<Throwable, Completable> retryStrategy) {
        super(delegate);
        this.retryStrategy = retryStrategy;
    }

    @Override
    public Publisher<ServiceDiscovererEvent<InetAddress>> discover(final String unresolvedAddress) {
        return super.discover(unresolvedAddress).retryWhen((i, t) -> {
            if (t instanceof UnknownHostException) {
                LOGGER.warn("Unable to resolve host {}", unresolvedAddress, t);
                return retryStrategy.apply(i, t);
            } else {
                return Completable.error(t);
            }
        });
    }
}
