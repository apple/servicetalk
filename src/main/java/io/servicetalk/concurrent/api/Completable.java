/**
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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.Cancellable;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * An asynchronous computation that does not emit any data. It just completes or emits an error.
 */
public abstract class Completable implements io.servicetalk.concurrent.Completable {
    private static final AtomicReference<BiConsumer<? super Subscriber, Consumer<? super Subscriber>>> SUBSCRIBE_PLUGIN_REF = new AtomicReference<>();
    private static final Object CONVERSION_VALUE = new Object();

    /**
     * Add a plugin that will be invoked on each {@link #subscribe(Subscriber)} call. This can be used for visibility or to
     * extend functionality to all {@link Subscriber}s which pass through {@link #subscribe(Subscriber)}.
     * @param subscribePlugin the plugin that will be invoked on each {@link #subscribe(Subscriber)} call.
     */
    @SuppressWarnings("rawtypes")
    public static void addSubscribePlugin(BiConsumer<? super Subscriber, Consumer<? super Subscriber>> subscribePlugin) {
        requireNonNull(subscribePlugin);
        SUBSCRIBE_PLUGIN_REF.updateAndGet(currentPlugin -> currentPlugin == null ? subscribePlugin :
                (subscriber, handleSubscribe) -> subscribePlugin.accept(subscriber, subscriber2 -> subscribePlugin.accept(subscriber2, handleSubscribe))
        );
    }

    /**
     * Handles a subscriber to this {@code Completable}.
     * <p>
     * This method is invoked internally by {@link Completable} for every call to the {@link Completable#subscribe(Completable.Subscriber)} method.
     * @param subscriber the subscriber.
     */
    protected abstract void handleSubscribe(Subscriber subscriber);

    @Override
    public final void subscribe(Subscriber subscriber) {
        requireNonNull(subscriber);
        BiConsumer<? super Subscriber, Consumer<? super Subscriber>> plugin = SUBSCRIBE_PLUGIN_REF.get();
        if (plugin != null) {
            plugin.accept(subscriber, this::handleSubscribe);
        } else {
            handleSubscribe(subscriber);
        }
    }

    /**
     * Subscribe to this {@link Completable} and log any {@link Subscriber#onError(Throwable)}.
     *
     * @return {@link Cancellable} used to invoke {@link Cancellable#cancel()} on the parameter of
     * {@link Subscriber#onSubscribe(Cancellable)} for this {@link Completable}.
     */
    public final Cancellable subscribe() {
        SimpleCompletableSubscriber subscriber = new SimpleCompletableSubscriber();
        subscribe(subscriber);
        return subscriber;
    }

    /**
     * Merges this {@link Completable} with the {@code other} {@link Completable}s so that the resulting
     * {@link Completable} terminates successfully when all of these complete or terminates with an error when any one terminates with an error.
     *
     * @param other {@link Completable}s to merge.
     * @return {@link Completable} that terminates successfully when this and all {@code other} {@link Completable}s complete or terminates with an error when any one terminates with an error.
     */
    public final Completable merge(Completable... other) {
        return new MergeCompletable(false, this, other);
    }

    /**
     * Merges this {@link Completable} with the {@code other} {@link Completable}s so that the resulting
     * {@link Completable} terminates successfully when all of these complete or terminates with an error when any one terminates with an error.
     *
     * @param other {@link Completable}s to merge.
     * @return {@link Completable} that terminates successfully when this and all {@code other} {@link Completable}s complete or terminates with an error when any one terminates with an error.
     */
    public final Completable merge(Iterable<? extends Completable> other) {
        return new IterableMergeCompletable(false, this, other);
    }

    /**
     * Merges this {@link Completable} with the {@code other} {@link Completable}s, and delays error notification until all involved {@link Completable}s terminate.
     * <p>
     * Use {@link #merge(Completable...)} if any error should immediately terminate the returned {@link Completable}.
     * @param other {@link Completable}s to merge.
     * @return {@link Completable} that terminates after {@code this} {@link Completable} and all {@code other} {@link Completable}s.
     * If all involved {@link Completable}s terminate successfully then the return value will terminate successfully. If any {@link Completable}
     * terminates in an error, then the return value will also terminate in an error.
     */
    public final Completable mergeDelayError(Completable... other) {
        return new MergeCompletable(true, this, other);
    }

