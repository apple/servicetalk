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
import io.servicetalk.capacity.limiter.api.RequestRejectedException;
import io.servicetalk.circuit.breaker.api.CircuitBreaker;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpHeaderNames;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.http.utils.TimeoutHttpServiceFilter;
import io.servicetalk.transport.api.ServerListenContext;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpHeaderNames.RETRY_AFTER;
import static java.lang.Integer.MAX_VALUE;
import static java.lang.String.valueOf;
import static java.util.Objects.requireNonNull;

/**
 * A {@link StreamingHttpServiceFilterFactory} to enforce capacity control for a server.
 * Requests that are not able to acquire a {@link Ticket permit}, will fail with a {@link RequestRejectedException}.
 * <br><br>
 * <h2>Ordering of filters</h2>
 * Ordering of the {@link TrafficResilienceHttpClientFilter capacity-filter} is important for various reasons:
 * <ul>
 *     <li>The traffic control filter should be as early as possible in the execution chain to offer a fast-fail
 *     reaction and ideally trigger a natural back-pressure mechanism with the transport. It's recommended to
 *     not offload this filter by using
 *     the {@link HttpServerBuilder#appendNonOffloadingServiceFilter(StreamingHttpServiceFilterFactory)} variant
 *     when appending to the service builder. Therefore, it's expected that any function provided through
 *     the {@link Builder}<strong>, should not block, to avoid any impact on the I/O threads.</strong> since this
 *     filter will not be offloaded.</li>
 *     <li>The traffic control filter should not be offloaded <u>if possible</u> to avoid situations where
 *     continuous traffic overflows the offloading subsystem.</li>
 *     <li>If the traffic control filter is ordered after a
 *     {@link TimeoutHttpServiceFilter timeout-filter} then a potential timeout will be
 *     delivered to it in the form of a cancellation, in which case you may want to override the default
 *     {@link Builder#onCancellationTicketTerminal terminal event} of the ticket, to {@link Ticket#dropped() drop it}
 *     to avail for local throttling, since a timeout is a good indicator that a sub-process in the pipeline is not
 *     completing fast enough.
 *     </li>
 *     <li>If the traffic control filter is ordered before a
 *     {@link TimeoutHttpServiceFilter timeout-filter} then a potential timeout will be
 *     delivered to it in the form of a {@link TimeoutException}, which is in turn triggers the
 *     {@link Ticket#dropped() drop-event of the ticket} by default. Behavior can be overridden through this
 *     {@link Builder#onErrorTicketTerminal callback}.
 *     </li>
 * </ul>
 *
 */
