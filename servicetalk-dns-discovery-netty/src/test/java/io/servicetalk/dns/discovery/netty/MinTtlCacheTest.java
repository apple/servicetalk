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

import io.netty.channel.EventLoop;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.resolver.dns.DnsCache;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MinTtlCacheTest {

    private static final DnsRecord[] EMPTY_ADDITIONALS = new DnsRecord[0];
    private static final String HOSTNAME = "target.example.com";
    private static final long INITIAL_TTL = 5;
    private static final long ORIGINAL_TTL = 20;

    @Test
    void clearHostClearsMinTtlState() {
        DnsCache delegate = mock(DnsCache.class);
        when(delegate.clear(HOSTNAME)).thenReturn(true);
        MinTtlCache cache = newCache(delegate);
        cacheAddress(cache);

        assertThat(cache.minTtl(HOSTNAME), is(ORIGINAL_TTL));
        assertThat(cache.clear(HOSTNAME), is(true));
        assertThat(cache.minTtl(HOSTNAME), is(INITIAL_TTL));
        verify(delegate).clear(HOSTNAME);
    }

    @Test
    void clearMinTtlDoesNotClearDelegateCache() {
        DnsCache delegate = mock(DnsCache.class);
        MinTtlCache cache = newCache(delegate);
        cacheAddress(cache);
        clearInvocations(delegate);

        cache.clearMinTtl(HOSTNAME);

        assertThat(cache.minTtl(HOSTNAME), is(INITIAL_TTL));
        verifyNoInteractions(delegate);
    }

    private static MinTtlCache newCache(final DnsCache delegate) {
        return new MinTtlCache(delegate, INITIAL_TTL, unit -> 0);
    }

    private static void cacheAddress(final MinTtlCache cache) {
        cache.cache(HOSTNAME, EMPTY_ADDITIONALS, InetAddress.getLoopbackAddress(), ORIGINAL_TTL,
                mock(EventLoop.class));
    }
}
