/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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

import io.netty.resolver.dns.DnsNameResolverBuilder;
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

    @Nullable
    private static final MethodHandle CONSOLIDATE_CACHE_SIZE;

    static {
        MethodHandle consolidateCacheSize;
        try {
            // Find a new method that exists only in Netty starting from 4.1.88.Final:
            // https://github.com/netty/netty/commit/d010e63bf5bf744f2ab6d0fc4386611efe7954e6
            consolidateCacheSize = MethodHandles.publicLookup()
                    .findVirtual(DnsNameResolverBuilder.class, "consolidateCacheSize",
                            methodType(DnsNameResolverBuilder.class, int.class));
            // Verify the method is working as expected:
            consolidateCacheSize(consolidateCacheSize, new DnsNameResolverBuilder(), 1);
        } catch (Throwable cause) {
            LOGGER.debug("DnsNameResolverBuilder#consolidateCacheSize(int) is available only starting from " +
                            "Netty 4.1.88.Final. Detected Netty version: {}", NETTY_VERSION, cause);
            consolidateCacheSize = null;
        }
        CONSOLIDATE_CACHE_SIZE = consolidateCacheSize;
    }

    private DnsNameResolverBuilderUtils() {
        // No instances
    }

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
}
