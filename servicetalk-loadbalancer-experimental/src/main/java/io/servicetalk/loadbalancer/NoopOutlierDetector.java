/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.loadbalancer;

import io.servicetalk.client.api.LoadBalancedConnection;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;

import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

// A simple outlier detector implementation that only provides the basic `RequestTracker` implementation
// so the P2C LoadBalancingPolicy can still be effective.
final class NoopOutlierDetector<ResolvedAddress, C extends LoadBalancedConnection>
        implements OutlierDetector<ResolvedAddress, C> {

    private final OutlierDetectorConfig outlierDetectorConfig;
    private final Executor executor;

    NoopOutlierDetector(OutlierDetectorConfig outlierDetectorConfig, Executor executor) {
        this.outlierDetectorConfig = requireNonNull(outlierDetectorConfig, "outlierDetectorConfig");
        this.executor = requireNonNull(executor, "executor");
    }

    @Override
    public void cancel() {
        // noop.
    }

    @Override
    public HealthIndicator<ResolvedAddress, C> newHealthIndicator(
            ResolvedAddress resolvedAddress, LoadBalancerObserver.HostObserver hostObserver) {
        return new BasicHealthIndicator();
    }

    @Override
    public Publisher<Void> healthStatusChanged() {
        return Publisher.never();
    }

    private final class BasicHealthIndicator extends DefaultRequestTracker
            implements HealthIndicator<ResolvedAddress, C> {

        BasicHealthIndicator() {
            super(outlierDetectorConfig.ewmaHalfLife().toNanos(), outlierDetectorConfig.ewmaCancellationPenalty(),
                    outlierDetectorConfig.ewmaCancellationPenalty(), outlierDetectorConfig.pendingRequestPenalty());
        }

        @Override
        public void cancel() {
            // noop
        }

        @Override
        public long beforeConnectStart() {
            return currentTimeNanos();
        }

        @Override
        public void onConnectSuccess(long beforeConnectStart) {
            // noop
        }

        @Override
        public void onConnectError(long beforeConnectStart, ConnectTracker.ErrorClass errorClass) {
            // noop
        }

        @Override
        protected long currentTimeNanos() {
            return executor.currentTime(TimeUnit.NANOSECONDS);
        }

        @Override
        public boolean isHealthy() {
            return true;
        }

        @Override
        public void setHost(Host<ResolvedAddress, C> host) {
            // noop
        }
    }
}
