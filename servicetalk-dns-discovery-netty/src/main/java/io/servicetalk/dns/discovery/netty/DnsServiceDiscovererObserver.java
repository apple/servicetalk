/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

/**
 * An observer that provides visibility into <a href="https://tools.ietf.org/html/rfc1034">DNS</a>
 * {@link ServiceDiscoverer} built by {@link DefaultDnsServiceDiscovererBuilder}.
 */
public interface DnsServiceDiscovererObserver {

    /**
     * Notifies that a new {@link ServiceDiscoverer#discover(Object) discovery} started.
     *
     * @param name the name of DNS record to be discovered
     * @return {@link DnsDiscoveryObserver} that provides visibility into individual DNS resolutions behind the
     * associated discovery
     */
    DnsDiscoveryObserver onNewDiscovery(String name);

    /**
     * An observer that provides visibility into individual DNS resolutions.
     */
    interface DnsDiscoveryObserver {

        /**
         * Notifies that a new DNS resolution started.
         *
         * @param name the name for the <a href="https://tools.ietf.org/html/rfc1035#section-4.1.2">DNS question</a> to
         * be queried
         * @return {@link DnsResolutionObserver} that provides visibility into results of the current DNS resolution
         */
        DnsResolutionObserver onNewResolution(String name);
    }

    /**
     * An observer that provides visibility into DNS resolution results.
     */
    interface DnsResolutionObserver {

        /**
         * Notifies that the current DNS resolution failed.
         *
         * @param cause {@link Throwable} as a cause for the failure
         */
        void resolutionFailed(Throwable cause);

        /**
         * Notifies that the current DNS resolution completed successfully.
         *
         * @param result the {@link ResolutionResult}
         */
        void resolutionCompleted(ResolutionResult result);
    }

    /**
     * Results of the current DNS resolution.
     */
    interface ResolutionResult {

        /**
         * Number of resolved DNS records.
         *
         * @return the number of resolved DNS records
         */
        int resolvedRecords();

        /**
         * Minimum Time To Live (TTL) of the resolved DNS records in seconds.
         *
         * @return the minimum Time To Live (TTL) of the resolved DNS records in seconds
         */
        int ttl();

        /**
         * Number of resolved records that became {@link ServiceDiscovererEvent.Status#AVAILABLE available}.
         *
         * @return the number of resolved records that became {@link ServiceDiscovererEvent.Status#AVAILABLE available}
         */
        int nAvailable();

        /**
         * Number of missing records compared to the previous resolution result.
         *
         * @return number of missing records compared to the previous resolution result.
         */
        int nMissing();
    }
}
