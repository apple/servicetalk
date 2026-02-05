/*
 * Copyright © 2021-2024 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.netty;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.client.api.DelayedRetryException;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.LoadBalancerReadyEvent;
import io.servicetalk.client.api.NoAvailableHostException;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.concurrent.api.BiIntFunction;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.RetryStrategies;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableReservedStreamingHttpConnection;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpHeaderNames;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.ExecutionStrategyInfluencer;
import io.servicetalk.transport.api.RetryableException;
import io.servicetalk.utils.internal.ThrowableUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.failed;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithConstantBackoffDeltaJitter;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithConstantBackoffFullJitter;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithExponentialBackoffDeltaJitter;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithExponentialBackoffFullJitter;
import static io.servicetalk.http.api.HeaderUtils.DEFAULT_HEADER_FILTER;
import static io.servicetalk.http.api.HttpContextKeys.HTTP_EXECUTION_STRATEGY_KEY;
import static io.servicetalk.http.api.HttpHeaderNames.EXPECT;
import static io.servicetalk.http.api.HttpHeaderValues.CONTINUE;
import static io.servicetalk.http.api.HttpResponseStatus.EXPECTATION_FAILED;
import static io.servicetalk.http.netty.RetryingHttpRequesterFilter.BackOffPolicy.NO_RETRIES;
import static io.servicetalk.utils.internal.DurationUtils.ensureNonNegative;
import static io.servicetalk.utils.internal.DurationUtils.ensurePositive;
import static io.servicetalk.utils.internal.NumberUtils.ensureNonNegative;
import static io.servicetalk.utils.internal.NumberUtils.ensurePositive;
import static java.time.Duration.ofDays;
import static java.util.Objects.requireNonNull;

/**
 * A filter to enable retries for HTTP clients.
 * <p>
 * Retries are supported for both the request flow and the response flow. Retries, in other words, can be triggered
 * as part of a service response if needed, through {@link Builder#responseMapper(Function)}.
 * <p>
 * Retries can have different criteria and different backoff polices, as defined from the relevant Builder methods (i.e.
 * {@link Builder#retryOther(BiFunction)}).
 * Similarly, max-retries for each flow can be set in the {@link BackOffPolicy}, as well
 * as a total max-retries to be respected by both flows, as set in
 * {@link Builder#maxTotalRetries(int)}.
 * @see RetryStrategies
 */
