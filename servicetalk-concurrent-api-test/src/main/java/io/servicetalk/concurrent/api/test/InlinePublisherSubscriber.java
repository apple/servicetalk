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
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Processors.newPublisherProcessor;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static io.servicetalk.concurrent.internal.TerminalNotification.error;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

final class InlinePublisherSubscriber<T> implements Subscriber<T>, InlineVerifiableSubscriber {
    @SuppressWarnings("rawtypes")
    private static final AtomicLongFieldUpdater<InlinePublisherSubscriber> outstandingDemandUpdater =
            AtomicLongFieldUpdater.newUpdater(InlinePublisherSubscriber.class, "outstandingDemand");
    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<InlinePublisherSubscriber, PublisherEvent> eventRef =
            newUpdater(InlinePublisherSubscriber.class, PublisherEvent.class, "currEvent");
    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<InlinePublisherSubscriber, PublisherEvent> lastNoSignalsEventRef =
            newUpdater(InlinePublisherSubscriber.class, PublisherEvent.class, "lastNoSignalsEvent");
    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<InlinePublisherSubscriber, PublisherEvent>
            lastSubscriptionEventRef = newUpdater(InlinePublisherSubscriber.class, PublisherEvent.class,
            "lastSubscriptionEvent");
    private final NormalizedTimeSource timeSource;
    private final Queue<PublisherEvent> events;
    private final Processor<VerifyThreadEvent, VerifyThreadEvent> verifyThreadProcessor =
            newPublisherProcessor(Integer.MAX_VALUE);
    private final String exceptionClassNamePrefix;
    @Nullable
    private Subscription subscription;
    @Nullable
    private Long noSignalsUntil;
    @Nullable
    private TerminalNotification terminal;
    @Nullable
    private SubscriptionEventAggregate subscriptionAggregate;
    private volatile long outstandingDemand;
    @Nullable
    private volatile PublisherEvent currEvent;
    @Nullable
    private volatile PublisherEvent lastNoSignalsEvent;
    @Nullable
    private volatile PublisherEvent lastSubscriptionEvent;

