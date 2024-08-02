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
import io.servicetalk.concurrent.api.TerminalSignalConsumer;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.utils.BeforeFinallyHttpOperator;
import io.servicetalk.traffic.resilience.http.ClientPeerRejectionPolicy.PassthroughRequestDroppedException;
import io.servicetalk.traffic.resilience.http.TrafficResiliencyObserver.TicketObserver;
import io.servicetalk.transport.api.ServerListenContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.internal.ThrowableUtils.unknownStackTrace;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNone;
import static java.lang.System.nanoTime;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

abstract class AbstractTrafficResilienceHttpFilter implements HttpExecutionStrategyInfluencer {
    private static final RequestDroppedException CAPACITY_REJECTION = unknownStackTrace(
            new RequestDroppedException("Service under heavy load", null, false, true),
            AbstractTrafficResilienceHttpFilter.class, "remoteRejection");
    private static final RequestDroppedException BREAKER_REJECTION = unknownStackTrace(
            new RequestDroppedException("Service Unavailable", null, false, true),
            AbstractTrafficResilienceHttpFilter.class, "breakerRejection");

    protected static final Single<StreamingHttpResponse> DEFAULT_CAPACITY_REJECTION =
            Single.failed(CAPACITY_REJECTION);

    protected static final Single<StreamingHttpResponse> DEFAULT_BREAKER_REJECTION =
            Single.failed(BREAKER_REJECTION);

    private final Supplier<Function<HttpRequestMetaData, CapacityLimiter>> capacityPartitionsSupplier;

    private final Consumer<Ticket> onSuccessTicketTerminal;

    private final Consumer<Ticket> onCancellationTicketTerminal;

    private final BiConsumer<Ticket, Throwable> onErrorTicketTerminal;

    private final boolean rejectWhenNotMatchedCapacityPartition;

    private final Supplier<Function<HttpRequestMetaData, Classification>> classifier;

    private final Predicate<HttpResponseMetaData> capacityRejectionPredicate;

    private final Predicate<HttpResponseMetaData> breakerRejectionPredicate;

    private final Supplier<Function<HttpRequestMetaData, CircuitBreaker>> circuitBreakerPartitionsSupplier;

    private final TrafficResiliencyObserver observer;

    AbstractTrafficResilienceHttpFilter(
            final Supplier<Function<HttpRequestMetaData, CapacityLimiter>> capacityPartitionsSupplier,
            final boolean rejectWhenNotMatchedCapacityPartition,
            final Supplier<Function<HttpRequestMetaData, Classification>> classifier,
            final Predicate<HttpResponseMetaData> capacityRejectionPredicate,
            final Predicate<HttpResponseMetaData> breakerRejectionPredicate,
            final Consumer<Ticket> onSuccessTicketTerminal,
            final Consumer<Ticket> onCancellationTicketTerminal,
            final BiConsumer<Ticket, Throwable> onErrorTicketTerminal,
            final Supplier<Function<HttpRequestMetaData, CircuitBreaker>> circuitBreakerPartitionsSupplier,
            final TrafficResiliencyObserver observer) {
        this.capacityPartitionsSupplier = requireNonNull(capacityPartitionsSupplier, "capacityPartitionsSupplier");
        this.rejectWhenNotMatchedCapacityPartition = rejectWhenNotMatchedCapacityPartition;
        this.capacityRejectionPredicate = requireNonNull(capacityRejectionPredicate, "capacityRejectionPredicate");
        this.breakerRejectionPredicate = requireNonNull(breakerRejectionPredicate, "breakerRejectionPredicate");
        this.classifier = requireNonNull(classifier, "classifier");
        this.onSuccessTicketTerminal = requireNonNull(onSuccessTicketTerminal, "onSuccessTicketTerminal");
        this.onCancellationTicketTerminal = requireNonNull(onCancellationTicketTerminal,
                "onCancellationTicketTerminal");
        this.onErrorTicketTerminal = requireNonNull(onErrorTicketTerminal, "onErrorTicketTerminal");
        this.circuitBreakerPartitionsSupplier = requireNonNull(circuitBreakerPartitionsSupplier,
                "circuitBreakerPartitionsSupplier");
        this.observer = requireNonNull(observer, "observer");
    }

    @Override
    public HttpExecutionStrategy requiredOffloads() {
        return offloadNone();
    }

    // Each filter needs a new capacity & breaker partitions functions.
    final Function<HttpRequestMetaData, CapacityLimiter> newCapacityPartitions() {
        return capacityPartitionsSupplier.get();
    }

    final Function<HttpRequestMetaData, CircuitBreaker> newCircuitBreakerPartitions() {
        return circuitBreakerPartitionsSupplier.get();
    }

    final Function<HttpRequestMetaData, Classification> newClassifier() {
        return classifier.get();
    }

    Function<HttpResponseMetaData, Duration> newDelayProvider() {
        return __ -> Duration.ZERO;
    }

