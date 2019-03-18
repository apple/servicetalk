/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.internal;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.SingleSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Consumer;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.internal.EmptySubscription.EMPTY_SUBSCRIPTION;

/**
 * A set of utilities for common {@link Subscriber} tasks.
 */
public final class SubscriberUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriberUtils.class);
    public static final int SUBSCRIBER_STATE_IDLE = 0;
    public static final int SUBSCRIBER_STATE_ON_NEXT = 1;
    public static final int SUBSCRIBER_STATE_TERMINATED = 2;
    public static final Object NULL_TOKEN = new Object();

    private SubscriberUtils() {
        // No instances.
    }

    /**
     * There are some scenarios where {@link Subscriber} can be terminated (invoke {@link Subscriber#onError(Throwable)}
     * or {@link Subscriber#onComplete()}) from different places concurrently but it is guaranteed that
     * {@link Subscriber#onNext(Object)} would not be invoked concurrently with itself. In such cases, using this method
     * provides guarantees that we do not violate ReactiveStreams specs w.r.t concurrent invocation of a
     * {@link Subscriber}.
     * <p>
     * <b>It is assumed that the {@link Subscriber} here is terminated using
     * {@link #checkTerminationValidWithConcurrentOnNextCheck(Object, Object, AtomicIntegerFieldUpdater,
     * AtomicReferenceFieldUpdater, Object)}.</b>
     *
     * @param subscriber {@link Subscriber} to deliver {@code next} to via {@link Subscriber#onNext(Object)}.
     * @param next The data to deliver via {@link Subscriber#onNext(Object)}.
     * @param terminator If there was a concurrent termination, {@link Consumer} to do the terminal action.
     * @param subscriberStateUpdater An {@link AtomicIntegerFieldUpdater} for updating {@link Subscriber} state.
     * <em>Assumed that this state is not updated from anywhere else but here and in
     * {@link #checkTerminationValidWithConcurrentOnNextCheck(Object, Object, AtomicIntegerFieldUpdater,
     * AtomicReferenceFieldUpdater, Object)}.</em>
     * @param terminalNotificationUpdater An {@link AtomicReferenceFieldUpdater} to store the terminal state.
     * @param owner Owner of the atomic fields passed here.
     * @param <T> The data type to deliver to {@code subscriber}.
     * @param <TERM> Type of terminal notification.
     * @param <R> Type of the owner of the atomic fields.
     */
    public static <T, TERM, R> void sendOnNextWithConcurrentTerminationCheck(
            Subscriber<? super T> subscriber, @Nullable T next, Consumer<TERM> terminator,
            AtomicIntegerFieldUpdater<R> subscriberStateUpdater,
            AtomicReferenceFieldUpdater<R, TERM> terminalNotificationUpdater, R owner) {
        boolean acquiredSubscriberLock = subscriberStateUpdater.compareAndSet(
                owner, SUBSCRIBER_STATE_IDLE, SUBSCRIBER_STATE_ON_NEXT);
        // Allow reentry because we don't want to drop data.
        if (!acquiredSubscriberLock && subscriberStateUpdater.get(owner) != SUBSCRIBER_STATE_ON_NEXT) {
            // The only possible state is TERMINATED. We don't have to worry about concurrency for Subscriber#onNext.
            return;
        }
        try {
            subscriber.onNext(next);
        } finally {
            sendOnNextWithConcurrentTerminationCheckFinally(acquiredSubscriberLock, terminator,
                    subscriberStateUpdater, terminalNotificationUpdater, owner);
        }
    }

    /**
     * There are some scenarios where {@link Subscriber} can be terminated (invoke {@link Subscriber#onError(Throwable)}
     * or {@link Subscriber#onComplete()}) from different places concurrently but it is guaranteed that
     * {@link Subscriber#onNext(Object)} would not be invoked concurrently with itself. In such cases, using this method
     * provides guarantees that we do not violate ReactiveStreams specs w.r.t concurrent invocation of a
     * {@link Subscriber}.
     * <p>
     * <b>It is assumed that the {@link Subscriber} here is terminated using
     * {@link #checkTerminationValidWithConcurrentOnNextCheck(Object, Object, AtomicIntegerFieldUpdater,
     * AtomicReferenceFieldUpdater, Object)}.</b>
     *
     * @param onNextInvoker {@link Runnable} that should invoke {@link Subscriber#onNext(Object)}.
     * @param terminator If there was a concurrent termination, {@link Consumer} to do the terminal action.
     * @param subscriberStateUpdater An {@link AtomicIntegerFieldUpdater} for updating {@link Subscriber} state.
     * <em>Assumed that this state is not updated from anywhere else but here and in
     * {@link #checkTerminationValidWithConcurrentOnNextCheck(Object, Object, AtomicIntegerFieldUpdater,
     * AtomicReferenceFieldUpdater, Object)}.</em>
     * @param terminalNotificationUpdater An {@link AtomicReferenceFieldUpdater} to store the terminal state.
     * @param owner Owner of the atomic fields passed here.
     * @param <TERM> Type of terminal notification.
     * @param <R> Type of the owner of the atomic fields.
     */
    public static <TERM, R> void sendOnNextWithConcurrentTerminationCheck(Runnable onNextInvoker,
                             Consumer<TERM> terminator, AtomicIntegerFieldUpdater<R> subscriberStateUpdater,
                             AtomicReferenceFieldUpdater<R, TERM> terminalNotificationUpdater, R owner) {
        boolean acquiredSubscriberLock = subscriberStateUpdater.compareAndSet(
                owner, SUBSCRIBER_STATE_IDLE, SUBSCRIBER_STATE_ON_NEXT);
        // Allow reentry because we don't want to drop data.
        if (!acquiredSubscriberLock && subscriberStateUpdater.get(owner) != SUBSCRIBER_STATE_ON_NEXT) {
            // The only possible state is TERMINATED. We don't have to worry about concurrency for Subscriber#onNext.
            return;
        }
        try {
            onNextInvoker.run();
        } finally {
            sendOnNextWithConcurrentTerminationCheckFinally(acquiredSubscriberLock, terminator,
                    subscriberStateUpdater, terminalNotificationUpdater, owner);
        }
    }

    private static <TERM, R> void sendOnNextWithConcurrentTerminationCheckFinally(boolean acquiredSubscriberLock,
                                Consumer<TERM> terminator, AtomicIntegerFieldUpdater<R> subscriberStateUpdater,
                                AtomicReferenceFieldUpdater<R, TERM> terminalNotificationUpdater, R owner) {
        if (acquiredSubscriberLock) {
            TERM terminalNotification = terminalNotificationUpdater.get(owner);
            if (terminalNotification == null) {
                subscriberStateUpdater.set(owner, SUBSCRIBER_STATE_IDLE);
                terminalNotification = terminalNotificationUpdater.get(owner);
                if (terminalNotification != null && subscriberStateUpdater.compareAndSet(
                        owner, SUBSCRIBER_STATE_IDLE, SUBSCRIBER_STATE_TERMINATED)) {
                    terminator.accept(terminalNotification);
                }
            } else {
                subscriberStateUpdater.set(owner, SUBSCRIBER_STATE_TERMINATED);
                terminator.accept(terminalNotification);
            }
        }
    }

    /**
     * There are some scenarios where {@link Subscriber} can be terminated (invoke {@link Subscriber#onError(Throwable)}
     * or {@link Subscriber#onComplete()} from different places concurrently but it is guaranteed that
     * {@link Subscriber#onNext(Object)} would not be invoked concurrently with itself.
     * In such cases, using this method provides guarantees that we do not violate ReactiveStreams specs w.r.t
     * concurrent invocation of a {@link Subscriber}.
     * <p>
     * <b>It is assumed that the {@link Subscriber#onNext(Object)} is invoked using one of the following:</b>
     * <ul>
     *     <li>{@link #sendOnNextWithConcurrentTerminationCheck(Runnable, Consumer, AtomicIntegerFieldUpdater,
     *     AtomicReferenceFieldUpdater, Object)}.</li>
     *     <li>{@link #sendOnNextWithConcurrentTerminationCheck(PublisherSource.Subscriber, Object, Consumer,
     *     AtomicIntegerFieldUpdater, AtomicReferenceFieldUpdater, Object)}</li>
     * </ul>
     *
     * @param expect Expected value of the {@code terminalNotificationUpdater}.
     * @param terminalNotification {@link TerminalNotification} representing the terminal event.
     * @param subscriberStateUpdater An {@link AtomicIntegerFieldUpdater} for updating {@link Subscriber} state.
     * <em>Assumed that this state is not updated from anywhere else but here and in
     * {@link #sendOnNextWithConcurrentTerminationCheck(Runnable, Consumer, AtomicIntegerFieldUpdater,
     * AtomicReferenceFieldUpdater, Object)}.</em>
     * @param terminalNotificationUpdater An {@link AtomicReferenceFieldUpdater} to store the terminal state.
     * @param owner Owner of the atomic fields passed here.
     * @param <TERM> Type of terminal notification.
     * @param <R> Type of the owner of the atomic fields.
     * @return {@code true} if the termination of {@link Subscriber} is valid i.e. it has not already been terminated or
     * an {@link Subscriber#onNext(Object)} isn't in progress.
     */
    public static <TERM, R> boolean checkTerminationValidWithConcurrentOnNextCheck(@Nullable TERM expect,
                                       TERM terminalNotification, AtomicIntegerFieldUpdater<R> subscriberStateUpdater,
                                       AtomicReferenceFieldUpdater<R, TERM> terminalNotificationUpdater, R owner) {
        return terminalNotificationUpdater.compareAndSet(owner, expect, terminalNotification)
                && subscriberStateUpdater.compareAndSet(owner, SUBSCRIBER_STATE_IDLE, SUBSCRIBER_STATE_TERMINATED);
    }

    /**
     * Checks for an already existing {@link Subscription} and if one is given calls {@link Subscription#cancel()} on
     * {@code next} and returns {@code false}.
     *
     * @param existing the existing {@link Subscription} or {@code null} if none exists.
     * @param next the next {@link Subscription} to use.
     * @return {@code true} if no {@link Subscription} exists, {@code false} otherwise.
     */
    public static boolean checkDuplicateSubscription(@Nullable Subscription existing, Subscription next) {
        if (existing != null) {
            next.cancel();
            return false;
        }
        return true;
    }

    /**
     * Returns {@code false} if the requested amount of elements {@code n} is not-positive, {@code true} otherwise.
     *
     * @param n the number of elements to request.
     * @return {@code false} if the requested amount of elements {@code n} is not-positive, {@code true} otherwise.
     */
    public static boolean isRequestNValid(long n) {
        return n > 0;
    }

    /**
     * Create a new exception for an invalid amount of {@link Subscription#request(long)} according to
     * <a href="https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.1/README.md#3.9">Reactive Streams,
     * Rule 3.9</a>.
     * @param n the invalid request count.
     * @return The exception which clarifies the invalid behavior.
     */
    public static IllegalArgumentException newExceptionForInvalidRequestN(long n) {
        return new IllegalArgumentException("Rule 3.9 states non-positive request signals are illegal, but got: " + n);
    }

    /**
     * Attempts to increment {@code sourceRequestedUpdater} in order to make it the same as {@code requestNUpdater}
     * while not exceeding the {@code limit}.
     * @param requestNUpdater The total number which has been requested (typically from
     * {@link Subscription#request(long)}).
     * @param sourceRequestedUpdater The total number which has actually been passed to
     * {@link Subscription#request(long)}. This outstanding count
     * {@code sourceRequestedUpdater() - emittedUpdater.get()} should never exceed {@code limit}.
     * @param emittedUpdater The amount of data that has been emitted/delivered by the source.
     * @param limit The maximum outstanding demand from the source at any given time.
     * @param owner The object which all atomic updater parameters are associated with.
     * @param <T> The type of object which owns the atomic updater parameters.
     * @return The amount that {@code sourceRequestedUpdater} was increased by. This value is typically used to call
     * {@link Subscription#request(long)}.
     */
    public static <T> int calculateSourceRequested(final AtomicLongFieldUpdater<T> requestNUpdater,
                                                   final AtomicLongFieldUpdater<T> sourceRequestedUpdater,
                                                   final AtomicLongFieldUpdater<T> emittedUpdater,
                                                   final int limit,
                                                   final T owner) {
        for (;;) {
            final long sourceRequested = sourceRequestedUpdater.get(owner);
            final long requested = requestNUpdater.get(owner);
            if (requested == sourceRequested) {
                return 0;
            }

            // emitted ...[outstanding]... sourceRequested ...[delta]... requested
            final long outstanding = sourceRequested - emittedUpdater.get(owner);
            final long delta = requested - sourceRequested;
            final int toRequest = (int) (limit - outstanding >= delta ? delta : limit - outstanding);
            if (sourceRequestedUpdater.compareAndSet(owner, sourceRequested, sourceRequested + toRequest)) {
                return toRequest;
            }
        }
    }

    /**
     * There are some scenarios where a completion {@link TerminalNotification} can be overridden with an error if
     * errors are produced asynchronously.
     * <p>
     * This method helps set {@link TerminalNotification} atomically providing such an override.
     *
     * @param toSet {@link TerminalNotification} to set.
     * @param overrideComplete Whether exisiting {@link TerminalNotification#complete()} should be overridden with the
     * {@code toSet}.
     * @param terminalNotificationUpdater {@link AtomicReferenceFieldUpdater} to access the current
     * {@link TerminalNotification}.
     * @param flagOwner instance of {@link R} that owns the current {@link TerminalNotification} field referenced by
     * {@code terminalNotificationUpdater}.
     * @param <R> Type of {@code flagOwner}.
     * @return {@code true} if {@code toSet} is updated as the current {@link TerminalNotification}.
     */
    public static <R> boolean trySetTerminal(TerminalNotification toSet, boolean overrideComplete,
                     AtomicReferenceFieldUpdater<R, TerminalNotification> terminalNotificationUpdater, R flagOwner) {
        for (;;) {
            TerminalNotification curr = terminalNotificationUpdater.get(flagOwner);
            if (curr != null && !overrideComplete) {
                // Once terminated, terminalNotification will never be set back to null.
                return false;
            } else if (curr == null && terminalNotificationUpdater.compareAndSet(flagOwner, null, toSet)) {
                return true;
            } else if (curr != null && curr.cause() == null) {
                // Override complete
                if (terminalNotificationUpdater.compareAndSet(flagOwner, curr, toSet)) {
                    return true;
                }
            } else {
                return false;
            }
        }
    }

    /**
     * Deliver a terminal error to a {@link Subscriber} that has not yet had
     * {@link Subscriber#onSubscribe(PublisherSource.Subscription)} called.
     * @param subscriber The {@link Subscriber} to terminate.
     * @param cause The terminal event.
     * @param <T> The type of {@link Subscriber}.
     */
    public static <T> void deliverTerminalFromSource(Subscriber<T> subscriber, Throwable cause) {
        try {
            subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
        } catch (Throwable t) {
            LOGGER.debug("Ignoring exception from onSubscribe of Subscriber {}.", subscriber, t);
            return;
        }
        try {
            subscriber.onError(cause);
        } catch (Throwable t) {
            LOGGER.debug("Ignoring exception from onError of Subscriber {}.", subscriber, t);
        }
    }

    /**
     * Deliver a terminal complete to a {@link Subscriber} that has not yet had
     * {@link Subscriber#onSubscribe(PublisherSource.Subscription)} called.
     * @param subscriber The {@link Subscriber} to terminate.
     * @param <T> The type of {@link Subscriber}.
     */
    public static <T> void deliverTerminalFromSource(Subscriber<T> subscriber) {
        try {
            subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
        } catch (Throwable t) {
            LOGGER.debug("Ignoring exception from onSubscribe of Subscriber {}.", subscriber, t);
            return;
        }
        try {
            subscriber.onComplete();
        } catch (Throwable t) {
            LOGGER.debug("Ignoring exception from onComplete of Subscriber {}.", subscriber, t);
        }
    }

    /**
     * Deliver a terminal error to a {@link SingleSource.Subscriber} that has not yet had
     * {@link SingleSource.Subscriber#onSubscribe(Cancellable)} called.
     * @param subscriber The {@link SingleSource.Subscriber} to terminate.
     * @param cause The terminal event.
     * @param <T> The type of {@link SingleSource.Subscriber}.
     */
    public static <T> void deliverTerminalFromSource(SingleSource.Subscriber<T> subscriber, Throwable cause) {
        try {
            subscriber.onSubscribe(IGNORE_CANCEL);
        } catch (Throwable t) {
            LOGGER.debug("Ignoring exception from onSubscribe of Subscriber {}.", subscriber, t);
            return;
        }
        try {
            subscriber.onError(cause);
        } catch (Throwable t) {
            LOGGER.debug("Ignoring exception from onError of Subscriber {}.", subscriber, t);
        }
    }

    /**
     * Deliver a terminal error to a {@link CompletableSource.Subscriber} that has not yet had
     * {@link CompletableSource.Subscriber#onSubscribe(Cancellable)} called.
     * @param subscriber The {@link CompletableSource.Subscriber} to terminate.
     * @param cause The terminal event.
     */
    public static void deliverTerminalFromSource(CompletableSource.Subscriber subscriber, Throwable cause) {
        try {
            subscriber.onSubscribe(IGNORE_CANCEL);
        } catch (Throwable t) {
            LOGGER.debug("Ignoring exception from onSubscribe of Subscriber {}.", subscriber, t);
            return;
        }
        try {
            subscriber.onError(cause);
        } catch (Throwable t) {
            LOGGER.debug("Ignoring exception from onError of Subscriber {}.", subscriber, t);
        }
    }

    /**
     * Handle the case when a call to {@link Subscriber#onSubscribe(PublisherSource.Subscription)} throws from a source.
     * @param subscriber The {@link Subscriber} that threw an exception from
     * {@link Subscriber#onSubscribe(PublisherSource.Subscription)}.
     * @param cause The exception thrown by {@code subscriber}.
     * @param <T> The type of {@link Subscriber}.
     */
    public static <T> void handleExceptionFromOnSubscribe(Subscriber<T> subscriber, Throwable cause) {
        LOGGER.warn("Ignoring exception from onSubscribe of Subscriber {}.", subscriber, cause);
    }

    /**
     * Handle the case when a call to {@link SingleSource.Subscriber#onSubscribe(Cancellable)} throws from a source.
     * @param subscriber The {@link SingleSource.Subscriber} that threw an exception from
     * {@link SingleSource.Subscriber#onSubscribe(Cancellable)}.
     * @param cause The exception thrown by {@code subscriber}.
     * @param <T> The type of {@link SingleSource.Subscriber}.
     */
    public static <T> void handleExceptionFromOnSubscribe(SingleSource.Subscriber<T> subscriber, Throwable cause) {
        LOGGER.warn("Ignoring exception from onSubscribe of Subscriber {}.", subscriber, cause);
    }

    /**
     * Invokes {@link SingleSource.Subscriber#onError(Throwable)} ignoring an occurred exception if any.
     * @param subscriber The {@link SingleSource.Subscriber} that may threw an exception from
     * {@link SingleSource.Subscriber#onError(Throwable)}.
     * @param cause The occurred {@link Throwable} for {@link SingleSource.Subscriber#onError(Throwable)}.
     * @param <T> The type of {@link SingleSource.Subscriber}.
     */
    public static <T> void safeOnError(SingleSource.Subscriber<T> subscriber, Throwable cause) {
        try {
            subscriber.onError(cause);
        } catch (Throwable t) {
            LOGGER.debug("Ignoring exception from onError of Subscriber {}.", subscriber, t);
        }
    }
}
