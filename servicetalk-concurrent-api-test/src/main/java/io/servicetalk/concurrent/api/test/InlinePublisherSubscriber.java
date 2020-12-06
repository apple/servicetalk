/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api.test;

import io.servicetalk.concurrent.PublisherSource.Processor;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.test.InlineStepVerifier.PublisherEvent;
import io.servicetalk.concurrent.internal.FlowControlUtils;
import io.servicetalk.concurrent.internal.TerminalNotification;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Processors.newPublisherProcessor;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.api.test.InlineStepVerifier.PublisherEvent.notEqualsOnNext;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static io.servicetalk.concurrent.internal.TerminalNotification.error;
import static java.util.Objects.requireNonNull;

final class InlinePublisherSubscriber<T> implements Subscriber<T>, InlineVerifiableSubscriber {
    @SuppressWarnings("rawtypes")
    private static final AtomicLongFieldUpdater<InlinePublisherSubscriber> outstandingDemandUpdater =
            AtomicLongFieldUpdater.newUpdater(InlinePublisherSubscriber.class, "outstandingDemand");
    @SuppressWarnings("rawtypes")
    private static final AtomicIntegerFieldUpdater<InlinePublisherSubscriber> eventIndexUpdater =
            AtomicIntegerFieldUpdater.newUpdater(InlinePublisherSubscriber.class, "eventIndex");
    private final NormalizedTimeSource timeSource;
    private final List<PublisherEvent> events;
    private final Processor<VerifyThreadEvent, VerifyThreadEvent> verifyThreadProcessor =
            newPublisherProcessor(Integer.MAX_VALUE);
    private final String exceptionClassNamePrefix;
    /**
     * Used for {@link OnSubscriptionEvent}s.
     */
    @Nullable
    private List<Long> currSubscriptionIndexes;
    /**
     * Used for: {@link OnNextIterableEvent} and {@link OnNextIgnoreEvent}.
     */
    private long currCount;
    /**
     * Used for: {@link OnNextIterableEvent}.
     */
    @Nullable
    private Iterator<? extends T> currIterator;
    /**
     * Used for: {@link OnNextAggregateEvent}.
     */
    @Nullable
    private List<T> currAggregateSignals;
    /**
     * Used for: {@link NoSignalForDurationEvent}s.
     */
    @Nullable
    private Long noSignalsUntil;
    /**
     * Used for {@link NoSignalForDurationEvent}
     */
    private int prevNoSignalsEventIndex;
    @Nullable
    private PublisherEvent currEvent;

    @Nullable
    private Subscription subscription;
    @Nullable
    private TerminalNotification terminal;
    private volatile long outstandingDemand;
    private volatile int eventIndex = -1;

    InlinePublisherSubscriber(long initialDemand, NormalizedTimeSource timeSource, List<PublisherEvent> events,
                              String exceptionClassNamePrefix) {
        outstandingDemand = initialDemand;
        this.timeSource = timeSource;
        this.events = events;
        this.exceptionClassNamePrefix = exceptionClassNamePrefix;
        pollNextEvent();
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        requireNonNull(subscription, "Null Subscription is not permitted " +
                    "https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#2.13");
        verifyNoTerminal("onSubscribe", null, false);
        if (this.subscription != null) {
            throw new IllegalStateException("Subscription already set to " + this.subscription +
                    ". New Subscription " + subscription + " is not supported.");
        }
        this.subscription = new Subscription() {
            @Override
            public void request(final long n) {
                try {
                    if (n > 0) { // let negative demand through to test if the source delivers an error.
                        outstandingDemandUpdater.accumulateAndGet(InlinePublisherSubscriber.this, n,
                                FlowControlUtils::addWithOverflowProtection);
                    }
                } finally {
                    subscription.request(n);
                }
            }

            @Override
            public void cancel() {
                try {
                    subscription.cancel();
                } finally {
                    verifyThreadProcessor.onComplete();
                }
            }
        };

        PublisherEvent event = currEvent();
        event = checkNoSignalsExpectation("onSubscribe", subscription, event);
        if (event instanceof OnSubscriptionEvent) {
            try {
                ((OnSubscriptionEvent) event).subscription(this.subscription);
                event = pollNextEvent();
            } catch (Throwable cause) {
                failVerification(cause, event);
            }
        }
        requestIfNecessary(event);
    }

