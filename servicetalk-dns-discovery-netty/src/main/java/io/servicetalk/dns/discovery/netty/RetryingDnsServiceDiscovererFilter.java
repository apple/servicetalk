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
import io.servicetalk.client.api.ServiceDiscovererFilter;
import io.servicetalk.concurrent.api.BiIntFunction;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static io.servicetalk.concurrent.api.Completable.error;

/**
 * Applies a retry strategy to a DNS {@link ServiceDiscoverer}.
 * <p>
 * {@link Throwable}s that pass {@link #shouldRetry(Throwable)} are logged, and the retry strategy is invoked. All
 * other {@link Throwable}s will result in the {@link Publisher} terminating with the {@link Throwable}.
 */
public class RetryingDnsServiceDiscovererFilter extends ServiceDiscovererFilter<String, InetAddress,
        ServiceDiscovererEvent<InetAddress>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RetryingDnsServiceDiscovererFilter.class);

    private final BiIntFunction<Throwable, Completable> retryStrategy;

    /**
     * Create an instance of the filter.
     *
     * @param delegate the {@link ServiceDiscoverer} to delegate to
     * @param retryStrategy the retry strategy to apply
     */
    public RetryingDnsServiceDiscovererFilter(
            final ServiceDiscoverer<String, InetAddress, ServiceDiscovererEvent<InetAddress>> delegate,
            final BiIntFunction<Throwable, Completable> retryStrategy) {
        super(delegate);
        this.retryStrategy = retryStrategy;
    }

    @Override
    public Publisher<ServiceDiscovererEvent<InetAddress>> discover(final String unresolvedAddress) {
        return super.discover(unresolvedAddress).retryWhen((i, t) -> {
            if (shouldRetry(t)) {
                LOGGER.warn("Unable to resolve host {}", unresolvedAddress, t);
                return retryStrategy.apply(i, t);
            } else {
                return error(t);
            }
        });
    }

    /**
     * Determines which {@link Throwable}s should be retried.
     *
     * @param cause the {@link Throwable} to check
     * @return true to retry, false to terminate the {@link Publisher}
     */
    protected boolean shouldRetry(final Throwable cause) {
        return cause instanceof UnknownHostException;
    }
}
