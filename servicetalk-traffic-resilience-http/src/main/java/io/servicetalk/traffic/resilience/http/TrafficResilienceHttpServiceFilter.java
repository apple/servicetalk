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
import io.servicetalk.concurrent.api.Single;
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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.traffic.resilience.http.ServiceRejectionPolicy.DEFAULT_REJECTION_POLICY;
import static java.lang.Integer.MAX_VALUE;
import static java.util.Objects.requireNonNull;

/**
 * A {@link StreamingHttpServiceFilterFactory} to enforce capacity control for a server.
 * Requests that are not able to acquire a {@link Ticket permit}, will fail with a {@link RequestDroppedException}.
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
public final class TrafficResilienceHttpServiceFilter extends AbstractTrafficResilienceHttpFilter
        implements StreamingHttpServiceFilterFactory {

    private final ServiceRejectionPolicy serviceRejectionPolicy;

    private TrafficResilienceHttpServiceFilter(final Supplier<Function<HttpRequestMetaData, CapacityLimiter>>
                                                       capacityPartitionsSupplier,
                                               final boolean rejectNotMatched,
                                               final Supplier<Function<HttpRequestMetaData, Classification>> classifier,
                                               final Consumer<Ticket> onCompletion,
                                               final Consumer<Ticket> onCancellation,
                                               final BiConsumer<Ticket, Throwable> onError,
                                               final Supplier<Function<HttpRequestMetaData, CircuitBreaker>>
                                                       circuitBreakerPartitionsSupplier,
                                               final ServiceRejectionPolicy onServiceRejectionPolicy,
                                               final TrafficResiliencyObserver observer,
                                               final boolean dryRun) {
        super(capacityPartitionsSupplier, rejectNotMatched, classifier, __ -> false, __ -> false,
                onCompletion, onCancellation, onError, circuitBreakerPartitionsSupplier, observer,
                /*isClient*/ false, dryRun);
        this.serviceRejectionPolicy = onServiceRejectionPolicy;
    }

    @Override
    public StreamingHttpServiceFilter create(final StreamingHttpService service) {
        return TrackPendingRequestsHttpFilter.BEFORE.create(new StreamingHttpServiceFilter(
                TrackPendingRequestsHttpFilter.AFTER.create(service)) {

            final Function<HttpRequestMetaData, CapacityLimiter> capacityPartitions = newCapacityPartitions();
            final Function<HttpRequestMetaData, CircuitBreaker> circuitBreakerPartitions =
                    newCircuitBreakerPartitions();
            final Function<HttpRequestMetaData, Classification> classifier = newClassifier();
            final Function<HttpResponseMetaData, Duration> delayProvider = newDelayProvider();

            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        final StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {
                final ServerListenContext actualContext = ctx.parent() instanceof ServerListenContext ?
                        (ServerListenContext) ctx.parent() :
                        ctx;
                return applyCapacityControl(capacityPartitions, circuitBreakerPartitions, classifier, delayProvider,
                        actualContext, request, responseFactory,
                        request1 -> delegate().handle(ctx, request1, responseFactory));
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
        if (serviceRejectionPolicy.onLimitStopAcceptingConnections()) {
            serverListenContext.acceptConnections(false);
        }

        if (responseFactory != null) {
            return serviceRejectionPolicy.onLimitResponseBuilder()
                    .apply(request, responseFactory)
                    .map(resp -> {
                        serviceRejectionPolicy.onLimitRetryAfter().accept(resp);
                        return resp;
                    });
        }

        return DEFAULT_CAPACITY_REJECTION;
    }

    @Override
    Single<StreamingHttpResponse> handleLocalBreakerRejection(
            final StreamingHttpRequest request,
            @Nullable final StreamingHttpResponseFactory responseFactory,
            final CircuitBreaker breaker) {
        if (responseFactory != null) {
            return serviceRejectionPolicy.onOpenCircuitResponseBuilder()
                    .apply(request, responseFactory)
                    .map(resp -> {
                        serviceRejectionPolicy.onOpenCircuitRetryAfter()
                                .accept(resp, new StateContext(breaker));
                        return resp;
                    })
                    .shareContextOnSubscribe();
        }

        return DEFAULT_BREAKER_REJECTION;
    }

    /**
     * A {@link TrafficResilienceHttpServiceFilter} instance builder.
     *
     */
    public static final class Builder {
        private boolean rejectNotMatched;
        private Supplier<Function<HttpRequestMetaData, CapacityLimiter>> capacityPartitionsSupplier;
        private Supplier<Function<HttpRequestMetaData, Classification>> classifier = () -> __ -> () -> MAX_VALUE;
        private Supplier<Function<HttpRequestMetaData, CircuitBreaker>> circuitBreakerPartitionsSupplier =
                () -> __ -> null;
        private ServiceRejectionPolicy onServiceRejectionPolicy = DEFAULT_REJECTION_POLICY;
        private final Consumer<Ticket> onCompletionTicketTerminal = Ticket::completed;
        private Consumer<Ticket> onCancellationTicketTerminal = Ticket::ignored;
        private BiConsumer<Ticket, Throwable> onErrorTicketTerminal = (ticket, throwable) -> {
            if (throwable instanceof RequestDroppedException || throwable instanceof TimeoutException) {
                ticket.dropped();
            } else {
                ticket.failed(throwable);
            }
        };
        private TrafficResiliencyObserver observer = NoOpTrafficResiliencyObserver.INSTANCE;
        private boolean dryRun;

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
         * If {@code true} then the request will be {@link RequestDroppedException rejected}.
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
         * If {@code true} then the request will be {@link RequestDroppedException rejected}.
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
         * @param classifier A {@link Supplier} of a {@link Function} that maps an incoming {@link HttpRequestMetaData}
         * to a {@link Classification}.
         * @return {@code this}.
         */
        public Builder classifier(final Supplier<Function<HttpRequestMetaData, Classification>> classifier) {
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
         * {@link ServiceRejectionPolicy#onOpenCircuitRetryAfter()} can be used to hint peers about the fact that
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
         * Defines the {@link ServiceRejectionPolicy} which in turn defines the behavior of the service when a
         * rejection occurs due to {@link CapacityLimiter capacity} or {@link CircuitBreaker breaker}.
         *
         * @param policy The policy to put into effect when a rejection occurs.
         * @return {@code this}.
         * @see ServiceRejectionPolicy#DEFAULT_REJECTION_POLICY
         */
        public Builder rejectionPolicy(final ServiceRejectionPolicy policy) {
            this.onServiceRejectionPolicy = requireNonNull(policy, "policy");
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
         * @param dryRun whether to use the resilience filter in dry-run mode.
         * @return {@code this}
         */
        public Builder dryRun(final boolean dryRun) {
            this.dryRun = dryRun;
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
                    onErrorTicketTerminal, circuitBreakerPartitionsSupplier, onServiceRejectionPolicy, observer,
                    dryRun);
        }
    }

    private static final class ServerResumptionTicketWrapper implements Ticket {
        private final Ticket ticket;
        private final ServerListenContext listenContext;

        private ServerResumptionTicketWrapper(final ServerListenContext listenContext, final Ticket ticket) {
            this.ticket = ticket;
            this.listenContext = listenContext;
        }

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
