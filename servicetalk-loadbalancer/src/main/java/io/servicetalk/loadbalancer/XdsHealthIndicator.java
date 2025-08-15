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
import io.servicetalk.client.api.RequestTracker;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.loadbalancer.LoadBalancerObserver.HostObserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;

import static io.servicetalk.loadbalancer.OutlierDetectorConfig.enforcing;
import static io.servicetalk.utils.internal.NumberUtils.ensureNonNegative;
import static io.servicetalk.utils.internal.RandomUtils.nextLongInclusive;
import static java.lang.Math.max;

abstract class XdsHealthIndicator<ResolvedAddress, C extends LoadBalancedConnection> extends DefaultRequestTracker
        implements HealthIndicator<ResolvedAddress, C> {

    private static final Logger LOGGER = LoggerFactory.getLogger(XdsHealthIndicator.class);

    private static final Throwable CONSECUTIVE_5XX_CAUSE = new EjectedCause("consecutive 5xx");
    private static final Throwable OUTLIER_DETECTOR_CAUSE = new EjectedCause("outlier detector");

    private final SequentialExecutor sequentialExecutor;
    private final Executor executor;
    private final HostObserver hostObserver;
    private final boolean cancellationIsError;
    private final ResolvedAddress address;
    private final String lbDescription;
    private final AtomicInteger consecutive5xx = new AtomicInteger();
    private final AtomicLong successes = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();

    @Nullable
    private Host<ResolvedAddress, C> host;


    // reads and writes protected by the helpers `SequentialExecutor`.
    private boolean cancelled;
    // reads and writes protected by the helpers `SequentialExecutor`.
    private int failureMultiplier;
    // Any thread can read this value at any time but mutations are serialized by the `SequentialExecutor`.
    @Nullable
    private volatile Long evictedUntilNanos;

    XdsHealthIndicator(final SequentialExecutor sequentialExecutor, final Executor executor,
                       final Duration ewmaHalfLife, final int cancellationPenalty, final int errorPenalty,
                       final int pendingRequestPenalty,
                       final boolean cancellationIsError, final ResolvedAddress address, String lbDescription,
                       final HostObserver hostObserver) {
        super(ewmaHalfLife.toNanos(),
                ensureNonNegative(cancellationPenalty, "cancellationPenalty"),
                ensureNonNegative(errorPenalty, "errorPenalty"),
                ensureNonNegative(pendingRequestPenalty, "pendingRequestPenalty"));
        this.cancellationIsError = cancellationIsError;
        this.sequentialExecutor = sequentialExecutor;
        this.executor = executor;
        assert executor instanceof NormalizedTimeSourceExecutor;
        this.address = address;
        this.lbDescription = lbDescription;
        this.hostObserver = hostObserver;
    }

    /**
     * Get the current configuration.
     * @return the current configuration.
     */
    abstract OutlierDetectorConfig currentConfig();

    /**
     * Attempt to mark the host as ejected with the parent XDS health checker.
     * @return whether this host was successfully ejected.
     */
    abstract boolean tryEjectHost();

    /**
     * Alert the parent {@link XdsOutlierDetector} that this host has transitions from healthy to unhealthy.
     */
    abstract void hostRevived();

    /**
     * Alert the parent {@link XdsOutlierDetector} that this {@link HealthIndicator} is no longer being used.
     */
    abstract void doCancel();

    @Override
    final long currentTimeNanos() {
        return executor.currentTime(TimeUnit.NANOSECONDS);
    }

    @Override
    public final void setHost(Host<ResolvedAddress, C> host) {
        this.host = host;
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
                final Long innerEvictedUntilNanos = this.evictedUntilNanos;
                if (!cancelled && innerEvictedUntilNanos != null && innerEvictedUntilNanos <= currentTimeNanos()) {
                    sequentialRevive();
                }
            });
            return true;
        }

        // We're either cancelled or still evicted.
        return false;
    }

    @Override
    public final void onRequestSuccess(final long beforeStartTimeNs) {
        super.onRequestSuccess(beforeStartTimeNs);
        successes.incrementAndGet();
        consecutive5xx.set(0);
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("{}-{}: observed request success", lbDescription, address);
        }
    }

    @Override
    public final void onRequestError(final long beforeStartTimeNs, RequestTracker.ErrorClass errorClass) {
        super.onRequestError(beforeStartTimeNs, errorClass);
        doOnError(errorClass == RequestTracker.ErrorClass.CANCELLED, errorClass);
    }

    @Override
    public long beforeConnectStart() {
        return currentTimeNanos();
    }

    @Override
    public void onConnectError(long beforeConnectStart, ConnectTracker.ErrorClass errorClass) {
        // This assumes that the connect request was intended to be used for a request dispatch which
        // will have now failed. This is not strictly true: a connection can be acquired and simply not
        // used, but in practice it's a very good assumption.
        doOnError(errorClass == ConnectTracker.ErrorClass.CANCELLED, errorClass);
    }

    @Override
    public void onConnectSuccess(long beforeConnectStart) {
        // noop: the request path will now determine if the request was a success or failure.
    }

    private void doOnError(boolean isCancellation, Object errorClass) {
        if (!cancellationIsError && isCancellation) {
            // short circuit: it's a cancellation, and we don't consider them to be errors.
            return;
        }
        failures.incrementAndGet();
        final int consecutiveFailures = consecutive5xx.incrementAndGet();
        final OutlierDetectorConfig localConfig = currentConfig();
        if (consecutiveFailures >= localConfig.consecutive5xx() && enforcing(localConfig.enforcingConsecutive5xx())) {
            sequentialExecutor.execute(() -> {
                if (!cancelled && evictedUntilNanos == null &&
                        sequentialTryEject(currentConfig(), CONSECUTIVE_5XX_CAUSE) /*side effecting*/) {
                    LOGGER.info("{}-{}: observed error of type {} which did result in consecutive 5xx ejection. " +
                                    "Consecutive 5xx: {}, limit: {}.", lbDescription, address, errorClass,
                            consecutiveFailures, localConfig.consecutive5xx());
                }
            });
        } else {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("{}-{}: observed error of type {} which did not result in ejection. " +
                                "Consecutive 5xx: {}, limit: {}",
                        lbDescription, address, errorClass, consecutiveFailures, localConfig.consecutive5xx());
            }
        }
    }

    final void forceRevival() {
        assert sequentialExecutor.isCurrentThreadDraining();
        if (!cancelled && evictedUntilNanos != null) {
            sequentialRevive();
        }
    }

    final boolean updateOutlierStatus(OutlierDetectorConfig config, boolean isOutlier) {
        assert sequentialExecutor.isCurrentThreadDraining();
        if (cancelled) {
            return false;
        }

        Long evictedUntilNanos = this.evictedUntilNanos;
        if (evictedUntilNanos != null) {
            if (evictedUntilNanos <= currentTimeNanos()) {
                sequentialRevive();
            }
            // If we are evicted or just transitioned out of eviction we shouldn't be marked as an outlier this round.
            // Note that this differs from the envoy behavior. If we want to mimic it, then I think we need to just
            // fall through and maybe attempt to eject again.
            LOGGER.info("{}-{}: Health indicator revived.", lbDescription, address);
            return false;
        } else if (isOutlier) {
            final boolean result = sequentialTryEject(config, OUTLIER_DETECTOR_CAUSE);
            if (result) {
                LOGGER.info("{}-{}: Health indicator was found to be an outlier and was ejected. " +
                        "Failure multiplier: {}.", lbDescription, address, failureMultiplier);
            } else if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("{}-{}: Health indicator was found to be an outlier but was not ejected. " +
                        "Failure multiplier: {}.", lbDescription, address, failureMultiplier);
            }
            return result;
        } else {
            // All we have to do is decrement our failure multiplier.
            failureMultiplier = max(0, failureMultiplier - 1);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("{}-{}: markAsOutlier(isOutlier = false). " +
                        "Failure multiplier: {}", lbDescription, address, failureMultiplier);
            }
            return false;
        }
    }

    final void resetCounters() {
        successes.set(0);
        failures.set(0);
    }

    final long getSuccesses() {
        return successes.get();
    }

    final long getFailures() {
        return failures.get();
    }

    @Override
    public final void cancel() {
        sequentialExecutor.execute(this::sequentialCancel);
    }

    @Override
    public String toString() {
        Long evictedUntilNanos = this.evictedUntilNanos;
        long remainingEvictionTimeNanos = evictedUntilNanos == null ? 0 :
                Math.max(0, evictedUntilNanos - currentTimeNanos());
        return "XdsHealthIndicator{" +
                "isHealthy=" + isHealthy() +
                ", successes=" + successes.get() +
                ", failures=" + failures.get() +
                ", consecutive5xx=" + consecutive5xx.get() +
                ", failureMultiplier=" + failureMultiplier +
                ", remainingEvictionTimeNanos=" + remainingEvictionTimeNanos +
                '}';
    }

    void sequentialCancel() {
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
        final long jitterNanos = nextLongInclusive(config.ejectionTimeJitter().toNanos());
        evictedUntilNanos = currentTimeNanos() + ejectTimeNanos + jitterNanos;
        hostObserver.onHostMarkedUnhealthy(cause);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("{}-{}: ejecting indicator for {} milliseconds",
                    lbDescription, address, (ejectTimeNanos + jitterNanos) / 1_000_000);
        }
        return true;
    }

    private void sequentialRevive() {
        assert sequentialExecutor.isCurrentThreadDraining();
        assert !cancelled;
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("{}-{}: host revived", lbDescription, address);
        }
        evictedUntilNanos = null;
        // Envoy resets the `consecutive5xx` counter on revival. I'm not sure that's the best because chances
        // are reasonable that it's still a bad host, so we'll want to mark it as an outlier again immediately if
        // the next request also fails.
        hostRevived();
        hostObserver.onHostRevived();
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
