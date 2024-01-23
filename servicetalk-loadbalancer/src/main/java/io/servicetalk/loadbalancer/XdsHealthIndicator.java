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

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.loadbalancer.LoadBalancerObserver.HostObserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;

import static io.servicetalk.loadbalancer.OutlierDetectorConfig.enforcing;
import static java.lang.Math.max;
import static java.util.Objects.requireNonNull;

abstract class XdsHealthIndicator<ResolvedAddress> extends DefaultRequestTracker implements HealthIndicator {

    private static final Logger LOGGER = LoggerFactory.getLogger(XdsHealthIndicator.class);

    private static final Throwable CONSECUTIVE_5XX_CAUSE = new EjectedCause("consecutive 5xx");
    private static final Throwable OUTLIER_DETECTOR_CAUSE = new EjectedCause("outlier detector");

    private final SequentialExecutor sequentialExecutor;
    private final Executor executor;
    private final HostObserver<ResolvedAddress> hostObserver;
    private final ResolvedAddress address;
    private final AtomicInteger consecutive5xx = new AtomicInteger();
    private final AtomicLong successes = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();


    // reads and writes protected by the helpers `SequentialExecutor`.
    private boolean cancelled;
    // reads and writes protected by the helpers `SequentialExecutor`.
    private int failureMultiplier;
    // Any thread can read this value at any time but mutations are serialized by the `SequentialExecutor`.
    @Nullable
    private volatile Long evictedUntilNanos;

    XdsHealthIndicator(final SequentialExecutor sequentialExecutor, final Executor executor,
                       final ResolvedAddress address, final HostObserver<ResolvedAddress> hostObserver) {
        super(1);
        this.sequentialExecutor = requireNonNull(sequentialExecutor, "sequentialExecutor");
        this.executor = requireNonNull(executor, "executor");
        assert executor instanceof NormalizedTimeSourceExecutor;
        this.address = requireNonNull(address, "address");
        this.hostObserver = requireNonNull(hostObserver, "hostObserver");
    }

    /**
     * Get the current configuration.
     * @return the current configuration.
     */
    protected abstract OutlierDetectorConfig currentConfig();

    /**
     * Attempt to mark the host as ejected with the parent XDS health checker.
     * @return whether this host was successfully ejected.
     */
    protected abstract boolean tryEjectHost();

    /**
     * Alert the parent {@link XdsHealthChecker} that this host has transitions from healthy to unhealthy.
     */
    protected abstract void hostRevived();

    /**
     * Alert the parent {@link XdsHealthChecker} that this {@link HealthIndicator} is no longer being used.
     */
    protected abstract void doCancel();

    @Override
    protected final long currentTimeNanos() {
        return executor.currentTime(TimeUnit.NANOSECONDS);
    }

    @Override
    public final boolean isHealthy() {
        final Long evictedUntilNanos = this.evictedUntilNanos;
        if (evictedUntilNanos == null) {
            return true;
        }
        // Envoy technically will perform revival (un-ejection) on the same timer as the outlier detection. If we want
        // to remove some overhead from the sad path we can go to that at the cost of leaving hosts unhealthy longer
        // than the eviction time technically prescribes.
        if (evictedUntilNanos <= currentTimeNanos()) {
            sequentialExecutor.execute(() -> {
                if (!cancelled && this.evictedUntilNanos != null && this.evictedUntilNanos <= currentTimeNanos()) {
                    sequentialRevive();
                }
            });
            return true;
        }

        // We're either cancelled or still evicted.
        return false;
    }

    @Override
    public final void onSuccess(final long beforeStartTimeNs) {
        super.onSuccess(beforeStartTimeNs);
        successes.incrementAndGet();
        consecutive5xx.set(0);
        LOGGER.trace("Observed success for address {}", address);
    }

