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
package io.servicetalk.traffic.resilience.http;

import io.servicetalk.capacity.limiter.api.CapacityLimiter;
import io.servicetalk.capacity.limiter.api.CapacityLimiter.Ticket;
import io.servicetalk.capacity.limiter.api.Classification;
import io.servicetalk.capacity.limiter.api.RequestDroppedException;
import io.servicetalk.circuit.breaker.api.CircuitBreaker;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.utils.TimeoutHttpRequesterFilter;
import io.servicetalk.traffic.resilience.http.ClientPeerRejectionPolicy.PassthroughRequestDroppedException;
import io.servicetalk.transport.api.ServerListenContext;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.ThrowableUtils.unknownStackTrace;
import static io.servicetalk.http.api.HttpResponseStatus.SERVICE_UNAVAILABLE;
import static io.servicetalk.traffic.resilience.http.ClientPeerRejectionPolicy.DEFAULT_PEER_REJECTION_POLICY;
import static io.servicetalk.traffic.resilience.http.ClientPeerRejectionPolicy.Type.REJECT;
import static io.servicetalk.traffic.resilience.http.ClientPeerRejectionPolicy.Type.REJECT_PASSTHROUGH;
import static io.servicetalk.traffic.resilience.http.ClientPeerRejectionPolicy.Type.REJECT_RETRY;
import static io.servicetalk.utils.internal.DurationUtils.isPositive;
import static java.lang.Integer.MAX_VALUE;
import static java.util.Objects.requireNonNull;

/**
 * A {@link StreamingHttpClientFilterFactory} to enforce capacity and circuit-breaking control for a client.
 * Requests that are not able to acquire a capacity ticket or a circuit permit,
 * will fail with a {@link RequestDroppedException}.
 * <br><br>
 * <h2>Ordering of filters</h2>
 * Ordering of the {@link TrafficResilienceHttpClientFilter capacity-filter} is important for various reasons:
 * <ul>
 *     <li>The traffic control filter should be as early as possible in the execution chain to offer a fast-fail
 *     reaction and ideally trigger a natural back-pressure mechanism with the transport.</li>
 *     <li>The traffic control filter should not be offloaded <u>if possible</u> to avoid situations where
 *     continuous traffic overflows the offloading subsystem.</li>
 *     <li>The traffic control filter should be ordered after a
 *     {@code io.servicetalk.http.netty.RetryingHttpRequesterFilter} if one is used, to avail the
 *     benefit of retrying requests that failed due to (local or remote) capacity issues.
 *     {@link RetryableRequestDroppedException} are safely retry-able errors, since they occur on the outgoing
 *     side before they even touch the network. {@link DelayedRetryRequestDroppedException} errors on the other
 *     side, are remote rejections, and its up to the application logic to opt-in for them to be retryable, by
 *     configuring the relevant predicate of the {@code io.servicetalk.http.netty.RetryingHttpRequesterFilter}</li>
 *     <li>The traffic control filter should be ordered after a
 *     {@code io.servicetalk.http.netty.RetryingHttpRequesterFilter} to allow an already acquired
 *     {@link Ticket permit} to be released in case
 *     of other errors/timeouts of the operation, before retrying to re-acquire a
 *     {@link Ticket permit}. Otherwise, a
 *     {@link Ticket permit} may be held idle for as
 *     long as the operation is awaiting to be re-tried, thus, mis-utilising available resources for other requests
 *     through the same {@link HttpClient client}.
 *     </li>
 *     <li>If the traffic control filter is ordered after a
 *     {@link TimeoutHttpRequesterFilter timeout-filter} then a potential timeout will be
 *     delivered to it in the form of a cancellation. The default terminal callback for the ticket in that case, is
 *     set to {@link Ticket#dropped() dropped} to avail for local throttling, since a timeout is a good indicator
 *     that a sub-process in the pipeline is not completing fast enough.
 *     </li>
 *     <li>If the traffic control filter is ordered before a
 *     {@link TimeoutHttpRequesterFilter timeout-filter} then a potential timeout will be
 *     delivered to it in the form of a {@link TimeoutException}, which is in turn triggers the
 *     {@link Ticket#dropped() drop-event of the ticket} by default. Behavior can be overridden through this
 *     {@link Builder#onErrorTicketTerminal(BiConsumer)}.
 *     </li>
 * </ul>
 *
 */