public final class RetryingHttpRequesterFilter
        implements StreamingHttpClientFilterFactory, ExecutionStrategyInfluencer<HttpExecutionStrategy> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RetryingHttpRequesterFilter.class);

    static final int DEFAULT_MAX_TOTAL_RETRIES = 4;

    // This is the largest positive duration possible
    private static final Duration DURATION_MAX_VALUE = Duration.ofNanos(Long.MAX_VALUE);

    private static final RetryingHttpRequesterFilter DISABLE_AUTO_RETRIES =
            new RetryingHttpRequesterFilter(DURATION_MAX_VALUE, false, false, false, 1, null,
                    (__, ___) -> NO_RETRIES, null);
    private static final RetryingHttpRequesterFilter DISABLE_ALL_RETRIES =
            new RetryingHttpRequesterFilter(DURATION_MAX_VALUE, true, false, false, 0, null,
                    (__, ___) -> NO_RETRIES, null);

    private final Duration lbAvailableTimeout;
    private final boolean ignoreSdErrors;
    private final boolean mayReplayRequestPayload;
    private final boolean returnOriginalResponses;
    private final int maxTotalRetries;
    @Nullable
    private final Function<HttpResponseMetaData, HttpResponseException> responseMapper;
    private final BiFunction<HttpRequestMetaData, Throwable, BackOffPolicy> retryFor;
    @Nullable
    private final RetryCallbacks onRequestRetry;

    RetryingHttpRequesterFilter(
            final Duration lbAvailableTimeout,
            final boolean ignoreSdErrors, final boolean mayReplayRequestPayload,
            final boolean returnOriginalResponses, final int maxTotalRetries,
            @Nullable final Function<HttpResponseMetaData, HttpResponseException> responseMapper,
            final BiFunction<HttpRequestMetaData, Throwable, BackOffPolicy> retryFor,
            @Nullable final RetryCallbacks onRequestRetry) {
        this.lbAvailableTimeout = lbAvailableTimeout;
        this.ignoreSdErrors = ignoreSdErrors;
        this.mayReplayRequestPayload = mayReplayRequestPayload;
        this.returnOriginalResponses = returnOriginalResponses;
        this.maxTotalRetries = maxTotalRetries;
        this.responseMapper = responseMapper;
        this.retryFor = retryFor;
        this.onRequestRetry = onRequestRetry;
    }

    @Override
    public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
        return new ContextAwareRetryingHttpClientFilter(client);
    }

    @Override
    public HttpExecutionStrategy requiredOffloads() {
        // No influence since we do not block.
        return HttpExecutionStrategies.offloadNone();
    }

    final class ContextAwareRetryingHttpClientFilter extends StreamingHttpClientFilter {
        @Nullable
        private Completable sdStatus;
        @Nullable
        private Publisher<Object> lbEventStream;

        /**
         * Create a new instance.
         *
         * @param delegate The {@link FilterableStreamingHttpClient} to delegate all calls to.
         */
        private ContextAwareRetryingHttpClientFilter(final FilterableStreamingHttpClient delegate) {
            super(delegate);
        }

        void inject(@Nullable final Publisher<Object> lbEventStream,
                    @Nullable final Completable sdStatus) {
            this.sdStatus = ignoreSdErrors ? null : requireNonNull(sdStatus);
            this.lbEventStream = lbAvailableTimeout.isZero() ? null : requireNonNull(lbEventStream);
        }

        private final class OuterRetryStrategy implements BiIntFunction<Throwable, Completable> {
            private final Executor executor;
            private final HttpRequestMetaData requestMetaData;
            @Nullable
            private final RetryCallbacks retryCallbacks;
            /**
             * The outer retry strategy handles both "load balancer not ready" and "request failed" cases. This count
             * discounts the former so the ladder strategies only count actual request attempts.
             */
            private int lbNotReadyCount;

            private OuterRetryStrategy(final Executor executor,
                                       final HttpRequestMetaData requestMetaData,
                                       @Nullable final RetryCallbacks retryCallbacks) {
                this.executor = executor;
                this.requestMetaData = requestMetaData;
                this.retryCallbacks = retryCallbacks;
            }

            private Exception enrichTimeoutException(TimeoutException underlying) {
                Exception result = StacklessTimeoutException.newInstance(
                        "Load balancer availability timeout: " + lbAvailableTimeout.toMillis() + " ms",
                        RetryingHttpRequesterFilter.class, "awaitSdStatus()");
                result.initCause(underlying);
                return result;
            }

            private Completable computeLbAvailableDelay(@Nullable Completable injectedStatus) {
                if (injectedStatus == null || ignoreSdErrors) {
                    // If we don't have or don't care about service discovery errors we just need a completable that
                    // will result in a timeout.
                    return Completable.never().timeout(lbAvailableTimeout, executor)
                            .onErrorMap(TimeoutException.class, this::enrichTimeoutException);
                } else {
                    // The behavior is as follows:
                    // 1. The injected status will resolve either by error or timeout, whichever happens first.
                    //    This means it will always result in either an SD error or a timeout error.
                    // 2. The timer and the .mergeDelayError call will result in delaying the result of 1 for the
                    //    expected timeout.
                    return executor.timer(lbAvailableTimeout)
                            .mergeDelayError(injectedStatus.timeout(lbAvailableTimeout, executor)
                                    .onErrorMap(TimeoutException.class, this::enrichTimeoutException));
                }
            }

            @Override
            public Completable apply(final int count, final Throwable t) {
                if (count > maxTotalRetries) {
                    return failed(t);
                }

                if (lbEventStream != null && t instanceof NoAvailableHostException) {
                    ++lbNotReadyCount;
                    final Completable onHostsAvailable = lbEventStream
                            .onCompleteError(() -> new IllegalStateException("Subscriber listening for " +
                                    LoadBalancerReadyEvent.class.getSimpleName() +
                                    " completed unexpectedly"))
                            .takeWhile(lbEvent ->
                                    // Don't complete until we get a LoadBalancerReadyEvent that is ready.
                                    !(lbEvent instanceof LoadBalancerReadyEvent &&
                                            ((LoadBalancerReadyEvent) lbEvent).isReady()))
                            .ignoreElements();
                    return applyRetryCallbacks(onHostsAvailable.ambWith(computeLbAvailableDelay(sdStatus)), count, t);
                }

                try {
                    BackOffPolicy backOffPolicy = retryFor.apply(requestMetaData, t);
                    if (backOffPolicy != NO_RETRIES) {
                        final int offsetCount = count - lbNotReadyCount;
                        Completable retryWhen = backOffPolicy.newStrategy(executor).apply(offsetCount, t);
                        if (t instanceof DelayedRetryException) {
                            final Duration constant = ((DelayedRetryException) t).delay();
                            retryWhen = retryWhen.concat(executor.timer(constant));
                        }

                        return applyRetryCallbacks(retryWhen, count, t);
                    }
                } catch (Throwable tt) {
                    LOGGER.error("Unexpected exception when computing and applying backoff policy for {}({}). " +
                            "User-defined functions should not throw.",
                            RetryingHttpRequesterFilter.class.getName(), t.getMessage(), tt);
                    Completable result = failed(ThrowableUtils.addSuppressed(tt, t));
                    if (returnOriginalResponses) {
                        StreamingHttpResponse response = extractStreamingResponse(t);
                        if (response != null) {
                            result = drain(response).concat(result);
                        }
                    }
                    return result;
                }

                return failed(t);
            }

            Completable applyRetryCallbacks(final Completable completable, final int retryCount, final Throwable t) {
                Completable result = (retryCallbacks == null ? completable :
                        completable.beforeOnComplete(() -> retryCallbacks.beforeRetry(retryCount, requestMetaData, t)));
                if (returnOriginalResponses) {
                    final StreamingHttpResponse response = extractStreamingResponse(t);
                    if (response != null) {
                        // If we succeed, we need to drain the response body before we continue. The retry completable
                        // fails we want to surface the original exception and don't worry about draining since the
                        // response will be returned to the user.
                        result = result.onErrorMap(backoffError -> ThrowableUtils.addSuppressed(t, backoffError))
                                // If we get cancelled we also need to drain the message body as there is no guarantee
                                // we'll ever receive a completion event, error or success. This is okay to do since
                                // the subscriber has signaled they're no longer interested in the response.
                                .beforeCancel(() -> drain(response).subscribe())
                                .concat(drain(response));
                    }
                }
                return result;
            }
        }

        // Visible for testing
        BiIntFunction<Throwable, Completable> retryStrategy(final HttpRequestMetaData requestMetaData,
                                                            final ExecutionContext<HttpExecutionStrategy> context,
                                                            final boolean forRequest) {
            final HttpExecutionStrategy strategy = requestMetaData.context()
                    .getOrDefault(HTTP_EXECUTION_STRATEGY_KEY, context.executionStrategy());
            assert strategy != null;
            return new OuterRetryStrategy(strategy.isRequestResponseOffloaded() ?
                    context.executor() : context.ioExecutor(), requestMetaData,
                    forRequest ? onRequestRetry : null);
        }

        @Override
        public Single<? extends FilterableReservedStreamingHttpConnection> reserveConnection(
                final HttpRequestMetaData metaData) {
            return delegate().reserveConnection(metaData)
                    .retryWhen(retryStrategy(metaData, executionContext(), false));
        }

        @Override
        protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                        final StreamingHttpRequest request) {
            // State intentionally outside the defer because the request state is shared across subscribes. If
            // re-applying operators duplicates logic that isn't desirable and lead to StackOverflowException.
            final Publisher<Object> originalMessageBody = request.messageBody();
            Single<StreamingHttpResponse> single = Single.defer(() -> {
                final Single<StreamingHttpResponse> reqSingle = delegate.request(
                        request.transformMessageBody(mayReplayRequestPayload ?
                                messageBodyDuplicator(originalMessageBody) : p -> originalMessageBody));
                return reqSingle.shareContextOnSubscribe();
            });

            if (responseMapper != null) {
                single = single.flatMap(resp -> {
                    final HttpResponseException exception;
                    try {
                        exception = responseMapper.apply(resp);
                    } catch (Throwable t) {
                        LOGGER.error("Unexpected exception when mapping response ({}) to an exception. User-defined " +
                                "functions should not throw.", resp.status(), t);
                        return drain(resp).concat(Single.failed(t));
                    }
                    Single<StreamingHttpResponse> response;
                    if (exception == null) {
                        response = Single.succeeded(resp);
                    } else {
                        response = Single.failed(exception);
                        if (!returnOriginalResponses) {
                            response = drain(resp).concat(response);
                        }
                    }
                    return response.shareContextOnSubscribe();
                });
            }

            // 1. Metadata is shared across retries
            // 2. Publisher state is restored to original state for each retry
            // duplicatedRequest isn't used below because retryWhen must be applied outside the defer operator for (2).
            single = single.retryWhen(retryStrategy(request, executionContext(), true));
            if (returnOriginalResponses) {
                single = single.onErrorResume(HttpResponseException.class, t -> {
                    HttpResponseMetaData metaData = t.metaData();
                    return (metaData instanceof StreamingHttpResponse ?
                            Single.succeeded((StreamingHttpResponse) metaData) : Single.failed(t));
                });
            }
            return single;
        }
    }

    /**
     * Retrying filter that disables automatic retries for exceptions, but still waits until {@link LoadBalancer}
     * becomes {@link LoadBalancerReadyEvent ready} for the first time.
     *
     * @return a retrying filter that disables automatic retries for exceptions, but still waits until
     * {@link LoadBalancer} becomes {@link LoadBalancerReadyEvent ready} for the first time.
     * @see RetryingHttpRequesterFilter.Builder#waitForLoadBalancer(boolean)
     */
    public static RetryingHttpRequesterFilter disableAutoRetries() {
        return DISABLE_AUTO_RETRIES;
    }

    /**
     * Retrying filter that disables any form of retry behaviour. All types of failures will not be re-attempted,
     * including {@link LoadBalancer} {@link LoadBalancerReadyEvent readiness state}.
     *
     * @return a retrying filter that disables any form of retry behaviour. All types of failures will not be
     * re-attempted.
     * @see RetryingHttpRequesterFilter.Builder#waitForLoadBalancer(boolean)
     */
    public static RetryingHttpRequesterFilter disableAllRetries() {
        return DISABLE_ALL_RETRIES;
    }

    /**
     * This exception indicates response that matched the retrying rules of the {@link RetryingHttpRequesterFilter}
     * and will-be/was retried.
     * {@link HttpResponseException}s are user-provided errors, resulting from an {@link HttpRequestMetaData}, through
     * the {@link Builder#responseMapper(Function)}.
     */
    public static class HttpResponseException extends RuntimeException {

        private static final long serialVersionUID = -7182949760823647710L;

        // FIXME: 0.43 - make deprecated field private
        /**
         * {@link HttpResponseMetaData} of the response that caused this exception.
         *
         * @deprecated Use {@link #metaData()}.
         */
        @Deprecated
        public final HttpResponseMetaData metaData;

        // FIXME: 0.43 - remove deprecated field
        /**
         * Exception detail message.
         *
         * @deprecated Use {@link #getMessage()}.
         */
        @Deprecated
        public final String message;

        /**
         * Create a new instance.
         *
         * @param message the description message.
         * @param metaData received response meta-data.
         */
        public HttpResponseException(final String message, final HttpResponseMetaData metaData) {
            super(message);
            this.metaData = requireNonNull(metaData);
            this.message = requireNonNull(message);
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }

        /**
         * {@link HttpResponseMetaData} of the response that caused this exception.
         * @return The {@link HttpResponseMetaData} of the response that caused this exception.
         */
        public HttpResponseMetaData metaData() {
            return metaData;
        }

        @Override
        public String toString() {
            return super.toString() +
                    ", metaData=" + metaData.toString(DEFAULT_HEADER_FILTER);
        }
    }

    private static UnaryOperator<Publisher<?>> messageBodyDuplicator(Publisher<?> originalPublisher) {
        return p -> originalPublisher.map(item -> {
            if (item instanceof Buffer) {
                return ((Buffer) item).duplicate();
            }
            return item;
        });
    }

    /**
     * Definition and presets of retry backoff policies.
     */
    public static final class BackOffPolicy {

        private static final Duration FULL_JITTER = ofDays(1024);
        // Subtract 1 because the total strategy anticipates 1 failure from LB not being ready (due to SD available
        // events not yet arriving), however this level of retry is strictly applied to request/response failures.
        private static final BackOffPolicy IMMEDIATE_DEFAULT_RETRIES = new BackOffPolicy(DEFAULT_MAX_TOTAL_RETRIES - 1);

        // FIXME: 0.43 - change field accessor to default
        /**
         * Special {@link BackOffPolicy} to signal no retries.
         * @deprecated This will be removed in a future release of ST. Alternative offering here
         * {@link BackOffPolicy#ofNoRetries()}.
         */
        @Deprecated
        public static final BackOffPolicy NO_RETRIES = new BackOffPolicy(0);

        @Nullable
        final Duration initialDelay;
        @Nullable
        final Duration jitter;
        @Nullable
        final Duration maxDelay;
        @Nullable
        final Executor timerExecutor;
        final boolean exponential;
        final int maxRetries;

        BackOffPolicy(final Duration initialDelay,
                      final Duration jitter,
                      @Nullable final Duration maxDelay,
                      @Nullable final Executor timerExecutor,
                      final boolean exponential,
                      final int maxRetries) {
            this.initialDelay = ensurePositive(initialDelay, "Initial delay should be a positive value.");
            this.jitter = ensureNonNegative(jitter, "jitter should be a non-negative value.");
            this.maxDelay = maxDelay != null ? ensurePositive(maxDelay, "Max delay (if provided), should be a " +
                    "positive value.") : null;
            this.timerExecutor = timerExecutor;
            this.exponential = exponential;
            this.maxRetries = ensurePositive(maxRetries, "maxRetries");
        }

        BackOffPolicy(final int maxRetries) {
            this.initialDelay = null;
            this.jitter = null;
            this.maxDelay = null;
            this.timerExecutor = null;
            this.exponential = false;
            this.maxRetries = ensureNonNegative(maxRetries, "maxRetries");
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() +
                    "{maxRetries=" + maxRetries +
                    ", initialDelay=" + initialDelay +
                    ", jitter=" + jitter +
                    ", maxDelay=" + maxDelay +
                    ", exponential=" + exponential +
                    ", timerExecutor=" + timerExecutor +
                    '}';
        }

        /**
         * Creates a new {@link BackOffPolicy} that retries failures instantly up-to 3 max retries.
         *
         * @return a new {@link BackOffPolicy} that retries failures instantly up-to 3 max retries.
         * @deprecated Use {@link #ofImmediateBounded()}.
         */
        @Deprecated
        public static BackOffPolicy ofImmediate() {
            return ofImmediateBounded();
        }

        /**
         * Creates a new {@link BackOffPolicy} that retries failures instantly up-to 3 max retries.
         *
         * @return a new {@link BackOffPolicy} that retries failures instantly up-to 3 max retries.
         * @see #ofImmediate(int)
         */
        public static BackOffPolicy ofImmediateBounded() {
            return IMMEDIATE_DEFAULT_RETRIES;
        }

        /**
         * Creates a new {@link BackOffPolicy} that retries failures instantly up-to provided max retries.
         *
         * @param maxRetries the number of retry attempts for this {@link BackOffPolicy}.
         * @return a new {@link BackOffPolicy} that retries failures instantly up-to provided max retries.
         */
        public static BackOffPolicy ofImmediate(final int maxRetries) {
            return new BackOffPolicy(maxRetries);
        }

        /**
         * Special {@link BackOffPolicy} that signals that no retries will be attempted.
         * @return a special {@link BackOffPolicy} that signals that no retries will be attempted.
         */
        public static BackOffPolicy ofNoRetries() {
            return NO_RETRIES;
        }

        /**
         * Creates a new retrying {@link BackOffPolicy} which adds a randomized delay between retries
         * and uses the passed {@link Duration} as a maximum delay possible.
         * This additionally adds a "Full Jitter" for the backoff as described
         * <a href="https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/">here</a>.
         *
         * @param delay Maximum {@link Duration} of delay between retries
         * @param maxRetries The maximum retries before it gives up.
         * @return A new retrying {@link BackOffPolicy} which adds a randomized delay between retries
         */
        public static BackOffPolicy ofConstantBackoffFullJitter(final Duration delay, final int maxRetries) {
            return new BackOffPolicy(delay, FULL_JITTER, null, null, false, maxRetries);
        }

        /**
         * Creates a new retrying {@link BackOffPolicy} which adds a randomized delay between retries
         * and uses the passed {@link Duration} as a maximum delay possible.
         * This additionally adds a "Full Jitter" for the backoff as described
         * <a href="https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/">here</a>.
         *
         * @param delay Maximum {@link Duration} of delay between retries
         * @param maxRetries The maximum retries before it gives up.
         * @param timerExecutor {@link Executor} to be used to schedule timers for backoff.
         * It takes precedence over an alternative timer {@link Executor} from
         * {@link #newStrategy(Executor)} argument
         * @return A new retrying {@link BackOffPolicy} which adds a randomized delay between retries
         */
        public static BackOffPolicy ofConstantBackoffFullJitter(final Duration delay, final int maxRetries,
                                                                final Executor timerExecutor) {
            return new BackOffPolicy(delay, FULL_JITTER, null, timerExecutor, false, maxRetries);
        }

        /**
         * Creates a new retrying {@link BackOffPolicy} which adds a randomized delay between retries
         * and uses the passed {@link Duration} as a maximum delay possible.
         *
         * @param delay Maximum {@link Duration} of delay between retries
         * @param jitter The jitter which is used as and offset to {@code initialDelay} on each retry
         * @param maxRetries The maximum retries before it gives up.
         * @return A new retrying {@link BackOffPolicy} which adds a randomized delay between retries
         */
        public static BackOffPolicy ofConstantBackoffDeltaJitter(final Duration delay, final Duration jitter,
                                                                 final int maxRetries) {
            return new BackOffPolicy(delay, jitter, null, null, false, maxRetries);
        }

        /**
         * Creates a new retrying {@link BackOffPolicy} which adds a randomized delay between retries
         * and uses the passed {@link Duration} as a maximum delay possible.
         *
         * @param delay Maximum {@link Duration} of delay between retries
         * @param jitter The jitter which is used as and offset to {@code delay} on each retry
         * @param timerExecutor {@link Executor} to be used to schedule timers for backoff.
         * @param maxRetries The maximum retries before it gives up.
         * It takes precedence over an alternative timer {@link Executor} from
         * {@link #newStrategy(Executor)} argument
         * @return A new retrying {@link BackOffPolicy} which adds a randomized delay between retries
         */
        public static BackOffPolicy ofConstantBackoffDeltaJitter(final Duration delay, final Duration jitter,
                                                                 final Executor timerExecutor,
                                                                 final int maxRetries) {
            return new BackOffPolicy(delay, jitter, null, timerExecutor, false, maxRetries);
        }

        /**
         * Creates a new retrying {@link BackOffPolicy} which adds a delay between retries.
         * For first retry, the delay is {@code initialDelay} which is increased exponentially for subsequent
         * retries.
         * This additionally adds a "Full Jitter" for the backoff as described
         * <a href="https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/">here</a>.
         *
         * @param initialDelay Delay {@link Duration} for the first retry and increased exponentially
         * with each retry
         * @param maxDelay The maximum amount of delay that will be introduced.
         * @param maxRetries The maximum retries before it gives up.
         * @return A new retrying {@link BackOffPolicy} which adds an exponentially increasing
         * delay between retries with jitter
         */
        public static BackOffPolicy ofExponentialBackoffFullJitter(final Duration initialDelay,
                                                                   final Duration maxDelay,
                                                                   final int maxRetries) {
            return new BackOffPolicy(initialDelay, FULL_JITTER, maxDelay, null, true, maxRetries);
        }

        /**
         * Creates a new retrying {@link BackOffPolicy} which adds a delay between retries.
         * For first retry, the delay is {@code initialDelay} which is increased exponentially for subsequent
         * retries.
         * This additionally adds a "Full Jitter" for the backoff as described
         * <a href="https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/">here</a>.
         *
         * @param initialDelay Delay {@link Duration} for the first retry and increased exponentially
         * with each retry
         * @param maxDelay The maximum amount of delay that will be introduced.
         * @param maxRetries The maximum retries before it gives up.
         * @param timerExecutor {@link Executor} to be used to schedule timers for backoff.
         * It takes precedence over an alternative timer {@link Executor} from
         * {@link #newStrategy(Executor)} argument
         * @return A new retrying {@link BackOffPolicy} which adds an exponentially increasing
         * delay between retries with jitter
         */
        public static BackOffPolicy ofExponentialBackoffFullJitter(
                final Duration initialDelay, final Duration maxDelay, final int maxRetries,
                final Executor timerExecutor) {
            return new BackOffPolicy(initialDelay, FULL_JITTER, maxDelay, timerExecutor, true, maxRetries);
        }

        /**
         * Creates a new retrying {@link BackOffPolicy} which adds a delay between retries.
         * For first retry, the delay is {@code initialDelay} which is increased exponentially for subsequent
         * retries.
         *
         * @param initialDelay Delay {@link Duration} for the first retry and increased exponentially
         * with each retry
         * @param jitter The jitter which is used as and offset to {@code initialDelay} on each retry
         * @param maxDelay The maximum amount of delay that will be introduced.
         * @param maxRetries The maximum retries before it gives up.
         * @return A new retrying {@link BackOffPolicy} which adds an exponentially increasing
         * delay between retries with jitter
         */
        public static BackOffPolicy ofExponentialBackoffDeltaJitter(
                final Duration initialDelay, final Duration jitter, final Duration maxDelay, final int maxRetries) {
            return new BackOffPolicy(initialDelay, jitter, maxDelay, null, true, maxRetries);
        }

        /**
         * Creates a new retrying {@link BackOffPolicy} which adds a delay between retries.
         * For first retry, the delay is {@code initialDelay} which is increased exponentially for subsequent
         * retries.
         *
         * @param initialDelay Delay {@link Duration} for the first retry and increased exponentially
         * with each retry
         * @param jitter The jitter which is used as and offset to {@code initialDelay} on each retry
         * @param maxDelay The maximum amount of delay that will be introduced.
         * @param maxRetries The maximum retries before it gives up.
         * @param timerExecutor {@link Executor} to be used to schedule timers for backoff.
         * It takes precedence over an alternative timer {@link Executor} from
         * {@link #newStrategy(Executor)} argument
         * @return A new retrying {@link BackOffPolicy} which adds an exponentially increasing
         * delay between retries with jitter
         */
        public static BackOffPolicy ofExponentialBackoffDeltaJitter(
                final Duration initialDelay, final Duration jitter, final Duration maxDelay, final int maxRetries,
                final Executor timerExecutor) {
            return new BackOffPolicy(initialDelay, jitter, maxDelay, timerExecutor, true, maxRetries);
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
                assert jitter != null;
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

    /**
     * An interface that enhances any {@link Exception} to provide a constant {@link Duration delay} to be applied when
     * retrying through a {@link RetryingHttpRequesterFilter retrying-filter}.
     * <p>
     * Constant delay returned from {@link #delay()} will be additive to the backoff policy defined for a certain
     * retry-able failure.
     *
     * @deprecated Use {@link DelayedRetryException} instead.
     */
    @Deprecated // FIXME: 0.43 - remove deprecated interface
    @FunctionalInterface
    public interface DelayedRetry extends DelayedRetryException {

        /**
         * A constant delay to apply.
         * <p>
         * The total delay for the retry logic will be the sum of this value and the result of the
         * {@link BackOffPolicy back-off policy} in-use. Consider using 'full-jitter'
         * flavours from the {@link BackOffPolicy} to avoid having another constant delay applied per-retry.
         *
         * @return The {@link Duration} to apply as constant delay when retrying.
         */
        @Override
        Duration delay();

        @Override
        @SuppressWarnings("InstanceofIncompatibleInterface")
        default Throwable throwable() {
            if (this instanceof Throwable) {
                return (Throwable) this;
            }
            throw new UnsupportedOperationException("DelayedRetry#throwable() is not supported by " + getClass());
        }
    }

    /**
     * Callbacks invoked on a retry attempt.
     */
    @FunctionalInterface
    public interface RetryCallbacks {

        /**
         * Called after a retry decision has been made, but before the retry is performed.
         *
         * @param retryCount a current retry counter value for this attempt
         * @param requestMetaData {@link HttpRequestMetaData} that is being retried
         * @param cause {@link Throwable} cause for the retry
         */
        void beforeRetry(int retryCount, HttpRequestMetaData requestMetaData, Throwable cause);
    }

    /**
     * A builder for {@link RetryingHttpRequesterFilter}, which puts an upper bound on retry attempts.
     * To configure the maximum number of retry attempts see {@link #maxTotalRetries(int)}.
     */
    public static final class Builder {

        private static final Function<HttpResponseMetaData, HttpResponseException> EXPECTATION_FAILED_MAPPER =
                metaData -> EXPECTATION_FAILED.equals(metaData.status()) ?
                        new ExpectationFailedException("Expectation failed", metaData) : null;

        private boolean ignoreSdErrors;
        @Nullable
        private Duration lbAvailableTimeout = DURATION_MAX_VALUE;

        private int maxTotalRetries = DEFAULT_MAX_TOTAL_RETRIES;
        private boolean retryExpectationFailed;
        private boolean returnOriginalResponses;

        private BiFunction<HttpRequestMetaData, RetryableException, BackOffPolicy>
                retryRetryableExceptions = (requestMetaData, e) -> BackOffPolicy.ofImmediateBounded();

        @Nullable
        private Function<HttpResponseMetaData, HttpResponseException> responseMapper;

        @Nullable
        private BiFunction<HttpRequestMetaData, IOException, BackOffPolicy> retryIdempotentRequests;

        @Nullable
        private BiFunction<HttpRequestMetaData, DelayedRetry, BackOffPolicy> retryDelayedRetries;

        @Nullable
        private BiFunction<HttpRequestMetaData, DelayedRetryException, BackOffPolicy>
                retryDelayedRetryExceptions;

        @Nullable
        private BiFunction<HttpRequestMetaData, HttpResponseException, BackOffPolicy> retryResponses;

        @Nullable
        private BiFunction<HttpRequestMetaData, Throwable, BackOffPolicy> retryOther;

        @Nullable
        private RetryCallbacks onRequestRetry;

        /**
         * By default, automatic retries wait for the associated {@link LoadBalancer} to be
         * {@link LoadBalancerReadyEvent ready} before triggering a retry for requests. This behavior may add latency to
         * requests till the time the load balancer is ready instead of failing fast. This method allows controlling
         * that behavior.
         *
         * @param waitForLb Whether to wait for the {@link LoadBalancer} to be ready before retrying requests.
         * @return {@code this}.
         * @deprecated use {@link #waitForLoadBalancer(Duration)} instead.
         */
        @Deprecated
        public Builder waitForLoadBalancer(final boolean waitForLb) {
            return waitForLoadBalancer(waitForLb ? DURATION_MAX_VALUE : Duration.ZERO);
        }

        /**
         * By default, automatic retries will wait for the {@link LoadBalancer} to be
         * {@link LoadBalancerReadyEvent ready} or for the {@link ServiceDiscoverer} to report an error, whichever
         * happens first. This method allows users to modify the behavior such that automatic retries will give the
         * load balancer a grace period over which to be ready, after which the service discovery error (or a
         * timeout if there is no error) will be propagated. This allows users to prevent situations such as spurious
         * DNS failures from causing their client to be unhealthy while also not causing the client to lock up
         * indefinitely for real problems such as an invalid DNS name.
         *
         * @param timeout the time to wait for the load balancer to be ready before returning an error. A value of
         * {@link Duration#ZERO} disables waiting for the load balancer.
         * @return {@code this}.
         */
        public Builder waitForLoadBalancer(Duration timeout) {
            this.lbAvailableTimeout = ensureNonNegative(timeout, "timeout");
            return this;
        }

        /**
         * By default, fail a request if the last signal from the associated {@link ServiceDiscoverer} was an error.
         * This method disables that behavior.
         *
         * @param ignoreSdErrors ignore {@link ServiceDiscoverer} errors when evaluating a request failure.
         * @return {@code this}.
         */
        public Builder ignoreServiceDiscovererErrors(final boolean ignoreSdErrors) {
            this.ignoreSdErrors = ignoreSdErrors;
            return this;
        }

        /**
         * Set the maximum number of allowed retry operations before giving up, applied as total max across all retry
         * functions (see. {@link #retryDelayedRetries(BiFunction)}, {@link #retryIdempotentRequests(BiFunction)},
         * {@link #retryRetryableExceptions(BiFunction)}, {@link #retryResponses(BiFunction)},
         * {@link #retryOther(BiFunction)}).
         * <p>
         * Maximum total retries guards the LB/SD readiness flow, making sure LB connection issues will also be
         * retried with a limit.
         *
         * @param maxRetries Maximum number of allowed retries before giving up
         * @return {@code this}
         */
        public Builder maxTotalRetries(final int maxRetries) {
            this.maxTotalRetries = ensurePositive(maxRetries, "maxRetries");
            return this;
        }

        /**
         * Selectively map a {@link HttpResponseMetaData response} to an {@link HttpResponseException} that can match a
         * retry behaviour through {@link #retryResponses(BiFunction)}.
         *
         * @param mapper a {@link Function} that maps a {@link HttpResponseMetaData} to an
         * {@link HttpResponseException} or returns {@code null} if there is no mapping for response meta-data.
         * In the case that the request cannot be retried, the {@link HttpResponseException} will be returned via the
         * error pathway.
         * <p>
         * <strong>It's important that this {@link Function} doesn't block to avoid performance impacts.</strong>
         * <strong>It's important that this {@link Function} doesn't throw exceptions.</strong>
         *
         * @return {@code this}
         */
        public Builder responseMapper(final Function<HttpResponseMetaData, HttpResponseException> mapper) {
            this.responseMapper = requireNonNull(mapper);
            return this;
        }

        /**
         * The retrying-filter will evaluate for {@link RetryableException}s in the request flow.
         * <p>
         * To disable retries you can return {@link BackOffPolicy#ofNoRetries()} from the {@code mapper}.
         * <p>
         * <strong>It's important that this {@link BiFunction} doesn't block to avoid performance impacts.</strong>
         * <strong>It's important that this {@link BiFunction} doesn't throw exceptions.</strong>
         *
         * @param mapper The mapper to map the {@link HttpRequestMetaData} and the
         * {@link RetryableException} to a {@link BackOffPolicy}.
         * @return {@code this}.
         */
        public Builder retryRetryableExceptions(
                final BiFunction<HttpRequestMetaData, RetryableException, BackOffPolicy> mapper) {
            this.retryRetryableExceptions = requireNonNull(mapper);
            return this;
        }

        /**
         * Retries <a href="https://tools.ietf.org/html/rfc7231#section-4.2.2">idempotent</a> requests when applicable.
         * <p>
         * <b>Note:</b> This predicate expects that the retried {@link StreamingHttpRequest requests} have a
         * {@link StreamingHttpRequest#payloadBody() payload body} that is
         * <a href="https://reactivex.io/documentation/operators/replay.html">replayable</a>, i.e. multiple subscribes
         * to the payload {@link Publisher} observe the same data. {@link Publisher}s that do not emit any data or
         * which are created from in-memory data are typically replayable.
         * <p>
         * To disable retries you can return {@link BackOffPolicy#ofNoRetries()} from the {@code mapper}.
         * <p>
         * <strong>It's important that this {@link BiFunction} doesn't block to avoid performance impacts.</strong>
         * <strong>It's important that this {@link BiFunction} doesn't throw exceptions.</strong>
         *
         * @param mapper The mapper to map the {@link HttpRequestMetaData} and the
         * {@link IOException} to a {@link BackOffPolicy}.
         * @return {@code this}.
         */
        public Builder retryIdempotentRequests(
                final BiFunction<HttpRequestMetaData, IOException, BackOffPolicy> mapper) {
            this.retryIdempotentRequests = requireNonNull(mapper);
            return this;
        }

        /**
         * Retries {@link HttpResponseStatus#EXPECTATION_FAILED} response without {@link HttpHeaderNames#EXPECT} header.
         *
         * @param retryExpectationFailed if {@code true}, filter will automatically map
         * {@link HttpResponseStatus#EXPECTATION_FAILED} into {@link ExpectationFailedException} and retry a request
         * without {@link HttpHeaderNames#EXPECT} header.
         * @return {@code this}.
         * @see <a href="https://datatracker.ietf.org/doc/html/rfc7231#section-5.1.1">Expect</a>
         */
        public Builder retryExpectationFailed(boolean retryExpectationFailed) {
            this.retryExpectationFailed = retryExpectationFailed;
            return this;
        }

        /**
         * The retrying-filter will evaluate the {@link Throwable} marked with
         * {@link DelayedRetryException} interface and use the provided
         * {@link DelayedRetryException#delay() delay} as a constant delay on-top of the
         * retry period already defined.
         * <p>
         * In case a max-delay was set in this builder, the
         * {@link DelayedRetryException#delay() constant-delay} overrides it and takes precedence.
         * <p>
         * To disable retries and proceed evaluating other retry functions you can return,
         * {@link BackOffPolicy#ofNoRetries()} from the passed {@code mapper}.
         * <p>
         * <strong>It's important that this {@link BiFunction} doesn't block to avoid performance impacts.</strong>
         * <strong>It's important that this {@link BiFunction} doesn't throw exceptions.</strong>
         *
         * @param mapper The mapper to map the {@link HttpRequestMetaData} and the
         * {@link DelayedRetryException delayed-exception} to a {@link BackOffPolicy}.
         * @return {@code this}.
         */
        public Builder retryDelayedRetryExceptions(
                final BiFunction<HttpRequestMetaData, DelayedRetryException, BackOffPolicy> mapper) {
            this.retryDelayedRetryExceptions = requireNonNull(mapper);
            return this;
        }

        /**
         * The retrying-filter will evaluate the {@link DelayedRetry} marker interface
         * of an exception and use the provided {@link DelayedRetry#delay() delay} as a constant delay on-top of the
         * retry period already defined.
         * <p>
         * In case a max-delay was set in this builder, the {@link DelayedRetry#delay() constant-delay} overrides
         * it and takes precedence.
         * <p>
         * To disable retries you can return {@link BackOffPolicy#NO_RETRIES} from the {@code mapper}.
         * <p>
         * <strong>It's important that this {@link BiFunction} doesn't block to avoid performance impacts.</strong>
         * <strong>It's important that this {@link BiFunction} doesn't throw exceptions.</strong>
         *
         * @param mapper The mapper to map the {@link HttpRequestMetaData} and the
         * {@link DelayedRetry delayed-exception} to a {@link BackOffPolicy}.
         * @return {@code this}.
         * @deprecated Use {@link #retryDelayedRetryExceptions(BiFunction)} instead.
         */
        @Deprecated
        public Builder retryDelayedRetries(// FIXME: 0.43 - remove deprecated method
                final BiFunction<HttpRequestMetaData, DelayedRetry, BackOffPolicy> mapper) {
            this.retryDelayedRetries = requireNonNull(mapper);
            return this;
        }

        /**
         * The retrying-filter will evaluate {@link HttpResponseException} that resulted from the
         * {@link #responseMapper(Function)}, and support different retry behaviour according to the
         * {@link HttpRequestMetaData request} and the {@link HttpResponseMetaData response}.
         * <p>
         * To disable retries you can return {@link BackOffPolicy#NO_RETRIES} from the {@code mapper}.
         * <p>
         * <strong>It's important that this {@link BiFunction} doesn't block to avoid performance impacts.</strong>
         * <strong>It's important that this {@link BiFunction} doesn't throw exceptions.</strong>
         *
         * @param mapper The mapper to map the {@link HttpRequestMetaData} and the
         * {@link DelayedRetry delayed-exception} to a {@link BackOffPolicy}.
         * @return {@code this}.
         */
        public Builder retryResponses(
                final BiFunction<HttpRequestMetaData, HttpResponseException, BackOffPolicy> mapper) {
            return retryResponses(mapper, false);
        }

        /**
         * The retrying-filter will evaluate {@link HttpResponseException} that resulted from the
         * {@link #responseMapper(Function)}, and support different retry behaviour according to the
         * {@link HttpRequestMetaData request} and the {@link HttpResponseMetaData response}.
         * <p>
         * To disable retries you can return {@link BackOffPolicy#NO_RETRIES} from the {@code mapper}.
         * <p>
         * <strong>It's important that this {@link BiFunction} doesn't block to avoid performance impacts.</strong>
         * <strong>It's important that this {@link BiFunction} doesn't throw exceptions.</strong>
         *
         * @param mapper The mapper to map the {@link HttpRequestMetaData} and the
         * {@link DelayedRetry delayed-exception} to a {@link BackOffPolicy}.
         * @param returnOriginalResponses whether to unwrap the response defined by the {@link HttpResponseException}
         * meta-data in the case that the request is not retried.
         * @return {@code this}.
         */
        public Builder retryResponses(
                final BiFunction<HttpRequestMetaData, HttpResponseException, BackOffPolicy> mapper,
                final boolean returnOriginalResponses) {
            this.retryResponses = requireNonNull(mapper);
            this.returnOriginalResponses = returnOriginalResponses;
            return this;
        }

        /**
         * Support additional criteria for determining which requests or errors should be
         * retried.
         * <p>
         * To disable retries you can return {@link BackOffPolicy#NO_RETRIES} from the {@code mapper}.
         * <p>
         * <strong>It's important that this {@link BiFunction} doesn't block to avoid performance impacts.</strong>
         * <strong>It's important that this {@link BiFunction} doesn't throw exceptions.</strong>
         *
         * @param mapper {@link BiFunction} that checks whether a given combination of
         * {@link HttpRequestMetaData meta-data} and {@link Throwable cause} should be retried, producing a
         * {@link BackOffPolicy} in such cases.
         * @return {@code this}
         */
        public Builder retryOther(
                final BiFunction<HttpRequestMetaData, Throwable, BackOffPolicy> mapper) {
            this.retryOther = requireNonNull(mapper);
            return this;
        }

        /**
         * Callback invoked on every {@link StreamingHttpClient#request(StreamingHttpRequest) request} retry attempt.
         * <p>
         * This can be used to track when {@link BackOffPolicy} actually decides to retry a request, to update
         * {@link HttpRequestMetaData request meta-data} before a retry, or implement logging/metrics. However, it
         * can not be used to influence the retry decision, use other "retry*" functions for that purpose.
         *
         * @param onRequestRetry {@link RetryCallbacks} to get notified on every
         * {@link StreamingHttpClient#request(StreamingHttpRequest) request} retry attempt
         * @return {@code this}
         */
        public Builder onRequestRetry(final RetryCallbacks onRequestRetry) {
            this.onRequestRetry = requireNonNull(onRequestRetry);
            return this;
        }

        /**
         * Builds a retrying {@link RetryingHttpRequesterFilter} with this' builders configuration.
         *
         * @return A new retrying {@link RetryingHttpRequesterFilter}
         */
        public RetryingHttpRequesterFilter build() {
            final boolean retryExpectationFailed = this.retryExpectationFailed;
            final Function<HttpResponseMetaData, HttpResponseException> thisResponseMapper = this.responseMapper;
            final Function<HttpResponseMetaData, HttpResponseException> responseMapper;
            if (retryExpectationFailed) {
                responseMapper = thisResponseMapper == null ? EXPECTATION_FAILED_MAPPER : metaData -> {
                    final HttpResponseException e = thisResponseMapper.apply(metaData);
                    return e == null ? EXPECTATION_FAILED_MAPPER.apply(metaData) : e;
                };
            } else {
                responseMapper = thisResponseMapper;
            }

            final BiFunction<HttpRequestMetaData, RetryableException, BackOffPolicy> retryRetryableExceptions =
                    this.retryRetryableExceptions;
            final BiFunction<HttpRequestMetaData, IOException, BackOffPolicy> retryIdempotentRequests =
                    this.retryIdempotentRequests;
            final BiFunction<HttpRequestMetaData, DelayedRetryException, BackOffPolicy>
                    retryDelayedRetryExceptions = this.retryDelayedRetryExceptions;
            final BiFunction<HttpRequestMetaData, DelayedRetry, BackOffPolicy> retryDelayedRetries =
                    this.retryDelayedRetries;
            final BiFunction<HttpRequestMetaData, HttpResponseException, BackOffPolicy> retryResponses =
                    this.retryResponses;
            final BiFunction<HttpRequestMetaData, Throwable, BackOffPolicy> retryOther = this.retryOther;
            // This assumes RetryableExceptions are never written/consumed.
            final boolean mayReplayRequestPayload = retryIdempotentRequests != null ||
                    retryDelayedRetryExceptions != null ||
                    retryDelayedRetries != null ||
                    retryResponses != null ||
                    retryOther != null;

            final BiFunction<HttpRequestMetaData, Throwable, BackOffPolicy> allPredicate =
                    (requestMetaData, throwable) -> {
                        if (throwable instanceof RetryableException) {
                            final BackOffPolicy backOffPolicy =
                                    retryRetryableExceptions.apply(requestMetaData, (RetryableException) throwable);
                            if (backOffPolicy != NO_RETRIES) {
                                return backOffPolicy;
                            }
                        }

                        if (retryExpectationFailed && throwable instanceof ExpectationFailedException &&
                                requestMetaData.headers().containsIgnoreCase(EXPECT, CONTINUE)) {
                            requestMetaData.headers().remove(EXPECT);
                            return BackOffPolicy.ofImmediateBounded();
                        }

                        if (retryIdempotentRequests != null && throwable instanceof IOException
                                && requestMetaData.method().properties().isIdempotent()) {
                            final BackOffPolicy backOffPolicy =
                                    retryIdempotentRequests.apply(requestMetaData, (IOException) throwable);
                            if (backOffPolicy != NO_RETRIES) {
                                return backOffPolicy;
                            }
                        }

                        if (retryDelayedRetryExceptions != null &&
                                throwable instanceof DelayedRetryException) {
                            final BackOffPolicy backOffPolicy = retryDelayedRetryExceptions.apply(requestMetaData,
                                    (DelayedRetryException) throwable);
                            if (backOffPolicy != NO_RETRIES) {
                                return backOffPolicy;
                            }
                        }

                        if (retryDelayedRetries != null && throwable instanceof DelayedRetry) {
                            final BackOffPolicy backOffPolicy =
                                    retryDelayedRetries.apply(requestMetaData, (DelayedRetry) throwable);
                            if (backOffPolicy != NO_RETRIES) {
                                return backOffPolicy;
                            }
                        }

                        if (retryResponses != null && throwable instanceof HttpResponseException) {
                            final BackOffPolicy backOffPolicy =
                                        retryResponses.apply(requestMetaData, (HttpResponseException) throwable);
                            if (backOffPolicy != NO_RETRIES) {
                                return backOffPolicy;
                            }
                        }

                        if (retryOther != null) {
                            return retryOther.apply(requestMetaData, throwable);
                        }

                        return NO_RETRIES;
                    };
            return new RetryingHttpRequesterFilter(lbAvailableTimeout,
                    ignoreSdErrors, mayReplayRequestPayload,
                    returnOriginalResponses, maxTotalRetries, responseMapper, allPredicate, onRequestRetry);
        }
    }

    private static Completable drain(StreamingHttpResponse response) {
        return response.payloadBody().ignoreElements().onErrorComplete();
    }

    @Nullable
    private static StreamingHttpResponse extractStreamingResponse(Throwable t) {
        if (t instanceof HttpResponseException) {
            HttpResponseException responseException = (HttpResponseException) t;
            if (responseException.metaData() instanceof StreamingHttpResponse) {
                return (StreamingHttpResponse) responseException.metaData();
            } else {
                LOGGER.warn("Couldn't unpack response due to unexpected dynamic types. Required " +
                                "meta-data of type StreamingHttpResponse, found {}",
                        responseException.metaData().getClass());
            }
        }
        return null;
    }
}