    /**
     * Merges this {@link Completable} with the {@code other} {@link Completable}s, and delays error notification until all involved {@link Completable}s terminate.
     * <p>
     * Use {@link #merge(Iterable)} if any error should immediately terminate the returned {@link Completable}.
     * @param other {@link Completable}s to merge.
     * @return {@link Completable} that terminates after {@code this} {@link Completable} and all {@code other} {@link Completable}s.
     * If all involved {@link Completable}s terminate successfully then the return value will terminate successfully. If any {@link Completable}
     * terminates in an error, then the return value will also terminate in an error.
     */
    public final Completable mergeDelayError(Iterable<? extends Completable> other) {
        return new IterableMergeCompletable(true, this, other);
    }

    /**
     * Converts this {@code Completable} to a {@link Publisher}.
     * @param value The value to deliver to {@link org.reactivestreams.Subscriber#onNext(Object)} when this {@link Completable} completes.
     *              {@code null} is not allowed.
     * @param <T> The value type of the resulting {@link Publisher}.
     * @return A {@link Publisher} that mirrors the terminal signal from this {@link Completable}.
     */
    public final <T> Publisher<T> toPublisher(T value) {
        requireNonNull(value);
        return toPublisher(() -> value);
    }

    /**
     * Converts this {@code Completable} to a {@link Publisher}.
     * @param valueSupplier A {@link Supplier} that produces the value to deliver to {@link org.reactivestreams.Subscriber#onNext(Object)}
     *                      when this {@link Completable} completes. {@code null} return values are not allowed.
     * @param <T> The value type of the resulting {@link Publisher}.
     * @return A {@link Publisher} that mirrors the terminal signal from this {@link Completable}.
     */
    public final <T> Publisher<T> toPublisher(Supplier<T> valueSupplier) {
        return new CompletableToPublisher<>(this, valueSupplier);
    }

    /**
     * Once this {@link Completable} is terminated successfully, subscribe to {@code next} {@link Completable}
     * and propagate its terminal signal to the returned {@link Completable}.<p>
     *     Any error from this {@link Completable} and {@code next} {@link Completable} are propagated to the returned {@link Completable}.
     *
     * @param next {@link Completable} to subscribe after this {@link Completable} terminates successfully.
     * @return A {@link Completable} that emits the terminal signal of {@code next} {@link Completable}, after this {@link Completable} has terminated successfully.
     */
    public final Completable andThen(Completable next) {
        return new CompletableAndThenCompletable(this, next);
    }

    /**
     * Once this {@link Completable} is terminated successfully, subscribe to {@code next} {@link Single}
     * and propagate the result to the returned {@link Single}.<p>
     *     Any error from this {@link Completable} and {@code next} {@link Single} are propagated to the returned {@link Single}.
     *
     * @param next {@link Single} to subscribe after this {@link Completable} terminates successfully.
     * @param <T> Type of result of the returned {@link Single}.
     * @return A {@link Single} that emits the result of {@code next} {@link Single}, after this {@link Completable} has terminated successfully.
     */
    public final <T> Single<T> andThen(Single<T> next) {
        return new CompletableAndThenSingle<>(this, next);
    }

    /**
     * Once this {@link Completable} is terminated successfully, subscribe to {@code next} {@link Publisher}
     * and propagate all emissions to the returned {@link Publisher}.<p>
     *     Any error from this {@link Completable} and {@code next} {@link Publisher} are propagated to the returned {@link Publisher}.
     *
     * @param next {@link Publisher} to subscribe after this {@link Completable} terminates successfully.
     * @param <T> Type of objects emitted from the returned {@link Publisher}.
     * @return A {@link Publisher} that emits all items emitted from {@code next} {@link Publisher}, after this {@link Completable} has terminated successfully.
     */
    public final <T> Publisher<T> andThen(Publisher<T> next) {
        return toSingle().flatmapPublisher(aVoid -> next);
    }

