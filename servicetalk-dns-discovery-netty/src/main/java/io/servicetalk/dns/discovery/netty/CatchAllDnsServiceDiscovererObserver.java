/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static io.servicetalk.dns.discovery.netty.NoopDnsServiceDiscovererObserver.NoopDnsDiscoveryObserver;
import static io.servicetalk.dns.discovery.netty.NoopDnsServiceDiscovererObserver.NoopDnsResolutionObserver;
import static io.servicetalk.utils.internal.ThrowableUtils.addSuppressed;
import static java.util.Objects.requireNonNull;

/**
 * {@link DnsServiceDiscovererObserver} wrapper that catches and logs all exceptions.
 */
final class CatchAllDnsServiceDiscovererObserver implements DnsServiceDiscovererObserver {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatchAllDnsServiceDiscovererObserver.class);

    private final DnsServiceDiscovererObserver observer;

    CatchAllDnsServiceDiscovererObserver(final DnsServiceDiscovererObserver observer) {
        this.observer = requireNonNull(observer);
    }

    DnsServiceDiscovererObserver observer() {
        return observer;
    }

    @Override
    @SuppressWarnings("deprecation")
    public DnsDiscoveryObserver onNewDiscovery(final String name) {
        return safeReport(() -> observer.onNewDiscovery(name), observer, "new discovery",
                CatchAllDnsDiscoveryObserver::new, NoopDnsDiscoveryObserver.INSTANCE);
    }

    @Override
    public DnsDiscoveryObserver onNewDiscovery(final String serviceDiscovererId, final String name) {
        return safeReport(() -> observer.onNewDiscovery(serviceDiscovererId, name), observer, "new discovery",
                CatchAllDnsDiscoveryObserver::new, NoopDnsDiscoveryObserver.INSTANCE);
    }

    private static final class CatchAllDnsDiscoveryObserver implements DnsDiscoveryObserver {

        private final DnsDiscoveryObserver observer;

        private CatchAllDnsDiscoveryObserver(final DnsDiscoveryObserver observer) {
            this.observer = observer;
        }

        @Override
        public DnsResolutionObserver onNewResolution(final String name) {
            return safeReport(() -> observer.onNewResolution(name), observer, "new resolution",
                    CatchAllDnsResolutionObserver::new, NoopDnsResolutionObserver.INSTANCE);
        }

        @Override
        public void discoveryCancelled() {
            safeReport(observer::discoveryCancelled, observer, "discovery cancelled");
        }

        @Override
        public void discoveryFailed(final Throwable cause) {
            safeReport(() -> observer.discoveryFailed(cause), observer, "discovery failed", cause);
        }
    }

    private static final class CatchAllDnsResolutionObserver implements DnsResolutionObserver {

        private final DnsResolutionObserver observer;

        private CatchAllDnsResolutionObserver(final DnsResolutionObserver observer) {
            this.observer = observer;
        }

        @Override
        public void resolutionFailed(final Throwable cause) {
            safeReport(() -> observer.resolutionFailed(cause), observer, "resolution failed", cause);
        }

        @Override
        public void resolutionCompleted(final ResolutionResult result) {
            safeReport(() -> observer.resolutionCompleted(result), observer, "resolution completed");
        }
    }

    private static <T> T safeReport(final Supplier<T> supplier, final Object observer, final String eventName,
                                    final UnaryOperator<T> catchAllWrapper, final T defaultValue) {
        try {
            return catchAllWrapper.apply(requireNonNull(supplier.get()));
        } catch (Throwable unexpected) {
            LOGGER.warn("Unexpected exception from {} while reporting a {} event", observer, eventName, unexpected);
            return defaultValue;
        }
    }

    private static void safeReport(final Runnable runnable, final Object observer, final String eventName) {
        try {
            runnable.run();
        } catch (Throwable unexpected) {
            LOGGER.warn("Unexpected exception from {} while reporting a {} event", observer, eventName, unexpected);
        }
    }

    private static void safeReport(final Runnable runnable, final Object observer, final String eventName,
                                   final Throwable original) {
        try {
            runnable.run();
        } catch (Throwable unexpected) {
            addSuppressed(unexpected, original);
            LOGGER.warn("Unexpected exception from {} while reporting a {} event", observer, eventName, unexpected);
        }
    }
}