    @Override
    public void onNext(@Nullable T t) {
        verifyOnSubscribedAndNoTerminal("onNext", t, true);
        if (outstandingDemandUpdater.decrementAndGet(this) < 0) {
            throw new IllegalStateException(
                    "Too many onNext signals relative to Subscription request(n). " +
                            "https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#1.1");
        }

        PublisherEvent event = currEvent();
        event = checkNoSignalsExpectation("onNext", t, event);
        if (event instanceof OnNextEvent) {
            @SuppressWarnings("unchecked")
            final OnNextEvent<T> castEvent = ((OnNextEvent<T>) event);
            try {
                castEvent.onNext(t);
                event = pollNextEvent();
            } catch (Throwable cause) {
                failVerification(cause, event);
            }
        } else if (event instanceof OnNextAggregateEvent) {
            @SuppressWarnings("unchecked")
            final OnNextAggregateEvent<T> castEvent = ((OnNextAggregateEvent<T>) event);
            assert currAggregateSignals != null;
            currAggregateSignals.add(t);
            if (currAggregateSignals.size() == castEvent.maxOnNext()) {
                try {
                    List<T> tmp = currAggregateSignals;
                    currAggregateSignals = null;
                    castEvent.signalsConsumer.accept(tmp);
                    event = pollNextEvent();
                } catch (Throwable cause) {
                    event = failVerification(cause, event);
                }
            }
        } else if (event instanceof OnNextIterableEvent) {
            assert currIterator != null;
            try {
                final T iterNext = currIterator.next();
                if (notEqualsOnNext(iterNext, t)) {
                    event = failVerification(new AssertionError(event.description() + " iterator " + currIterator
                            + " failed at index " + currCount + ". expected: " + iterNext + " actual: " + t), event);
                } else if (currIterator.hasNext()) {
                    ++currCount;
                    assert subscription != null;
                    subscription.request(1);
                } else {
                    event = pollNextEvent();
                }
            } catch (Throwable cause) {
                event = failVerification(cause, event);
            }
        } else if (event instanceof OnNextIgnoreEvent) {
            if (++currCount >= ((OnNextIgnoreEvent) event).ignoreCount()) {
                event = pollNextEvent();
            }
        } else if (event != null) {
            failVerification(new IllegalStateException("expected " + event.description() + ", actual onNext: " + t),
                    event);
        }
    }

    @Override
    public void onError(Throwable t) {
        verifyOnSubscribedAndNoTerminal("onError", t, true);
        PublisherEvent event = currEvent();
        terminal = error(t);
        event = checkNoSignalsExpectation("onError", t, event);
        event = checkOnNextEventsFromTerminal("onError", event);
        if (event instanceof OnTerminalEvent) {
            try {
                ((OnTerminalEvent) event).onError(t);
                event = pollNextEvent();
            } catch (Throwable cause) {
                failVerification(cause, event);
            }
        } else if (event != null) {
            event = failVerification(new IllegalStateException("expected " + event.description() +
                    ", actual onError", t), event);
        }
    }

    @Override
    public void onComplete() {
        verifyOnSubscribedAndNoTerminal("onComplete", null, false);
        PublisherEvent event = currEvent();
        terminal = complete();
        event = checkNoSignalsExpectation("onComplete", null, event);
        event = checkOnNextEventsFromTerminal("onComplete", event);
        if (event instanceof OnTerminalEvent) {
            try {
                ((OnTerminalEvent) event).onComplete();
                event = pollNextEvent();
            } catch (Throwable cause) {
                failVerification(cause, event);
            }
        } else if (event != null) {
            event = failVerification(new IllegalStateException("expected " + event.description() +
                    ", actual onComplete"), event);
        }
    }

    @Override
    public Publisher<VerifyThreadEvent> verifyThreadEvents() {
        return fromSource(verifyThreadProcessor);
    }

    @Override
    @Nullable
    public PublisherEvent externalTimeout() {
        // called from a different thread, use the volatile index to get the current value.
        final int i = eventIndex;
        return i >= 0 && i < events.size() ? events.get(i) : null;
    }

    @Nullable
    private PublisherEvent currEvent() {
        return currEvent;
    }

