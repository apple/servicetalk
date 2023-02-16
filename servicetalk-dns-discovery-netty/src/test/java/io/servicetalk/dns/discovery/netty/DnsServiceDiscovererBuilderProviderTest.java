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

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DnsServiceDiscovererBuilderProviderTest {

    private static final AtomicInteger buildCounter = new AtomicInteger();
    private static final AtomicReference<String> buildId = new AtomicReference<>();

    @Test
    void appliesBuilderProvider() {
        assertEquals(0, buildCounter.get());
        assertNotNull(DnsServiceDiscoverers.builder("test"));
        assertEquals(1, buildCounter.get());
        assertEquals("test", buildId.get());
    }

    public static final class TestDnsServiceDiscovererBuilderProvider
            implements DnsServiceDiscovererBuilderProvider {
        @Override
        public DnsServiceDiscovererBuilder newBuilder(final String id, final DnsServiceDiscovererBuilder builder) {
            buildCounter.incrementAndGet();
            buildId.set(id);
            return new DelegatingDnsServiceDiscovererBuilder(builder);
        }
    }
}
