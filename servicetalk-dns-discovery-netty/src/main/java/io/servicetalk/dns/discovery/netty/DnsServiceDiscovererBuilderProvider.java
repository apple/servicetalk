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

/**
 * Provider for {@link DnsServiceDiscovererBuilder}.
 */
@FunctionalInterface
public interface DnsServiceDiscovererBuilderProvider {

    /**
     * Returns a {@link DnsServiceDiscovererBuilder} based on the (unique) id and
     * pre-initialized {@link DnsServiceDiscovererBuilder}.
     * <p>
     * This method may return the pre-initialized {@code builder} as-is, or apply custom builder settings before
     * returning it, or wrap it ({@link DelegatingDnsServiceDiscovererBuilder} may be helpful).
     *
     * @param id a (unique) identifier used to identify the underlying {@link DnsClient}.
     * @param builder pre-initialized {@link DnsServiceDiscovererBuilder}.
     * @return a {@link DnsServiceDiscovererBuilder} based on the unique ID and the
     * pre-initialized {@link DnsServiceDiscovererBuilder}.
     * @see DelegatingDnsServiceDiscovererBuilder
     */
    DnsServiceDiscovererBuilder newBuilder(String id, DnsServiceDiscovererBuilder builder);
}