    @Override
    public final void onError(final long beforeStartTimeNs) {
        super.onError(beforeStartTimeNs);
        failures.incrementAndGet();
        final int consecutiveFailures = consecutive5xx.incrementAndGet();
        final OutlierDetectorConfig localConfig = currentConfig();
        if (consecutiveFailures >= localConfig.consecutive5xx() && enforcing(localConfig.enforcingConsecutive5xx())) {
            sequentialExecutor.execute(() -> {
                if (!cancelled && evictedUntilNanos == null &&
                        sequentialTryEject(currentConfig(), CONSECUTIVE_5XX_CAUSE) && // this performs side effects.
                        LOGGER.isDebugEnabled()) {
                    LOGGER.debug("address {}: observed error which did result in consecutive 5xx ejection. " +
                                    "Consecutive 5xx: {}, limit: {}.", address, consecutiveFailures,
                            localConfig.consecutive5xx());
                }
            });
        } else {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("address {}: observed error which didn't result in ejection. " +
                        "Consecutive 5xx: {}, limit: {}", address, consecutiveFailures, localConfig.consecutive5xx());
            }
        }
    }

    public final void forceRevival() {
        assert sequentialExecutor.isCurrentThreadDraining();
        if (!cancelled && evictedUntilNanos != null) {
            sequentialRevive();
        }
    }

    public final boolean updateOutlierStatus(OutlierDetectorConfig config, boolean isOutlier) {
        assert sequentialExecutor.isCurrentThreadDraining();
        if (cancelled) {
            return false;
        }

        Long evictedUntilNanos = this.evictedUntilNanos;
        if (evictedUntilNanos != null) {
            if (evictedUntilNanos <= currentTimeNanos()) {
                sequentialRevive();
            }
            // If we are evicted or just transitioned out of eviction we shouldn't be marked as  an outlier this round.
            // Note that this differs from the envoy behavior. If we want to mimic it, then I think we need to just
            // fall through and maybe attempt to eject again.
            LOGGER.trace("address {}: markAsOutlier(..) resulted in host revival.", address);
            return false;
        } else if (isOutlier) {
            final boolean result = sequentialTryEject(config, OUTLIER_DETECTOR_CAUSE);
            if (result) {
                LOGGER.debug("address {}: markAsOutlier(isOutlier = true) resulted in ejection. " +
                        "Failure multiplier: {}.", address, failureMultiplier);
            } else {
                LOGGER.trace("address {}: markAsOutlier(isOutlier = true) did not result in ejection. " +
                        "Failure multiplier: {}.", address, failureMultiplier);
            }
            return result;
        } else {
            // All we have to do is decrement our failure multiplier.
            failureMultiplier = max(0, failureMultiplier - 1);
            LOGGER.trace("address {}: markAsOutlier(isOutlier = false). " +
                    "Failure multiplier: {}", address, failureMultiplier);
            return false;
        }
    }

    public final void resetCounters() {
        successes.set(0);
        failures.set(0);
    }

    public final long getSuccesses() {
        return successes.get();
    }

    public final long getFailures() {
        return failures.get();
    }

    @Override
    public final void cancel() {
        sequentialExecutor.execute(this::sequentialCancel);
    }

    private void sequentialCancel() {
        assert sequentialExecutor.isCurrentThreadDraining();
        if (cancelled) {
            return;
        }
        if (evictedUntilNanos != null) {
            sequentialRevive();
        }
        cancelled = true;
        doCancel();
    }

    private boolean sequentialTryEject(OutlierDetectorConfig config, Throwable cause) {
        assert sequentialExecutor.isCurrentThreadDraining();
        assert evictedUntilNanos == null;

        if (!tryEjectHost()) {
            return false;
        }
        // See if we can increase the multiplier or not.
        long baseEjectNanos = config.baseEjectionTime().toNanos();
        long ejectTimeNanos = baseEjectNanos * (1 + failureMultiplier);
        if (ejectTimeNanos >= config.maxEjectionTime().toNanos()) {
            // We've overflowed the max ejection time so trim it down and add jitter.
            ejectTimeNanos = config.maxEjectionTime().toNanos();
        } else {
            failureMultiplier++;
        }
        // Finally we add jitter to the ejection time.
        final long jitterNanos = ThreadLocalRandom.current().nextLong(config.maxEjectionTimeJitter().toNanos() + 1);
        evictedUntilNanos = currentTimeNanos() + ejectTimeNanos + jitterNanos;
        hostObserver.onHostMarkedUnhealthy(address, cause);
        return true;
    }

    private void sequentialRevive() {
        assert sequentialExecutor.isCurrentThreadDraining();
        assert !cancelled;
        evictedUntilNanos = null;
        // Envoy resets the `consecutive5xx` counter on revival. I'm not sure that's the best because chances
        // are reasonable that it's still a bad host, so we'll want to mark it as an outlier again immediately if
        // the next request also fails.
        hostRevived();
        hostObserver.onHostRevived(address);
    }

    private static final class EjectedCause extends Exception {

        private static final long serialVersionUID = 7474789866778792264L;

        EjectedCause(String reason) {
            super(reason);
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }
}