public final class TrafficResilienceHttpClientFilter extends AbstractTrafficResilienceHttpFilter
        implements StreamingHttpClientFilterFactory {

    private static final RequestDroppedException LOCAL_REJECTION_RETRYABLE_EXCEPTION = unknownStackTrace(
            new RetryableRequestDroppedException("Local capacity rejection", null, false, true),
            TrafficResilienceHttpClientFilter.class, "localRejection");

    private static final Single<StreamingHttpResponse> RETRYABLE_LOCAL_CAPACITY_REJECTION =
            Single.failed(LOCAL_REJECTION_RETRYABLE_EXCEPTION);

    /**
     * Default rejection observer for dropped requests from an external sourced due to service unavailability.
     * see. {@link Builder#peerBreakerRejection(HttpResponseMetaData, CircuitBreaker, Function)}.
     * <p>
     * The default predicate matches the following HTTP response codes:
     * <ul>
     *     <li>{@link HttpResponseStatus#SERVICE_UNAVAILABLE}</li>
     * </ul>
     */
    public static final Predicate<HttpResponseMetaData> DEFAULT_BREAKER_REJECTION_PREDICATE = metaData ->
            metaData.status().code() == SERVICE_UNAVAILABLE.code();

    private final ClientPeerRejectionPolicy clientPeerRejectionPolicy;
    private final boolean forceOpenCircuitOnPeerCircuitRejections;
    @Nullable
    private final Supplier<Function<HttpResponseMetaData, Duration>>
            focreOpenCircuitOnPeerCircuitRejectionsDelayProvider;
    @Nullable
    private final Executor circuitBreakerResetExecutor;

    private TrafficResilienceHttpClientFilter(final Supplier<Function<HttpRequestMetaData, CapacityLimiter>>
                                                      capacityPartitionsSupplier,
                                              final boolean rejectWhenNotMatchedCapacityPartition,
                                              final Supplier<Function<HttpRequestMetaData, CircuitBreaker>>
                                                      circuitBreakerPartitionsSupplier,
                                              final Supplier<Function<HttpRequestMetaData, Classification>> classifier,
                                              final ClientPeerRejectionPolicy clientPeerRejectionPolicy,
                                              final Predicate<HttpResponseMetaData> breakerRejectionPredicate,
                                              final Consumer<Ticket> onCompletion,
                                              final Consumer<Ticket> onCancellation,
                                              final BiConsumer<Ticket, Throwable> onError,
                                              final boolean forceOpenCircuitOnPeerCircuitRejections,
                                              @Nullable final Supplier<Function<HttpResponseMetaData, Duration>>
                                                      focreOpenCircuitOnPeerCircuitRejectionsDelayProvider,
                                              @Nullable final Executor circuitBreakerResetExecutor,
                                              final TrafficResiliencyObserver observer,
                                              final boolean dryRunMode) {
        super(capacityPartitionsSupplier, rejectWhenNotMatchedCapacityPartition, classifier,
                clientPeerRejectionPolicy.predicate(), breakerRejectionPredicate, onCompletion, onCancellation,
                onError, circuitBreakerPartitionsSupplier, observer, dryRunMode);
        this.clientPeerRejectionPolicy = clientPeerRejectionPolicy;
        this.forceOpenCircuitOnPeerCircuitRejections = forceOpenCircuitOnPeerCircuitRejections;
        this.focreOpenCircuitOnPeerCircuitRejectionsDelayProvider =
                focreOpenCircuitOnPeerCircuitRejectionsDelayProvider;
        this.circuitBreakerResetExecutor = circuitBreakerResetExecutor;
    }

    @Override
    public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
        return TrackPendingRequestsHttpFilter.BEFORE.create(new StreamingHttpClientFilter(
                TrackPendingRequestsHttpFilter.AFTER.create(client)) {

            final Function<HttpRequestMetaData, CapacityLimiter> capacityPartitions = newCapacityPartitions();
            final Function<HttpRequestMetaData, CircuitBreaker> circuitBreakerPartitions =
                    newCircuitBreakerPartitions();
            final Function<HttpRequestMetaData, Classification> classifier = newClassifier();
            final Function<HttpResponseMetaData, Duration> delayProvider = newDelayProvider();

            @Override
            protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                            final StreamingHttpRequest request) {
                return applyCapacityControl(capacityPartitions, circuitBreakerPartitions, classifier, delayProvider,
                        null, request, null, delegate::request)
                        .onErrorResume(PassthroughRequestDroppedException.class, t -> Single.succeeded(t.response()));
            }
        });
    }

    @Override
    Single<StreamingHttpResponse> handleLocalBreakerRejection(
            StreamingHttpRequest request, @Nullable StreamingHttpResponseFactory responseFactory,
            CircuitBreaker breaker) {
        return DEFAULT_BREAKER_REJECTION;
    }

    @Override
    Single<StreamingHttpResponse> handleLocalCapacityRejection(
            @Nullable final ServerListenContext serverListenContext,
            StreamingHttpRequest request, @Nullable StreamingHttpResponseFactory responseFactory) {
        return RETRYABLE_LOCAL_CAPACITY_REJECTION;
    }

    @Override
    RuntimeException peerRejection(final StreamingHttpResponse resp) {
        final ClientPeerRejectionPolicy.Type type = clientPeerRejectionPolicy.type();
        if (type == REJECT_RETRY) {
            final Duration delay = clientPeerRejectionPolicy.delayProvider().apply(resp);
            return new DelayedRetryRequestDroppedException(delay);
        } else if (type == REJECT) {
            return super.peerRejection(resp);
        } else if (type == REJECT_PASSTHROUGH) {
            return new PassthroughRequestDroppedException("Service under heavy load", resp);
        } else {
            return new IllegalStateException("Unexpected ClientPeerRejectionPolicy.Type: " + type);
        }
    }

    @Override
    RuntimeException peerBreakerRejection(
            final HttpResponseMetaData resp, final CircuitBreaker breaker,
            final Function<HttpResponseMetaData, Duration> delayProvider) {
        if (forceOpenCircuitOnPeerCircuitRejections) {
            assert circuitBreakerResetExecutor != null;
            final Duration delay = delayProvider.apply(resp);
            if (isPositive(delay)) {
                breaker.forceOpenState();
                circuitBreakerResetExecutor.schedule(breaker::reset, delay);
            }
        }

        return super.peerBreakerRejection(resp, breaker, delayProvider);
    }

    @Override
    Function<HttpResponseMetaData, Duration> newDelayProvider() {
        if (focreOpenCircuitOnPeerCircuitRejectionsDelayProvider == null) {
            return __ -> Duration.ZERO;
        }

        return focreOpenCircuitOnPeerCircuitRejectionsDelayProvider.get();
    }

    /**
     * A {@link TrafficResilienceHttpServiceFilter} instance builder.
     */
    public static final class Builder {
        private Supplier<Function<HttpRequestMetaData, CapacityLimiter>> capacityPartitionsSupplier;
        private boolean rejectWhenNotMatchedCapacityPartition;
        private Supplier<Function<HttpRequestMetaData, CircuitBreaker>> circuitBreakerPartitionsSupplier =
                () -> __ -> null;
        private Supplier<Function<HttpRequestMetaData, Classification>> classifier = () -> __ -> () -> MAX_VALUE;
        private ClientPeerRejectionPolicy clientPeerRejectionPolicy = DEFAULT_PEER_REJECTION_POLICY;
        private Predicate<HttpResponseMetaData> peerUnavailableRejectionPredicate = DEFAULT_BREAKER_REJECTION_PREDICATE;
        private final Consumer<Ticket> onCompletionTicketTerminal = Ticket::completed;
        private Consumer<Ticket> onCancellationTicketTerminal = Ticket::dropped;
        private BiConsumer<Ticket, Throwable> onErrorTicketTerminal = (ticket, throwable) -> {
            if (throwable instanceof RequestDroppedException || throwable instanceof TimeoutException) {
                ticket.dropped();
            } else {
                ticket.failed(throwable);
            }
        };
        private boolean forceOpenCircuitOnPeerCircuitRejections;
        @Nullable
        private Supplier<Function<HttpResponseMetaData, Duration>> focreOpenCircuitOnPeerCircuitRejectionsDelayProvider;
        @Nullable
        private Executor circuitBreakerResetExecutor;
        private TrafficResiliencyObserver observer = NoOpTrafficResiliencyObserver.INSTANCE;
        private boolean dryRunMode;

        /**
         * A {@link TrafficResilienceHttpClientFilter} with no partitioning schemes.
         * <p>
         * All requests will go through the {@link CapacityLimiter}.
         *
         * @param capacityLimiterSupplier The {@link Supplier} to create a new {@link CapacityLimiter} for each new
         * filter created by this {@link StreamingHttpClientFilterFactory factory}.
         */
        public Builder(final Supplier<CapacityLimiter> capacityLimiterSupplier) {
            requireNonNull(capacityLimiterSupplier);
            this.capacityPartitionsSupplier = () -> {
                CapacityLimiter capacityLimiter = capacityLimiterSupplier.get();
                return __ -> capacityLimiter;
            };
            this.rejectWhenNotMatchedCapacityPartition = true;
        }

        /**
         * A {@link TrafficResilienceHttpClientFilter} can support request partitioning schemes.
         * <p>
         * A partition in the context of capacity management, is a set of requests that represent an application
         * characteristic relevant to capacity, which can be isolated and have their own set of rules
         * (ie. {@link CapacityLimiter}).
         * <p>
         * An example of a partition can be to represent each customer in a multi-tenant service.
         * If an application wants to introduce customer API quotas, they can do so by identifying that customer
         * through the {@link HttpRequestMetaData} and providing a different {@link CapacityLimiter} for that customer.
         * <p>
         * If a {@code partitions} doesn't return a {@link CapacityLimiter} for the given {@link HttpRequestMetaData}
         * then the {@code rejectNotMatched} is evaluated to decide what the filter should do with this request.
         * If {@code true} then the request will be {@link RequestDroppedException rejected}.
         * <p>
         * <b>It's important that instances returned from this {@link Function mapper} are singletons and shared
         * across the same matched partitions. Otherwise, capacity will not be controlled as expected, and there
         * is also the risk for {@link OutOfMemoryError}.</b>
         *
         * @param capacityPartitionsSupplier A {@link Supplier} to create a new {@link Function} for each new filter
         * created by this {@link StreamingHttpClientFilterFactory factory}.
         * Function provides a {@link CapacityLimiter} instance for the given {@link HttpRequestMetaData}.
         * @param rejectNotMatched Flag that decides what the filter should do when {@code partitions} doesn't return
         * a {@link CapacityLimiter}.
         */
        public Builder(final Supplier<Function<HttpRequestMetaData, CapacityLimiter>> capacityPartitionsSupplier,
                       final boolean rejectNotMatched) {
            this.capacityPartitionsSupplier = requireNonNull(capacityPartitionsSupplier);
            this.rejectWhenNotMatchedCapacityPartition = rejectNotMatched;
        }

        /**
         * Define {@link CapacityLimiter} partitions.
         * <p>
         * A partition in the context of capacity management, is a set of requests that represent an application
         * characteristic relevant to capacity, which can be isolated and have their own set of rules
         * (ie. {@link CapacityLimiter}).
         * <p>
         * An example of a partition can be to represent each customer in a multi-tenant service.
         * If an application wants to introduce customer API quotas, they can do so by identifying that customer
         * through the {@link HttpRequestMetaData} and providing a different {@link CapacityLimiter} for that customer.
         * <p>
         * If a {@code partitions} doesn't return a {@link CapacityLimiter} for the given {@link HttpRequestMetaData}
         * then the {@code rejectNotMatched} is evaluated to decide what the filter should do with this request.
         * If {@code true} then the request will be {@link RequestDroppedException rejected}.
         * <p>
         * <b>It's important that instances returned from this {@link Function mapper} are singletons and shared
         * across the same matched partitions. Otherwise, capacity will not be controlled as expected, and there
         * is also the risk for {@link OutOfMemoryError}.</b>
         *
         * @param capacityPartitionsSupplier A {@link Supplier} to create a new {@link Function} for each new filter
         * created by this {@link StreamingHttpClientFilterFactory factory}.
         * Function provides a {@link CapacityLimiter} instance for the given {@link HttpRequestMetaData}.
         * @param rejectNotMatched Flag that decides what the filter should do when {@code partitions} doesn't return
         * a {@link CapacityLimiter}.
         * @return {@code this}
         */
        public Builder capacityPartitions(
                final Supplier<Function<HttpRequestMetaData, CapacityLimiter>> capacityPartitionsSupplier,
                final boolean rejectNotMatched) {
            this.capacityPartitionsSupplier = requireNonNull(capacityPartitionsSupplier);
            this.rejectWhenNotMatchedCapacityPartition = rejectNotMatched;
            return this;
        }

        /**
         * Classification in the context of capacity management allows for hints to the relevant
         * {@link CapacityLimiter} to be influenced on the decision-making process by the class of the
         * {@link HttpRequestMetaData request}.
         * <p>
         * An example of classification, could be health checks that need to be given preference and still allowed
         * a permit even under stress conditions. Another case, could be a separation of reads and writes, giving
         * preference to the reads will result in a more available system under stress, by rejecting earlier writes.
         * <p>
         * The behavior of the classification and their thresholds could be different among different
         * {@link CapacityLimiter} implementations, therefore the use of this API requires good understanding of how
         * the algorithm in use will react for the different classifications.
         * <p>
         * Classification works within the context of a single
         * {@link #Builder(Supplier, boolean)} partition} and not universally in the filter.
         * <p>
         * It's worth noting that classification is strictly a hint and could be ignored by the
         * {@link CapacityLimiter}.
         * @param classifier A {@link Supplier} of a {@link Function} that maps an incoming {@link HttpRequestMetaData}
         * to a {@link Classification}.
         * @return {@code this}.
         */
        public Builder classifier(final Supplier<Function<HttpRequestMetaData, Classification>> classifier) {
            this.classifier = requireNonNull(classifier);
            return this;
        }

        /**
         * Define {@link CircuitBreaker} to manage local or remote errors.
         * <p>
         * The breakers can either be universal or follow any partitioning scheme (i.e., API / service-path, customer
         * etc) but is recommended to follow similar schematics between service and client if possible for best
         * experience.
         * <p>
         * The matching {@link CircuitBreaker} for a {@link HttpRequestMetaData request} can be forced opened due to
         * a remote open circuit-breaker (i.e., {@link HttpResponseStatus#SERVICE_UNAVAILABLE})
         * dissallowing further outgoing requests for a fixed periods;
         * {@link #forceOpenCircuitOnPeerCircuitRejections(Supplier, Executor)}.
         *
         * @param circuitBreakerPartitionsSupplier A {@link Supplier} to create a new {@link Function} for each new
         * filter created by this {@link StreamingHttpClientFilterFactory factory}.
         * Function provides a {@link CircuitBreaker} instance for the given {@link HttpRequestMetaData}.
         * @return {@code this}.
         */
        public Builder circuitBreakerPartitions(
                final Supplier<Function<HttpRequestMetaData, CircuitBreaker>> circuitBreakerPartitionsSupplier) {
            this.circuitBreakerPartitionsSupplier = requireNonNull(circuitBreakerPartitionsSupplier);
            return this;
        }

        /**
         * Peers can reject and exception due to capacity reasons based on their own principals and implementation
         * details. A {@link TrafficResilienceHttpClientFilter} can benefit from this input as feedback for the
         * {@link CapacityLimiter} in use, that the request was dropped (i.e., rejected), thus it can also bring its
         * local limit down to help with the overloaded peer. Since what defines a rejection/drop or request for
         * backpressure is not universally common, one can define what response characteristics define that state.
         * <p>
         * It's important to know that if the passed {@code rejectionPredicate} tests {@code true} for a given
         * {@link HttpResponseMetaData} then the operation is {@link Single#failed(Throwable)}.
         * <p>
         * Out of the box if nothing custom is defined, the filter recognises as rejections requests with the following
         * status codes:
         * <ul>
         *     <li>{@link HttpResponseStatus#TOO_MANY_REQUESTS}</li>
         *     <li>{@link HttpResponseStatus#BAD_GATEWAY}</li>
         * </ul>
         *
         * <p>
         * Allowing retry, requests will fail with a {@link DelayedRetryRequestDroppedException} to support
         * retrying mechanisms (like retry-filters or retry operators) to re-attempt the same request.
         * Requests that fail due to capacity limitation, are good candidates for a retry, since we anticipate they are
         * safe to be executed again (no previous invocation actually started) and because this maximizes the success
         * chances.
         * @param policy The {@link ClientPeerRejectionPolicy} that represents the peer capacity rejection behavior.
         * @return {@code this}.
         * @see ClientPeerRejectionPolicy#DEFAULT_PEER_REJECTION_POLICY
         */
        public Builder rejectionPolicy(final ClientPeerRejectionPolicy policy) {
            this.clientPeerRejectionPolicy = requireNonNull(policy);
            return this;
        }

        /**
         * Peers can reject requests due to service unavailability.
         * A {@link TrafficResilienceHttpClientFilter} can benefit from this input as feedback for the
         * {@link CircuitBreaker} in use. A similar exception can be generated locally as a result of that feedback,
         * to help the active local {@link CircuitBreaker} to also adapt.
         * <p>
         * It's important to know that if the passed {@code rejectionPredicate} tests {@code true} for a given
         * {@link HttpResponseMetaData} then the operation will be {@link Single#failed(Throwable)}.
         * <p>
         * Out of the box if nothing custom is defined, the filter recognises as rejections requests with the following
         * status codes:
         * <ul>
         *     <li>{@link HttpResponseStatus#SERVICE_UNAVAILABLE}</li>
         * </ul>
         *
         * @param rejectionPredicate The {@link Function} that resolves a {@link HttpResponseMetaData response} to a
         * peer-rejection or not.
         * @return {@code this}.
         */
        public Builder peerUnavailableRejectionPredicate(final Predicate<HttpResponseMetaData> rejectionPredicate) {
            this.peerUnavailableRejectionPredicate = requireNonNull(rejectionPredicate);
            return this;
        }

        /**
         * When a peer rejects a {@link HttpRequestMetaData request} due to an open-circuit (see.
         * {@link #peerUnavailableRejectionPredicate(Predicate)}), the feedback can be used
         * to also forcefully open the local {@link HttpRequestMetaData request's} {@link CircuitBreaker}.
         * The local {@link CircuitBreaker} will close again once a delay period passes as defined/extracted through
         * the {@code delayProvider}.
         * <p>
         * If the delay provided is not a positive value, then the {@link CircuitBreaker} will not be modified.
         * <p>
         * To disable this behaviour see {@link #dontForceOpenCircuitOnPeerCircuitRejections()}.
         * @param delayProvider A function to provide / extract a delay in milliseconds for the
         * {@link CircuitBreaker} to remain open.
         * @param executor A {@link Executor} used to re-close the {@link CircuitBreaker} once the delay expires.
         * @return {@code this}.
         */
        public Builder forceOpenCircuitOnPeerCircuitRejections(
                final Supplier<Function<HttpResponseMetaData, Duration>> delayProvider,
                final Executor executor) {
            this.forceOpenCircuitOnPeerCircuitRejections = true;
            this.focreOpenCircuitOnPeerCircuitRejectionsDelayProvider = requireNonNull(delayProvider);
            this.circuitBreakerResetExecutor = requireNonNull(executor);
            return this;
        }

        /**
         * When a peer rejects a {@link HttpRequestMetaData request} due to an open-circuit (see.
         * {@link #peerUnavailableRejectionPredicate(Predicate)}), ignore feedback and leave local matching
         * {@link CircuitBreaker circuit-breake partition} closed.
         * <p>
         * To opt-in for this behaviour see {@link #forceOpenCircuitOnPeerCircuitRejections(Supplier, Executor)}.
         * @return {@code this}.
         */
        public Builder dontForceOpenCircuitOnPeerCircuitRejections() {
            this.forceOpenCircuitOnPeerCircuitRejections = false;
            this.focreOpenCircuitOnPeerCircuitRejectionsDelayProvider = null;
            this.circuitBreakerResetExecutor = null;
            return this;
        }

        /**
         * {@link Ticket Ticket} terminal callback override upon erroneous completion of the request operation.
         * Erroneous completion in this context means, that an error occurred as part of the operation or the
         * {@link #rejectionPolicy(ClientPeerRejectionPolicy)} triggered an exception.
         * By default the terminal callback is {@link Ticket#failed(Throwable)}.
         *
         * @param onError Callback to override default {@link Ticket ticket} terminal event for an erroneous
         * operation.
         * @return {@code this}.
         */
        public Builder onErrorTicketTerminal(final BiConsumer<Ticket, Throwable> onError) {
            this.onErrorTicketTerminal = requireNonNull(onError);
            return this;
        }

        /**
         * {@link Ticket Ticket} terminal callback override upon cancellation of the request operation.
         * By default the terminal callback is {@link Ticket#dropped()}.
         * <p>
         * You may need to adjust this callback depending on the ordering this filter was applied.
         * For example if the filter is applied after the
         * {@link TimeoutHttpRequesterFilter timeout-filter} then you may want to also
         * {@link Ticket#dropped() drop the ticket} to let the algorithm apply throttling accounting for this timeout.
         * @param onCancellation Callback to override default {@link Ticket ticket} terminal event when an operation
         * is cancelled.
         * @return {@code this}.
         */
        public Builder onCancelTicketTerminal(final Consumer<Ticket> onCancellation) {
            this.onCancellationTicketTerminal = requireNonNull(onCancellation);
            return this;
        }

        /**
         * Provide an observer to track interactions of the filter and requests.
         * @param observer an observer to track interactions of the filter and requests.
         * @return {@code this}.
         */
        public Builder observer(final TrafficResiliencyObserver observer) {
            this.observer = new SafeTrafficResiliencyObserver(observer);
            return this;
        }

        /**
         * Use the resilience filter in dry-run mode.
         * In dry-run mode the capacity limiter will track requests and log their results but request which would
         * have been rejected will instead pass through to the underlying client.
         * @param dryRunMode whether to use the resilience filter in dry-run mode.
         * @return {@code this}
         */
        public Builder dryRunMode(final boolean dryRunMode) {
            this.dryRunMode = dryRunMode;
            return this;
        }

        /**
         * Invoke to build an instance of {@link TrafficResilienceHttpClientFilter} filter to be used inside the
         * HttpClientBuilder.
         *
         * @return An instance of {@link TrafficResilienceHttpClientFilter} with the characteristics
         * of this builder input.
         */
        public TrafficResilienceHttpClientFilter build() {
            return new TrafficResilienceHttpClientFilter(capacityPartitionsSupplier,
                    rejectWhenNotMatchedCapacityPartition,
                    circuitBreakerPartitionsSupplier, classifier, clientPeerRejectionPolicy,
                    peerUnavailableRejectionPredicate, onCompletionTicketTerminal, onCancellationTicketTerminal,
                    onErrorTicketTerminal, forceOpenCircuitOnPeerCircuitRejections,
                    focreOpenCircuitOnPeerCircuitRejectionsDelayProvider, circuitBreakerResetExecutor, observer,
                    dryRunMode);
        }
    }
}