    /**
     * Converts this {@code Completable} to a {@link Single}.
     * <p>
     * The return value's {@link Single.Subscriber#onSuccess(Object)} value is undefined, and if the value matters see {@link #toSingle(Object)}.
     * @return A {@link Single} that mirrors the terminal signal from this {@link Completable}.
     */
    private Single<Object> toSingle() {
        return toSingle(CONVERSION_VALUE);
    }

    /**
     * Converts this {@code Completable} to a {@link Single}.
     * @param value The value to deliver to {@link Single.Subscriber#onSuccess(Object)} when this {@link Completable} completes.
     *              {@code null} is not allowed.
     * @param <T> The value type of the resulting {@link Single}.
     * @return A {@link Single} that mirrors the terminal signal from this {@link Completable}.
     */
    public final <T> Single<T> toSingle(T value) {
        requireNonNull(value);
        return toSingle(() -> value);
    }

    /**
     * Converts this {@code Completable} to a {@link Single}.
     * @param valueSupplier A {@link Supplier} that produces the value to deliver to {@link Single.Subscriber#onSuccess(Object)}
     *                      when this {@link Completable} completes. {@code null} return values are not allowed.
     * @param <T> The value type of the resulting {@link Single}.
     * @return A {@link Single} that mirrors the terminal signal from this {@link Completable}.
     */
    public final <T> Single<T> toSingle(Supplier<T> valueSupplier) {
        return new CompletableToSingle<>(this, valueSupplier);
    }

    /**
     * Invokes the {@code onSubscribe} {@link Consumer} argument <strong>before</strong> {@link Subscriber#onSubscribe(Cancellable)} is called for {@link Subscriber}s of the returned {@link Completable}.
     *
     * @param onSubscribe Invoked <strong>before</strong> {@link Subscriber#onSubscribe(Cancellable)} is called for {@link Subscriber}s of the returned {@link Completable}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Completable}.
     */
    public final Completable doBeforeSubscribe(Consumer<Cancellable> onSubscribe) {
        requireNonNull(onSubscribe);
        Subscriber subscriber = new Subscriber() {
            @Override
            public void onSubscribe(Cancellable cancellable) {
                onSubscribe.accept(cancellable);
            }

            @Override
            public void onComplete() {
                // NOOP
            }

            @Override
            public void onError(Throwable t) {
                // NOOP
            }
        };
        return doBeforeSubscriber(() -> subscriber);
    }

    /**
     * Invokes the {@code onComplete} {@link Runnable} argument <strong>before</strong> {@link Subscriber#onComplete()} is called for {@link Subscriber}s of the returned {@link Completable}.
     *
     * @param onComplete Invoked <strong>before</strong> {@link Subscriber#onComplete()} is called for {@link Subscriber}s of the returned {@link Completable}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Completable}.
     */
    public final Completable doBeforeComplete(Runnable onComplete) {
        requireNonNull(onComplete);
        Subscriber subscriber = new Subscriber() {
            @Override
            public void onSubscribe(Cancellable cancellable) {
                // NOOP
            }

            @Override
            public void onComplete() {
                onComplete.run();
            }

            @Override
            public void onError(Throwable t) {
                // NOOP
            }
        };
        return doBeforeSubscriber(() -> subscriber);
    }

    /**
     * Invokes the {@code onError} {@link Consumer} argument <strong>before</strong> {@link Subscriber#onError(Throwable)} is called for {@link Subscriber}s of the returned {@link Completable}.
     *
     * @param onError Invoked <strong>before</strong> {@link Subscriber#onError(Throwable)} is called for {@link Subscriber}s of the returned {@link Completable}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Completable}.
     */
    public final Completable doBeforeError(Consumer<Throwable> onError) {
        requireNonNull(onError);
        Subscriber subscriber = new Subscriber() {
            @Override
            public void onSubscribe(Cancellable cancellable) {
                // NOOP
            }

            @Override
            public void onComplete() {
                // NOOP
            }

            @Override
            public void onError(Throwable t) {
                onError.accept(t);
            }
        };
        return doBeforeSubscriber(() -> subscriber);
    }

