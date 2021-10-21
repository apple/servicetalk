/*
 * Copyright © 2018-2019, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.utils;

import io.servicetalk.client.api.AbstractRetryingFilterBuilder;
import io.servicetalk.client.api.AbstractRetryingFilterBuilder.ReadOnlyRetryableSettings;
import io.servicetalk.client.api.LoadBalancerReadySubscriber;
import io.servicetalk.client.api.NoAvailableHostException;
import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.BiIntFunction;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.RetryStrategies;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableReservedStreamingHttpConnection;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.InjectableStreamingClientFilterFactory;
import io.servicetalk.http.api.LoadBalancerReadinessAware;
import io.servicetalk.http.api.ServiceDiscoveryStatusAware;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.RetryableException;

import java.io.IOException;
import java.time.Duration;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncCloseables.emptyAsyncCloseable;
import static io.servicetalk.concurrent.api.AsyncCloseables.toAsyncCloseable;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.failed;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithConstantBackoffDeltaJitter;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithConstantBackoffFullJitter;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithExponentialBackoffDeltaJitter;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithExponentialBackoffFullJitter;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.http.api.HeaderUtils.DEFAULT_HEADER_FILTER;
import static java.time.Duration.ZERO;
import static java.time.Duration.ofDays;
import static java.util.Objects.requireNonNull;

/**
 * A filter to enable retries for HTTP requests.
 * <p>
 * Retries are supported for both the request flow and the response flow. Retries, in other words, can be triggered
 * as part of a service response if needed.
 * <p>
 * The two behaviors can have different criteria, as defined from the relevant Builder methods (i.e.
 * {@link RetryingHttpRequesterFilterBuilder#retryRequests(BiFunction)}
 * or {@link RetryingHttpRequesterFilterBuilder#retryResponses(Function)}).
 * Both return a {@link BackOffPolicy} when the request or the response is matching the conditions, which allows
 * control of the retry backoff period independently.
 * Similarly, max-retries for each flow can be set in the {@link BackOffPolicy}, as well
 * as a total max-retries to be respected by both flows, as set in
 * {@link RetryingHttpRequesterFilterBuilder#maxTotalRetries(int)}.
 * <p>
 * If the existing preset of {@link BackOffPolicy backoff policies} aren't enough to meet your expectations, the
 * class is extendable.
 * <p>
 * Note that applying this filter on a client it will automatically disable the use of
 * {@link SingleAddressHttpClientBuilder#appendClientFilter(StreamingHttpClientFilterFactory)}.
 * @see RetryStrategies
 */
