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
    private final boolean isClient;
    private final boolean dryRun;

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
            final TrafficResiliencyObserver observer,
            final boolean isClient,
            final boolean dryRun
            ) {
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
        this.isClient = isClient;
        this.dryRun = dryRun;
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

    final Single<StreamingHttpResponse> applyCapacityControl(
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
                        doHandleLocalCapacityRejection(delegate, null, request, responseFactory)
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
                return doHandleLocalCapacityRejection(delegate, serverListenContext, request, responseFactory)
                        .shareContextOnSubscribe();
            }
            final CircuitBreaker breaker = circuitBreakerPartitions.apply(request);
            if (breaker != null && !breaker.tryAcquirePermit()) {
                observer.onRejectedOpenCircuit(request, breaker.name(), meta, classification);
                // Ignore the acquired ticket if breaker was open.
                ticket.ignored();
                return doHandleLocalBreakerRejection(delegate, request, responseFactory, breaker)
                        .shareContextOnSubscribe();
            }

            // Ticket lifetime must be completed at all points now, try/catch to ensure if anything throws (e.g.
            // reactive flow isn't followed) we still complete ticket lifetime.
            try {
                final TicketObserver ticketObserver = observer.onAllowedThrough(request, ticket.state());
                return doHandleAllow(delegate, delayProvider, request, wrapTicket(serverListenContext, ticket),
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

    private Single<StreamingHttpResponse> doHandleLocalCapacityRejection(
            Function<StreamingHttpRequest, Single<StreamingHttpResponse>> delegate,
            @Nullable ServerListenContext serverListenContext,
            StreamingHttpRequest request,
            @Nullable StreamingHttpResponseFactory responseFactory) {
        return dryRun ? handlePassthrough(delegate, request) :
                handleLocalCapacityRejection(serverListenContext, request, responseFactory);
    }

    abstract Single<StreamingHttpResponse> handleLocalCapacityRejection(
            @Nullable ServerListenContext serverListenContext,
            StreamingHttpRequest request,
            @Nullable StreamingHttpResponseFactory responseFactory);

    private Single<StreamingHttpResponse> doHandleLocalBreakerRejection(
            Function<StreamingHttpRequest, Single<StreamingHttpResponse>> delegate,
            StreamingHttpRequest request,
            @Nullable StreamingHttpResponseFactory responseFactory,
            CircuitBreaker breaker) {
        return dryRun ? handlePassthrough(delegate, request) :
                handleLocalBreakerRejection(request, responseFactory, breaker);
    }

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

    private Single<StreamingHttpResponse> doHandleAllow(
            final Function<StreamingHttpRequest, Single<StreamingHttpResponse>> delegate,
            final Function<HttpResponseMetaData, Duration> delayProvider, final StreamingHttpRequest request,
            final Ticket ticket, final TicketObserver ticketObserver, @Nullable final CircuitBreaker breaker,
            final long startTimeNs) {
        return dryRun ? dryRunHandleAllow(
                delegate, delayProvider, request, ticket, ticketObserver, breaker, startTimeNs) :
                handleAllow(delegate, delayProvider, request, ticket, ticketObserver, breaker, startTimeNs);
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
                .liftSync(new BeforeFinallyHttpOperator(
                        new SignalConsumer(this, isClient, request, ticket, ticketObserver, breaker, startTimeNs),
                        true));
    }

    private Single<StreamingHttpResponse> dryRunHandleAllow(
            final Function<StreamingHttpRequest, Single<StreamingHttpResponse>> delegate,
            final Function<HttpResponseMetaData, Duration> delayProvider, final StreamingHttpRequest request,
            final Ticket ticket,
            final TicketObserver ticketObserver, @Nullable final CircuitBreaker breaker, final long startTimeNs) {
        SignalConsumer signalConsumer = new SignalConsumer(
                this, isClient, request, ticket, ticketObserver, breaker, startTimeNs);
        return delegate.apply(request)
                // This logic has the same general structure as the `handleAllow` case, but there is a twist: we want
                // to always return the successful single even when we fail the predicates. Because we use a latch in
                // SignalConsumer it's safe to signal those conditions eagerly in the `.flatMap` operation and let the
                // request proceed as usual and any following signals (such as from body processing) will be ignored.
                .map(resp -> {
                    if (breaker != null && breakerRejectionPredicate.test(resp)) {
                        Exception rejection = peerBreakerRejection(resp, breaker, delayProvider);
                        signalConsumer.onError(rejection);
                    } else if (capacityRejectionPredicate.test(resp)) {
                        final RuntimeException rejection = peerRejection(resp);
                        signalConsumer.onError(rejection);
                    }
                    return resp;
                })
                .liftSync(new BeforeFinallyHttpOperator(signalConsumer, true));
    }

    private static final class SignalConsumer implements TerminalSignalConsumer {

        private static final AtomicIntegerFieldUpdater<SignalConsumer> STATE_UPDATER =
                AtomicIntegerFieldUpdater.newUpdater(SignalConsumer.class, "state");
        private static final int IDLE = 0;
        private static final int REQUEST_COMPLETE = 1;
        private static final int RESPONSE_COMPLETE = 2;
        private static final int FINISHED = 3;

        private final AbstractTrafficResilienceHttpFilter parent;
        private final Ticket ticket;
        private final TicketObserver ticketObserver;
        @Nullable
        private final CircuitBreaker breaker;
        private final long startTimeNs;

        // We store our state in the `state` variable. For responses, we must also store whether we cancelled
        // or if we errored. In those cases we set the field before transitioning state so we get memory visibility
        // without needing to make those fields volatile as well.
        private volatile int state;
        @Nullable
        private Throwable responseError;
        private boolean cancelled;

        SignalConsumer(final AbstractTrafficResilienceHttpFilter parent, boolean isClient, StreamingHttpRequest request,
                              final Ticket ticket, final TicketObserver ticketObserver,
                              @Nullable final CircuitBreaker breaker, final long startTimeNs) {
            this.parent = parent;
            this.ticket = ticket;
            this.ticketObserver = ticketObserver;
            this.breaker = breaker;
            this.startTimeNs = startTimeNs;
            if (isClient) {
                state = REQUEST_COMPLETE;
            } else {
                request.transformMessageBody(body -> body.beforeFinally(this::requestComplete));
            }
        }

        @Override
        public void onComplete() {
            if (responseFinished()) {
                doOnComplete();
            }
        }

        @Override
        public void onError(final Throwable throwable) {
            responseError = throwable;
            if (responseFinished()) {
                doOnError(throwable);
            }
        }

        @Override
        public void cancel() {
            cancelled = true;
            if (responseFinished()) {
                doCancel();
            }
        }

        private void doOnComplete() {
            try {
                if (breaker != null) {
                    breaker.onSuccess(nanoTime() - startTimeNs, NANOSECONDS);
                }
            } finally {
                parent.onSuccessTicketTerminal.accept(ticket);
                ticketObserver.onComplete();
            }
        }

        private void doOnError(final Throwable throwable) {
            parent.onError(throwable, breaker, startTimeNs, ticket);
            ticketObserver.onError(throwable);
        }

        private void doCancel() {
            try {
                if (breaker != null) {
                    breaker.ignorePermit();
                }
            } finally {
                parent.onCancellationTicketTerminal.accept(ticket);
                ticketObserver.onCancel();
            }
        }

        private void requestComplete() {
            if (STATE_UPDATER.compareAndSet(this, IDLE, REQUEST_COMPLETE)) {
                // nothing to do: it's up to the response to finish now.
                return;
            }
            if (STATE_UPDATER.compareAndSet(this, RESPONSE_COMPLETE, FINISHED)) {
                // This operation completed the sequence. We need to finish according to how the response completed.
                // Technically response error and cancellation can race. We give preference to the response error
                // form because that will likely be the more interesting in terms of observability.
                Throwable responseError = this.responseError;
                if (responseError != null) {
                    doOnError(responseError);
                } else if (cancelled) {
                    doCancel();
                } else {
                    doOnComplete();
                }
            }
        }

        private boolean responseFinished() {
            if (STATE_UPDATER.compareAndSet(this, IDLE, RESPONSE_COMPLETE)) {
                // nothing to do: it's up to the request to finish now.
                return false;
            }
            return STATE_UPDATER.compareAndSet(this, REQUEST_COMPLETE, FINISHED);
        }
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
            signal(SIGNAL_COMPLETED);
            return delegate.completed();
        }

        @Override
        public int dropped() {
            signal(SIGNAL_DROPPED);
            return delegate.dropped();
        }

        @Override
        public int failed(final Throwable error) {
            signal(SIGNAL_FAILED);
            return delegate.failed(error);
        }

        @Override
        public int ignored() {
            signal(SIGNAL_IGNORED);
            return delegate.ignored();
        }

        private void signal(final int newSignal) {
            for (;;) {
                final int oldValue = signaled;
                if (signaledUpdater.compareAndSet(this, oldValue, oldValue | newSignal)) {
                    if (oldValue > NOT_SIGNALED) {
                        // We have a double signal, log this event since it is not expected.
                        LOGGER.warn("{} signaled completion more than once. Already signaled with {}, new signal {}.",
                                getClass().getSimpleName(), oldValue, newSignal);
                    }
                    return;
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
    }
}
