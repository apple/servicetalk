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
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.SourceAdapters;
import io.servicetalk.concurrent.internal.SequentialCancellable;
import io.servicetalk.loadbalancer.LoadBalancerObserver.HostObserver;
import io.servicetalk.utils.internal.RandomUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.servicetalk.concurrent.api.Processors.newPublisherProcessorDropHeadOnOverflow;
import static io.servicetalk.concurrent.internal.FlowControlUtils.addWithOverflowProtection;
import static java.lang.Math.max;
import static java.util.Objects.requireNonNull;

/**
 * A {@link OutlierDetector} implementation that supports xDS outlier detector configuration.
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
final class XdsOutlierDetector<ResolvedAddress, C extends LoadBalancedConnection>
        implements OutlierDetector<ResolvedAddress, C> {

    private static final Logger LOGGER = LoggerFactory.getLogger(XdsOutlierDetector.class);

    private final OutlierDetectorConfig outlierDetectorConfig;
    private final SequentialExecutor sequentialExecutor;
    private final Executor executor;
    private final String lbDescription;
    private final PublisherSource.Processor<Void, Void> healthStatusChangeProcessor =
            newPublisherProcessorDropHeadOnOverflow(1);
    private final Kernel kernel;
    private final AtomicInteger indicatorCount = new AtomicInteger();
    // Protected by `sequentialExecutor`.
    private final Set<XdsHealthIndicatorImpl> indicators = new HashSet<>();
    // reads and writes are protected by `sequentialExecutor`.
    private int ejectedHostCount;

    XdsOutlierDetector(final Executor executor, final OutlierDetectorConfig outlierDetectorConfig,
                       final String lbDescription, SequentialExecutor.ExceptionHandler exceptionHandler) {
        this.sequentialExecutor = new SequentialExecutor(exceptionHandler);
        this.outlierDetectorConfig = requireNonNull(outlierDetectorConfig, "outlierDetectorConfig");
        this.executor = requireNonNull(executor, "executor");
        this.lbDescription = requireNonNull(lbDescription, "lbDescription");
        this.kernel = new Kernel(outlierDetectorConfig);
    }

    XdsOutlierDetector(final Executor executor, final OutlierDetectorConfig config, final String lbDescription) {
        this(executor, config, lbDescription, (uncaughtException) -> LOGGER.error("{}: Uncaught exception in {}",
                lbDescription, XdsOutlierDetector.class.getSimpleName(), uncaughtException));
    }

    @Override
    public HealthIndicator<ResolvedAddress, C> newHealthIndicator(ResolvedAddress address, HostObserver hostObserver) {
        XdsHealthIndicatorImpl result = new XdsHealthIndicatorImpl(
                address, kernel.config, hostObserver);
        sequentialExecutor.execute(() -> indicators.add(result));
        indicatorCount.incrementAndGet();
        return result;
    }

    @Override
    public void cancel() {
        kernel.cancel();
        sequentialExecutor.execute(() -> {
            List<XdsHealthIndicator<ResolvedAddress, C>> indicatorList = new ArrayList<>(indicators);
            for (XdsHealthIndicator<ResolvedAddress, C> indicator : indicatorList) {
                indicator.sequentialCancel();
            }
            assert indicators.isEmpty();
            assert indicatorCount.get() == 0;
            healthStatusChangeProcessor.onComplete();
        });
    }

    @Override
    public Publisher<Void> healthStatusChanged() {
        return SourceAdapters.fromSource(healthStatusChangeProcessor);
    }

    // Exposed for testing. Not thread safe.
    int ejectedHostCount() {
        return ejectedHostCount;
    }

    @Override
    public String toString() {
        return "XdsOutlierDetector{" +
                "lbDescription=" + lbDescription +
                ", outlierDetectorConfig=" + outlierDetectorConfig +
                ", executor=" + executor +
                '}';
    }

    private final class XdsHealthIndicatorImpl extends XdsHealthIndicator<ResolvedAddress, C> {

        // Protected by `sequentialExecutor`.
        private boolean lastObservedHealthy = true;

        XdsHealthIndicatorImpl(final ResolvedAddress address, OutlierDetectorConfig outlierDetectorConfig,
                               HostObserver hostObserver) {
            super(sequentialExecutor, executor, outlierDetectorConfig.ewmaHalfLife(),
                    outlierDetectorConfig.ewmaCancellationPenalty(), outlierDetectorConfig.ewmaErrorPenalty(),
                    outlierDetectorConfig.concurrentRequestPenalty(), outlierDetectorConfig.cancellationIsError(),
                    address, lbDescription, hostObserver);
        }

        @Override
        OutlierDetectorConfig currentConfig() {
            return kernel.config;
        }

        @Override
        public boolean tryEjectHost() {
            assert sequentialExecutor.isCurrentThreadDraining();
            final int maxEjected = max(1, indicatorCount.get() * currentConfig().maxEjectionPercentage() / 100);
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
        private final List<XdsOutlierDetectorAlgorithm<ResolvedAddress, C>> algorithms;
        private final OutlierDetectorConfig config;

        Kernel(final OutlierDetectorConfig config) {
            this.config = requireNonNull(config, "config");
            this.algorithms = getAlgorithms(config);
            this.cancellable = new SequentialCancellable(scheduleNextOutliersCheck(config));
        }

        public void cancel() {
            cancellable.cancel();
        }

        private Cancellable scheduleNextOutliersCheck(OutlierDetectorConfig currentConfig) {
            Runnable checkOutliers = () -> sequentialExecutor.execute(this::sequentialCheckOutliers);
            final long minIntervalNanos = currentConfig.failureDetectorInterval().toNanos() -
                    currentConfig.failureDetectorIntervalJitter().toNanos();
            final long maxIntervalNanos = addWithOverflowProtection(currentConfig.failureDetectorInterval().toNanos(),
                    currentConfig.failureDetectorIntervalJitter().toNanos());
            return executor.schedule(checkOutliers, RandomUtils.nextLongInclusive(minIntervalNanos, maxIntervalNanos),
                    TimeUnit.NANOSECONDS);
        }

        private void sequentialCheckOutliers() {
            assert sequentialExecutor.isCurrentThreadDraining();

            for (XdsOutlierDetectorAlgorithm<ResolvedAddress, C> outlierDetector : algorithms) {
                outlierDetector.detectOutliers(config, indicators);
            }
            cancellable.nextCancellable(scheduleNextOutliersCheck(config));

            // Check to see if any of our health states changed from the previous scan and fire an event if they did.
            boolean emitChange = false;
            for (XdsHealthIndicatorImpl indicator : indicators) {
                boolean currentlyIsHealthy = indicator.isHealthy();
                if (indicator.lastObservedHealthy != currentlyIsHealthy) {
                    indicator.lastObservedHealthy = currentlyIsHealthy;
                    emitChange = true;
                }
            }
            if (emitChange) {
                LOGGER.debug("Health status change observed. Emitting event.");
                healthStatusChangeProcessor.onNext(null);
            }
        }
    }

    private List<XdsOutlierDetectorAlgorithm<ResolvedAddress, C>> getAlgorithms(OutlierDetectorConfig config) {
        List<XdsOutlierDetectorAlgorithm<ResolvedAddress, C>> detectors = new ArrayList<>(2);
        if (config.enforcingFailurePercentage() > 0) {
            detectors.add(new FailurePercentageXdsOutlierDetectorAlgorithm<>());
        }
        if (config.enforcingSuccessRate() > 0) {
            detectors.add(new SuccessRateXdsOutlierDetectorAlgorithm<>());
        }
        // We need at least one failure detector so that we can decrement the failure multiplier on each interval.
        if (detectors.isEmpty()) {
            detectors.add(new AlwaysHealthyOutlierDetectorAlgorithm<>());
        }
        return detectors;
    }

    private static final class AlwaysHealthyOutlierDetectorAlgorithm<ResolvedAddress, C extends LoadBalancedConnection>
            implements XdsOutlierDetectorAlgorithm<ResolvedAddress, C> {

        @Override
        public void detectOutliers(final OutlierDetectorConfig config,
                                   final Collection<? extends XdsHealthIndicator<ResolvedAddress, C>> indicators) {
            int unhealthy = 0;
            for (XdsHealthIndicator<ResolvedAddress, C> indicator : indicators) {
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
