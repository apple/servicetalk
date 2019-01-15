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

import io.netty.channel.EventLoop;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.resolver.dns.DnsCache;
import io.netty.resolver.dns.DnsCacheEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

import static java.lang.Math.max;

final class MinTtlCache implements DnsCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(MinTtlCache.class);

    private final DnsCache cache;
    private final long initialTtl;
    private final Map<String, Long> minTtlMap = new HashMap<>();

    MinTtlCache(final DnsCache cache) {
        this(cache, 2);
    }

    MinTtlCache(final DnsCache cache, final long initialTtl) {
        this.cache = cache;
        this.initialTtl = initialTtl;
    }

    void prepareForResolution(final String hostname) {
        minTtlMap.remove(hostname.toLowerCase());
    }

    long minTtl(final String hostname) {
        final Long minTtl = minTtlMap.get(hostname.toLowerCase());
        return minTtl == null ? initialTtl : minTtl;
    }

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public boolean clear(final String hostname) {
        return cache.clear(hostname);
    }

    @Nullable
    @Override
    public List<? extends DnsCacheEntry> get(final String hostname, final DnsRecord[] additionals) {
        final List<? extends DnsCacheEntry> entries = cache.get(hostname, additionals);
        if (entries != null) {
            // This means that either:
            //  1. there were multiple `discover` calls for the same hostname (on `DefaultDnsServiceDiscoverer`), or
            //  2. the scheduled lookup happened before the cache expired the entries.
            // #1 is ok. #2 means that stale results will be returned until the next TTL scheduled lookup.
            LOGGER.debug("Found cached entries for {}: {}", hostname, entries);
        }
        return entries;
    }

    @Override
    public DnsCacheEntry cache(final String hostname, final DnsRecord[] additionals, final InetAddress address, final long originalTtl, final EventLoop loop) {
        minTtlMap.merge(hostname.toLowerCase(), max(initialTtl, originalTtl), Math::min);
        return cache.cache(hostname, additionals, address, originalTtl, loop);
    }

    @Override
    public DnsCacheEntry cache(final String hostname, final DnsRecord[] additionals, final Throwable cause, final EventLoop loop) {
        return cache.cache(hostname, additionals, cause, loop);
    }
}
