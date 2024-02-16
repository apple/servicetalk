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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.internal.SequentialCancellable;
import io.servicetalk.loadbalancer.LoadBalancerObserver.HostObserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Math.max;
import static java.util.Objects.requireNonNull;

/**
 * A {@link HealthChecker} implementation that supports xDS outlier detector configuration.
 * <p>
 * The xDS is a protocol originated in the <a href='https://www.envoyproxy.io'>Envoy</a> project and defines a standard
 * protocol for configuring a data plane as envisioned in the blog post <a href="https://blog.envoyproxy.io/
 * the-universal-data-plane-api-d15cec7a">The universal data plane API</a>. The xDS protocol has since evolved into a
 * CNCF project driven by the <a href="https://github.com/cncf/xds">xDS API Working Group (xDS-WG)</a> with the goal of
 * making the xDS protocol client neutral.
 * <p>
 * ServiceTalk load balancers aim to consume the xDS protocol to facilitate first class integration with xDS based
 * control planes. Client configuration includes a set of <a href="https://www.envoyproxy.io/docs/envoy/v1.29.0/intro/
 * arch_overview/upstream/outlier.html">outlier detectors</a> implemented by Envoy and other xDS compatible clients such
 * as <a href="https://grpc.io"> gRPC</a>.
 * <p>
 * The ServiceTalk xDS health checker implements most of the outlier detectors supported by the xDS protocol v3. It
 * supports the consecutive failure detection, success rate outlier detection, and the fixed value failure percentage
 * outlier detector.
 * <p>
 * xDS is not strictly required to use the xDS outlier detector implementations: xDS outlier detectors can be configured
 * by the application without a control plane.
 * @param <ResolvedAddress> the type of the resolved address.
 */
final class XdsHealthChecker<ResolvedAddress> implements HealthChecker<ResolvedAddress> {

    private static final Logger LOGGER = LoggerFactory.getLogger(XdsHealthChecker.class);

    private final SequentialExecutor sequentialExecutor;
    private final Executor executor;
    private final String lbDescription;
    private final Kernel kernel;
    private final AtomicInteger indicatorCount = new AtomicInteger();
    // Protected by `sequentialExecutor`.
    private final Set<XdsHealthIndicator> indicators = new HashSet<>();
    // reads and writes are protected by `sequentialExecutor`.
    private int ejectedHostCount;

    XdsHealthChecker(final Executor executor, final OutlierDetectorConfig config, String lbDescription) {
        this.sequentialExecutor = new SequentialExecutor((uncaughtException) ->
            LOGGER.error("{}: Uncaught exception in " + this.getClass().getSimpleName(), this, uncaughtException));
        this.executor = requireNonNull(executor, "executor");
        this.lbDescription = requireNonNull(lbDescription, "lbDescription");
        this.kernel = new Kernel(config);
    }

    @Override
    public HealthIndicator newHealthIndicator(ResolvedAddress address, HostObserver hostObserver) {
        XdsHealthIndicator result = new XdsHealthIndicatorImpl(address, kernel.config.ewmaHalfLife(), hostObserver);
        sequentialExecutor.execute(() -> indicators.add(result));
        indicatorCount.incrementAndGet();
        return result;
    }

    @Override
    public void cancel() {
        kernel.cancel();
        sequentialExecutor.execute(() -> {
            List<XdsHealthIndicator> indicatorList = new ArrayList<>(indicators);
            for (XdsHealthIndicator indicator : indicatorList) {
                indicator.cancel();
            }
            assert indicators.isEmpty();
            assert indicatorCount.get() == 0;
        });
    }

    private final class XdsHealthIndicatorImpl extends XdsHealthIndicator<ResolvedAddress> {

        XdsHealthIndicatorImpl(final ResolvedAddress address, Duration ewmaHalfLife, HostObserver hostObserver) {
            super(sequentialExecutor, executor, ewmaHalfLife, address, lbDescription, hostObserver);
        }

        @Override
        protected OutlierDetectorConfig currentConfig() {
            return kernel.config;
        }

        @Override
        public boolean tryEjectHost() {
            assert sequentialExecutor.isCurrentThreadDraining();
            final int maxEjected = indicatorCount.get() * currentConfig().maxEjectionPercentage() / 100;
            if (ejectedHostCount >= maxEjected) {
                return false;
            } else {
                ejectedHostCount++;
                return true;
            }
        }

        @Override
        public void hostRevived() {
            assert sequentialExecutor.isCurrentThreadDraining();
            ejectedHostCount--;
        }

        @Override
        public void doCancel() {
            assert sequentialExecutor.isCurrentThreadDraining();
            if (indicators.remove(this)) {
                indicatorCount.decrementAndGet();
            }
        }
    }

    // TODO: if we make configuration dynamic we can do so by making new instances of `Kernel` when we get a new
    //  config update. However, we'll need to make a `Helper` that dynamically forwards to the latest `Kernel`.
    private final class Kernel {
        private final SequentialCancellable cancellable;
        private final List<XdsOutlierDetector> outlierDetectors;
        private final OutlierDetectorConfig config;

        Kernel(final OutlierDetectorConfig config) {
            this.config = requireNonNull(config, "config");
            this.outlierDetectors = getDetectors(config);
            this.cancellable = new SequentialCancellable(scheduleNextOutliersCheck(config));
        }

        public void cancel() {
            cancellable.cancel();
        }

        private Cancellable scheduleNextOutliersCheck(OutlierDetectorConfig currentConfig) {
            Runnable checkOutliers = () -> sequentialExecutor.execute(this::sequentialCheckOutliers);
            return executor.schedule(checkOutliers, currentConfig.interval());
        }

        private void sequentialCheckOutliers() {
            assert sequentialExecutor.isCurrentThreadDraining();
            for (XdsOutlierDetector outlierDetector : outlierDetectors) {
                outlierDetector.detectOutliers(config, indicators);
            }
            cancellable.nextCancellable(scheduleNextOutliersCheck(config));
        }
    }

    private static List<XdsOutlierDetector> getDetectors(OutlierDetectorConfig config) {
        List<XdsOutlierDetector> detectors = new ArrayList<>(2);
        if (config.enforcingFailurePercentage() > 0) {
            detectors.add(FailurePercentageXdsOutlierDetector.INSTANCE);
        }
        if (config.enforcingSuccessRate() > 0) {
            detectors.add(SuccessRateXdsOutlierDetector.INSTANCE);
        }
        // We need at least one failure detector so that we can decrement the failure multiplier on each interval.
        if (detectors.isEmpty()) {
            detectors.add(AlwaysHealthyOutlierDetector.INSTANCE);
        }
        return detectors;
    }

    private static final class AlwaysHealthyOutlierDetector implements XdsOutlierDetector {

        static final XdsOutlierDetector INSTANCE = new AlwaysHealthyOutlierDetector();

        private AlwaysHealthyOutlierDetector() {
            // singleton.
        }

        @Override
        public void detectOutliers(OutlierDetectorConfig config, Collection<XdsHealthIndicator> indicators) {
            int unhealthy = 0;
            for (XdsHealthIndicator indicator : indicators) {
                // Hosts can still be marked unhealthy due to consecutive failures.
                final boolean isHealthy = indicator.isHealthy();
                if (isHealthy) {
                    // If the indicator is healthy we need to mark it as health to make sure
                    // we decrement the failure multiplier.
                    indicator.updateOutlierStatus(config, false);
                } else {
                    unhealthy++;
                }
            }
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("NoopOutlierDetector found {} unhealthy instances out of a total of {}.",
                        unhealthy, indicators.size());
            }
        }
    }
}
