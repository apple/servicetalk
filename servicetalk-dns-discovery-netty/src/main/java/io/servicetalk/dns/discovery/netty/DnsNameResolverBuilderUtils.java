/*
 * Copyright © 2023-2024 Apple Inc. and the ServiceTalk project authors
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

import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.resolver.dns.DnsNameResolverBuilder;
import io.netty.resolver.dns.DnsNameResolverChannelStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import javax.annotation.Nullable;

import static io.servicetalk.dns.discovery.netty.DefaultDnsServiceDiscovererBuilder.DEFAULT_CONSOLIDATE_CACHE_SIZE;
import static io.servicetalk.utils.internal.ThrowableUtils.throwException;
import static java.lang.invoke.MethodType.methodType;

final class DnsNameResolverBuilderUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(DnsNameResolverBuilderUtils.class);
    private static final String NETTY_VERSION = DnsNameResolverBuilder.class.getPackage().getImplementationVersion();
    private static final String DEFAULT_DATAGRAM_CHANNEL_STRATEGY = "ChannelPerResolver";

    @Nullable
    private static final MethodHandle CONSOLIDATE_CACHE_SIZE;
    @Nullable
    private static final MethodHandle TCP_FALLBACK_ON_TIMEOUT;
    @Nullable
    private static final MethodHandle DATAGRAM_CHANNEL_STRATEGY;

    static {
        final DnsNameResolverBuilder builder = new DnsNameResolverBuilder();

        MethodHandle consolidateCacheSize;
        try {
            // Find a new method that exists only in Netty starting from 4.1.88.Final:
            // https://github.com/netty/netty/commit/d010e63bf5bf744f2ab6d0fc4386611efe7954e6
            consolidateCacheSize = MethodHandles.publicLookup()
                    .findVirtual(DnsNameResolverBuilder.class, "consolidateCacheSize",
                            methodType(DnsNameResolverBuilder.class, int.class));
            // Verify the method is working as expected:
            consolidateCacheSize(consolidateCacheSize, builder, 1);
        } catch (Throwable cause) {
            LOGGER.debug("DnsNameResolverBuilder#consolidateCacheSize(int) is available only starting from " +
                            "Netty 4.1.88.Final. Detected Netty version: {}", NETTY_VERSION, cause);
            consolidateCacheSize = null;
        }
        CONSOLIDATE_CACHE_SIZE = consolidateCacheSize;

        MethodHandle tcpFallbackOnTimeout;
        try {
            // Find a new method that exists only in Netty starting from 4.1.105.Final:
            // https://github.com/netty/netty/commit/684dfd88e319bb7870d88977bd6a63d5fea765c0
            tcpFallbackOnTimeout = MethodHandles.publicLookup()
                    .findVirtual(DnsNameResolverBuilder.class, "socketChannelType",
                            methodType(DnsNameResolverBuilder.class, Class.class, boolean.class));
            // Verify the method is working as expected:
            enableTcpFallback(tcpFallbackOnTimeout, builder, NioSocketChannel.class, true);
        } catch (Throwable cause) {
            LOGGER.debug("DnsNameResolverBuilder#socketChannelType(Class, boolean) is available only starting from " +
                    "Netty 4.1.105.Final. Detected Netty version: {}", NETTY_VERSION, cause);
            tcpFallbackOnTimeout = null;
        }
        TCP_FALLBACK_ON_TIMEOUT = tcpFallbackOnTimeout;

        MethodHandle datagramChannelStrategy;
        try {
            // Find a new method that exists only in Netty starting from 4.1.114.Final:
            // https://github.com/netty/netty/commit/d5f4bfb6c9ca14bd5820fa61a9ce3352492de872
            datagramChannelStrategy = MethodHandles.publicLookup()
                    .findVirtual(DnsNameResolverBuilder.class, "datagramChannelStrategy",
                            methodType(DnsNameResolverBuilder.class,
                                    Class.forName("io.netty.resolver.dns.DnsNameResolverChannelStrategy")));
            // Verify the method is working as expected:
            datagramChannelStrategy(datagramChannelStrategy, builder, DEFAULT_DATAGRAM_CHANNEL_STRATEGY);
        } catch (Throwable cause) {
            LOGGER.debug("DnsNameResolverBuilder#datagramChannelStrategy(DnsNameResolverChannelStrategy) is " +
                    "available only starting from Netty 4.1.114.Final. Detected Netty version: {}",
                    NETTY_VERSION, cause);
            datagramChannelStrategy = null;
        }
        DATAGRAM_CHANNEL_STRATEGY = datagramChannelStrategy;
    }

    private DnsNameResolverBuilderUtils() {
        // No instances
    }

    @SuppressWarnings("UnusedReturnValue")
    private static DnsNameResolverBuilder consolidateCacheSize(final MethodHandle consolidateCacheSize,
                                                               final DnsNameResolverBuilder builder,
                                                               final int maxNumConsolidation) {
        try {
            // invokeExact requires return type cast to match the type signature
            return (DnsNameResolverBuilder) consolidateCacheSize.invokeExact(builder, maxNumConsolidation);
        } catch (Throwable t) {
            throwException(t);
            return builder;
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    private static DnsNameResolverBuilder enableTcpFallback(final MethodHandle tcpFallbackOnTimeout,
                                                            final DnsNameResolverBuilder builder,
                                                            final Class<? extends SocketChannel> socketChannelClass,
                                                            final boolean retryOnTimeout) {
        try {
            // invokeExact requires return type cast to match the type signature
            return (DnsNameResolverBuilder) tcpFallbackOnTimeout
                    .invokeExact(builder, socketChannelClass, retryOnTimeout);
        } catch (Throwable t) {
            throwException(t);
            return builder;
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    private static DnsNameResolverBuilder datagramChannelStrategy(final MethodHandle datagramChannelStrategy,
                                                                  final DnsNameResolverBuilder builder,
                                                                  final String strategy) {
        try {
            // invokeExact requires return type cast to match the type signature
            return (DnsNameResolverBuilder) datagramChannelStrategy.invokeExact(builder,
                    LazyHolder.toNettyStrategy(strategy));
        } catch (Throwable t) {
            throwException(t);
            return builder;
        }
    }

    static void consolidateCacheSize(final String id,
                                     final DnsNameResolverBuilder builder,
                                     final int maxNumConsolidation) {
        if (CONSOLIDATE_CACHE_SIZE == null) {
            if (maxNumConsolidation != DEFAULT_CONSOLIDATE_CACHE_SIZE) {
                LOGGER.warn("consolidateCacheSize({}) can not be applied for a new DNS ServiceDiscoverer '{}' " +
                                "because io.netty.resolver.dns.DnsNameResolverBuilder#consolidateCacheSize(int) " +
                                "method is not available in Netty {}, expected Netty version is 4.1.88.Final or later.",
                        maxNumConsolidation, id, NETTY_VERSION);
            }
            return;
        }
        consolidateCacheSize(CONSOLIDATE_CACHE_SIZE, builder, maxNumConsolidation);
    }

    static void enableTcpFallback(final String id,
                                  final DnsNameResolverBuilder builder,
                                  final Class<? extends SocketChannel> socketChannelClass,
                                  final boolean retryOnTimeout) {
        if (TCP_FALLBACK_ON_TIMEOUT == null) {
            if (retryOnTimeout) {
                LOGGER.warn("tcpFallbackOnTimeout({}) can not be applied for a new DNS ServiceDiscoverer '{}' " +
                            "because io.netty.resolver.dns.DnsNameResolverBuilder#socketChannelType(Class, boolean) " +
                            "method is not available in Netty {}, expected Netty version is 4.1.105.Final or later.",
                        retryOnTimeout, id, NETTY_VERSION);
            }
            // Still configure TCP fallback for truncated responses.
            builder.socketChannelType(socketChannelClass);
            return;
        }
        enableTcpFallback(TCP_FALLBACK_ON_TIMEOUT, builder, socketChannelClass, retryOnTimeout);
    }

    static void datagramChannelStrategy(final String id,
                                        final DnsNameResolverBuilder builder,
                                        final String datagramChannelStrategy) {
        if (DATAGRAM_CHANNEL_STRATEGY == null) {
            if (!DEFAULT_DATAGRAM_CHANNEL_STRATEGY.equals(datagramChannelStrategy)) {
                LOGGER.warn("datagramChannelStrategy({}) can not be applied for a new DNS ServiceDiscoverer '{}' " +
                            "because io.netty.resolver.dns.DnsNameResolverBuilder#datagramChannelStrategy(...) " +
                            "method is not available in Netty {}, expected Netty version is 4.1.114.Final or later.",
                        datagramChannelStrategy, id, NETTY_VERSION);
            }
            return;
        }
        datagramChannelStrategy(DATAGRAM_CHANNEL_STRATEGY, builder, datagramChannelStrategy);
    }

    // Defer loading of the class that is available only in Netty 4.1.114.Final or later versions.
    private static final class LazyHolder {
        static DnsNameResolverChannelStrategy toNettyStrategy(final String strategy) {
            try {
                return DnsNameResolverChannelStrategy.valueOf(strategy);
            } catch (IllegalArgumentException e) {
                DnsNameResolverChannelStrategy fallback = DnsNameResolverChannelStrategy.ChannelPerResolver;
                LOGGER.warn("Unknown {} value: {}. Fallback to {}",
                        DnsNameResolverChannelStrategy.class.getName(), strategy, fallback);
                return fallback;
            }
        }
    }
}