public final class RetryingHttpRequesterFilter
        implements StreamingHttpClientFilterFactory, StreamingHttpConnectionFilterFactory,
                   InjectableStreamingClientFilterFactory,
                   LoadBalancerReadinessAware, ServiceDiscoveryStatusAware {

    private final boolean waitForLb;
    private final boolean ignoreSdErrors;
    private final int maxTotalRetries;
    @Nullable
    private final Function<HttpResponseMetaData, BackOffPolicy> retryForResponsesMapper;
    @Nullable
    private final BiFunction<HttpRequestMetaData, Throwable, BackOffPolicy> retryForRequestsMapper;
    @Nullable
    private Publisher<Object> lbEventStream;
    @Nullable
    private LoadBalancerReadySubscriber loadBalancerReadySubscriber;
    @Nullable
    private Completable sdStatus;

    @Nullable
    private final ReadOnlyRetryableSettings<HttpRequestMetaData> settings;

    RetryingHttpRequesterFilter(
            final boolean waitForLb, final boolean ignoreSdErrors, final int maxTotalRetries,
            @Nullable final Function<HttpResponseMetaData, BackOffPolicy> retryForResponsesMapper,
            @Nullable final BiFunction<HttpRequestMetaData, Throwable, BackOffPolicy> retryForRequestsMapper) {
        this.waitForLb = waitForLb;
        this.ignoreSdErrors = ignoreSdErrors;
        this.maxTotalRetries = maxTotalRetries;
        this.retryForResponsesMapper = retryForResponsesMapper;
        this.retryForRequestsMapper = retryForRequestsMapper;
        this.settings = null;
    }

    @Deprecated
    private RetryingHttpRequesterFilter(final ReadOnlyRetryableSettings<HttpRequestMetaData> settings) {
        this.settings = settings;
        this.waitForLb = false;
        this.ignoreSdErrors = false;
        this.maxTotalRetries = 0;
        this.retryForResponsesMapper = null;
        this.retryForRequestsMapper = null;
    }

    @Deprecated
    private Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                  final StreamingHttpRequest request,
                                                  final BiIntFunction<Throwable, Completable> retryStrategy,
                                                  final Executor executor) {
        assert settings != null;
        return delegate.request(request).retryWhen((count, t) -> {
            if (settings.isRetryable(request, t)) {
                if (settings.evaluateDelayedRetries() && t instanceof DelayedRetry) {
                    final Duration constant = ((DelayedRetry) t).delay();
                    return retryStrategy.apply(count, t).concat(executor.timer(constant));
                }

                return retryStrategy.apply(count, t);
            }
            return failed(t);
        });
    }

    private Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                  final StreamingHttpRequest request,
                                                  final Executor executor) {
        Single<StreamingHttpResponse> single = delegate.request(request);
        if (retryForResponsesMapper != null) {
            single = single.map(resp -> {
                final BackOffPolicy backOffPolicy = retryForResponsesMapper.apply(resp);
                if (backOffPolicy != null) {
                    throw new StacklessRetryResponseException(backOffPolicy, resp);
                }

                return resp;
            });
        }

        return single.retryWhen(retryStrategy(executor, request));
    }

    private BiIntFunction<Throwable, Completable> retryStrategy(final Executor executor,
                                                                final HttpRequestMetaData requestMetaData) {
        return (count, t) -> {
            if (count > maxTotalRetries) {
                return failed(t);
            }

            if (loadBalancerReadySubscriber != null && t instanceof NoAvailableHostException) {
                final Completable onHostsAvailable = loadBalancerReadySubscriber.onHostsAvailable();
                return sdStatus == null ? onHostsAvailable : onHostsAvailable.ambWith(sdStatus);
            }

            final boolean isResponseError = t.getClass() == StacklessRetryResponseException.class;
            if (isResponseError) {
                return ((StacklessRetryResponseException) t).backOffPolicy.newStrategy(executor).apply(count, t);
            }

            final BackOffPolicy requestRetryBackOff = retryForRequestsMapper != null ?
                    retryForRequestsMapper.apply(requestMetaData, t) : null;
            if (requestRetryBackOff != null) {
                if (t instanceof DelayedRetry) {
                    final Duration constant = ((DelayedRetry) t).delay();
                    return requestRetryBackOff.newStrategy(executor).apply(count, t).concat(executor.timer(constant));
                }

                return requestRetryBackOff.newStrategy(executor).apply(count, t);
            }

            return failed(t);
        };
    }

    @Override
    public void inject(final Publisher<Object> lbEventStream) {
        this.lbEventStream = lbEventStream;
    }

    @Override
    public void inject(final Completable sdStatus) {
        this.sdStatus = ignoreSdErrors ? null : sdStatus;
    }

    @Override
    public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
        return new ContextAwareClientFilter(client);
    }

    @Override
    public StreamingHttpConnectionFilter create(final FilterableStreamingHttpConnection connection) {
        //TODO
        return new StreamingHttpConnectionFilter(connection) {
            private final Executor executor = connection.executionContext().executor();
            private final BiIntFunction<Throwable, Completable> retryStrategy =
                    settings.newStrategy(executor);

            @Override
            public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
                if (settings != null) {
                    return RetryingHttpRequesterFilter.this.request(delegate(), request, retryStrategy, executor);
                }
                return RetryingHttpRequesterFilter.this.request(delegate(), request, executor);
            }
       };
    }

    @Override
    public HttpExecutionStrategy requiredOffloads() {
        // No influence since we do not block.
        return HttpExecutionStrategies.offloadNone();
    }

    private final class ContextAwareClientFilter extends StreamingHttpClientFilter {

        @Nullable
        private AsyncCloseable closeAsync;

        private final Executor executor;

        @Nullable
        private final BiIntFunction<Throwable, Completable> retryStrategy;

        /**
         * Create a new instance.
         *
         * @param delegate The {@link FilterableStreamingHttpClient} to delegate all calls to.
         */
        private ContextAwareClientFilter(final FilterableStreamingHttpClient delegate) {
            super(delegate);
            this.executor = delegate.executionContext().executor();
            this.retryStrategy = settings != null ? settings.newStrategy(executor) : null;
            init();
        }

        public void init() {
            if (waitForLb) {
                assert lbEventStream != null;
                loadBalancerReadySubscriber = new LoadBalancerReadySubscriber();
                closeAsync = toAsyncCloseable(__ -> {
                    loadBalancerReadySubscriber.cancel();
                    return completed();
                });
                toSource(lbEventStream).subscribe(loadBalancerReadySubscriber);
            } else {
                loadBalancerReadySubscriber = null;
                closeAsync = emptyAsyncCloseable();
            }
        }

        @Override
        public Single<? extends FilterableReservedStreamingHttpConnection> reserveConnection(
                final HttpRequestMetaData metaData) {
            return delegate().reserveConnection(metaData)
                    .retryWhen(retryStrategy(executor, metaData));
        }

        @Override
        protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                        final StreamingHttpRequest request) {
            if (settings != null) {
                assert retryStrategy != null;
                return RetryingHttpRequesterFilter.this.request(delegate(), request, retryStrategy, executor);
            }
            return RetryingHttpRequesterFilter.this.request(delegate(), request, executor);
        }

        @Override
        public Completable closeAsync() {
            if (closeAsync != null) {
                closeAsync.closeAsync();
            }
            return super.closeAsync();
        }

        @Override
        public Completable closeAsyncGracefully() {
            if (closeAsync != null) {
                closeAsync.closeAsyncGracefully();
            }
            return super.closeAsyncGracefully();
        }
    }

    private static final class StacklessRetryResponseException extends RuntimeException {

        private final BackOffPolicy backOffPolicy;
        private final HttpResponseMetaData metaData;

        StacklessRetryResponseException(final BackOffPolicy backOffPolicy, final HttpResponseMetaData metaData) {
            this.backOffPolicy = backOffPolicy;
            this.metaData = metaData;
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }

        @Override
        public String toString() {
            return "StacklessRetryResponseException{ metaData=" + metaData.toString(DEFAULT_HEADER_FILTER) + '}';
        }
    }

    /**
     * Definition and presets of retry backoff policies.
     */
    public static class BackOffPolicy {

        private static final Duration FULL_JITTER = ofDays(1024);

        @Nullable
        final Duration initialDelay;
        final Duration jitter;
        @Nullable
        final Duration maxDelay;
        @Nullable
        final Executor timerExecutor;
        final boolean exponential;
        final int maxRetries;

        BackOffPolicy(@Nullable final Duration initialDelay,
                      final Duration jitter,
                      @Nullable final Duration maxDelay,
                      @Nullable final Executor timerExecutor,
                      final boolean exponential,
                      final int maxRetries) {
            this.initialDelay = initialDelay;
            this.jitter = jitter;
            this.maxDelay = maxDelay;
            this.timerExecutor = timerExecutor;
            this.exponential = exponential;
            this.maxRetries = maxRetries > 0 ? maxRetries : (exponential ? 2 : 1);
        }

        /**
         * Creates a new {@link BackOffPolicy} that retries failures instantly up-to 3 max retries.
         * @return a new {@link BackOffPolicy} that retries failures instantly up-to 3 max retries.
         */
        public static BackOffPolicy ofInstant() {
            return new BackOffPolicy(null, ZERO, null, null, false, 3);
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
     * Default request retry policy builder.
     */
    public static final class DefaultRequestRetryPolicyBuilder {
        private boolean retryRetryableExceptions = true;
        private boolean retryIdempotentRequests;
        private boolean retryDelayedRetries;
        private BackOffPolicy backOffPolicy = BackOffPolicy.ofInstant();

        /**
         * The retrying-filter will evaluate for {@link RetryableException}s in the request flow.
         *
         * @param retry The flag indicating whether this check takes place or not.
         * @return {@code this}.
         */
        public DefaultRequestRetryPolicyBuilder retryRetryableExceptions(final boolean retry) {
            this.retryRetryableExceptions = retry;
            return this;
        }

        /**
         * The retrying-filter will evaluate the {@link DelayedRetry} marker interface
         * of an exception and use the provided {@link DelayedRetry#delay() constant-delay} in the retry period.
         * In case a max-delay was set in this builder, the {@link DelayedRetry#delay() constant-delay} overrides
         * it and takes precedence.
         *
         * @param retry Evaluate the {@link Throwable errors} for the {@link DelayedRetry} marker interface, and
         * if matched, then use the {@link DelayedRetry#delay() constant-delay} additionally to the backoff
         * strategy in use.
         * @return {@code this}.
         */
        public DefaultRequestRetryPolicyBuilder retryDelayedRetries(final boolean retry) {
            this.retryDelayedRetries = retry;
            return this;
        }

        /**
         * Retries <a href="https://tools.ietf.org/html/rfc7231#section-4.2.2">idempotent</a> requests when applicable.
         * <p>
         * <b>Note:</b> This predicate expects that the retried {@link StreamingHttpRequest requests} have a
         * {@link StreamingHttpRequest#payloadBody() payload body} that is
         * <a href="http://reactivex.io/documentation/operators/replay.html">replayable</a>, i.e. multiple subscribes to
         * the payload {@link Publisher} observe the same data. {@link Publisher}s that do not emit any data or which
         * are created from in-memory data are typically replayable.
         *
         * @param retry The flag indicating whether this check takes place or not.
         * @return {@code this}.
         */
        public DefaultRequestRetryPolicyBuilder retryIdempotentRequests(final boolean retry) {
            this.retryIdempotentRequests = retry;
            return this;
        }

        public DefaultRequestRetryPolicyBuilder backOffPolicy(final BackOffPolicy backOffPolicy) {
            this.backOffPolicy = backOffPolicy;
            return this;
        }

        public BiFunction<HttpRequestMetaData, Throwable, BackOffPolicy> build() {
            BiPredicate<HttpRequestMetaData, Throwable> predicate = (req, error) -> false;
            if (retryIdempotentRequests) {
                predicate = predicate.or((meta, t) ->
                        t instanceof IOException && meta.method().properties().isIdempotent());
            }

            if (retryDelayedRetries) {
                predicate = predicate.or((meta, t) -> t instanceof DelayedRetry);
            }

            if (retryRetryableExceptions) {
                predicate = predicate.or((meta, t) -> t instanceof RetryableException);
            }

            final BiPredicate<HttpRequestMetaData, Throwable> finalPredicate = predicate;
            return (meta, error) -> {
                if (finalPredicate.test(meta, error)) {
                    return backOffPolicy;
                }

                return null;
            };
        }
    }

    /**
     * A builder for {@link RetryingHttpRequesterFilter}, which puts an upper bound on retry attempts.
     * To configure the maximum number of retry attempts see {@link #maxRetries(int)}.
     * @deprecated This will be removed in future versions of ServiceTalk.
     * Alternatively use {@link RetryingHttpRequesterFilterBuilder}
     */
    @Deprecated
    public static final class Builder
            extends AbstractRetryingFilterBuilder<Builder, RetryingHttpRequesterFilter, HttpRequestMetaData> {

        /**
         * The retrying-filter will also evaluate the {@link DelayedRetry} marker interface
         * of an exception and use the provided {@link DelayedRetry#delay() constant-delay} in the retry period.
         * In case a max-delay was set in this builder, the {@link DelayedRetry#delay() constant-delay} overrides
         * it and takes precedence.
         *
         * @param evaluate Evaluate the {@link Throwable errors} for the {@link DelayedRetry} marker interface, and
         * if matched, then use the {@link DelayedRetry#delay() constant-delay} additionally to the backoff
         * strategy in use.
         * @return {@code this}.
         */
        public Builder evaluateDelayedRetries(final boolean evaluate) {
            this.evaluateDelayedRetries = evaluate;
            return this;
        }

        @Override
        protected RetryingHttpRequesterFilter build(
                final ReadOnlyRetryableSettings<HttpRequestMetaData> readOnlySettings) {
            return new RetryingHttpRequesterFilter(readOnlySettings);
        }

        /**
         * Behaves as {@link #defaultRetryForPredicate()}, but also retries
         * <a href="https://tools.ietf.org/html/rfc7231#section-4.2.2">idempotent</a> requests when applicable.
         * <p>
         * <b>Note:</b> This predicate expects that the retried {@link StreamingHttpRequest requests} have a
         * {@link StreamingHttpRequest#payloadBody() payload body} that is
         * <a href="http://reactivex.io/documentation/operators/replay.html">replayable</a>, i.e. multiple subscribes to
         * the payload {@link Publisher} observe the same data. {@link Publisher}s that do not emit any data or which
         * are created from in-memory data are typically replayable.
         *
         * @return a {@link BiPredicate} for {@link #retryFor(BiPredicate)} builder method
         */
        public BiPredicate<HttpRequestMetaData, Throwable> retryForIdempotentRequestsPredicate() {
            return defaultRetryForPredicate().or((meta, throwable) ->
                    throwable instanceof IOException && meta.method().properties().isIdempotent());
        }
    }

    /**
     * An interface that enhances any {@link Exception} to provide a constant {@link Duration delay} to be applied when
     * retrying through a {@link RetryingHttpRequesterFilter retrying-filter}.
     * <p>
     * Constant delay returned from {@link #delay()} will only be considered if the
     * {@link RetryingHttpRequesterFilterBuilder#retryRequests(BiFunction)} evaluates to {@code true} for a particular
     * request failure.
     */
    public interface DelayedRetry {

        /**
         * A constant delay to apply in milliseconds.
         * The total delay for the retry logic will be the sum of this value and the result of the
         * {@link BackOffPolicy back-off policy} in-use. Consider using 'full-jitter'
         * flavours from the {@link BackOffPolicy} to avoid having another constant delay applied per-retry.
         *
         * @return The {@link Duration} to apply as constant delay when retrying.
         */
        Duration delay();
    }
}