    /**
     * Invokes the {@code onCancel} {@link Runnable} argument <strong>before</strong> {@link Cancellable#cancel()} is called for Subscriptions of the returned {@link Completable}.
     *
     * @param onCancel Invoked <strong>before</strong> {@link Cancellable#cancel()} is called for Subscriptions of the returned {@link Completable}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Completable}.
     */
    public final Completable doBeforeCancel(Runnable onCancel) {
        return new DoCancellableCompletable(this, onCancel::run, true);
    }

    /**
     * Invokes the {@code doFinally} {@link Runnable} argument <strong>before</strong> any of the following terminal methods are called:
     * <ul>
     *     <li>{@link Subscriber#onComplete()}</li>
     *     <li>{@link Subscriber#onError(Throwable)}</li>
     *     <li>{@link Cancellable#cancel()}</li>
     * </ul>
     * for Subscriptions/{@link Subscriber}s of the returned {@link Completable}.
     *
     * @param doFinally Invoked <strong>before</strong> any of the following terminal methods are called:
     * <ul>
     *     <li>{@link Subscriber#onComplete()}</li>
     *     <li>{@link Subscriber#onError(Throwable)}</li>
     *     <li>{@link Cancellable#cancel()}</li>
     * </ul>
     * for Subscriptions/{@link Subscriber}s of the returned {@link Completable}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Completable}.
     */
    public final Completable doBeforeFinally(Runnable doFinally) {
        return new DoBeforeFinallyCompletable(this, doFinally);
    }

    /**
     * Creates a new {@link Subscriber} (via the {@code subscriberSupplier} argument) on each call to {@link #subscribe(Subscriber)} and invokes
     * all the {@link Subscriber} methods <strong>before</strong> the {@link Subscriber}s of the returned {@link Completable}.
     *
     * @param subscriberSupplier Creates a new {@link Subscriber} on each call to {@link #subscribe(Subscriber)} and invokes
     * all the {@link Subscriber} methods <strong>before</strong> the {@link Subscriber}s of the returned {@link Completable}. {@link Subscriber} methods <strong>MUST NOT</strong> throw.
     * @return The new {@link Completable}.
     */
    public final Completable doBeforeSubscriber(Supplier<Subscriber> subscriberSupplier) {
        return new DoBeforeSubscriberCompletable(this, subscriberSupplier);
    }

    /**
     * Invokes the {@code onSubscribe} {@link Consumer} argument <strong>after</strong> {@link Subscriber#onSubscribe(Cancellable)} is called for {@link Subscriber}s of the returned {@link Completable}.
     *
     * @param onSubscribe Invoked <strong>after</strong> {@link Subscriber#onSubscribe(Cancellable)} is called for {@link Subscriber}s of the returned {@link Completable}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Completable}.
     */
    public final Completable doAfterSubscribe(Consumer<Cancellable> onSubscribe) {
        requireNonNull(onSubscribe);
        Subscriber subscriber = new Subscriber() {
            @Override
            public void onSubscribe(Cancellable cancellable) {
                onSubscribe.accept(cancellable);
            }

            @Override
            public void onComplete() {
                // NOOP
            }

            @Override
            public void onError(Throwable t) {
                // NOOP
            }
        };
        return doAfterSubscriber(() -> subscriber);
    }

    /**
     * Invokes the {@code onComplete} {@link Runnable} argument <strong>after</strong> {@link Subscriber#onComplete()} is called for {@link Subscriber}s of the returned {@link Completable}.
     *
     * @param onComplete Invoked <strong>after</strong> {@link Subscriber#onComplete()} is called for {@link Subscriber}s of the returned {@link Completable}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Completable}.
     */
    public final Completable doAfterComplete(Runnable onComplete) {
        requireNonNull(onComplete);
        Subscriber subscriber = new Subscriber() {
            @Override
            public void onSubscribe(Cancellable cancellable) {
                // NOOP
            }

            @Override
            public void onComplete() {
                onComplete.run();
            }

            @Override
            public void onError(Throwable t) {
                // NOOP
            }
        };
        return doAfterSubscriber(() -> subscriber);
    }

