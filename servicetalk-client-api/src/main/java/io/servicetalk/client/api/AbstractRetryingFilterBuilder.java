/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.transport.api.RetryableException;

import java.time.Duration;
import java.util.function.BiPredicate;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.failed;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithConstantBackoffDeltaJitter;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithConstantBackoffFullJitter;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithExponentialBackoffDeltaJitter;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithExponentialBackoffFullJitter;
import static java.time.Duration.ofDays;
import static java.time.Duration.ofMillis;
import static java.util.Objects.requireNonNull;

/**
 * An abstract builder for retrying filters.
 *
 * @param <Builder> the type of builder for retrying filter
 * @param <Filter> the type of retrying filter to build
 * @param <Meta> the type of meta-data for {@link #retryFor(BiPredicate)}
 * @see RetryStrategies
 * @deprecated Moving forward ServiceTalk will remove this abstraction. Please rely on the protocol specific filter.
 */
@Deprecated
public abstract class AbstractRetryingFilterBuilder<Builder
        extends AbstractRetryingFilterBuilder<Builder, Filter, Meta>, Filter, Meta> {
    private static final Duration FULL_JITTER = ofDays(1024);
    private static final Duration NULL_JITTER = ofMillis(1);
    private int maxRetries;
    protected boolean evaluateDelayedRetries;
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
     * Creates a new retrying {@link Filter} which adds a randomized delay between retries and uses the passed
     * {@link Duration} as a maximum delay possible. This additionally adds a "Full Jitter" for the backoff as described
     * <a href="https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/">here</a>.
     *
     * @param delay Maximum {@link Duration} of delay between retries
     * @return A new retrying {@link Filter} which adds a randomized delay between retries
     */
    public final Filter buildWithConstantBackoffFullJitter(final Duration delay) {
        return build(readOnlySettings(delay, FULL_JITTER, null, null, false, evaluateDelayedRetries));
    }

    /**
     * Creates a new retrying {@link Filter} which adds a randomized delay between retries and uses the passed
     * {@link Duration} as a maximum delay possible. This additionally adds a "Full Jitter" for the backoff as described
     * <a href="https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/">here</a>.
     *
     * @param delay Maximum {@link Duration} of delay between retries
     * @param timerExecutor {@link Executor} to be used to schedule timers for backoff. It takes precedence over an
     * alternative timer {@link Executor} from {@link ReadOnlyRetryableSettings#newStrategy(Executor)} argument
     * @return A new retrying {@link Filter} which adds a randomized delay between retries
     */
    public final Filter buildWithConstantBackoffFullJitter(final Duration delay, final Executor timerExecutor) {
        return build(readOnlySettings(delay, FULL_JITTER, null, timerExecutor, false, evaluateDelayedRetries));
    }

    /**
     * Creates a new retrying {@link Filter} which adds a randomized delay between retries and uses the passed
     * {@link Duration} as a maximum delay possible.
     *
     * @param delay Maximum {@link Duration} of delay between retries
     * @param jitter The jitter which is used as and offset to {@code initialDelay} on each retry
     * @return A new retrying {@link Filter} which adds a randomized delay between retries
     */
    public final Filter buildWithConstantBackoffDeltaJitter(final Duration delay, final Duration jitter) {
        return build(readOnlySettings(delay, jitter, null, null, false, evaluateDelayedRetries));
    }

    /**
     * Creates a new retrying {@link Filter} which adds a randomized delay between retries and uses the passed
     * {@link Duration} as a maximum delay possible.
     *
     * @param delay Maximum {@link Duration} of delay between retries
     * @param jitter The jitter which is used as and offset to {@code delay} on each retry
     * @param timerExecutor {@link Executor} to be used to schedule timers for backoff. It takes precedence over an
     * alternative timer {@link Executor} from {@link ReadOnlyRetryableSettings#newStrategy(Executor)} argument
     * @return A new retrying {@link Filter} which adds a randomized delay between retries
     */
    public final Filter buildWithConstantBackoffDeltaJitter(final Duration delay, final Duration jitter,
                                                            final Executor timerExecutor) {
        return build(readOnlySettings(delay, jitter, null, timerExecutor, false, evaluateDelayedRetries));
    }

    /**
     * Creates a new retrying {@link Filter} which adds a delay between retries. For first retry, the delay is
     * {@code initialDelay} which is increased exponentially for subsequent retries. This additionally adds a
     * "Full Jitter" for the backoff as described
     * <a href="https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/">here</a>.
     *
     * @param initialDelay Delay {@link Duration} for the first retry and increased exponentially with each retry
     * @param maxDelay The maximum amount of delay that will be introduced.
     * @return A new retrying {@link Filter} which adds an exponentially increasing delay between retries with jitter
     */
    public final Filter buildWithExponentialBackoffFullJitter(final Duration initialDelay, final Duration maxDelay) {
        return build(readOnlySettings(initialDelay, FULL_JITTER, maxDelay, null, true, evaluateDelayedRetries));
    }

    /**
     * Creates a new retrying {@link Filter} which adds a delay between retries. For first retry, the delay is
     * {@code initialDelay} which is increased exponentially for subsequent retries. This additionally adds a
     * "Full Jitter" for the backoff as described
     * <a href="https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/">here</a>.
     *
     * @param initialDelay Delay {@link Duration} for the first retry and increased exponentially with each retry
     * @param maxDelay The maximum amount of delay that will be introduced.
     * @param timerExecutor {@link Executor} to be used to schedule timers for backoff. It takes precedence over an
     * alternative timer {@link Executor} from {@link ReadOnlyRetryableSettings#newStrategy(Executor)} argument
     * @return A new retrying {@link Filter} which adds an exponentially increasing delay between retries with jitter
     */
    public final Filter buildWithExponentialBackoffFullJitter(final Duration initialDelay, final Duration maxDelay,
                                                              final Executor timerExecutor) {
        return build(readOnlySettings(initialDelay, FULL_JITTER, maxDelay, timerExecutor, true,
                evaluateDelayedRetries));
    }

    /**
     * Creates a new retrying {@link Filter} which adds a delay between retries. For first retry, the delay is
     * {@code initialDelay} which is increased exponentially for subsequent retries.
     *
     * @param initialDelay Delay {@link Duration} for the first retry and increased exponentially with each retry
     * @param jitter The jitter which is used as and offset to {@code initialDelay} on each retry
     * @param maxDelay The maximum amount of delay that will be introduced.
     * @return A new retrying {@link Filter} which adds an exponentially increasing delay between retries with jitter
     */
    public final Filter buildWithExponentialBackoffDeltaJitter(final Duration initialDelay, final Duration jitter,
                                                               final Duration maxDelay) {
        return build(readOnlySettings(initialDelay, jitter, maxDelay, null, true, evaluateDelayedRetries));
    }

    /**
     * Creates a new retrying {@link Filter} which adds a delay between retries. For first retry, the delay is
     * {@code initialDelay} which is increased exponentially for subsequent retries.
     *
     * @param initialDelay Delay {@link Duration} for the first retry and increased exponentially with each retry
     * @param jitter The jitter which is used as and offset to {@code initialDelay} on each retry
     * @param maxDelay The maximum amount of delay that will be introduced.
     * @param timerExecutor {@link Executor} to be used to schedule timers for backoff. It takes precedence over an
     * alternative timer {@link Executor} from {@link ReadOnlyRetryableSettings#newStrategy(Executor)} argument
     * @return A new retrying {@link Filter} which adds an exponentially increasing delay between retries with jitter
     */
    public final Filter buildWithExponentialBackoffDeltaJitter(final Duration initialDelay, final Duration jitter,
                                                               final Duration maxDelay, final Executor timerExecutor) {
        return build(readOnlySettings(initialDelay, jitter, maxDelay, timerExecutor, true, evaluateDelayedRetries));
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
    public BiPredicate<Meta, Throwable> defaultRetryForPredicate() {
        return (meta, throwable) -> throwable instanceof RetryableException;
    }

    private ReadOnlyRetryableSettings<Meta> readOnlySettings(@Nullable final Duration initialDelay,
                                                             final Duration jitter,
                                                             @Nullable final Duration maxDelay,
                                                             @Nullable final Executor timerExecutor,
                                                             final boolean exponential,
                                                             final boolean evaluateDelayedRetries) {
        return new ReadOnlyRetryableSettings<>(maxRetries > 0 ? maxRetries : (exponential ? 2 : 1),
                retryForPredicate != null ? retryForPredicate : defaultRetryForPredicate(),
                initialDelay, jitter, maxDelay, timerExecutor, exponential, evaluateDelayedRetries);
    }

    /**
     * A read-only settings for retryable filters.
     *
     * @param <Meta> the type of meta-data for {@link #retryFor(BiPredicate)}
     * @deprecated Moving forward ServiceTalk will remove this abstraction. Please rely on the protocol specific
     * filter and builder.
     */
    @Deprecated
    public static final class ReadOnlyRetryableSettings<Meta> {

        private final int maxRetries;
        private final BiPredicate<Meta, Throwable> retryForPredicate;
        @Nullable
        private final Duration initialDelay;
        private final Duration jitter;
        @Nullable
        private final Duration maxDelay;
        @Nullable
        private final Executor timerExecutor;
        private final boolean exponential;
        private final boolean evaluateDelayedRetries;

        private ReadOnlyRetryableSettings(final int maxRetries,
                                          final BiPredicate<Meta, Throwable> retryForPredicate,
                                          @Nullable final Duration initialDelay,
                                          final Duration jitter,
                                          @Nullable final Duration maxDelay,
                                          @Nullable final Executor timerExecutor,
                                          final boolean exponential,
                                          final boolean evaluateDelayedRetries) {
            this.maxRetries = maxRetries;
            this.retryForPredicate = retryForPredicate;
            this.initialDelay = initialDelay;
            this.timerExecutor = timerExecutor;
            this.exponential = exponential;
            this.jitter = requireNonNull(jitter);
            this.maxDelay = maxDelay;
            this.evaluateDelayedRetries = evaluateDelayedRetries;
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
         * Checks whether to evaluate the {@link Throwable} for a constant delay info, in which case that delay will
         * be additive to this configuration.
         * @return {@code true} if the {@link Throwable} should be evaluated for constant delay info.
         */
        public boolean evaluateDelayedRetries() {
            return evaluateDelayedRetries;
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
                return (count, throwable) -> count <= maxRetries ? completed() : failed(throwable);
            } else {
                final Executor effectiveExecutor = timerExecutor == null ?
                        requireNonNull(alternativeTimerExecutor) : timerExecutor;
                if (exponential) {
                    assert maxDelay != null;
                    return jitter == FULL_JITTER ?
                            retryWithExponentialBackoffFullJitter(
                                    maxRetries, t -> true, initialDelay, maxDelay, effectiveExecutor) :
                            retryWithExponentialBackoffDeltaJitter(
                                    maxRetries, t -> true, initialDelay, jitter, maxDelay, effectiveExecutor);
                } else {
                    return jitter == FULL_JITTER ?
                            retryWithConstantBackoffFullJitter(
                                    maxRetries, t -> true, initialDelay, effectiveExecutor) :
                            retryWithConstantBackoffDeltaJitter(
                                    maxRetries, t -> true, initialDelay, jitter, effectiveExecutor);
                }
            }
        }
    }
}
