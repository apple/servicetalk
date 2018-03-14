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
import java.util.function.LongFunction;

import static io.servicetalk.concurrent.api.Completable.error;
import static java.util.Objects.requireNonNull;

/**
 * A set of strategies to use for repeating with {@link Publisher#repeatWhen(IntFunction)}, {@link Single#repeatWhen(IntFunction)}
 * and {@link Completable#repeatWhen(IntFunction)} or in general.
 */
public final class RepeatStrategies {

    /**
     * An {@link Exception} instance used to indicate termination of repeats.
     */
    public static final class TerminateRepeatException extends Exception {

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
     * @param timerProvider {@link LongFunction} that given a duration in nanoseconds, returns a {@link Completable}
     *                      that completes after the passed duration has passed.
     * @return An {@link IntFunction} to be used for repeats which given a repeat count returns a {@link Completable}
     * that terminates successfully when the source has to be repeated or terminates with error if the source should not be repeated.
     */
    public static IntFunction<Completable> repeatWithConstantBackoff(int maxRepeats, Duration backoff,
                                                                     LongFunction<? extends Completable> timerProvider) {
        requireNonNull(timerProvider);
        final long backoffNanos = backoff.toNanos();
        return repeatCount -> {
            if (repeatCount > maxRepeats) {
                return terminateRepeat();
            }
            return timerProvider.apply(backoffNanos);
        };
    }

    /**
     * Creates a new repeat function that adds a delay between repeats. For first repeat, the delay is {@code initialDelay}
     * which is increased exponentially for subsequent repeats. <p>
     *     This method may not attempt to check for overflow if the repeat count is high enough that an exponential delay
     *     causes {@link Long} overflow.
     *
     * @param maxRepeats Maximum number of allowed repeats, after which the returned {@link IntFunction} will return
     *                   a failed {@link Completable} with {@link TerminateRepeatException} as the cause.
     * @param initialDelay Delay {@link Duration} for the first repeat and increased exponentially with each repeat.
     * @param timerProvider {@link LongFunction} that given a duration in nanoseconds, returns a {@link Completable}
     *                      that completes after the passed duration has passed.
     * @return An {@link IntFunction} to be used for repeats which given a repeat count returns a {@link Completable}
     * that terminates successfully when the source has to be repeated or terminates with error if the source should not be repeated.
     */
    public static IntFunction<Completable> repeatWithExponentialBackoff(int maxRepeats, Duration initialDelay,
                                                                        LongFunction<? extends Completable> timerProvider) {
        requireNonNull(timerProvider);
        final long initialDelayNanos = initialDelay.toNanos();
        return repeatCount -> {
            if (repeatCount > maxRepeats) {
                return terminateRepeat();
            }
            return timerProvider.apply(initialDelayNanos << (repeatCount - 1));
        };
    }

    /**
     * Creates a new repeat function that adds a delay between repeats. For first repeat, the delay is {@code initialDelay}
     * which is increased exponentially for subsequent repeats.
     * This additionally adds a "Full Jitter" for the backoff as described <a href="https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/">here</a>.<p>
     *     This method may not attempt to check for overflow if the repeat count is high enough that an exponential delay
     *     causes {@link Long} overflow.
     *
     * @param maxRepeats Maximum number of allowed repeats, after which the returned {@link IntFunction} will return
     *                   a failed {@link Completable} with {@link TerminateRepeatException} as the cause.
     * @param initialDelay Delay {@link Duration} for the first repeat and increased exponentially with each repeat.
     * @param timerProvider {@link LongFunction} that given a duration in nanoseconds, returns a {@link Completable}
     *                      that completes after the passed duration has passed.
     * @return An {@link IntFunction} to be used for repeats which given a repeat count returns a {@link Completable}
     * that terminates successfully when the source has to be repeated or terminates with error if the source should not be repeated.
     */
    public static IntFunction<Completable> repeatWithExponentialBackoffAndJitter(int maxRepeats, Duration initialDelay,
                                                                                 LongFunction<? extends Completable> timerProvider) {
        requireNonNull(timerProvider);
        final long initialDelayNanos = initialDelay.toNanos();
        return repeatCount -> {
            if (repeatCount > maxRepeats) {
                return terminateRepeat();
            }
            return timerProvider.apply(ThreadLocalRandom.current().nextLong(0, initialDelayNanos << (repeatCount - 1)));
        };
    }

    private static Completable terminateRepeat() {
        return error(TerminateRepeatException.INSTANCE);
    }
}
