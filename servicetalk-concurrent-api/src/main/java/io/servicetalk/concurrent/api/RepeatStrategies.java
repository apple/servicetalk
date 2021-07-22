/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api;

import java.time.Duration;
import java.util.function.IntFunction;

import static io.servicetalk.concurrent.api.Completable.failed;
import static io.servicetalk.concurrent.api.RetryStrategies.baseDelayNanos;
import static io.servicetalk.concurrent.api.RetryStrategies.checkFullJitter;
import static io.servicetalk.concurrent.api.RetryStrategies.checkJitterDelta;
import static io.servicetalk.concurrent.api.RetryStrategies.checkMaxRetries;
import static io.servicetalk.concurrent.api.RetryStrategies.maxShift;
import static io.servicetalk.concurrent.internal.FlowControlUtils.addWithOverflowProtection;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.ThreadLocalRandom.current;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * A set of strategies to use for repeating with {@link Publisher#repeatWhen(IntFunction)},
 * {@link Single#repeatWhen(IntFunction)} and {@link Completable#repeatWhen(IntFunction)} or in general.
 */
public final class RepeatStrategies {

    /**
     * An {@link Exception} instance used to indicate termination of repeats.
     */
    public static final class TerminateRepeatException extends Exception {
        private static final long serialVersionUID = -1725458427890873886L;

        // It is fine to reuse this instance and let it escape to the user as enableSuppression is set to false.
        static final TerminateRepeatException INSTANCE = new TerminateRepeatException();

        // Package-private as the user should never instance it.
        private TerminateRepeatException() {
            super(null, null, false, false);
        }
    }

    private RepeatStrategies() {
        // No instances.
    }

    /**
     * Creates a new repeat function that adds the passed constant {@link Duration} as delay between repeats.
     *
     * @param delay Constant {@link Duration} of delay between repeats.
     * @param timerExecutor {@link Executor} to be used to schedule timers for delay.
     *
     * @return An {@link IntFunction} to be used for repeats which given a repeat count returns a {@link Completable}
     * that terminates successfully when the source has to be repeated or terminates with error if the source should not
     * be repeated.
     */
    public static IntFunction<Completable> repeatWithConstantBackoffFullJitter(final Duration delay,
                                                                               final Executor timerExecutor) {
        requireNonNull(timerExecutor);
        final long delayNanos = delay.toNanos();
        checkFullJitter(delayNanos);
        return repeatCount -> timerExecutor.timer(current().nextLong(0, delayNanos), NANOSECONDS);
    }

    /**
     * Creates a new repeat function that adds the passed constant {@link Duration} as delay between repeats.
     *
     * @param maxRepeats Maximum number of allowed repeats, after which the returned {@link IntFunction} will return
     *                   a failed {@link Completable} with {@link TerminateRepeatException} as the cause.
     * @param delay Constant {@link Duration} of delay between repeats.
     * @param timerExecutor {@link Executor} to be used to schedule timers for delay.
     *
     * @return An {@link IntFunction} to be used for repeats which given a repeat count returns a {@link Completable}
     * that terminates successfully when the source has to be repeated or terminates with error if the source should not
     * be repeated.
     */
    public static IntFunction<Completable> repeatWithConstantBackoffFullJitter(final int maxRepeats,
                                                                               final Duration delay,
                                                                               final Executor timerExecutor) {
        checkMaxRetries(maxRepeats);
        requireNonNull(timerExecutor);
        final long delayNanos = delay.toNanos();
        checkFullJitter(delayNanos);
        return repeatCount -> repeatCount <= maxRepeats ?
                timerExecutor.timer(current().nextLong(0, delayNanos), NANOSECONDS) : terminateRepeat();
    }

    /**
     * Creates a new repeat function that adds the passed constant {@link Duration} as delay between repeats.
     *
     * @param delay Constant {@link Duration} of delay between repeats.
     * @param jitter The jitter to apply to {@code delay} on each repeat.
     * @param timerExecutor {@link Executor} to be used to schedule timers for delay.
     *
     * @return An {@link IntFunction} to be used for repeats which given a repeat count returns a {@link Completable}
     * that terminates successfully when the source has to be repeated or terminates with error if the source should not
     * be repeated.
     */
    public static IntFunction<Completable> repeatWithConstantBackoffDeltaJitter(final Duration delay,
                                                                                final Duration jitter,
                                                                                final Executor timerExecutor) {
        requireNonNull(timerExecutor);
        final long delayNanos = delay.toNanos();
        final long jitterNanos = jitter.toNanos();
        checkJitterDelta(jitterNanos, delayNanos);
        final long lowerBound = delayNanos - jitterNanos;
        final long upperBound = delayNanos + jitterNanos;
        return repeatCount -> timerExecutor.timer(current().nextLong(lowerBound, upperBound), NANOSECONDS);
    }

    /**
     * Creates a new repeat function that adds the passed constant {@link Duration} as delay between repeats.
     *
     * @param maxRepeats Maximum number of allowed repeats, after which the returned {@link IntFunction} will return
     *                   a failed {@link Completable} with {@link TerminateRepeatException} as the cause.
     * @param delay Constant {@link Duration} of delay between repeats.
     * @param jitter The jitter to apply to {@code delay} on each repeat.
     * @param timerExecutor {@link Executor} to be used to schedule timers for delay.
     *
     * @return An {@link IntFunction} to be used for repeats which given a repeat count returns a {@link Completable}
     * that terminates successfully when the source has to be repeated or terminates with error if the source should not
     * be repeated.
     */
    public static IntFunction<Completable> repeatWithConstantBackoffDeltaJitter(final int maxRepeats,
                                                                                final Duration delay,
                                                                                final Duration jitter,
                                                                                final Executor timerExecutor) {
        checkMaxRetries(maxRepeats);
        requireNonNull(timerExecutor);
        final long delayNanos = delay.toNanos();
        final long jitterNanos = jitter.toNanos();
        checkJitterDelta(jitterNanos, delayNanos);
        final long lowerBound = delayNanos - jitterNanos;
        final long upperBound = delayNanos + jitterNanos;
        return repeatCount -> repeatCount <= maxRepeats ?
                timerExecutor.timer(current().nextLong(lowerBound, upperBound), NANOSECONDS) : terminateRepeat();
    }

    /**
     * Creates a new repeat function that adds a delay between repeats. For first repeat, the delay is
     * {@code initialDelay} which is increased exponentially for subsequent repeats.
     * This additionally adds a "Full Jitter" for the backoff as described
     * <a href="https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/">here</a>.
     *
     * @param initialDelay Delay {@link Duration} for the first repeat and increased exponentially with each repeat.
     * @param maxDelay The maximum amount of delay that will be introduced.
     * @param timerExecutor {@link Executor} to be used to schedule timers for backoff.
     *
     * @return An {@link IntFunction} to be used for repeats which given a repeat count returns a {@link Completable}
     * that terminates successfully when the source has to be repeated or terminates with error if the source should not
     * be repeated.
     */
    public static IntFunction<Completable> repeatWithExponentialBackoffFullJitter(final Duration initialDelay,
                                                                                  final Duration maxDelay,
                                                                                  final Executor timerExecutor) {
        requireNonNull(timerExecutor);
        final long initialDelayNanos = initialDelay.toNanos();
        final long maxDelayNanos = maxDelay.toNanos();
        final long maxInitialShift = maxShift(initialDelayNanos);
        return repeatCount -> timerExecutor.timer(current().nextLong(0,
                baseDelayNanos(initialDelayNanos, maxDelayNanos, maxInitialShift, repeatCount)), NANOSECONDS);
    }

    /**
     * Creates a new repeat function that adds a delay between repeats. For first repeat, the delay is
     * {@code initialDelay} which is increased exponentially for subsequent repeats.
     * This additionally adds a "Full Jitter" for the backoff as described
     * <a href="https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/">here</a>.
     *
     * @param maxRepeats Maximum number of allowed repeats, after which the returned {@link IntFunction} will return
     *                   a failed {@link Completable} with {@link TerminateRepeatException} as the cause.
     * @param initialDelay Delay {@link Duration} for the first repeat and increased exponentially with each repeat.
     * @param maxDelay The maximum amount of delay that will be introduced.
     * @param timerExecutor {@link Executor} to be used to schedule timers for backoff.
     *
     * @return An {@link IntFunction} to be used for repeats which given a repeat count returns a {@link Completable}
     * that terminates successfully when the source has to be repeated or terminates with error if the source should not
     * be repeated.
     */
    public static IntFunction<Completable> repeatWithExponentialBackoffFullJitter(final int maxRepeats,
                                                                                  final Duration initialDelay,
                                                                                  final Duration maxDelay,
                                                                                  final Executor timerExecutor) {
        checkMaxRetries(maxRepeats);
        requireNonNull(timerExecutor);
        final long initialDelayNanos = initialDelay.toNanos();
        final long maxDelayNanos = maxDelay.toNanos();
        final long maxInitialShift = maxShift(initialDelayNanos);
        return repeatCount -> repeatCount <= maxRepeats ?
                timerExecutor.timer(current().nextLong(0,
                        baseDelayNanos(initialDelayNanos, maxDelayNanos, maxInitialShift, repeatCount)), NANOSECONDS) :
                terminateRepeat();
    }

    /**
     * Creates a new repeat function that adds a delay between repeats. For first repeat, the delay is
     * {@code initialDelay} which is increased exponentially for subsequent repeats.
     *
     * @param initialDelay Delay {@link Duration} for the first repeat and increased exponentially with each repeat.
     * @param jitter The jitter to apply to {@code initialDelay} on each repeat.
     * @param maxDelay The maximum amount of delay that will be introduced.
     * @param timerExecutor {@link Executor} to be used to schedule timers for backoff.
     *
     * @return An {@link IntFunction} to be used for repeats which given a repeat count returns a {@link Completable}
     * that terminates successfully when the source has to be repeated or terminates with error if the source should not
     * be repeated.
     */
    public static IntFunction<Completable> repeatWithExponentialBackoffDeltaJitter(final Duration initialDelay,
                                                                                   final Duration jitter,
                                                                                   final Duration maxDelay,
                                                                                   final Executor timerExecutor) {
        requireNonNull(timerExecutor);
        final long initialDelayNanos = initialDelay.toNanos();
        final long jitterNanos = jitter.toNanos();
        final long maxDelayNanos = maxDelay.toNanos();
        final long maxInitialShift = maxShift(initialDelayNanos);
        return repeatCount -> {
            final long baseDelayNanos = baseDelayNanos(initialDelayNanos, maxDelayNanos, maxInitialShift, repeatCount);
            return timerExecutor.timer(
                    current().nextLong(max(0, baseDelayNanos - jitterNanos),
                            min(maxDelayNanos, addWithOverflowProtection(baseDelayNanos, jitterNanos))),
                    NANOSECONDS);
        };
    }

    /**
     * Creates a new repeat function that adds a delay between repeats. For first repeat, the delay is
     * {@code initialDelay} which is increased exponentially for subsequent repeats.
     *
     * @param maxRepeats Maximum number of allowed repeats, after which the returned {@link IntFunction} will return
     *                   a failed {@link Completable} with {@link TerminateRepeatException} as the cause.
     * @param initialDelay Delay {@link Duration} for the first repeat and increased exponentially with each repeat.
     * @param jitter The jitter to apply to {@code initialDelay} on each repeat.
     * @param maxDelay The maximum amount of delay that will be introduced.
     * @param timerExecutor {@link Executor} to be used to schedule timers for backoff.
     *
     * @return An {@link IntFunction} to be used for repeats which given a repeat count returns a {@link Completable}
     * that terminates successfully when the source has to be repeated or terminates with error if the source should not
     * be repeated.
     */
    public static IntFunction<Completable> repeatWithExponentialBackoffDeltaJitter(final int maxRepeats,
                                                                                   final Duration initialDelay,
                                                                                   final Duration jitter,
                                                                                   final Duration maxDelay,
                                                                                   final Executor timerExecutor) {
        checkMaxRetries(maxRepeats);
        requireNonNull(timerExecutor);
        final long initialDelayNanos = initialDelay.toNanos();
        final long jitterNanos = jitter.toNanos();
        final long maxDelayNanos = maxDelay.toNanos();
        final long maxInitialShift = maxShift(initialDelayNanos);
        return repeatCount -> {
            if (repeatCount > maxRepeats) {
                return terminateRepeat();
            }
            final long baseDelayNanos = baseDelayNanos(initialDelayNanos, maxDelayNanos, maxInitialShift, repeatCount);
            return timerExecutor.timer(
                    current().nextLong(max(0, baseDelayNanos - jitterNanos),
                            min(maxDelayNanos, addWithOverflowProtection(baseDelayNanos, jitterNanos))),
                    NANOSECONDS);
        };
    }

    private static Completable terminateRepeat() {
        return failed(TerminateRepeatException.INSTANCE);
    }
}