    /**
     * Invokes the {@code onError} {@link Consumer} argument <strong>after</strong> {@link Subscriber#onError(Throwable)} is called for {@link Subscriber}s of the returned {@link Completable}.
     *
     * @param onError Invoked <strong>after</strong> {@link Subscriber#onError(Throwable)} is called for {@link Subscriber}s of the returned {@link Completable}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Completable}.
     */
    public final Completable doAfterError(Consumer<Throwable> onError) {
        requireNonNull(onError);
        Subscriber subscriber = new Subscriber() {
            @Override
            public void onSubscribe(Cancellable cancellable) {
                // NOOP
            }

            @Override
            public void onComplete() {
                // NOOP
            }

            @Override
            public void onError(Throwable t) {
                onError.accept(t);
            }
        };
        return doAfterSubscriber(() -> subscriber);
    }

    /**
     * Invokes the {@code onCancel} {@link Runnable} argument <strong>after</strong> {@link Cancellable#cancel()} is called for Subscriptions of the returned {@link Completable}.
     *
     * @param onCancel Invoked <strong>after</strong> {@link Cancellable#cancel()} is called for Subscriptions of the returned {@link Completable}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Completable}.
     */
    public final Completable doAfterCancel(Runnable onCancel) {
        return new DoCancellableCompletable(this, onCancel::run, false);
    }

    /**
     * Invokes the {@code doFinally} {@link Runnable} argument <strong>after</strong> any of the following terminal methods are called:
     * <ul>
     *     <li>{@link Subscriber#onComplete()}</li>
     *     <li>{@link Subscriber#onError(Throwable)}</li>
     *     <li>{@link Cancellable#cancel()}</li>
     * </ul>
     * for Subscriptions/{@link Subscriber}s of the returned {@link Completable}.
     *
     * @param doFinally Invoked <strong>after</strong> any of the following terminal methods are called:
     * <ul>
     *     <li>{@link Subscriber#onComplete()}</li>
     *     <li>{@link Subscriber#onError(Throwable)}</li>
     *     <li>{@link Cancellable#cancel()}</li>
     * </ul>
     * for Subscriptions/{@link Subscriber}s of the returned {@link Completable}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Completable}.
     */
    public final Completable doAfterFinally(Runnable doFinally) {
        return new DoAfterFinallyCompletable(this, doFinally);
    }

    /**
     * Creates a new {@link Subscriber} (via the {@code subscriberSupplier} argument) on each call to {@link #subscribe(Subscriber)} and invokes
     * all the {@link Subscriber} methods <strong>after</strong> the {@link Subscriber}s of the returned {@link Completable}.
     *
     * @param subscriberSupplier Creates a new {@link Subscriber} on each call to {@link #subscribe(Subscriber)} and invokes
     * all the {@link Subscriber} methods <strong>after</strong> the {@link Subscriber}s of the returned {@link Completable}. {@link Subscriber} methods <strong>MUST NOT</strong> throw.
     * @return The new {@link Completable}.
     */
    public final Completable doAfterSubscriber(Supplier<Subscriber> subscriberSupplier) {
        return new DoAfterSubscriberCompletable(this, subscriberSupplier);
    }

    /**
     * Ignores any error returned by this {@link Completable} and resume to a new {@link Completable}.
     *
     * @param nextFactory Returns the next {@link Completable}, if this {@link Completable} emits an error.
     * @return The new {@link Completable}.
     */
    public final Completable onErrorResume(Function<Throwable, Completable> nextFactory) {
        return new ResumeCompletable(this, nextFactory);
    }

    /**
     * Creates a realized completed {@code Completable}.
     *
     * @return A new {@code Completable}.
     */
    public static Completable completed() {
        return CompletedCompletable.INSTANCE;
    }

    /**
     * Creates a realized failed {@code Completable}.
     *
     * @param cause error that the returned {@code Completable} completes with.
     * @return A new {@code Completable}.
     */
    public static Completable error(Throwable cause) {
        return new FailedCompletable(requireNonNull(cause));
    }

