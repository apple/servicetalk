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

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.internal.EmptySubscriptions.EMPTY_SUBSCRIPTION;

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
        return n > 0L;
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
     * Deliver a terminal complete to a {@link Subscriber} that has not yet had
     * {@link PublisherSource.Subscriber#onSubscribe(PublisherSource.Subscription)} called.
     * @param subscriber The {@link PublisherSource.Subscriber} to terminate.
     * @param <T> The type of {@link PublisherSource.Subscriber}.
     */
    public static <T> void deliverCompleteFromSource(Subscriber<T> subscriber) {
        try {
            subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
        } catch (Throwable t) {
            handleExceptionFromOnSubscribe(subscriber, t);
            return;
        }
        safeOnComplete(subscriber);
    }

    /**
     * Invokes {@link SingleSource.Subscriber#onSuccess(Object)} ignoring an occurred exception if any.
     * @param subscriber The {@link SingleSource.Subscriber} that may throw an exception from
     * {@link SingleSource.Subscriber#onSuccess(Object)}.
     * @param value The value to pass to {@link SingleSource.Subscriber#onSuccess(Object)}.
     * @param <T> The type of {@link SingleSource.Subscriber}.
     */
    public static <T> void deliverSuccessFromSource(SingleSource.Subscriber<T> subscriber, @Nullable T value) {
        try {
            subscriber.onSubscribe(IGNORE_CANCEL);
        } catch (Throwable t) {
            handleExceptionFromOnSubscribe(subscriber, t);
            return;
        }
        safeOnSuccess(subscriber, value);
    }

    /**
     * Deliver a terminal complete to a {@link CompletableSource.Subscriber} that has not yet had
     * {@link CompletableSource.Subscriber#onSubscribe(Cancellable)} called.
     * @param subscriber The {@link CompletableSource.Subscriber} to terminate.
     */
    public static void deliverCompleteFromSource(CompletableSource.Subscriber subscriber) {
        try {
            subscriber.onSubscribe(IGNORE_CANCEL);
        } catch (Throwable t) {
            handleExceptionFromOnSubscribe(subscriber, t);
            return;
        }
        safeOnComplete(subscriber);
    }

    /**
     * Deliver a terminal error to a {@link Subscriber} that has not yet had
     * {@link PublisherSource.Subscriber#onSubscribe(PublisherSource.Subscription)} called.
     * @param subscriber The {@link PublisherSource.Subscriber} to terminate.
     * @param cause The terminal event.
     * @param <T> The type of {@link PublisherSource.Subscriber}.
     */
    public static <T> void deliverErrorFromSource(Subscriber<T> subscriber, Throwable cause) {
        try {
            subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
        } catch (Throwable t) {
            handleExceptionFromOnSubscribe(subscriber, t);
            return;
        }
        safeOnError(subscriber, cause);
    }

    /**
     * Deliver a terminal error to a {@link SingleSource.Subscriber} that has not yet had
     * {@link SingleSource.Subscriber#onSubscribe(Cancellable)} called.
     * @param subscriber The {@link SingleSource.Subscriber} to terminate.
     * @param cause The terminal event.
     * @param <T> The type of {@link SingleSource.Subscriber}.
     */
    public static <T> void deliverErrorFromSource(SingleSource.Subscriber<T> subscriber, Throwable cause) {
        try {
            subscriber.onSubscribe(IGNORE_CANCEL);
        } catch (Throwable t) {
            handleExceptionFromOnSubscribe(subscriber, t);
            return;
        }
        safeOnError(subscriber, cause);
    }

    /**
     * Deliver a terminal error to a {@link CompletableSource.Subscriber} that has not yet had
     * {@link CompletableSource.Subscriber#onSubscribe(Cancellable)} called.
     * @param subscriber The {@link CompletableSource.Subscriber} to terminate.
     * @param cause The terminal event.
     */
    public static void deliverErrorFromSource(CompletableSource.Subscriber subscriber, Throwable cause) {
        try {
            subscriber.onSubscribe(IGNORE_CANCEL);
        } catch (Throwable t) {
            handleExceptionFromOnSubscribe(subscriber, t);
            return;
        }
        safeOnError(subscriber, cause);
    }

    /**
     * Handle the case when a call to {@link PublisherSource.Subscriber#onSubscribe(PublisherSource.Subscription)}
     * throws from a source.
     * @param subscriber The {@link Subscriber} that threw an exception from
     * {@link PublisherSource.Subscriber#onSubscribe(PublisherSource.Subscription)}.
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
        safeOnError(subscriber, cause);
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
        safeOnError(subscriber, cause);
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
        safeOnError(subscriber, cause);
        LOGGER.warn("Unexpected exception from onSubscribe of Subscriber {}.", subscriber, cause);
    }

    /**
     * Invokes {@link CompletableSource.Subscriber#onError(Throwable)} ignoring an occurred exception if any.
     * @param subscriber The {@link CompletableSource.Subscriber} that may throw an exception from
     * {@link CompletableSource.Subscriber#onError(Throwable)}.
     * @param cause The occurred {@link Throwable} for {@link CompletableSource.Subscriber#onError(Throwable)}.
     */
    public static void safeOnError(CompletableSource.Subscriber subscriber, Throwable cause) {
        try {
            subscriber.onError(cause);
        } catch (Throwable t) {
            LOGGER.info("Ignoring exception from onError of Subscriber {}.", subscriber, t);
        }
    }

    /**
     * Invokes {@link SingleSource.Subscriber#onError(Throwable)} ignoring an occurred exception if any.
     * @param subscriber The {@link SingleSource.Subscriber} that may throw an exception from
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

    /**
     * Invokes {@link PublisherSource.Subscriber#onError(Throwable)} ignoring an occurred exception if any.
     * @param subscriber The {@link PublisherSource.Subscriber} that may throw an exception from
     * {@link PublisherSource.Subscriber#onError(Throwable)}.
     * @param cause The occurred {@link Throwable} for {@link PublisherSource.Subscriber#onError(Throwable)}.
     * @param <T> The type of {@link PublisherSource.Subscriber}.
     */
    public static <T> void safeOnError(PublisherSource.Subscriber<T> subscriber, Throwable cause) {
        try {
            subscriber.onError(cause);
        } catch (Throwable t) {
            LOGGER.info("Ignoring exception from onError of Subscriber {}.", subscriber, t);
        }
    }

    /**
     * Invokes {@link Subscriber#onComplete()} ignoring an occurred exception if any.
     * @param subscriber The {@link PublisherSource.Subscriber} that may throw an exception from
     * {@link Subscriber#onComplete()}.
     * @param <T> The type of {@link PublisherSource.Subscriber}.
     */
    public static <T> void safeOnComplete(PublisherSource.Subscriber<T> subscriber) {
        try {
            subscriber.onComplete();
        } catch (Throwable t) {
            LOGGER.info("Ignoring exception from onComplete of Subscriber {}.", subscriber, t);
        }
    }

    /**
     * Invokes {@link SingleSource.Subscriber#onSuccess(Object)} ignoring an occurred exception if any.
     * @param subscriber The {@link SingleSource.Subscriber} that may throw an exception from
     * {@link SingleSource.Subscriber#onSuccess(Object)}.
     * @param value The value to pass to {@link SingleSource.Subscriber#onSuccess(Object)}.
     * @param <T> The type of {@link SingleSource.Subscriber}.
     */
    public static <T> void safeOnSuccess(SingleSource.Subscriber<T> subscriber, @Nullable T value) {
        try {
            subscriber.onSuccess(value);
        } catch (Throwable t) {
            LOGGER.info("Ignoring exception from onSuccess of Subscriber {}.", subscriber, t);
        }
    }

    /**
     * Invokes {@link CompletableSource.Subscriber#onComplete()} ignoring an occurred exception if any.
     * @param subscriber The {@link CompletableSource.Subscriber} that may throw an exception from
     * {@link CompletableSource.Subscriber#onComplete()}.
     */
    public static void safeOnComplete(CompletableSource.Subscriber subscriber) {
        try {
            subscriber.onComplete();
        } catch (Throwable t) {
            LOGGER.info("Ignoring exception from onComplete of Subscriber {}.", subscriber, t);
        }
    }

    /**
     * Invokes {@link Cancellable#cancel()} ignoring any exceptions that are thrown.
     * @param cancellable The {@link Cancellable} to {@link Cancellable#cancel() cancel}.
     */
    public static void safeCancel(Cancellable cancellable) {
        try {
            cancellable.cancel();
        } catch (Throwable t) {
            LOGGER.info("Ignoring exception from cancel {}.", cancellable, t);
        }
    }

    /**
     * Log if the ReactiveStreams specification has been violated related to out of order
     * {@link PublisherSource.Subscriber#onSubscribe(PublisherSource.Subscription)} or duplicate terminal signals.
     * @param subscriber The {@link PublisherSource.Subscriber}.
     * @param <T> The type of {@link PublisherSource.Subscriber}.
     */
    public static <T> void logDuplicateTerminal(PublisherSource.Subscriber<T> subscriber) {
        logDuplicateTerminal0(subscriber, null);
    }

    /**
     * Log if the ReactiveStreams specification has been violated related to out of order
     * {@link PublisherSource.Subscriber#onSubscribe(PublisherSource.Subscription)} or duplicate terminal signals.
     * @param subscriber The {@link PublisherSource.Subscriber}.
     * @param cause The cause from {@link PublisherSource.Subscriber#onError(Throwable)}.
     * @param <T> The type of {@link PublisherSource.Subscriber}.
     */
    public static <T> void logDuplicateTerminal(PublisherSource.Subscriber<T> subscriber, Throwable cause) {
        logDuplicateTerminal0(subscriber, cause);
    }

    /**
     * Log if the ReactiveStreams specification has been violated related to out of order
     * {@link SingleSource.Subscriber#onSubscribe(Cancellable)} or duplicate terminal signals.
     * @param subscriber The {@link SingleSource.Subscriber}.
     * @param <T> The type of {@link SingleSource.Subscriber}.
     */
    public static <T> void logDuplicateTerminal(SingleSource.Subscriber<T> subscriber) {
        logDuplicateTerminal0(subscriber, null);
    }

    /**
     * Log if the ReactiveStreams specification has been violated related to out of order
     * {@link SingleSource.Subscriber#onSubscribe(Cancellable)} or duplicate terminal signals.
     * @param subscriber The {@link SingleSource.Subscriber}.
     * @param onSuccess The signal delivered to {@link SingleSource.Subscriber#onSuccess(Object)}.
     * @param <T> The type of {@link SingleSource.Subscriber}.
     */
    public static <T> void logDuplicateTerminalOnSuccess(SingleSource.Subscriber<T> subscriber, @Nullable T onSuccess) {
        logDuplicateTerminal0(subscriber, new IllegalStateException("ignoring onSuccess: " + onSuccess));
    }

    /**
     * Log if the ReactiveStreams specification has been violated related to out of order
     * {@link SingleSource.Subscriber#onSubscribe(Cancellable)} or duplicate terminal signals.
     * @param subscriber The {@link SingleSource.Subscriber}.
     * @param cause The cause from {@link SingleSource.Subscriber#onError(Throwable)}.
     * @param <T> The type of {@link SingleSource.Subscriber}.
     */
    public static <T> void logDuplicateTerminal(SingleSource.Subscriber<T> subscriber, Throwable cause) {
        logDuplicateTerminal0(subscriber, cause);
    }

    private static void logDuplicateTerminal0(Object subscriber, @Nullable Throwable cause) {
        LOGGER.warn("onSubscribe not called before terminal or duplicate terminal on Subscriber {}", subscriber,
                new IllegalStateException(
                        "onSubscribe not called before terminal or duplicate terminal on Subscriber " + subscriber +
                        " forbidden see: " +
                        "https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#1.9" +
                        "https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#1.7", cause));
    }
}
