/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.client.api;

import io.servicetalk.concurrent.api.BiIntFunction;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.RetryStrategies;
import io.servicetalk.concurrent.api.Single;

import java.time.Duration;
import java.util.function.BiPredicate;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.error;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithConstantBackoff;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithConstantBackoffAndJitter;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithExponentialBackoff;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithExponentialBackoffAndJitter;
import static java.util.Objects.requireNonNull;

/**
 * An abstract builder for retrying filters.
 *
 * @param <Builder> the type of builder for retrying filter
 * @param <Filter> the type of retrying filter to build
 * @param <Meta> the type of meta-data for {@link #retryFor(BiPredicate)}
 *
 * @see RetryStrategies
 */
public abstract class AbstractRetryingFilterBuilder<Builder
        extends AbstractRetryingFilterBuilder<Builder, Filter, Meta>, Filter, Meta> {

    private int maxRetries = 2;
    @Nullable
    private BiPredicate<Meta, Throwable> retryForPredicate;

    @SuppressWarnings("unchecked")
    private Builder castThis() {
        return (Builder) this;
    }

    /**
     * Set the maximum number of allowed retry operations before giving up.
     *
     * @param maxRetries Maximum number of allowed retries before giving up
     * @return {@code this}
     */
    public final Builder maxRetries(final int maxRetries) {
        if (maxRetries <= 0) {
            throw new IllegalArgumentException("maxRetries: " + maxRetries + " (expected: >0)");
        }
        this.maxRetries = maxRetries;
        return castThis();
    }

    /**
     * Overrides the default criterion for determining which requests or errors should be retried.
     *
     * @param retryForPredicate {@link BiPredicate} that checks whether a given combination of
     * {@link Meta meta-data} and {@link Throwable cause} should be retried
     * @return {@code this}
     */
    public final Builder retryFor(final BiPredicate<Meta, Throwable> retryForPredicate) {
        this.retryForPredicate = requireNonNull(retryForPredicate);
        return castThis();
    }

    /**
     * Creates a new retrying {@link Filter} which retries without delay.
     *
     * @return a new retrying {@link Filter} which retries without delay
     */
    public final Filter buildWithImmediateRetries() {
        return build(new ReadOnlyRetryableSettings<>(maxRetries, predicate(), null, null, false, false));
    }

    /**
     * Creates a new retrying {@link Filter} which adds the passed constant {@link Duration} as a delay between retries.
     *
     * @param delay Constant {@link Duration} of delay between retries
     * @return A new retrying {@link Filter} which adds a constant delay between retries
     */
    public final Filter buildWithConstantBackoff(final Duration delay) {
        return build(new ReadOnlyRetryableSettings<>(maxRetries, predicate(), delay, null, false, false));
    }

    /**
     * Creates a new retrying {@link Filter} which adds the passed constant {@link Duration} as a delay between retries.
     *
     * @param delay Constant {@link Duration} of delay between retries
     * @param timerExecutor {@link Executor} to be used to schedule timers for backoff. It takes precedence over an
     * alternative timer {@link Executor} from {@link ReadOnlyRetryableSettings#newStrategy(Executor)} argument
     * @return A new retrying {@link Filter} which adds a constant delay between retries
     */
    public final Filter buildWithConstantBackoff(final Duration delay, final Executor timerExecutor) {
        return build(new ReadOnlyRetryableSettings<>(maxRetries, predicate(), delay, timerExecutor, false, false));
    }

    /**
     * Creates a new retrying {@link Filter} which adds a randomized delay between retries and uses the passed
     * {@link Duration} as a maximum delay possible.
     *
     * @param delay Maximum {@link Duration} of delay between retries
     * @return A new retrying {@link Filter} which adds a randomized delay between retries
     */
    public final Filter buildWithConstantBackoffAndJitter(final Duration delay) {
        return build(new ReadOnlyRetryableSettings<>(maxRetries, predicate(), delay, null, false, true));
    }

    /**
     * Creates a new retrying {@link Filter} which adds a randomized delay between retries and uses the passed
     * {@link Duration} as a maximum delay possible.
     *
     * @param delay Maximum {@link Duration} of delay between retries
     * @param timerExecutor {@link Executor} to be used to schedule timers for backoff. It takes precedence over an
     * alternative timer {@link Executor} from {@link ReadOnlyRetryableSettings#newStrategy(Executor)} argument
     * @return A new retrying {@link Filter} which adds a randomized delay between retries
     */
    public final Filter buildWithConstantBackoffAndJitter(final Duration delay, final Executor timerExecutor) {
        return build(new ReadOnlyRetryableSettings<>(maxRetries, predicate(), delay, timerExecutor, false, true));
    }

    /**
     * Creates a new retrying {@link Filter} which adds a delay between retries. For first retry, the delay is
     * {@code initialDelay} which is increased exponentially for subsequent retries.
     * <p>
     * Returned {@link Filter} may not attempt to check for overflow if the retry count is high enough that an
     * exponential delay causes {@link Long} overflow.
     *
     * @param initialDelay Delay {@link Duration} for the first retry and increased exponentially with each retry
     * @return A new retrying {@link Filter} which adds an exponentially increasing delay between retries
     */
    public final Filter buildWithExponentialBackoff(final Duration initialDelay) {
        return build(
                new ReadOnlyRetryableSettings<>(maxRetries, predicate(), initialDelay, null, true, false));
    }

    /**
     * Creates a new retrying {@link Filter} which adds a delay between retries. For first retry, the delay is
     * {@code initialDelay} which is increased exponentially for subsequent retries.
     * <p>
     * Returned {@link Filter} may not attempt to check for overflow if the retry count is high enough that an
     * exponential delay causes {@link Long} overflow.
     *
     * @param initialDelay Delay {@link Duration} for the first retry and increased exponentially with each retry
     * @param timerExecutor {@link Executor} to be used to schedule timers for backoff. It takes precedence over an
     * alternative timer {@link Executor} from {@link ReadOnlyRetryableSettings#newStrategy(Executor)} argument
     * @return A new retrying {@link Filter} which adds an exponentially increasing delay between retries
     */
    public final Filter buildWithExponentialBackoff(final Duration initialDelay, final Executor timerExecutor) {
        return build(
                new ReadOnlyRetryableSettings<>(maxRetries, predicate(), initialDelay, timerExecutor, true, false));
    }

    /**
     * Creates a new retrying {@link Filter} which adds a delay between retries. For first retry, the delay is
     * {@code initialDelay} which is increased exponentially for subsequent retries. This additionally adds a
     * "Full Jitter" for the backoff as described
     * <a href="https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/">here</a>.
     * <p>
     * Returned {@link Filter} may not attempt to check for overflow if the retry count is high enough that an
     * exponential delay causes {@link Long} overflow.
     *
     * @param initialDelay Delay {@link Duration} for the first retry and increased exponentially with each retry
     * @return A new retrying {@link Filter} which adds an exponentially increasing delay between retries with jitter
     */
    public final Filter buildWithExponentialBackoffAndJitter(final Duration initialDelay) {
        return build(
                new ReadOnlyRetryableSettings<>(maxRetries, predicate(), initialDelay, null, true, true));
    }

    /**
     * Creates a new retrying {@link Filter} which adds a delay between retries. For first retry, the delay is
     * {@code initialDelay} which is increased exponentially for subsequent retries. This additionally adds a
     * "Full Jitter" for the backoff as described
     * <a href="https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/">here</a>.
     * <p>
     * Returned {@link Filter} may not attempt to check for overflow if the retry count is high enough that an
     * exponential delay causes {@link Long} overflow.
     *
     * @param initialDelay Delay {@link Duration} for the first retry and increased exponentially with each retry
     * @param timerExecutor {@link Executor} to be used to schedule timers for backoff. It takes precedence over an
     * alternative timer {@link Executor} from {@link ReadOnlyRetryableSettings#newStrategy(Executor)} argument
     * @return A new retrying {@link Filter} which adds an exponentially increasing delay between retries with jitter
     */
    public final Filter buildWithExponentialBackoffAndJitter(final Duration initialDelay,
                                                             final Executor timerExecutor) {
        return build(
                new ReadOnlyRetryableSettings<>(maxRetries, predicate(), initialDelay, timerExecutor, true, true));
    }

    /**
     * Builds a retrying {@link Filter} for provided
     * {@link ReadOnlyRetryableSettings ReadOnlyRetryableSettings&lt;Meta&gt;}.
     *
     * @param readOnlySettings a read-only settings for retryable filters
     * @return A new retrying {@link Filter}
     */
    protected abstract Filter build(ReadOnlyRetryableSettings<Meta> readOnlySettings);

    /**
     * Returns a default value for {@link #retryFor(BiPredicate)}.
     *
     * @return a default value for {@link #retryFor(BiPredicate)}
     */
    public abstract BiPredicate<Meta, Throwable> defaultRetryForPredicate();

    private BiPredicate<Meta, Throwable> predicate() {
        return retryForPredicate != null ? retryForPredicate : defaultRetryForPredicate();
    }

    /**
     * A read-only settings for retryable filters.
     *
     * @param <Meta> the type of meta-data for {@link #retryFor(BiPredicate)}
     */
    public static final class ReadOnlyRetryableSettings<Meta> {

        @Nullable
        private final Executor timerExecutor;
        private final int maxRetries;
        private final boolean jitter;
        private final boolean exponential;
        @Nullable
        private final Duration initialDelay;
        private final BiPredicate<Meta, Throwable> retryForPredicate;

        private ReadOnlyRetryableSettings(final int maxRetries,
                                          final BiPredicate<Meta, Throwable> retryForPredicate,
                                          @Nullable final Duration initialDelay,
                                          @Nullable final Executor timerExecutor,
                                          final boolean exponential,
                                          final boolean jitter) {
            this.timerExecutor = timerExecutor;
            this.maxRetries = maxRetries;
            this.jitter = jitter;
            this.exponential = exponential;
            this.initialDelay = initialDelay;
            this.retryForPredicate = retryForPredicate;
        }

        /**
         * Checks the provided pair of {@link Meta} and {@link Throwable} that the case is retryable.
         *
         * @param meta a meta-data of a type {@link Meta} to check
         * @param throwable an exception occurred
         * @return {@code true} if it is desirable to retry, {@code false} otherwise
         */
        public boolean isRetryable(final Meta meta, final Throwable throwable) {
            return retryForPredicate.test(meta, throwable);
        }

        /**
         * Builds a new retry strategy {@link BiIntFunction} for retrying with
         * {@link Publisher#retryWhen(BiIntFunction)}, {@link Single#retryWhen(BiIntFunction)}, and
         * {@link Completable#retryWhen(BiIntFunction)} or in general with an alternative timer {@link Executor}.
         *
         * @param alternativeTimerExecutor {@link Executor} to be used to schedule timers for backoff if no executor
         * was provided at the build time
         * @return a new retry strategy {@link BiIntFunction}
         */
        public BiIntFunction<Throwable, Completable> newStrategy(final Executor alternativeTimerExecutor) {
            if (initialDelay == null) {
                return (count, throwable) -> count <= maxRetries ? completed() : error(throwable);
            } else {
                final Executor effectiveExecutor = timerExecutor == null ?
                        requireNonNull(alternativeTimerExecutor) : timerExecutor;
                if (exponential) {
                    if (jitter) {
                        return retryWithExponentialBackoffAndJitter(
                                maxRetries, t -> true, initialDelay, effectiveExecutor);
                    } else {
                        return retryWithExponentialBackoff(maxRetries, t -> true, initialDelay, effectiveExecutor);
                    }
                } else {
                    if (jitter) {
                        return retryWithConstantBackoffAndJitter(
                                maxRetries, t -> true, initialDelay, effectiveExecutor);
                    } else {
                        return retryWithConstantBackoff(maxRetries, t -> true, initialDelay, effectiveExecutor);
                    }
                }
            }
        }
    }
}
