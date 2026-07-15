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

import io.servicetalk.dns.discovery.netty.NoopDnsServiceDiscovererObserver.NoopDnsDiscoveryObserver;
import io.servicetalk.dns.discovery.netty.NoopDnsServiceDiscovererObserver.NoopDnsResolutionObserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;

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
    public DnsDiscoveryObserver onNewDiscovery(final String discoveryName) {
        // Deprecated overload: no client id is available, so pass null and idName degrades the logged detail to just
        // the discovery target rather than dropping attribution entirely.
        return safeReport(() -> observer.onNewDiscovery(discoveryName), observer, "new discovery", null, discoveryName,
                o -> new CatchAllDnsDiscoveryObserver(o, null, discoveryName), NoopDnsDiscoveryObserver.INSTANCE);
    }

    @Override
    public DnsDiscoveryObserver onNewDiscovery(final String serviceDiscovererId, final String discoveryName) {
        return safeReport(() -> observer.onNewDiscovery(serviceDiscovererId, discoveryName), observer, "new discovery",
                serviceDiscovererId, discoveryName,
                o -> new CatchAllDnsDiscoveryObserver(o, serviceDiscovererId, discoveryName),
                NoopDnsDiscoveryObserver.INSTANCE);
    }

    private static final class CatchAllDnsDiscoveryObserver implements DnsDiscoveryObserver {

        private final DnsDiscoveryObserver observer;
        @Nullable
        private final String serviceDiscovererId;
        private final String discoveryName;

        private CatchAllDnsDiscoveryObserver(final DnsDiscoveryObserver observer,
                                             @Nullable final String serviceDiscovererId, final String discoveryName) {
            this.observer = observer;
            this.serviceDiscovererId = serviceDiscovererId;
            this.discoveryName = discoveryName;
        }

        @Override
        public DnsResolutionObserver onNewResolution(final String resolutionName) {
            return safeReport(() -> observer.onNewResolution(resolutionName), observer, "new resolution",
                    serviceDiscovererId, resolutionName,
                    o -> new CatchAllDnsResolutionObserver(o, serviceDiscovererId, resolutionName),
                    NoopDnsResolutionObserver.INSTANCE);
        }

        @Override
        public void discoveryCancelled() {
            safeReport(observer::discoveryCancelled, observer, "discovery cancelled",
                    serviceDiscovererId, discoveryName);
        }

        @Override
        public void discoveryFailed(final Throwable cause) {
            safeReport(() -> observer.discoveryFailed(cause), observer, "discovery failed",
                    serviceDiscovererId, discoveryName, cause);
        }
    }

    private static final class CatchAllDnsResolutionObserver implements DnsResolutionObserver {

        private final DnsResolutionObserver observer;
        @Nullable
        private final String serviceDiscovererId;
        private final String resolutionName;

        private CatchAllDnsResolutionObserver(final DnsResolutionObserver observer,
                                              @Nullable final String serviceDiscovererId,
                                              final String resolutionName) {
            this.observer = observer;
            this.serviceDiscovererId = serviceDiscovererId;
            this.resolutionName = resolutionName;
        }

        @Override
        public void resolutionFailed(final Throwable cause) {
            safeReport(() -> observer.resolutionFailed(cause), observer, "resolution failed",
                    serviceDiscovererId, resolutionName, cause);
        }

        @Override
        public void resolutionCompleted(final ResolutionResult result) {
            safeReport(() -> observer.resolutionCompleted(result), observer, "resolution completed",
                    serviceDiscovererId, resolutionName, result);
        }
    }

    private static <T> T safeReport(final Supplier<T> supplier, final Object observer, final String eventName,
                                    @Nullable final String serviceDiscovererId, final String targetName,
                                    final UnaryOperator<T> catchAllWrapper, final T defaultValue) {
        try {
            return catchAllWrapper.apply(requireNonNull(supplier.get()));
        } catch (Throwable unexpected) {
            LOGGER.warn("Unexpected exception from {} while reporting a {} event{}",
                    observer, eventName, idName(serviceDiscovererId, targetName), unexpected);
            return defaultValue;
        }
    }

    private static void safeReport(final Runnable runnable, final Object observer, final String eventName,
                                   @Nullable final String serviceDiscovererId, final String targetName) {
        try {
            runnable.run();
        } catch (Throwable unexpected) {
            LOGGER.warn("Unexpected exception from {} while reporting a {} event{}",
                    observer, eventName, idName(serviceDiscovererId, targetName), unexpected);
        }
    }

    private static void safeReport(final Runnable runnable, final Object observer, final String eventName,
                                   @Nullable final String serviceDiscovererId, final String targetName,
                                   final Throwable original) {
        try {
            runnable.run();
        } catch (Throwable unexpected) {
            addSuppressed(unexpected, original);
            LOGGER.warn("Unexpected exception from {} while reporting a {} event{}",
                    observer, eventName, idName(serviceDiscovererId, targetName), unexpected);
        }
    }

    private static void safeReport(final Runnable runnable, final Object observer, final String eventName,
                                   @Nullable final String serviceDiscovererId, final String targetName,
                                   final Object result) {
        try {
            runnable.run();
        } catch (Throwable unexpected) {
            LOGGER.warn("Unexpected exception from {} while reporting a {} event{}: {}",
                    observer, eventName, idName(serviceDiscovererId, targetName), result, unexpected);
        }
    }

    private static String idName(@Nullable final String serviceDiscovererId, final String targetName) {
        return serviceDiscovererId == null ? " (" + targetName + ')' :
                " (" + serviceDiscovererId + ' ' + targetName + ')';
    }
}
