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

import java.time.Duration;

import static io.servicetalk.client.api.ServiceDiscovererEvent.Status.AVAILABLE;
import static io.servicetalk.client.api.ServiceDiscovererEvent.Status.EXPIRED;
import static io.servicetalk.client.api.ServiceDiscovererEvent.Status.UNAVAILABLE;
import static java.lang.Integer.MAX_VALUE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DnsServiceDiscovererBuilderValidationTest {

    private final DnsServiceDiscovererBuilder builder = DnsServiceDiscoverers.builder(getClass().getSimpleName());

    @Test
    void id() {
        assertThrows(NullPointerException.class, () -> DnsServiceDiscoverers.builder(null));
        assertThrows(IllegalArgumentException.class, () -> DnsServiceDiscoverers.builder(""));
    }

    @Test
    void ttl() {
        assertThrows(IllegalArgumentException.class, () -> builder.ttl(-1, 5));
        assertThrows(IllegalArgumentException.class, () -> builder.ttl(0, 5));
        assertThrows(IllegalArgumentException.class, () -> builder.ttl(6, 5));
        assertThrows(IllegalArgumentException.class, () -> builder.ttl(1, -1));
        assertThrows(IllegalArgumentException.class, () -> builder.ttl(1, 0));

        assertDoesNotThrow(() -> builder.ttl(1, 1));
        assertDoesNotThrow(() -> builder.ttl(1, 5));
        assertDoesNotThrow(() -> builder.ttl(1, MAX_VALUE));
    }

    @Test
    void ttlWithCache() {
        assertThrows(IllegalArgumentException.class, () -> builder.ttl(1, 5, -1, 3));
        assertThrows(IllegalArgumentException.class, () -> builder.ttl(1, 5, 2, 3));
        assertThrows(IllegalArgumentException.class, () -> builder.ttl(1, 5, 1, -1));
        assertThrows(IllegalArgumentException.class, () -> builder.ttl(1, 5, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> builder.ttl(1, 5, 1, 6));

        assertDoesNotThrow(() -> builder.ttl(1, 1, 0, 0));
        assertDoesNotThrow(() -> builder.ttl(1, 1, 1, 1));
        assertDoesNotThrow(() -> builder.ttl(1, 3, 1, 3));
        assertDoesNotThrow(() -> builder.ttl(1, MAX_VALUE, 1, MAX_VALUE));
        assertDoesNotThrow(() -> builder.ttl(1, 3, 0, 2));
        assertDoesNotThrow(() -> builder.ttl(1, 3, 1, 2));
    }

    @Test
    void ttlJitter() {
        assertThrows(IllegalArgumentException.class, () -> builder.ttlJitter(Duration.ofNanos(1).negated()));
        assertThrows(IllegalArgumentException.class, () -> builder.ttlJitter(Duration.ZERO));
        assertDoesNotThrow(() -> builder.ttlJitter(Duration.ofNanos(1)));
    }

    @Test
    void maxUdpPayloadSize() {
        assertThrows(IllegalArgumentException.class, () -> builder.maxUdpPayloadSize(-1));
        assertThrows(IllegalArgumentException.class, () -> builder.maxUdpPayloadSize(0));
        assertDoesNotThrow(() -> builder.maxUdpPayloadSize(1));
    }

    @Test
    void missingRecordStatus() {
        assertThrows(IllegalArgumentException.class, () -> builder.missingRecordStatus(AVAILABLE));
        assertDoesNotThrow(() -> builder.missingRecordStatus(EXPIRED));
        assertDoesNotThrow(() -> builder.missingRecordStatus(UNAVAILABLE));
    }
}
