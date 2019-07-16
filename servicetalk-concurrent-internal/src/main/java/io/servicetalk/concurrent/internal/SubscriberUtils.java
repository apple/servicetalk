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

import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.internal.EmptySubscription.EMPTY_SUBSCRIPTION;

/**
 * A set of utilities for common {@link Subscriber} tasks.
 */
public final class SubscriberUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriberUtils.class);

    private SubscriberUtils() {
        // No instances.
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
            handleExceptionFromOnSubscribe(subscriber, t);
            return;
        }
        try {
            subscriber.onError(cause);
        } catch (Throwable t) {
            LOGGER.info("Ignoring exception from onError of Subscriber {}.", subscriber, t);
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
            handleExceptionFromOnSubscribe(subscriber, t);
            return;
        }
        try {
            subscriber.onComplete();
        } catch (Throwable t) {
            LOGGER.info("Ignoring exception from onComplete of Subscriber {}.", subscriber, t);
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
            handleExceptionFromOnSubscribe(subscriber, t);
            return;
        }
        try {
            subscriber.onError(cause);
        } catch (Throwable t) {
            LOGGER.info("Ignoring exception from onError of Subscriber {}.", subscriber, t);
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
            handleExceptionFromOnSubscribe(subscriber, t);
            return;
        }
        try {
            subscriber.onError(cause);
        } catch (Throwable t) {
            LOGGER.info("Ignoring exception from onError of Subscriber {}.", subscriber, t);
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
        // The Subscriber violated the spec by throwing from onSubscribe [1]. However we make a best effort to
        // complete the async control flow by calling onError even though the Subscriber state is unknown. The "best
        // effort" may end up violating RFC single terminal signal delivery [2] and concurrency [3] rules, but we are
        // in an invalid state.
        // [1] https://github.com/reactive-streams/reactive-streams-jvm#1.9
        // [2] https://github.com/reactive-streams/reactive-streams-jvm#1.7
        // [3] https://github.com/reactive-streams/reactive-streams-jvm#1.3
        subscriber.onError(cause);
        LOGGER.warn("Unexpected exception from onSubscribe of Subscriber {}.", subscriber, cause);
    }

    /**
     * Handle the case when a call to {@link SingleSource.Subscriber#onSubscribe(Cancellable)} throws from a source.
     * @param subscriber The {@link SingleSource.Subscriber} that threw an exception from
     * {@link SingleSource.Subscriber#onSubscribe(Cancellable)}.
     * @param cause The exception thrown by {@code subscriber}.
     * @param <T> The type of {@link SingleSource.Subscriber}.
     */
    public static <T> void handleExceptionFromOnSubscribe(SingleSource.Subscriber<T> subscriber, Throwable cause) {
        // The Subscriber violated the spec by throwing from onSubscribe [1]. However we make a best effort to
        // complete the async control flow by calling onError even though the Subscriber state is unknown. The "best
        // effort" may end up violating RFC single terminal signal delivery [2] and concurrency [3] rules, but we are
        // in an invalid state.
        // [1] https://github.com/reactive-streams/reactive-streams-jvm#1.9
        // [2] https://github.com/reactive-streams/reactive-streams-jvm#1.7
        // [3] https://github.com/reactive-streams/reactive-streams-jvm#1.3
        subscriber.onError(cause);
        LOGGER.warn("Unexpected exception from onSubscribe of Subscriber {}.", subscriber, cause);
    }

    /**
     * Handle the case when a call to {@link CompletableSource.Subscriber#onSubscribe(Cancellable)} throws from a
     * source.
     * @param subscriber The {@link CompletableSource.Subscriber} that threw an exception from
     * {@link CompletableSource.Subscriber#onSubscribe(Cancellable)}.
     * @param cause The exception thrown by {@code subscriber}.
     */
    public static void handleExceptionFromOnSubscribe(CompletableSource.Subscriber subscriber, Throwable cause) {
        // The Subscriber violated the spec by throwing from onSubscribe [1]. However we make a best effort to
        // complete the async control flow by calling onError even though the Subscriber state is unknown. The "best
        // effort" may end up violating RFC single terminal signal delivery [2] and concurrency [3] rules, but we are
        // in an invalid state.
        // [1] https://github.com/reactive-streams/reactive-streams-jvm#1.9
        // [2] https://github.com/reactive-streams/reactive-streams-jvm#1.7
        // [3] https://github.com/reactive-streams/reactive-streams-jvm#1.3
        subscriber.onError(cause);
        LOGGER.warn("Unexpected exception from onSubscribe of Subscriber {}.", subscriber, cause);
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
            LOGGER.info("Ignoring exception from onError of Subscriber {}.", subscriber, t);
        }
    }
}
