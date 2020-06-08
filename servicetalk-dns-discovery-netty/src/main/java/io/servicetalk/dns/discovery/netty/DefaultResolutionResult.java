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

import io.servicetalk.dns.discovery.netty.DnsServiceDiscovererObserver.ResolutionResult;

final class DefaultResolutionResult implements ResolutionResult {

    private final int resolvedRecords;
    private final int ttl;
    private final int nAvailable;
    private final int nUnavailable;

    DefaultResolutionResult(final int resolvedRecords, final int ttl,
                            final int nAvailable, final int nUnavailable) {
        this.resolvedRecords = resolvedRecords;
        this.ttl = ttl;
        this.nAvailable = nAvailable;
        this.nUnavailable = nUnavailable;
    }

    @Override
    public int resolvedRecords() {
        return resolvedRecords;
    }

    @Override
    public int ttl() {
        return ttl;
    }

    @Override
    public int nAvailable() {
        return nAvailable;
    }

    @Override
    public int nUnavailable() {
        return nUnavailable;
    }

    @Override
    public String toString() {
        return "DefaultResolutionResult{" +
                "resolvedRecords=" + resolvedRecords +
                ", ttl=" + ttl +
                ", nAvailable=" + nAvailable +
                ", nUnavailable=" + nUnavailable +
                '}';
    }
}
