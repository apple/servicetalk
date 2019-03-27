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
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntFunction;

import static io.servicetalk.concurrent.api.Completable.failed;
import static java.util.Objects.requireNonNull;
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
     * @param maxRepeats Maximum number of allowed repeats, after which the returned {@link IntFunction} will return
     *                   a failed {@link Completable} with {@link TerminateRepeatException} as the cause.
     * @param backoff Constant {@link Duration} of backoff between repeats.
     * @param timerExecutor {@link Executor} to be used to schedule timers for backoff.
     *
     * @return An {@link IntFunction} to be used for repeats which given a repeat count returns a {@link Completable}
     * that terminates successfully when the source has to be repeated or terminates with error if the source should not
     * be repeated.
     */
    public static IntFunction<Completable> repeatWithConstantBackoff(final int maxRepeats, final Duration backoff,
                                                                     final Executor timerExecutor) {
        requireNonNull(timerExecutor);
        final long backoffNanos = backoff.toNanos();
        return repeatCount -> {
            if (repeatCount > maxRepeats) {
                return terminateRepeat();
            }
            return timerExecutor.timer(backoffNanos, NANOSECONDS);
        };
    }

    /**
     * Creates a new repeat function that adds a delay between repeats. For first repeat, the delay is
     * {@code initialDelay} which is increased exponentially for subsequent repeats. <p>
     * This method may not attempt to check for overflow if the repeat count is high enough that an exponential
     * delay causes {@link Long} overflow.
     *
     * @param maxRepeats Maximum number of allowed repeats, after which the returned {@link IntFunction} will return
     * a failed {@link Completable} with {@link TerminateRepeatException} as the cause.
     * @param initialDelay Delay {@link Duration} for the first repeat and increased exponentially with each repeat.
     * @param timerExecutor {@link Executor} to be used to schedule timers for backoff.
     *
     * @return An {@link IntFunction} to be used for repeats which given a repeat count returns a {@link Completable}
     * that terminates successfully when the source has to be repeated or terminates with error if the source should not
     * be repeated.
     */
    public static IntFunction<Completable> repeatWithExponentialBackoff(final int maxRepeats,
                                                                        final Duration initialDelay,
                                                                        final Executor timerExecutor) {
        requireNonNull(timerExecutor);
        final long initialDelayNanos = initialDelay.toNanos();
        return repeatCount -> {
            if (repeatCount > maxRepeats) {
                return terminateRepeat();
            }
            return timerExecutor.timer(initialDelayNanos << (repeatCount - 1), NANOSECONDS);
        };
    }

    /**
     * Creates a new repeat function that adds a delay between repeats. For first repeat, the delay is
     * {@code initialDelay} which is increased exponentially for subsequent repeats.
     * This additionally adds a "Full Jitter" for the backoff as described
     * <a href="https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/">here</a>.<p>
     * This method may not attempt to check for overflow if the repeat count is high enough that an exponential delay
     * causes {@link Long} overflow.
     *
     * @param maxRepeats Maximum number of allowed repeats, after which the returned {@link IntFunction} will return
     *                   a failed {@link Completable} with {@link TerminateRepeatException} as the cause.
     * @param initialDelay Delay {@link Duration} for the first repeat and increased exponentially with each repeat.
     * @param timerExecutor {@link Executor} to be used to schedule timers for backoff.
     *
     * @return An {@link IntFunction} to be used for repeats which given a repeat count returns a {@link Completable}
     * that terminates successfully when the source has to be repeated or terminates with error if the source should not
     * be repeated.
     */
    public static IntFunction<Completable> repeatWithExponentialBackoffAndJitter(final int maxRepeats,
                                                                                 final Duration initialDelay,
                                                                                 final Executor timerExecutor) {
        requireNonNull(timerExecutor);
        final long initialDelayNanos = initialDelay.toNanos();
        return repeatCount -> {
            if (repeatCount > maxRepeats) {
                return terminateRepeat();
            }
            return timerExecutor.timer(ThreadLocalRandom.current().nextLong(0, initialDelayNanos << (repeatCount - 1)),
                    NANOSECONDS);
        };
    }

    private static Completable terminateRepeat() {
        return failed(TerminateRepeatException.INSTANCE);
    }
}