    Single<StreamingHttpResponse> applyCapacityControl(
            final Function<HttpRequestMetaData, CapacityLimiter> capacityPartitions,
            final Function<HttpRequestMetaData, CircuitBreaker> circuitBreakerPartitions,
            final Function<HttpRequestMetaData, Classification> classifier,
            final Function<HttpResponseMetaData, Duration> delayProvider,
            @Nullable final ServerListenContext serverListenContext,
            final StreamingHttpRequest request,
            @Nullable final StreamingHttpResponseFactory responseFactory,
            final Function<StreamingHttpRequest, Single<StreamingHttpResponse>> delegate) {
        return defer(() -> {
            final long startTime = nanoTime();
            final CapacityLimiter partition = capacityPartitions.apply(request);
            if (partition == null) {
                observer.onRejectedUnmatchedPartition(request);
                return rejectWhenNotMatchedCapacityPartition ?
                        handleLocalCapacityRejection(null, request, responseFactory)
                                .shareContextOnSubscribe() :
                        handlePassthrough(delegate, request)
                                .shareContextOnSubscribe();
            }

            final ContextMap meta = request.context();
            final Classification classification = classifier.apply(request);
            Ticket ticket = partition.tryAcquire(classification, meta);
            if (ticket != null) {
                ticket = new TrackingDelegatingTicket(ticket, request.hashCode());
            }

            if (ticket == null) {
                observer.onRejectedLimit(request, partition.name(), meta, classification);
                return handleLocalCapacityRejection(serverListenContext, request, responseFactory)
                        .shareContextOnSubscribe();
            }
            final CircuitBreaker breaker = circuitBreakerPartitions.apply(request);
            if (breaker != null && !breaker.tryAcquirePermit()) {
                observer.onRejectedOpenCircuit(request, breaker.name(), meta, classification);
                // Ignore the acquired ticket if breaker was open.
                ticket.ignored();
                return handleLocalBreakerRejection(request, responseFactory, breaker).shareContextOnSubscribe();
            }

            // Ticket lifetime must be completed at all points now, try/catch to ensure if anything throws (e.g.
            // reactive flow isn't followed) we still complete ticket lifetime.
            try {
                final TicketObserver ticketObserver = observer.onAllowedThrough(request, ticket.state());
                return handleAllow(delegate, delayProvider, request, wrapTicket(serverListenContext, ticket),
                        ticketObserver, breaker, startTime).shareContextOnSubscribe();
            } catch (Throwable cause) {
                onError(cause, breaker, startTime, ticket);
                throw cause;
            }
        });
    }

    Ticket wrapTicket(@Nullable final ServerListenContext serverListenContext, final Ticket ticket) {
        return ticket;
    }

    abstract Single<StreamingHttpResponse> handleLocalCapacityRejection(
            @Nullable ServerListenContext serverListenContext,
            StreamingHttpRequest request,
            @Nullable StreamingHttpResponseFactory responseFactory);

    abstract Single<StreamingHttpResponse> handleLocalBreakerRejection(
            StreamingHttpRequest request,
            @Nullable StreamingHttpResponseFactory responseFactory,
            CircuitBreaker breaker);

    RuntimeException peerRejection(final StreamingHttpResponse resp) {
        return CAPACITY_REJECTION;
    }

    RuntimeException peerBreakerRejection(final HttpResponseMetaData resp, final CircuitBreaker breaker,
                                          final Function<HttpResponseMetaData, Duration> delayProvider) {
        return BREAKER_REJECTION;
    }

    private static Single<StreamingHttpResponse> handlePassthrough(
            final Function<StreamingHttpRequest, Single<StreamingHttpResponse>> delegate,
            final StreamingHttpRequest request) {
        return delegate.apply(request);
    }

