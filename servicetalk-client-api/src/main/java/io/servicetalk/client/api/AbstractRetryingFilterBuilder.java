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
import static java.time.Duration.ofMillis;
import static java.util.Objects.requireNonNull;

/**
 * An abstract builder for retrying filters.
 *
 * @param <B> the type of builder for retrying filter
 * @param <F> the type of retrying filter to build
 * @param <M> the type of meta-data for {@link #retryFor(BiPredicate)}
 *
 * @see RetryStrategies
 */
public abstract class AbstractRetryingFilterBuilder<B extends AbstractRetryingFilterBuilder<B, F, M>, F, M> {

    private int maxRetries = 1;
    @Nullable
    private Duration initialDelay = ofMillis(10);
    private boolean exponential = true;
    private boolean jitter = true;
    @Nullable
    private Executor timerExecutor;
    @Nullable
    private BiPredicate<M, Throwable> retryForPredicate;

    @SuppressWarnings("unchecked")
    private B castThis() {
        return (B) this;
    }

    /**
     * Set the maximum number of allowed retry operations before giving up.
     *
     * @param maxRetries Maximum number of allowed retries before giving up
     * @return {@code this}
     */
    public final B maxRetries(final int maxRetries) {
        if (maxRetries <= 0) {
            throw new IllegalArgumentException("maxRetries: " + maxRetries + " (expected: >0)");
        }
        this.maxRetries = maxRetries;
        return castThis();
    }

    /**
     * Adds a {@code delay} between retries.
     *
     * @param delay Constant {@link Duration} of delay between retries
     * @return {@code this}
     */
    public final B backoff(final Duration delay) {
        this.initialDelay = requireNonNull(delay);
        this.exponential = false;
        return castThis();
    }

    /**
     * Adds a delay between retries. For first retry, the delay is {@code initialDelay} which is increased
     * exponentially for subsequent retries.
     * <p>
     * The resulting {@link F} from {@link #build()} may not attempt to check for
     * overflow if the retry count is high enough that an exponential delay causes {@link Long} overflow.
     *
     * @param initialDelay Delay {@link Duration} for the first retry and increased exponentially with each retry.
     * @return {@code this}
     */
    public final B exponentialBackoff(final Duration initialDelay) {
        this.initialDelay = requireNonNull(initialDelay);
        this.exponential = true;
        return castThis();
    }

    /**
     * Disables any delay between retries, if {@link #exponentialBackoff(Duration)} or {@link #backoff(Duration)} was
     * used.
     *
     * @return {@code this}
     */
    public final B noBackoff() {
        this.initialDelay = null;
        return castThis();
    }

    /**
     * When {@link #exponentialBackoff(Duration)} or {@link #backoff(Duration)} is used, adding jitter will
     * randomize the delays between the retries.
     *
     * @return {@code this}
     */
    public final B addJitter() {
        this.jitter = true;
        return castThis();
    }

    /**
     * Disables randomization of delays between the retries when {@link #exponentialBackoff(Duration)} or
     * {@link #backoff(Duration)} is used.
     *
     * @return {@code this}
     */
    public final B noJitter() {
        this.jitter = false;
        return castThis();
    }

    /**
     * Uses the passed {@link Executor} for scheduling timers if {@link #backoff(Duration)} or
     * {@link #exponentialBackoff(Duration)} is used. It takes precedence over an alternative timer {@link Executor}
     * from {@link ReadOnlyRetryableSettings#newStrategy(Executor)} argument.
     *
     * @param timerExecutor {@link Executor} to be used to schedule timers for backoff. If {@code null}, a passed
     * alternative timer {@link Executor} from {@link ReadOnlyRetryableSettings#newStrategy(Executor)} argument will be
     * used
     * @return {@code this}
     */
    public final B timerExecutor(@Nullable final Executor timerExecutor) {
        this.timerExecutor = timerExecutor;
        return castThis();
    }

    /**
     * Overrides the default criterion for determining which requests or errors should be retried.
     *
     * @param retryForPredicate {@link BiPredicate} that checks whether a given combination of
     * {@link M meta-data} and {@link Throwable cause} should be retried
     * @return {@code this}
     */
    public final B retryFor(final BiPredicate<M, Throwable> retryForPredicate) {
        this.retryForPredicate = requireNonNull(retryForPredicate);
        return castThis();
    }

    /**
     * Returns a default value for {@link #retryFor(BiPredicate)}.
     *
     * @return a default value for {@link #retryFor(BiPredicate)}
     */
    public abstract BiPredicate<M, Throwable> defaultRetryForPredicate();

    /**
     * Builds a retrying filter of type {@link F}.
     *
     * @return A new retrying filter of type {@link F}
     */
    public abstract F build();

    /**
     * Returns a {@link ReadOnlyRetryableSettings read-only} representation of retrying settings.
     *
     * @return a {@link ReadOnlyRetryableSettings read-only} representation of retrying settings
     */
    protected final ReadOnlyRetryableSettings<M> readOnlySettings() {
        return new ReadOnlyRetryableSettings<>(timerExecutor, maxRetries, jitter, exponential, initialDelay,
                retryForPredicate != null ? retryForPredicate : defaultRetryForPredicate());
    }

    /**
     * A read-only settings for retryable filters.
     *
     * @param <M> the type of meta-data for {@link #retryFor(BiPredicate)}
     */
    public static final class ReadOnlyRetryableSettings<M> {

        @Nullable
        private final Executor timerExecutor;
        private final int maxRetries;
        private final boolean jitter;
        private final boolean exponential;
        @Nullable
        private final Duration initialDelay;
        private final BiPredicate<M, Throwable> retryForPredicate;

        private ReadOnlyRetryableSettings(@Nullable final Executor timerExecutor,
                                          final int maxRetries,
                                          final boolean jitter,
                                          final boolean exponential,
                                          @Nullable final Duration initialDelay,
                                          final BiPredicate<M, Throwable> retryForPredicate) {
            this.timerExecutor = timerExecutor;
            this.maxRetries = maxRetries;
            this.jitter = jitter;
            this.exponential = exponential;
            this.initialDelay = initialDelay;
            this.retryForPredicate = retryForPredicate;
        }

        /**
         * Checks the provided pair of meta-data of a type {@link M} and {@link Throwable} that the case is retryable.
         *
         * @param meta a meta-data of a type {@link M} to check
         * @param throwable an exception occurred
         * @return {@code true} if it is desirable to retry, {@code false} otherwise
         */
        public boolean isRetryable(final M meta, final Throwable throwable) {
            return retryForPredicate.test(meta, throwable);
        }

        /**
         * Builds a new retry strategy {@link BiIntFunction} for retrying with
         * {@link Publisher#retryWhen(BiIntFunction)}, {@link Single#retryWhen(BiIntFunction)}, and
         * {@link Completable#retryWhen(BiIntFunction)} or in general with an alternative timer {@link Executor}.
         *
         * @param alternativeTimerExecutor {@link Executor} to be used to schedule timers for backoff if no executor
         * was provided via {@link #timerExecutor(Executor)} builder method.
         * @return a new retry strategy {@link BiIntFunction}
         */
        public BiIntFunction<Throwable, Completable> newStrategy(@Nullable final Executor alternativeTimerExecutor) {
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