public final class TrafficResilienceHttpServiceFilter extends AbstractTrafficManagementHttpFilter
        implements StreamingHttpServiceFilterFactory {

    private final RejectionPolicy rejectionPolicy;

    private TrafficResilienceHttpServiceFilter(final Supplier<Function<HttpRequestMetaData, CapacityLimiter>>
                                                       capacityPartitionsSupplier,
                                               final boolean rejectNotMatched,
                                               final Function<HttpRequestMetaData, Classification> classifier,
                                               final Consumer<Ticket> onCompletion,
                                               final Consumer<Ticket> onCancellation,
                                               final BiConsumer<Ticket, Throwable> onError,
                                               final Supplier<Function<HttpRequestMetaData, CircuitBreaker>>
                                                       circuitBreakerPartitionsSupplier,
                                               final RejectionPolicy onRejectionPolicy,
                                               final TrafficResiliencyObserver observer) {
        super(capacityPartitionsSupplier, rejectNotMatched, classifier, __ -> false, __ -> false,
                onCompletion, onCancellation, onError, circuitBreakerPartitionsSupplier, observer);
        this.rejectionPolicy = onRejectionPolicy;
    }

    @Override
    public StreamingHttpServiceFilter create(final StreamingHttpService service) {
        return TrackPendingRequestsHttpFilter.BEFORE.create(new StreamingHttpServiceFilter(
                TrackPendingRequestsHttpFilter.AFTER.create(service)) {

            final Function<HttpRequestMetaData, CapacityLimiter> capacityPartitions = newCapacityPartitions();
            final Function<HttpRequestMetaData, CircuitBreaker> circuitBreakerPartitions =
                    newCircuitBreakerPartitions();

            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        final StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {
                final ServerListenContext actualContext = ctx.parent() instanceof ServerListenContext ?
                        (ServerListenContext) ctx.parent() :
                        ctx;
                return applyCapacityControl(capacityPartitions, circuitBreakerPartitions, actualContext, request,
                        responseFactory, request1 -> delegate().handle(ctx, request1, responseFactory));
            }
        });
    }

    @Override
    Ticket wrapTicket(@Nullable final ServerListenContext serverListenContext, final Ticket ticket) {
        return serverListenContext == null ? ticket : new ServerResumptionTicketWrapper(serverListenContext, ticket);
    }

    @Override
    Single<StreamingHttpResponse> handleLocalCapacityRejection(
            @Nullable final ServerListenContext serverListenContext,
            final StreamingHttpRequest request,
            @Nullable final StreamingHttpResponseFactory responseFactory) {
        assert serverListenContext != null;
        if (rejectionPolicy.onLimitStopAcceptingConnections) {
            serverListenContext.acceptConnections(false);
        }

        if (responseFactory != null) {
            return rejectionPolicy.onLimitResponseBuilder
                    .apply(request, responseFactory)
                    .map(resp -> {
                        rejectionPolicy.onLimitRetryAfter.accept(resp);
                        return resp;
                    });
        }

        return DEFAULT_CAPACITY_REJECTION;
    }

    @Override
    Single<StreamingHttpResponse> handleLocalBreakerRejection(
            final StreamingHttpRequest request,
            @Nullable final StreamingHttpResponseFactory responseFactory,
            @Nullable final CircuitBreaker breaker) {
        if (responseFactory != null) {
            return rejectionPolicy.onOpenCircuitResponseBuilder
                    .apply(request, responseFactory)
                    .map(resp -> {
                        rejectionPolicy.onOpenCircuitRetryAfter
                                .accept(resp, new StateContext(breaker));
                        return resp;
                    })
                    .shareContextOnSubscribe();
        }

        return DEFAULT_BREAKER_REJECTION;
    }

    /**
     * Default response rejection policy.
     * <ul>
     *     <li>When a request is rejected due to capacity, the service will respond
     *     {@link RejectionPolicy#tooManyRequests()}.</li>
     *     <li>When a request is rejected due to capacity, the service will NOT include a retry-after header.</li>
     *     <li>When a request is rejected due to breaker, the service will respond
     *     {@link RejectionPolicy#serviceUnavailable()}.</li>
     *     <li>When a request is rejected due to breaker, the service will respond with Retry-After header hinting
     *     the duration the breaker will remain open.</li>
     * </ul>
     *
     * @return The default {@link RejectionPolicy}.
     */
    public static RejectionPolicy defaultRejectionResponsePolicy() {
        return new RejectionPolicy.Builder().build();
    }

    /**
     * A {@link TrafficResilienceHttpServiceFilter} instance builder.
     *
     */
    public static final class Builder {
        private boolean rejectNotMatched;
        private Supplier<Function<HttpRequestMetaData, CapacityLimiter>> capacityPartitionsSupplier;
        private Function<HttpRequestMetaData, Classification> classifier = __ -> () -> MAX_VALUE;
        private Supplier<Function<HttpRequestMetaData, CircuitBreaker>> circuitBreakerPartitionsSupplier =
                () -> __ -> null;
        private RejectionPolicy onRejectionPolicy = defaultRejectionResponsePolicy();
        private final Consumer<Ticket> onCompletionTicketTerminal = Ticket::completed;
        private Consumer<Ticket> onCancellationTicketTerminal = Ticket::ignored;
        private BiConsumer<Ticket, Throwable> onErrorTicketTerminal = (ticket, throwable) -> {
            if (throwable instanceof RequestRejectedException || throwable instanceof TimeoutException) {
                ticket.dropped();
            } else {
                ticket.failed(throwable);
            }
        };
        private TrafficResiliencyObserver observer = NoOpTrafficResiliencyObserver.INSTANCE;

        /**
         * A {@link TrafficResilienceHttpServiceFilter} with no partitioning schemes.
         * <p>
         * All requests will go through the provided {@link CapacityLimiter}.
         *
         * @param capacityLimiterSupplier The {@link Supplier} to create a new {@link CapacityLimiter} for each new
         * filter created by this {@link StreamingHttpServiceFilterFactory factory}.
         */
        public Builder(Supplier<CapacityLimiter> capacityLimiterSupplier) {
            requireNonNull(capacityLimiterSupplier, "capacityLimiterSupplier");
            this.capacityPartitionsSupplier = () -> {
                CapacityLimiter capacityLimiter = capacityLimiterSupplier.get();
                return __ -> capacityLimiter;
            };
            this.rejectNotMatched = true;
        }

        /**
         * A {@link TrafficResilienceHttpServiceFilter} can support request partitioning schemes.
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
         * If {@code true} then the request will be {@link RequestRejectedException rejected}.
         * <p>
         * <b>It's important that instances returned from this {@link Function mapper} are singletons and shared
         * across the same matched partitions. Otherwise, capacity will not be controlled as expected, and there
         * is also the risk for {@link OutOfMemoryError}.</b>
         *
         * @param capacityPartitionsSupplier A {@link Supplier} to create a new {@link Function} for each new filter
         * created by this {@link StreamingHttpServiceFilterFactory factory}.
         * Function provides a {@link CapacityLimiter} instance for the given {@link HttpRequestMetaData}.
         * @param rejectNotMatched Flag that decides what the filter should do when {@code partitions} doesn't return
         * a {@link CapacityLimiter}.
         */
        public Builder(final Supplier<Function<HttpRequestMetaData, CapacityLimiter>> capacityPartitionsSupplier,
                       final boolean rejectNotMatched) {
            this.capacityPartitionsSupplier = requireNonNull(capacityPartitionsSupplier, "capacityPartitionsSupplier");
            this.rejectNotMatched = rejectNotMatched;
        }

        /**
         * Define {@link CapacityLimiter} partitions.
         * <p>
         * A partition in the context of capacity management, is a set of requests that represent an application
         * characteristic relevant to capacity, which can be isolated and have their own set of rules
         * (i.e. {@link CapacityLimiter}).
         * <p>
         * An example of a partition can be to represent each customer in a multi-tenant service.
         * If an application wants to introduce customer API quotas, they can do so by identifying that customer
         * through the {@link HttpRequestMetaData} and providing a different {@link CapacityLimiter} for that customer.
         * <p>
         * If a {@code partitions} doesn't return a {@link CapacityLimiter} for the given {@link HttpRequestMetaData}
         * then the {@code rejectNotMatched} is evaluated to decide what the filter should do with this request.
         * If {@code true} then the request will be {@link RequestRejectedException rejected}.
         * <p>
         * <b>It's important that instances returned from this {@link Function mapper} are singletons and shared
         * across the same matched partitions. Otherwise, capacity will not be controlled as expected, and there
         * is also the risk for {@link OutOfMemoryError}.</b>
         *
         * @param capacityPartitionsSupplier A {@link Supplier} to create a new {@link Function} for each new filter
         * created by this {@link StreamingHttpServiceFilterFactory factory}.
         * Function provides a {@link CapacityLimiter} instance for the given {@link HttpRequestMetaData}.
         * @param rejectNotMatched Flag that decides what the filter should do when {@code partitions} doesn't return
         * a {@link CapacityLimiter}.
         * @return {@code this}.
         */
        public Builder capacityPartitions(
                final Supplier<Function<HttpRequestMetaData, CapacityLimiter>> capacityPartitionsSupplier,
                final boolean rejectNotMatched) {
            this.capacityPartitionsSupplier = requireNonNull(capacityPartitionsSupplier, "capacityPartitionsSupplier");
            this.rejectNotMatched = rejectNotMatched;
            return this;
        }

        /**
         * Classification in the context of capacity management allows for hints to the relevant
         * {@link CapacityLimiter} to be influenced on the decision making process by the class of the
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
         * Classification work within the context of a single {@link #Builder(Supplier, boolean)}  partition}
         * and not universally in the filter.
         * @param classifier A {@link Function} that maps an incoming {@link HttpRequestMetaData} to a
         * {@link Classification}.
         * @return {@code this}.
         */
        public Builder classifier(final Function<HttpRequestMetaData, Classification> classifier) {
            this.classifier = requireNonNull(classifier, "classifier");
            return this;
        }

        /**
         * Define {@link CircuitBreaker} partitions to manage local errors.
         * <p>
         * The breakers can either be universal or follow any partitioning scheme (i.e., API / service-path, customer
         * e.t.c) but is recommended to follow similar schematics between service and client if possible for best
         * experience.
         * <p>
         * Once a matching {@link CircuitBreaker} transitions to open state, requests that match the same breaker
         * will fail (e.g., {@link io.servicetalk.http.api.HttpResponseStatus#SERVICE_UNAVAILABLE}) and
         * {@link RejectionPolicy#onOpenCircuitRetryAfter} can be used to hint peers about the fact that
         * the circuit will remain open for a certain amount of time.
         *
         * @param circuitBreakerPartitionsSupplier A {@link Supplier} to create a new {@link Function} for each new
         * filter created by this {@link StreamingHttpServiceFilterFactory factory}.
         * Function provides a {@link CircuitBreaker} instance for the given {@link HttpRequestMetaData}.
         * @return {@code this}.
         */
        public Builder circuitBreakerPartitions(
                final Supplier<Function<HttpRequestMetaData, CircuitBreaker>> circuitBreakerPartitionsSupplier) {
            this.circuitBreakerPartitionsSupplier = requireNonNull(circuitBreakerPartitionsSupplier,
                    "circuitBreakerPartitionsSupplier");
            return this;
        }

        /**
         * {@link Ticket Ticket} terminal callback override upon erroneous completion of the operation.
         * Erroneous completion in this context means, that an error occurred for either the {@link Single} or the
         * {@link io.servicetalk.concurrent.api.Publisher} of the operation.
         * By default, the terminal callback is {@link Ticket#failed(Throwable)}.
         *
         * @param onError Callback to override default {@link Ticket ticket} terminal event for an erroneous
         * operation.
         * @return {@code this}.
         */
        public Builder onErrorTicketTerminal(final BiConsumer<Ticket, Throwable> onError) {
            this.onErrorTicketTerminal = requireNonNull(onError, "onError");
            return this;
        }

        /**
         * {@link Ticket Ticket} terminal callback override upon cancellation of the operation.
         * By default, the terminal callback is {@link Ticket#ignored()}.
         * <p>
         * You may need to adjust this callback depending on the ordering this filter was applied.
         * For example if the filter is applied after the
         * {@link io.servicetalk.http.utils.TimeoutHttpRequesterFilter timeout-filter} then you may want to also
         * {@link Ticket#dropped() drop the ticket} to let the algorithm apply throttling accounting for this timeout.
         * @param onCancellation Callback to override default {@link Ticket ticket} terminal event when an operation
         * is cancelled.
         * @return {@code this}.
         */
        public Builder onCancelTicketTerminal(final Consumer<Ticket> onCancellation) {
            this.onCancellationTicketTerminal = requireNonNull(onCancellation, "onCancellation");
            return this;
        }

        /**
         * Defines the {@link RejectionPolicy} which in turn defines the behavior of the service when a
         * rejection occurs due to {@link CapacityLimiter capacity} or {@link CircuitBreaker breaker}.
         *
         * @param policy The policy to put into effect when a rejection occurs.
         * @return {@code this}.
         */
        public Builder onRejectionPolicy(final RejectionPolicy policy) {
            this.onRejectionPolicy = requireNonNull(policy, "policy");
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
         * Invoke to build an instance of {@link TrafficResilienceHttpServiceFilter} filter to be used inside the
         * {@link HttpServerBuilder}.
         * @return An instance of {@link TrafficResilienceHttpServiceFilter} with the characteristics
         * of this builder input.
         */
        public TrafficResilienceHttpServiceFilter build() {
            return new TrafficResilienceHttpServiceFilter(capacityPartitionsSupplier, rejectNotMatched,
                    classifier, onCompletionTicketTerminal, onCancellationTicketTerminal,
                    onErrorTicketTerminal, circuitBreakerPartitionsSupplier, onRejectionPolicy, observer);
        }
    }

    /**
     * Policy to rule the behavior of service rejections due to capacity or open circuit.
     */
    public static final class RejectionPolicy {

        /**
         * Custom retry-after header that supports milliseconds resolution, rather than seconds.
         */
        public static final CharSequence RETRY_AFTER_MILLIS = newAsciiString("retry-after-millis");

        private final BiFunction<HttpRequestMetaData, StreamingHttpResponseFactory, Single<StreamingHttpResponse>>
                onLimitResponseBuilder;

        private final Consumer<HttpResponseMetaData> onLimitRetryAfter;

        private final boolean onLimitStopAcceptingConnections;

        private final BiFunction<HttpRequestMetaData, StreamingHttpResponseFactory, Single<StreamingHttpResponse>>
                onOpenCircuitResponseBuilder;

        private final BiConsumer<HttpResponseMetaData, StateContext> onOpenCircuitRetryAfter;

        private RejectionPolicy(final BiFunction<HttpRequestMetaData, StreamingHttpResponseFactory,
                Single<StreamingHttpResponse>> onLimitResponseBuilder,
                                final Consumer<HttpResponseMetaData> onLimitRetryAfter,
                                final boolean onLimitStopAcceptingConnections,
                                final BiFunction<HttpRequestMetaData, StreamingHttpResponseFactory,
                                                Single<StreamingHttpResponse>> onOpenCircuitResponseBuilder,
                                final BiConsumer<HttpResponseMetaData, StateContext>
                                                onOpenCircuitRetryAfter) {
            this.onLimitResponseBuilder = onLimitResponseBuilder;
            this.onLimitRetryAfter = onLimitRetryAfter;
            this.onLimitStopAcceptingConnections = onLimitStopAcceptingConnections;
            this.onOpenCircuitResponseBuilder = onOpenCircuitResponseBuilder;
            this.onOpenCircuitRetryAfter = onOpenCircuitRetryAfter;
        }

        /**
         * A hard-coded delay in seconds to be supplied as a Retry-After HTTP header in a {@link HttpResponseMetaData}.
         *
         * @param seconds The value (in seconds) to be used in the Retry-After header.
         * @return A {@link HttpResponseMetaData} consumer, that enhances the headers with a fixed Retry-After figure in
         * seconds.
         */
        public static Consumer<HttpResponseMetaData> retryAfterHint(final int seconds) {
            final CharSequence secondsSeq = newAsciiString(valueOf(seconds));
            return resp -> resp.addHeader(RETRY_AFTER, secondsSeq);
        }

        /**
         * A delay in seconds to be supplied as a Retry-After HTTP header in a {@link HttpResponseMetaData} based on the
         * {@link CircuitBreaker} that matched the {@link HttpRequestMetaData}.
         *
         * @param fallbackSeconds The value (in seconds) to be used if no {@link CircuitBreaker} matched.
         * @return A {@link HttpResponseMetaData} consumer, that enhances the headers with a Retry-After figure in
         * seconds based on the duration the matching {@link CircuitBreaker} will remain open, or a fallback period.
         */
        public static BiConsumer<HttpResponseMetaData, StateContext>
        retryAfterHintOfBreaker(final int fallbackSeconds) {
            final CharSequence secondsSeq = newAsciiString(valueOf(fallbackSeconds));
            return (resp, state) -> {
                if (state.breaker() != null || fallbackSeconds > 0) {
                    resp.setHeader(RETRY_AFTER, state.breaker() != null ? newAsciiString(valueOf(
                                    state.breaker().remainingDurationInOpenState().getSeconds())) : secondsSeq);
                }
            };
        }

        /**
         * A hard-coded delay in milliseconds to be supplied as a Retry-After-Millis HTTP header in a
         * {@link HttpResponseMetaData}. Being a custom Http header, it will require special handling on the peer side.
         *
         * @param duration The duration to be used in the Retry-After-Millis header.
         * @return A {@link HttpResponseMetaData} consumer, that enhances the headers with a fixed
         * Retry-After-Millis figure in milliseconds.
         */
        public static BiConsumer<HttpResponseMetaData, CircuitBreaker> retryAfterMillisHint(final Duration duration) {
            final CharSequence millisSeq = newAsciiString(valueOf(duration.toMillis()));
            return (resp, breaker) -> resp.setHeader(RETRY_AFTER_MILLIS, millisSeq);
        }

        /**
         * Pre-defined {@link StreamingHttpResponse response} that signals
         * {@link io.servicetalk.http.api.HttpResponseStatus#TOO_MANY_REQUESTS} to the peer.
         *
         * @return A {@link BiFunction} that regardless the input, it will always return a
         * {@link StreamingHttpResponseFactory#tooManyRequests() too-many-requests} response.
         */
        public static BiFunction<HttpRequestMetaData, StreamingHttpResponseFactory, Single<StreamingHttpResponse>>
        tooManyRequests() {
            return (__, factory) -> succeeded(factory.tooManyRequests());
        }

        /**
         * Pre-defined {@link StreamingHttpResponse response} that signals
         * {@link io.servicetalk.http.api.HttpResponseStatus#SERVICE_UNAVAILABLE} to the peer.
         *
         * @return A {@link BiFunction} that regardless the input, it will always return a
         * {@link StreamingHttpResponseFactory#serviceUnavailable() service-unavailable} response.
         */
        public static BiFunction<HttpRequestMetaData, StreamingHttpResponseFactory, Single<StreamingHttpResponse>>
        serviceUnavailable() {
            return (__, factory) -> succeeded(factory.serviceUnavailable());
        }

        /**
         * A {@link RejectionPolicy} builder to support a custom policy.
         */
        public static final class Builder {
            private BiFunction<HttpRequestMetaData, StreamingHttpResponseFactory, Single<StreamingHttpResponse>>
                    onLimitResponseBuilder = tooManyRequests();

            private Consumer<HttpResponseMetaData> onLimitRetryAfter = __ -> { };

            private boolean onLimitStopAcceptingConnections;

            private BiFunction<HttpRequestMetaData, StreamingHttpResponseFactory, Single<StreamingHttpResponse>>
                    onOpenCircuitResponseBuilder = serviceUnavailable();

            private BiConsumer<HttpResponseMetaData, StateContext> onOpenCircuitRetryAfter =
                    retryAfterHintOfBreaker(-1);

            /**
             * Determines the {@link StreamingHttpResponse} when a capacity limit is met.
             *
             * @param onLimitResponseBuilder A factory function used to generate a {@link StreamingHttpResponse} based
             * on the {@link HttpRequestMetaData request} when a {@link CapacityLimiter capacity} limit is observed.
             * @return {@code this}.
             */
            public Builder onLimitResponseBuilder(final BiFunction<HttpRequestMetaData, StreamingHttpResponseFactory,
                    Single<StreamingHttpResponse>> onLimitResponseBuilder) {
                this.onLimitResponseBuilder = requireNonNull(onLimitResponseBuilder);
                return this;
            }

            /**
             * Determines a {@link HttpHeaderNames#RETRY_AFTER retry-after} header in the
             * {@link StreamingHttpResponse} when a capacity limit is met.
             *
             * @param onLimitRetryAfter A {@link HttpResponseMetaData} consumer, that can allow response decoration with
             * additional headers to hint the peer (upon capacity limits) about a possible wait-time before a
             * retry could be issued.
             * @return {@code this}.
             */
            public Builder onLimitRetryAfter(final Consumer<HttpResponseMetaData> onLimitRetryAfter) {
                this.onLimitRetryAfter = requireNonNull(onLimitRetryAfter);
                return this;
            }

            /**
             * When a certain {@link CapacityLimiter} rejects a request due to the active limit,
             * (e.g., no {@link Ticket} is returned) influence the server to also stop accepting new connections
             * until the capacity is under healthy conditions again.
             * <b>This setting only works when a {@link CapacityLimiter} matches the incoming request, in cases this
             * doesn't hold (see. {@link TrafficResilienceHttpServiceFilter.Builder#Builder(Supplier, boolean)}
             * Builder's rejectedNotMatched argument}) this won't be effective.</b>
             * <p>
             * When a server socket stops accepting new connections
             * (see. {@link HttpServiceContext#acceptConnections(boolean)}) due to capacity concerns, the state will be
             * toggled back when the {@link Ticket ticket's} terminal callback ({@link Ticket#dropped() dropped},
             * {@link Ticket#failed(Throwable) failed}, {@link Ticket#completed() completed}, {@link Ticket#ignored()
             * ignored}) returns a positive or negative value, demonstrating available capacity or not_supported
             * respectively. When the returned value is {@code 0} that means no-capacity available, which will keep the
             * server in the not-accepting mode.
             * <p>
             * When enabling this feature, it's recommended for clients using this service to configure timeouts
             * for their opening connection time and connection idleness time. For example, a client without
             * connection-timeout or idle-timeout on the outgoing connections towards this service, won't be able to
             * detect on time the connection delays. Likewise, on the server side you can configure the
             * {@link io.servicetalk.transport.api.ServiceTalkSocketOptions#SO_BACKLOG server backlog} to a very small
             * number or even disable it completely, to avoid holding established connections in the OS.
             * <p>
             * Worth noting that established connections that stay in the OS backlog, usually have a First In First Out
             * behavior, which depending on the size of that queue, may result in extending latencies on newer
             * requests because older ones are served first. Disabling the
             * {@link io.servicetalk.transport.api.ServiceTalkSocketOptions#SO_BACKLOG server backlog} will give a
             * better behavior.
             * @param stopAccepting {@code true} will allow this filter to control the connection acceptance of the
             * overall server socket.
             * @return {@code this}.
             */
            public Builder onLimitStopAcceptingConnections(final boolean stopAccepting) {
                this.onLimitStopAcceptingConnections = stopAccepting;
                return this;
            }

            /**
             * Determines the {@link StreamingHttpResponse} when a circuit-breaker limit is met.
             *
             * @param onOpenCircuitResponseBuilder A factory function used to generate a {@link StreamingHttpResponse}
             * based on the {@link HttpRequestMetaData request} when an open {@link CircuitBreaker breaker} is observed.
             * @return {@code this}.
             */
            public Builder onOpenCircuitResponseBuilder(final BiFunction<HttpRequestMetaData,
                    StreamingHttpResponseFactory, Single<StreamingHttpResponse>> onOpenCircuitResponseBuilder) {
                this.onOpenCircuitResponseBuilder = requireNonNull(onOpenCircuitResponseBuilder);
                return this;
            }

            /**
             * Determines a {@link HttpHeaderNames#RETRY_AFTER retry-after} header in the
             * {@link StreamingHttpResponse} when a capacity limit is met.
             *
             * @param onOpenCircuitRetryAfter A {@link HttpResponseMetaData} consumer, that can allow response
             * decoration with additional headers to hint the peer (upon open breaker) about a possible wait-time
             * before a retry could be issued.
             * @return {@code this}.
             */
            public Builder onOpenCircuitRetryAfter(final BiConsumer<HttpResponseMetaData,
                    StateContext> onOpenCircuitRetryAfter) {
                this.onOpenCircuitRetryAfter = requireNonNull(onOpenCircuitRetryAfter);
                return this;
            }

            /**
             * Return a custom {@link RejectionPolicy} based on the options of this builder.
             * @return A custom {@link RejectionPolicy} based on the options of this builder.
             */
            public RejectionPolicy build() {
                return new RejectionPolicy(onLimitResponseBuilder, onLimitRetryAfter, onLimitStopAcceptingConnections,
                        onOpenCircuitResponseBuilder, onOpenCircuitRetryAfter);
            }
        }
    }

    private static final class ServerResumptionTicketWrapper implements Ticket {
        private final Ticket ticket;
        private final ServerListenContext listenContext;

        private ServerResumptionTicketWrapper(final ServerListenContext listenContext, final Ticket ticket) {
            this.ticket = ticket;
            this.listenContext = listenContext;
        }

        @Nullable
        @Override
        public CapacityLimiter.LimiterState state() {
            return ticket.state();
        }

        @Override
        public int completed() {
            final int result = ticket.completed();
            if (result == -1 || result > 0) {
                listenContext.acceptConnections(true);
            }
            return result;
        }

        @Override
        public int dropped() {
            final int result = ticket.dropped();
            if (result == -1 || result > 0) {
                listenContext.acceptConnections(true);
            }
            return result;
        }

        @Override
        public int failed(final Throwable error) {
            final int result = ticket.failed(error);
            if (result == -1 || result > 0) {
                listenContext.acceptConnections(true);
            }
            return result;
        }

        @Override
        public int ignored() {
            final int result = ticket.ignored();
            if (result == -1 || result > 0) {
                listenContext.acceptConnections(true);
            }
            return result;
        }
    }
}
