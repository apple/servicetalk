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

import io.servicetalk.dns.discovery.netty.DnsServiceDiscovererObserver.DnsDiscoveryObserver.DnsResolutionObserver.ResolutionResult;

final class DefaultResolutionResult implements ResolutionResult {

    private final int resolvedRecords;
    private final int ttl;
    private final int becameActive;
    private final int becameInactive;

    DefaultResolutionResult(final int resolvedRecords, final int ttl,
                            final int becameActive, final int becameInactive) {
        this.resolvedRecords = resolvedRecords;
        this.ttl = ttl;
        this.becameActive = becameActive;
        this.becameInactive = becameInactive;
    }

    public int resolvedRecords() {
        return resolvedRecords;
    }

    @Override
    public int ttl() {
        return ttl;
    }

    @Override
    public int becameActive() {
        return becameActive;
    }

    @Override
    public int becameInactive() {
        return becameInactive;
    }

    @Override
    public String toString() {
        return "DefaultResolutionResult{" +
                "resolvedRecords=" + resolvedRecords +
                ", ttl=" + ttl +
                ", becameActive=" + becameActive +
                ", becameInactive=" + becameInactive +
                '}';
    }
}