    InlinePublisherSubscriber(long initialDemand, NormalizedTimeSource timeSource, Queue<PublisherEvent> events,
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

        PublisherEvent event = eventRef.get(this);
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

        PublisherEvent event = eventRef.get(this);
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
            if (castEvent.add(t) == castEvent.maxOnNext()) {
                try {
                    castEvent.doVerify();
                    event = pollNextEvent();
                } catch (Throwable cause) {
                    event = failVerification(cause, event);
                }
            }
        } else if (event instanceof OnNextIterableEvent) {
            @SuppressWarnings("unchecked")
            final OnNextIterableEvent<T> castEvent = ((OnNextIterableEvent<T>) event);
            castEvent.onNext(t);
            if (castEvent.hasNext()) {
                assert subscription != null;
                subscription.request(1);
            } else {
                event = pollNextEvent();
            }
        } else if (event instanceof OnNextIgnoreEvent) {
            if (((OnNextIgnoreEvent) event).incOnNext()) {
                event = pollNextEvent();
            }
        } else if (event != null) {
            failVerification(new IllegalStateException("expected " + event.description() + ", actual onNext: " +
                    t), event);
        }
    }

    @Override
    public void onError(Throwable t) {
        verifyOnSubscribedAndNoTerminal("onError", t, true);
        PublisherEvent event = eventRef.get(this);
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
        PublisherEvent event = eventRef.get(this);
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
        PublisherEvent event = lastNoSignalsEventRef.getAndSet(this, null);
        PublisherEvent event2 = lastSubscriptionEventRef.getAndSet(this, null);
        PublisherEvent event3 = eventRef.getAndSet(this, null);
        return event == null ? event2 == null ? event3 : event2 : event;
    }

    @Nullable
    private PublisherEvent checkOnNextEventsFromTerminal(String signalName, @Nullable PublisherEvent event) {
        if (event instanceof OnNextAggregateEvent) {
            @SuppressWarnings("unchecked")
            final OnNextAggregateEvent<T> castEvent = ((OnNextAggregateEvent<T>) event);
            if (castEvent.size() >= castEvent.minOnNext()) {
                try {
                    castEvent.doVerify();
                    event = pollNextEvent();
                } catch (Throwable cause) {
                    event = failVerification(cause, event);
                }
            } else {
                event = failVerification(new IllegalStateException("expected " + event.description() + ", actual " +
                        signalName), event);
            }
        } else if (event instanceof OnNextIgnoreEvent) {
            event = pollNextEvent();
        }
        return event;
    }

    @Nullable
    private PublisherEvent pollNextEvent() {
        PublisherEvent event;
        Queue<VerifyThreadEvent> verifyThreadEvents = null;
        do {
            event = events.poll();
            eventRef.set(this, event);
            if (event == null) {
                // try to execute any remaining events before completing
                processSubscriptionAggregate();
                processVerifyThreadAggregate(verifyThreadEvents);
                verifyThreadProcessor.onComplete();
                break;
            } else if (event instanceof NoSignalForDurationEvent) {
                lastNoSignalsEventRef.set(this, event);
                Duration duration = ((NoSignalForDurationEvent) event).duration();
                noSignalsUntil = noSignalsUntil == null ?
                        timeSource.timeStampFromNow(duration) :
                        timeSource.timeStampFrom(noSignalsUntil, duration);
            } else if (event instanceof SubscriptionEvent) {
                lastSubscriptionEventRef.set(this, event);
                if (subscriptionAggregate == null) {
                    subscriptionAggregate = new SubscriptionEventAggregate();
                }
                subscriptionAggregate.add(((SubscriptionEvent) event));
            } else if (event instanceof VerifyThreadEvent) {
                if (verifyThreadEvents == null) {
                    verifyThreadEvents = new ArrayDeque<>();
                }
                verifyThreadEvents.add((VerifyThreadEvent) event);
            } else {
                if (subscription != null) {
                    requestIfNecessary(event);
                }
                break;
            }
        } while (true);

        processVerifyThreadAggregate(verifyThreadEvents);

        return event;
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

    private void processSubscriptionAggregate() {
        if (subscription != null && subscriptionAggregate != null) {
            try {
                subscriptionAggregate.subscription(subscription);
            } catch (Throwable cause) {
                failVerification(cause, subscriptionAggregate.currentEvent());
            }
        }
    }

    @Nullable
    private PublisherEvent checkNoSignalsExpectation(String signalName, @Nullable Object signal,
                                                     @Nullable PublisherEvent event) {
        if (subscriptionAggregate != null && subscriptionAggregate.currentEvent() == null) {
            lastSubscriptionEventRef.set(this, null);
        }
        while (noSignalsUntil != null) {
            if (timeSource.isExpired(noSignalsUntil)) {
                event = failVerification(new IllegalStateException("expectNoSignals expired by " +
                        timeSource.duration(noSignalsUntil).negated() + ". " + signalName + "(" + signal + ")"),
                        lastSubscriptionEventRef.getAndSet(this, null));
            } else {
                lastSubscriptionEventRef.set(this, null);
            }
            noSignalsUntil = null;
        }
        return event;
    }

    @Nullable
    private PublisherEvent failVerification(Throwable cause, @Nullable PublisherEvent event) {
        if (event != null) {
            verifyThreadProcessor.onError(event.newException(cause.getMessage(), cause, exceptionClassNamePrefix));
            eventRef.set(this, null);
        } else {
            event = lastNoSignalsEventRef.getAndSet(this, null);
            if (event != null) {
                verifyThreadProcessor.onError(event.newException(cause.getMessage(), cause,
                        exceptionClassNamePrefix));
            } else {
                verifyThreadProcessor.onError(new AssertionError("unexpected failure!", cause));
            }
        }
        events.clear();
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

    private static final class SubscriptionEventAggregate extends SubscriptionEvent {
        @Nullable
        private SubscriptionEvent currentEvent;
        private final Queue<SubscriptionEvent> subscriptionEvents;

        SubscriptionEventAggregate() {
            subscriptionEvents = new ArrayDeque<>();
        }

        @Override
        String description() {
            return currentEvent == null ? "SubscriptionEventAggregate[empty]" : currentEvent.description();
        }

        void add(SubscriptionEvent e) {
            subscriptionEvents.add(e);
        }

        @Nullable
        SubscriptionEvent currentEvent() {
            return currentEvent;
        }

        @Override
        void subscription(Subscription subscription) {
            while ((currentEvent = subscriptionEvents.poll()) != null) {
                currentEvent.subscription(subscription);
            }
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
        private long onNextCount;

        OnNextIgnoreEvent(long n) {
            if (n <= 0) {
                throw new IllegalArgumentException("n: " + n + " (expected >0)");
            }
            this.ignoreCount = n;
        }

        long ignoreCount() {
            return ignoreCount;
        }

        boolean incOnNext() {
            return ++onNextCount == ignoreCount;
        }

        @Override
        String description() {
            return "expectNextCount(" + ignoreCount + ") onNextCount: " + onNextCount;
        }
    }

    static final class OnNextIterableEvent<T> extends PublisherEvent {
        private long i;
        private final Iterator<? extends T> iterator;

        OnNextIterableEvent(Iterator<? extends T> iterator) {
            this.iterator = requireNonNull(iterator);
        }

        void onNext(@Nullable T next) {
            final T iterNext = iterator.next();
            if (notEqualsOnNext(iterNext, next)) {
                throw new AssertionError("expectNext(Iterable<T>) failed at index " + i + ". expected: " + iterNext +
                        " actual: " + next);
            }
            ++i;
        }

        boolean hasNext() {
            return iterator.hasNext();
        }

        String description() {
            return "expectNext(" + iterator + ") index: " + i;
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
        @Nullable
        private List<T> nexts;

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

        int add(@Nullable T next) {
            if (nexts == null) {
                nexts = new ArrayList<>();
            }
            nexts.add(next);
            return nexts.size();
        }

        void doVerify() {
            assert nexts != null;
            signalsConsumer.accept(nexts);
        }

        int size() {
            return nexts == null ? 0 : nexts.size();
        }

        @Override
        String description() {
            return "expectNext(" + minOnNext + "," + maxOnNext + "," + signalsConsumer + ") nexts: " +
                    (nexts == null ? null : (nexts.size() + " " + nexts));
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