    /**
     * Creates a {@link Completable} that never terminates.
     *
     * @return A new {@code Completable}.
     */
    public static Completable never() {
        return NeverCompletable.INSTANCE;
    }

    /**
     * Re-subscribes to this {@link Completable} if an error is emitted and the passed {@link BiIntPredicate} returns {@code true}.
     *
     * @param shouldRetry {@link BiIntPredicate} that given the retry count and the most recent {@link Throwable} emitted from this
     * {@link Completable} determines if the operation should be retried.
     * @return A {@link Completable} that completes with this {@link Completable} or re-subscribes if an error is emitted and if the passed {@link BiPredicate} returned {@code true}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/retry.html">ReactiveX retry operator.</a>
     */
    public final Completable retry(BiIntPredicate<Throwable> shouldRetry) {
        return toSingle().retry(shouldRetry).ignoreResult();
    }

    /**
     * Re-subscribes to this {@link Completable} if an error is emitted and the {@link Completable} returned by the supplied {@link BiIntFunction} completes successfully.
     * If the returned {@link Completable} emits an error, the returned {@link Completable} terminates with that error.
     *
     * @param retryWhen {@link BiIntFunction} that given the retry count and the most recent {@link Throwable} emitted from this
     * {@link Completable} returns a {@link Completable}. If this {@link Completable} emits an error, that error is emitted from the returned {@link Completable},
     * otherwise, original {@link Completable} is re-subscribed when this {@link Completable} completes.
     *
     * @return A {@link Completable} that completes with this {@link Completable} or re-subscribes if an error is emitted
     * and {@link Completable} returned by {@link BiFunction} completes successfully.
     *
     * @see <a href="http://reactivex.io/documentation/operators/retry.html">ReactiveX retry operator.</a>
     */
    public final Completable retryWhen(BiIntFunction<Throwable, Completable> retryWhen) {
        return toSingle().retryWhen(retryWhen).ignoreResult();
    }

    /**
     * Re-subscribes to this {@link Completable} when it completes and the passed {@link Predicate} returns {@code true}.
     *
     * @param shouldRepeat {@link IntPredicate} that given the repeat count determines if the operation should be repeated.
     * @return A {@link Completable} that completes after all re-subscriptions completes.
     *
     * @see <a href="http://reactivex.io/documentation/operators/repeat.html">ReactiveX repeat operator.</a>
     */
    public final Completable repeat(IntPredicate shouldRepeat) {
        return toPublisher(CONVERSION_VALUE).repeat(shouldRepeat).ignoreElements();
    }

    /**
     * Re-subscribes to this {@link Single} when it completes and the {@link Completable} returned by the supplied {@link IntFunction} completes successfully.
     * If the returned {@link Completable} emits an error, the returned {@link Single} emits an error.
     *
     * @param repeatWhen {@link IntFunction} that given the repeat count returns a {@link Completable}.
     * If this {@link Completable} emits an error repeat is terminated, otherwise, original {@link Single} is re-subscribed
     * when this {@link Completable} completes.
     *
     * @return A {@link Completable} that completes after all re-subscriptions completes.
     *
     * @see <a href="http://reactivex.io/documentation/operators/retry.html">ReactiveX retry operator.</a>
     */
    public final Completable repeatWhen(IntFunction<Completable> repeatWhen) {
        return toPublisher(CONVERSION_VALUE).repeatWhen(repeatWhen).ignoreElements();
    }

    /**
     * Defer creation of a {@link Completable} till it is subscribed to.
     *
     * @param completableFactory {@link Supplier} to create a new {@link Completable} for every call to {@link #subscribe(Subscriber)} to the returned {@link Completable}.
     * @return A new {@link Completable} that creates a new {@link Completable} using {@code completableFactory} for every call to {@link #subscribe(Subscriber)} and forwards
     * the termination signal from the newly created {@link Completable} to its {@link Subscriber}.
     */
    public static Completable defer(Supplier<Completable> completableFactory) {
        return new CompletableDefer(completableFactory);
    }
}