    @Nullable
    private PublisherEvent checkOnNextEventsFromTerminal(String signalName, @Nullable PublisherEvent event) {
        if (event instanceof OnNextAggregateEvent) {
            @SuppressWarnings("unchecked")
            final OnNextAggregateEvent<T> castEvent = ((OnNextAggregateEvent<T>) event);
            assert currAggregateSignals != null;
            if (currAggregateSignals.size() >= castEvent.minOnNext()) {
                try {
                    List<T> tmp = currAggregateSignals;
                    currAggregateSignals = null;
                    castEvent.signalsConsumer.accept(tmp);
                    event = pollNextEvent();
                } catch (Throwable cause) {
                    event = failVerification(cause, event);
                }
            } else {
                event = failVerification(new IllegalStateException("expected " + event.description() + " signals: " +
                        currAggregateSignals + ", actual " + signalName), event);
                currAggregateSignals = null;
            }
        } else if (event instanceof OnNextIgnoreEvent) {
            event = pollNextEvent();
        }
        return event;
    }

    @Nullable
    private PublisherEvent pollNextEvent() {
        Queue<VerifyThreadEvent> verifyThreadEvents = null;
        int subscriptionBeginIndex = -1;
        do {
            final int eventIndex = eventIndexUpdater.incrementAndGet(this);
            if (eventIndex >= events.size()) {
                // try to execute any remaining events before completing
                currEvent = null;
                subscriptionBeginIndex = addSubscriptionIndexRange(subscriptionBeginIndex, eventIndex);
                processSubscriptionAggregate();
                processVerifyThreadAggregate(verifyThreadEvents);
                verifyThreadProcessor.onComplete();
                break;
            }
            currEvent = events.get(eventIndex);
            if (currEvent instanceof NoSignalForDurationEvent) {
                subscriptionBeginIndex = addSubscriptionIndexRange(subscriptionBeginIndex, eventIndex);
                prevNoSignalsEventIndex = eventIndex;
                Duration duration = ((NoSignalForDurationEvent) currEvent).duration();
                noSignalsUntil = noSignalsUntil == null ?
                        timeSource.timeStampFromNow(duration) :
                        timeSource.timeStampFrom(noSignalsUntil, duration);
            } else if (currEvent instanceof SubscriptionEvent) {
                if (subscriptionBeginIndex < 0) {
                    subscriptionBeginIndex = eventIndex;
                }
            } else if (currEvent instanceof VerifyThreadEvent) {
                subscriptionBeginIndex = addSubscriptionIndexRange(subscriptionBeginIndex, eventIndex);
                if (verifyThreadEvents == null) {
                    verifyThreadEvents = new ArrayDeque<>();
                }
                verifyThreadEvents.add((VerifyThreadEvent) currEvent);
            } else {
                subscriptionBeginIndex = addSubscriptionIndexRange(subscriptionBeginIndex, eventIndex);
                if (currEvent instanceof OnNextIgnoreEvent) {
                    currCount = 0;
                } else if (currEvent instanceof OnNextIterableEvent) {
                    @SuppressWarnings("unchecked")
                    final OnNextIterableEvent<T> castEvent = (OnNextIterableEvent<T>) currEvent;
                    Iterator<? extends T> iterable = castEvent.iterable.iterator();
                    if (iterable == null) {
                        throw new NullPointerException(currEvent.description() + " returned a null " +
                                Iterator.class.getSimpleName());
                    }
                    if (!iterable.hasNext()) {
                        continue; // there is nothing to verify, skip this event and get the next one.
                    }
                    currIterator = iterable;
                    currCount = 0;
                } else if (currEvent instanceof OnNextAggregateEvent) {
                    @SuppressWarnings("unchecked")
                    final OnNextAggregateEvent<T> castEvent = (OnNextAggregateEvent<T>) currEvent;
                    currAggregateSignals = new ArrayList<>(castEvent.minOnNext());
                }
                if (subscription != null) {
                    requestIfNecessary(currEvent);
                }
                break;
            }
        } while (true);

        processVerifyThreadAggregate(verifyThreadEvents);

        return currEvent;
    }

    private void requestIfNecessary(@Nullable PublisherEvent event) {
        assert subscription != null;
        processSubscriptionAggregate();
        if (event instanceof OnNextEvent || event instanceof OnNextIterableEvent ||
                event instanceof NoSignalForDurationEvent) {
            subscription.request(1);
        } else if (event instanceof OnNextIgnoreEvent) {
            subscription.request(((OnNextIgnoreEvent) event).ignoreCount());
        } else if (event instanceof OnNextAggregateEvent) {
            subscription.request(((OnNextAggregateEvent<?>) event).maxOnNext());
        } else if (event instanceof OnTerminalEvent) {
            subscription.request(Long.MAX_VALUE);
        }
    }