    private Single<StreamingHttpResponse> handleAllow(
            final Function<StreamingHttpRequest, Single<StreamingHttpResponse>> delegate,
            final Function<HttpResponseMetaData, Duration> delayProvider,
            final StreamingHttpRequest request, final Ticket ticket, final TicketObserver ticketObserver,
            @Nullable final CircuitBreaker breaker, final long startTimeNs) {
        return delegate.apply(request)
                // The map is issuing an exception that will be propagated to the downstream BeforeFinallyHttpOperator
                // in order to invoke the appropriate callbacks to release resources.
                // If the BeforeFinallyHttpOperator comes earlier, the Single will succeed and only downstream will
                // see the exception, preventing callbacks to release permits; which results in the limiter eventually
                // throttling ALL future requests.
                // Before returning an error, we have to drain the response payload body to properly release resources
                // and avoid leaking a connection, except for the PassthroughRequestDroppedException case.
                .flatMap(resp -> {
                    if (breaker != null && breakerRejectionPredicate.test(resp)) {
                        return resp.payloadBody().ignoreElements()
                                .concat(Single.<StreamingHttpResponse>failed(
                                        peerBreakerRejection(resp, breaker, delayProvider)))
                                .shareContextOnSubscribe();
                    } else if (capacityRejectionPredicate.test(resp)) {
                        final RuntimeException rejection = peerRejection(resp);
                        if (PassthroughRequestDroppedException.class.equals(rejection.getClass())) {
                            return Single.<StreamingHttpResponse>failed(rejection).shareContextOnSubscribe();
                        }
                        return resp.payloadBody().ignoreElements()
                                .concat(Single.<StreamingHttpResponse>failed(rejection))
                                .shareContextOnSubscribe();
                    }
                    return Single.succeeded(resp).shareContextOnSubscribe();
                })
                .liftSync(new BeforeFinallyHttpOperator(new TerminalSignalConsumer() {
                    @Override
                    public void onComplete() {
                        try {
                            if (breaker != null) {
                                breaker.onSuccess(nanoTime() - startTimeNs, NANOSECONDS);
                            }
                        } finally {
                            onSuccessTicketTerminal.accept(ticket);
                            ticketObserver.onComplete();
                        }
                    }

                    @Override
                    public void onError(final Throwable throwable) {
                        AbstractTrafficResilienceHttpFilter.this.onError(throwable, breaker, startTimeNs, ticket);
                        ticketObserver.onError(throwable);
                    }

                    @Override
                    public void cancel() {
                        try {
                            if (breaker != null) {
                                breaker.ignorePermit();
                            }
                        } finally {
                            onCancellationTicketTerminal.accept(ticket);
                            ticketObserver.onCancel();
                        }
                    }
                }, true));
    }

    private void onError(final Throwable throwable, @Nullable final CircuitBreaker breaker,
                         final long startTimeNs, final Ticket ticket) {
        try {
            if (breaker != null && !CAPACITY_REJECTION.equals(throwable)) {
                // Capacity rejections should not count towards circuit-breaker stats
                breaker.onError(nanoTime() - startTimeNs, NANOSECONDS, throwable);
            }
        } finally {
            onErrorTicketTerminal.accept(ticket, throwable);
        }
    }

    /**
     * A ticket which delegates the actual calls to the delegate, but also tracks if the actual terminal signals
     * are called.
     */
    static final class TrackingDelegatingTicket implements Ticket {

        private static final Logger LOGGER = LoggerFactory.getLogger(TrackingDelegatingTicket.class);

        private static final int NOT_SIGNALED = 0;
        private static final int SIGNAL_COMPLETED = 1;
        private static final int SIGNAL_DROPPED = 2;
        private static final int SIGNAL_FAILED = 4;
        private static final int SIGNAL_IGNORED = 8;

        private static final AtomicIntegerFieldUpdater<TrackingDelegatingTicket> signaledUpdater =
                newUpdater(TrackingDelegatingTicket.class, "signaled");

        private final Ticket delegate;

        /**
         * Contains the hash code of the request which acquired this ticket.
         */
        private final int requestHashCode;

        private volatile int signaled;

        TrackingDelegatingTicket(final Ticket delegate, final int requestHashCode) {
            this.delegate = delegate;
            this.requestHashCode = requestHashCode;
        }

        @Override
        public CapacityLimiter.LimiterState state() {
            return delegate.state();
        }

        @Override
        public int completed() {
            return signal(SIGNAL_COMPLETED) ? delegate.completed() : -1;
        }

        @Override
        public int dropped() {
            return signal(SIGNAL_DROPPED) ? delegate.dropped() : -1;
        }

        @Override
        public int failed(final Throwable error) {
            return signal(SIGNAL_FAILED) ? delegate.failed(error) : -1;
        }

        @Override
        public int ignored() {
            return signal(SIGNAL_IGNORED) ? delegate.ignored() : -1;
        }

        // returns true if this was the first signal triggered, false otherwise.
        private boolean signal(final int newSignal) {
            for (;;) {
                final int oldValue = signaled;
                if (signaledUpdater.compareAndSet(this, oldValue, oldValue | newSignal)) {
                    if (oldValue == NOT_SIGNALED) {
                        return true;
                    } else {
                        // We have a double signal, log this event since it is not expected.
                        LOGGER.warn("{} signaled completion more than once. Already signaled with {}, new signal {}.",
                                getClass().getSimpleName(), oldValue, newSignal);
                        return false;
                    }
                }
            }
        }

        @Override
        public String toString() {
            return "TrackingDelegatingTicket{" +
                    "delegate=" + delegate +
                    ", requestHashCode=" + requestHashCode +
                    ", signaled=" + signaled +
                    '}';
        }

        // TODO: this should be removed after we're confident we're not leaking tickets.
        @Override
        protected void finalize() throws Throwable {
            if (signaledUpdater.get(this) != NOT_SIGNALED) {
                LOGGER.warn("Ticket for request with hashCode {} was abandoned. Considering it ignored.",
                        requestHashCode);
                ignored();
            }
        }
    }
}
