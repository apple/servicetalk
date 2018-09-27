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
import java.util.function.Predicate;

import static io.servicetalk.concurrent.api.Completable.error;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * A set of strategies to use for retrying with {@link Publisher#retryWhen(BiIntFunction)}, {@link Single#retryWhen(BiIntFunction)}
 * and {@link Completable#retryWhen(BiIntFunction)} or in general.
 */
public final class RetryStrategies {

    private RetryStrategies() {
        // No instances.
    }

    /**
     * Creates a new retry function that adds the passed constant {@link Duration} as delay between retries.
     *
     * @param maxRetries Maximum number of allowed retries, after which the returned {@link BiIntFunction} will return
     *                   a failed {@link Completable} with the passed {@link Throwable} as the cause.
     * @param causeFilter A {@link Predicate} that selects whether a {@link Throwable} cause should be retried.
     * @param backoff Constant {@link Duration} of backoff between retries.
     * @param timerExecutor {@link Executor} to be used to schedule timers for backoff.
     *
     * @return A {@link BiIntFunction} to be used for retries which given a retry count and a {@link Throwable} returns
     * a {@link Completable} that terminates successfully when the source has to be retried or terminates with error
     * if the source should not be retried for the passed {@link Throwable}.
     */
    public static BiIntFunction<Throwable, Completable> retryWithConstantBackoff(final int maxRetries,
                                                                                 final Predicate<Throwable> causeFilter,
                                                                                 final Duration backoff,
                                                                                 final Executor timerExecutor) {
        requireNonNull(timerExecutor);
        requireNonNull(causeFilter);
        final long backoffNanos = backoff.toNanos();
        return (retryCount, cause) -> {
            if (retryCount > maxRetries || !causeFilter.test(cause)) {
                return error(cause);
            }
            return timerExecutor.timer(backoffNanos, NANOSECONDS);
        };
    }

    /**
     * Creates a new retry function that adds the passed constant {@link Duration} as delay between retries.
     * This additionally adds a "Full Jitter" for the backoff as described
     * <a href="https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/">here</a>.
     * @param maxRetries Maximum number of allowed retries, after which the returned {@link BiIntFunction} will return
     *                   a failed {@link Completable} with the passed {@link Throwable} as the cause.
     * @param causeFilter A {@link Predicate} that selects whether a {@link Throwable} cause should be retried.
     * @param backoff Constant {@link Duration} of backoff between retries.
     * @param timerExecutor {@link Executor} to be used to schedule timers for backoff.
     *
     * @return A {@link BiIntFunction} to be used for retries which given a retry count and a {@link Throwable} returns
     * a {@link Completable} that terminates successfully when the source has to be retried or terminates with error
     * if the source should not be retried for the passed {@link Throwable}.
     */
    public static BiIntFunction<Throwable, Completable>
    retryWithConstantBackoffAndJitter(final int maxRetries, final Predicate<Throwable> causeFilter,
                                      final Duration backoff, final Executor timerExecutor) {
        requireNonNull(timerExecutor);
        requireNonNull(causeFilter);
        final long backoffNanos = backoff.toNanos();
        return (retryCount, cause) -> {
            if (retryCount > maxRetries || !causeFilter.test(cause)) {
                return error(cause);
            }
            return timerExecutor.timer(ThreadLocalRandom.current().nextLong(0, backoffNanos), NANOSECONDS);
        };
    }

    /**
     * Creates a new retry function that adds a delay between retries. For first retry, the delay is {@code initialDelay}
     * which is increased exponentially for subsequent retries. <p>
     *     This method may not attempt to check for overflow if the retry count is high enough that an exponential delay
     *     causes {@link Long} overflow.
     *
     * @param maxRetries Maximum number of allowed retries, after which the returned {@link BiIntFunction} will return
     *                   a failed {@link Completable} with the passed {@link Throwable} as the cause.
     * @param causeFilter A {@link Predicate} that selects whether a {@link Throwable} cause should be retried.
     * @param initialDelay Delay {@link Duration} for the first retry and increased exponentially with each retry.
     * @param timerExecutor {@link Executor} to be used to schedule timers for backoff.
     *
     * @return A {@link BiIntFunction} to be used for retries which given a retry count and a {@link Throwable} returns
     * a {@link Completable} that terminates successfully when the source has to be retried or terminates with error
     * if the source should not be retried for the passed {@link Throwable}.
     */
    public static BiIntFunction<Throwable, Completable> retryWithExponentialBackoff(final int maxRetries,
                                                                                    final Predicate<Throwable> causeFilter,
                                                                                    final Duration initialDelay,
                                                                                    final Executor timerExecutor) {
        requireNonNull(timerExecutor);
        requireNonNull(causeFilter);
        final long initialDelayNanos = initialDelay.toNanos();
        return (retryCount, cause) -> {
            if (retryCount > maxRetries || !causeFilter.test(cause)) {
                return error(cause);
            }
            return timerExecutor.timer(initialDelayNanos << (retryCount - 1), NANOSECONDS);
        };
    }

    /**
     * Creates a new retry function that adds a delay between retries. For first retry, the delay is {@code initialDelay}
     * which is increased exponentially for subsequent retries.
     * This additionally adds a "Full Jitter" for the backoff as described
     * <a href="https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/">here</a>.
     * <p>
     * This method may not attempt to check for overflow if the retry count is high enough that an exponential delay
     * causes {@link Long} overflow.
     *
     * @param maxRetries Maximum number of allowed retries, after which the returned {@link BiIntFunction} will return
     *                   a failed {@link Completable} with the passed {@link Throwable} as the cause.
     * @param causeFilter A {@link Predicate} that selects whether a {@link Throwable} cause should be retried.
     * @param initialDelay Delay {@link Duration} for the first retry and increased exponentially with each retry.
     * @param timerExecutor {@link Executor} to be used to schedule timers for backoff.
     *
     * @return A {@link BiIntFunction} to be used for retries which given a retry count and a {@link Throwable} returns
     * a {@link Completable} that terminates successfully when the source has to be retried or terminates with error
     * if the source should not be retried for the passed {@link Throwable}.
     */
    public static BiIntFunction<Throwable, Completable> retryWithExponentialBackoffAndJitter(final int maxRetries,
                                                                                             final Predicate<Throwable> causeFilter,
                                                                                             final Duration initialDelay,
                                                                                             final Executor timerExecutor) {
        requireNonNull(timerExecutor);
        requireNonNull(causeFilter);
        final long initialDelayNanos = initialDelay.toNanos();
        return (retryCount, cause) -> {
            if (retryCount > maxRetries || !causeFilter.test(cause)) {
                return error(cause);
            }
            return timerExecutor.timer(ThreadLocalRandom.current().nextLong(0, initialDelayNanos << (retryCount - 1)),
                    NANOSECONDS);
        };
    }
}