    private void processVerifyThreadAggregate(@Nullable Queue<VerifyThreadEvent> verifyThreadEvents) {
        if (verifyThreadEvents == null) {
            return;
        }
        VerifyThreadEvent event;
        while ((event = verifyThreadEvents.poll()) != null) {
            verifyThreadProcessor.onNext(event);
        }
    }

    private int addSubscriptionIndexRange(int beginIndex, int currEventIndex) {
        if (beginIndex >= 0) {
            if (currSubscriptionIndexes == null) {
                currSubscriptionIndexes = new ArrayList<>(2);
            }
            currSubscriptionIndexes.add((((long) beginIndex) << 32) | currEventIndex);
        }
        return -1;
    }

    private void processSubscriptionAggregate() {
        if (subscription != null && currSubscriptionIndexes != null) {
            for (Long indexes : currSubscriptionIndexes) {
                final int end = indexes.intValue();
                for (int i = (int) (indexes >>> 32); i < end; ++i) {
                    PublisherEvent event = events.get(i);
                    try {
                        ((SubscriptionEvent) event).subscription(subscription);
                    } catch (Throwable cause) {
                        failVerification(cause, event);
                    }
                }
            }
            currSubscriptionIndexes.clear();
        }
    }

    @Nullable
    private PublisherEvent checkNoSignalsExpectation(String signalName, @Nullable Object signal,
                                                     @Nullable PublisherEvent event) {
        while (noSignalsUntil != null) {
            if (timeSource.isExpired(noSignalsUntil)) {
                event = failVerification(new IllegalStateException("Received " + signalName + "(" +
                        signal + ") with " + timeSource.duration(noSignalsUntil).negated() +
                        " time remaining."), events.get(prevNoSignalsEventIndex));
            }
            noSignalsUntil = null;
        }
        return event;
    }

    @Nullable
    private PublisherEvent failVerification(Throwable cause, @Nullable PublisherEvent event) {
        if (event != null) {
            verifyThreadProcessor.onError(event.newException(cause.getMessage(), cause, exceptionClassNamePrefix));
        } else {
            event = currEvent();
            if (event != null) {
                verifyThreadProcessor.onError(event.newException(cause.getMessage(), cause,
                        exceptionClassNamePrefix));
            } else {
                verifyThreadProcessor.onError(new AssertionError("unexpected failure!", cause));
            }
        }
        return null;
    }

    private void verifyNoTerminal(String method, @Nullable Object param, boolean useParam) {
        if (terminal != null) {
            throw new IllegalStateException("Subscriber has already terminated [" + terminal +
                    "] " + method + (useParam ? " [ " + param + "]" : "") + " is not valid. " +
                    "See https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#1.7");
        }
    }

    private void verifyOnSubscribedAndNoTerminal(String method, @Nullable Object param, boolean useParam) {
        verifyNoTerminal(method, param, useParam);
        if (subscription == null) {
            throw new IllegalStateException("onSubscribe must be called before any other signals. " +
                    "https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#1.9");
        }
    }

    static final class VerifyThreadAwaitEvent extends VerifyThreadEvent {
        private final Duration duration;

        VerifyThreadAwaitEvent(Duration duration) {
            this.duration = requireNonNull(duration);
        }

        Duration duration() {
            return duration;
        }

        @Override
        String description() {
            return "thenAwait(" + duration + ")";
        }
    }

    static final class VerifyThreadRunEvent extends VerifyThreadEvent {
        private final Runnable runnable;

        VerifyThreadRunEvent(Runnable runnable) {
            this.runnable = requireNonNull(runnable);
        }

        void run() {
            runnable.run();
        }

        @Override
        String description() {
            return "then(" + runnable + ")";
        }
    }

    abstract static class VerifyThreadEvent extends PublisherEvent {
    }

    static final class OnNextIgnoreEvent extends PublisherEvent {
        private final long ignoreCount;

        OnNextIgnoreEvent(long n) {
            if (n <= 0) {
                throw new IllegalArgumentException("n: " + n + " (expected >0)");
            }
            this.ignoreCount = n;
        }

        long ignoreCount() {
            return ignoreCount;
        }

        @Override
        String description() {
            return "expectNextCount(" + ignoreCount + ")";
        }
    }

    static final class OnNextIterableEvent<T> extends PublisherEvent {
        private final Iterable<? extends T> iterable;

        OnNextIterableEvent(Iterable<? extends T> iterable) {
            this.iterable = requireNonNull(iterable);
        }

        String description() {
            return "expectNext(" + iterable + ")";
        }
    }

    static class NoSignalForDurationEvent extends PublisherEvent {
        private final Duration duration;

        NoSignalForDurationEvent(Duration duration) {
            this.duration = requireNonNull(duration);
        }

        Duration duration() {
            return duration;
        }

        @Override
        String description() {
            return "expectNoSignals(" + duration + ")";
        }
    }

    abstract static class OnNextEvent<T> extends PublisherEvent {
        abstract void onNext(@Nullable T next);
    }

    static final class OnNextAggregateEvent<T> extends PublisherEvent {
        private final int maxOnNext;
        private final int minOnNext;
        private final Consumer<? super Collection<? extends T>> signalsConsumer;

        OnNextAggregateEvent(int minOnNext, int maxOnNext, Consumer<? super Collection<? extends T>> signalsConsumer) {
            if (minOnNext <= 0) { // at least 1 element is required
                throw new IllegalArgumentException("minOnNext " + minOnNext + " (expected >0)");
            }
            if (maxOnNext < minOnNext) {
                throw new IllegalArgumentException("maxOnNext: " + maxOnNext + " < minOnNext" + minOnNext);
            }
            this.signalsConsumer = requireNonNull(signalsConsumer);
            this.maxOnNext = maxOnNext;
            this.minOnNext = minOnNext;
        }

        int maxOnNext() {
            return maxOnNext;
        }

        int minOnNext() {
            return minOnNext;
        }

        @Override
        String description() {
            return "expectNext(" + minOnNext + "," + maxOnNext + "," + signalsConsumer + ")";
        }
    }

    abstract static class OnSubscriptionEvent extends PublisherEvent {
        abstract void subscription(Subscription subscription);
    }

    static final class OnTerminalErrorClassChecker implements Consumer<Throwable> {
        private final Class<? extends Throwable> errorClass;

        OnTerminalErrorClassChecker(Class<? extends Throwable> errorClass) {
            this.errorClass = requireNonNull(errorClass);
        }

        @Override
        public void accept(Throwable t) {
            if (!errorClass.isInstance(t)) {
                throw new AssertionError("expected class: " + errorClass + " actual...", t);
            }
        }

        @Override
        public String toString() {
            return "==" + errorClass.getName();
        }
    }

    static final class OnTerminalErrorPredicate implements Consumer<Throwable> {
        private final Predicate<Throwable> errorPredicate;

        OnTerminalErrorPredicate(Predicate<Throwable> errorPredicate) {
            this.errorPredicate = requireNonNull(errorPredicate);
        }

        @Override
        public void accept(Throwable t) {
            if (!errorPredicate.test(t)) {
                throw new AssertionError("errorPredicate failed", t);
            }
        }

        @Override
        public String toString() {
            return errorPredicate.toString();
        }
    }

    static final class OnTerminalErrorEvent extends OnTerminalEvent {
        private final Consumer<Throwable> errorConsumer;

        OnTerminalErrorEvent(Consumer<Throwable> errorConsumer) {
            this.errorConsumer = requireNonNull(errorConsumer);
        }

        @Override
        void onError(Throwable cause) {
            errorConsumer.accept(cause);
        }

        @Override
        void onComplete() {
            throw new AssertionError("expected onError, actual onComplete");
        }

        @Override
        String description() {
            return "expectError(" + errorConsumer + ")";
        }
    }

    static final class OnTerminalCompleteEvent extends OnTerminalEvent {
        @Override
        void onError(Throwable cause) {
            throw new AssertionError("expected onComplete, actual onError", cause);
        }

        @Override
        void onComplete() {
        }

        @Override
        String description() {
            return "expectComplete()";
        }
    }

    abstract static class OnTerminalEvent extends PublisherEvent {
        abstract void onError(Throwable cause);

        abstract void onComplete();
    }

    abstract static class SubscriptionEvent extends PublisherEvent {
        abstract void subscription(Subscription subscription);
    }
}
