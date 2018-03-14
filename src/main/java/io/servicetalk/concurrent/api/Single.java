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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.empty;
import static java.util.Objects.requireNonNull;

/**
 * An asynchronous computation that either completes with success giving the result or completes with an error.
 *
 * @param <T> Type of the result of the single.
 */
public abstract class Single<T> implements io.servicetalk.concurrent.Single<T> {
    private static final AtomicReference<BiConsumer<? super Subscriber, Consumer<? super Subscriber>>> SUBSCRIBE_PLUGIN_REF = new AtomicReference<>();

    /**
     * Add a plugin that will be invoked on each {@link #subscribe(Subscriber)} call. This can be used for visibility or to
     * extend functionality to all {@link Subscriber}s which pass through {@link #subscribe(Subscriber)}.
     * @param subscribePlugin the plugin that will be invoked on each {@link #subscribe(Subscriber)} call.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void addSubscribePlugin(BiConsumer<? super Subscriber, Consumer<? super Subscriber>> subscribePlugin) {
        requireNonNull(subscribePlugin);
        SUBSCRIBE_PLUGIN_REF.updateAndGet(currentPlugin -> currentPlugin == null ? subscribePlugin :
                (subscriber, handleSubscribe) -> subscribePlugin.accept(subscriber, subscriber2 -> subscribePlugin.accept(subscriber2, handleSubscribe))
        );
    }

    /**
     * Handles a subscriber to this {@code Single}.
     * <p>
     * This method is invoked internally by {@link Single} for every call to the {@link Single#subscribe(Subscriber)} method.
     * @param subscriber the subscriber.
     */
    protected abstract void handleSubscribe(Subscriber<? super T> subscriber);

    @Override
    public final void subscribe(Subscriber<? super T> subscriber) {
        requireNonNull(subscriber);
        BiConsumer<? super Subscriber, Consumer<? super Subscriber>> plugin = SUBSCRIBE_PLUGIN_REF.get();
        if (plugin != null) {
            plugin.accept(subscriber, this::handleSubscribe);
        } else {
            handleSubscribe(subscriber);
        }
    }

    /**
     * Subscribe to this {@link Single}, emits the result to the passed {@link Consumer} and log any {@link Subscriber#onError(Throwable)}.
     *
     * @param resultConsumer {@link Consumer} to accept the result of this {@link Single}.
     *
     * @return {@link Cancellable} used to invoke {@link Cancellable#cancel()} on the parameter of
     * {@link Subscriber#onSubscribe(Cancellable)} for this {@link Single}.
     */
    public final Cancellable subscribe(Consumer<T> resultConsumer) {
        SimpleSingleSubscriber<T> subscriber = new SimpleSingleSubscriber<>(resultConsumer);
        subscribe(subscriber);
        return subscriber;
    }

    /**
     * Converts this {@code Single} to a {@link Publisher}.
     *
     * @return A {@link Publisher} that emits at most a single item which is emitted by this {@code Single}.
     */
    public final Publisher<T> toPublisher() {
        return new SingleToPublisher<>(this);
    }

    /**
     * Ignores the result of this {@link Single} and forwards the termination signal to the returned {@link Completable}.
     *
     * @return A {@link Completable} that mirrors the terminal signal from this {@code Single}.
     */
    public final Completable ignoreResult() {
        return new SingleToCompletable<>(this);
    }

    /**
     * Ignores any error returned by this {@link Single} and resume to a new {@link Single}.
     *
     * @param nextFactory Returns the next {@link Single}, when this {@link Single} emits an error.
     * @return A {@link Single} that ignores error from this {@code Single} and resume with the {@link Single} produced by {@code nextFactory}.
     */
    public final Single<T> onErrorResume(Function<Throwable, Single<T>> nextFactory) {
        return new ResumeSingle<>(this, nextFactory);
    }

    /**
     * Maps the result of this single to a different type. Error, if any is forwarded to the returned {@link Single}.
     *
     * @param mapper To convert this result to other.
     * @param <R>    Type of the returned {@code Single}.
     * @return A new {@link Single} that will now have the result of type {@link R}.
     */
    public final <R> Single<R> map(Function<T, R> mapper) {
        return new MapSingle<>(this, mapper);
    }

    /**
     * Returns a {@link Single} that mirrors emissions from the {@link Single} returned by {@code next}.
     * Any error emitted by this {@link Single} is forwarded to the returned {@link Single}.
     *
     * @param next Function to give the next {@link Single}.
     * @param <R>  Type of the result of the resulting {@link Single}.
     * @return New {@link Single} that switches to the {@link Single} returned by {@code next} after this {@link Single} completes successfully.
     */
    public final <R> Single<R> flatmap(Function<T, Single<R>> next) {
        return new SingleFlatmapSingle<>(this, next);
    }

    /**
     * Returns a {@link Completable} that mirrors emissions from the {@link Completable} returned by {@code next}.
     * Any error emitted by this {@link Single} is forwarded to the returned {@link Completable}.
     *
     * @param next Function to give the next {@link Completable}.
     * @return New {@link Completable} that switches to the {@link Completable} returned by {@code next} after this {@link Single} completes successfully.
     */
    public final Completable flatmapCompletable(Function<T, Completable> next) {
        return new SingleFlatmapCompletable<>(this, next);
    }

    /**
     * Returns a {@link Publisher} that mirrors emissions from the {@link Publisher} returned by {@code next}.
     * Any error emitted by this {@link Single} is forwarded to the returned {@link Publisher}.
     *
     * @param next Function to give the next {@link Publisher}.
     * @param <R>  Type of objects emitted by the returned {@link Publisher}.
     * @return New {@link Publisher} that switches to the {@link Publisher} returned by {@code next} after this {@link Single} completes successfully.
     */
    public final <R> Publisher<R> flatmapPublisher(Function<T, Publisher<R>> next) {
        return new SingleFlatmapPublisher<>(this, next);
    }

    /**
     * Returns a {@link Publisher} that first emits the result of this {@link Single} and then subscribes and emits result of {@code next} {@link Single}.
     * Any error emitted by this {@link Single} or {@code next} {@link Single} is forwarded to the returned {@link Publisher}.
     *
     * @param next {@link Single} to concat.
     * @return New {@link Publisher} that first emits the result of this {@link Single} and then subscribes and emits result of {@code next} {@link Single}.
     */
    public final Publisher<T> concatWith(Single<T> next) {
        return toPublisher().concatWith(next.toPublisher());
    }

    /**
     * Returns a {@link Single} that emits the result of this {@link Single} after {@code next} {@link Completable} terminates successfully.
     * {@code next} {@link Completable} will only be subscribed to after this {@link Single} terminates successfully.
     * Any error emitted by this {@link Single} or {@code next} {@link Completable} is forwarded to the returned {@link Single}.
     *
     * @param next {@link Completable} to concat.
     * @return New {@link Single} that emits the result of this {@link Single} after {@code next} {@link Completable} terminates successfully.
     */
    public final Single<T> concatWith(Completable next) {
        // We can not use next.toPublisher() here as that returns Publisher<Void> which can not be concatenated with Single<T>
        return concatWith(next.andThen(empty())).first();
    }

    /**
     * Returns a {@link Publisher} that first emits the result of this {@link Single} and then subscribes and emits all elements from {@code next} {@link Publisher}.
     * Any error emitted by this {@link Single} or {@code next} {@link Publisher} is forwarded to the returned {@link Publisher}.
     *
     * @param next {@link Publisher} to concat.
     * @return New {@link Publisher} that first emits the result of this {@link Single} and then subscribes and emits all elements from {@code next} {@link Publisher}.
     */
    public final Publisher<T> concatWith(Publisher<T> next) {
        return toPublisher().concatWith(next);
    }

    /**
     * Subscribes to {@code mergeWith} {@link Completable} and only terminates this {@link Single} when {@code mergeWith} terminates.
     *
     * @param mergeWith {@link Completable} to subscribe and forward error to the returned {@link Single}.
     *                  Only terminate the returned {@link Single} when this terminates.
     * @return New {@link Single} that awaits successful termination of both this {@link Single} and {@code mergeWith} or failed termination of one.
     */
    public final Single<T> merge(Completable mergeWith) {
        return new MergeWithCompletableSingle<>(this, mergeWith);
    }

    /**
     * Invokes the {@code onSubscribe} {@link Consumer} argument <strong>before</strong> {@link Subscriber#onSubscribe(Cancellable)} is called for {@link Subscriber}s of the returned {@link Single}.
     *
     * @param onSubscribe Invoked <strong>before</strong> {@link Subscriber#onSubscribe(Cancellable)} is called for {@link Subscriber}s of the returned {@link Single}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Single}.
     */
    public final Single<T> doBeforeSubscribe(Consumer<Cancellable> onSubscribe) {
        requireNonNull(onSubscribe);
        Subscriber<T> subscriber = new Subscriber<T>() {
            @Override
            public void onSubscribe(Cancellable cancellable) {
                onSubscribe.accept(cancellable);
            }

            @Override
            public void onSuccess(@Nullable T result) {
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
     * Invokes the {@code onSuccess} {@link Consumer} argument <strong>before</strong> {@link Subscriber#onSuccess(Object)} is called for {@link Subscriber}s of the returned {@link Single}.
     *
     * @param onSuccess Invoked <strong>before</strong> {@link Subscriber#onSuccess(Object)} is called for {@link Subscriber}s of the returned {@link Single}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Single}.
     */
    public final Single<T> doBeforeSuccess(Consumer<T> onSuccess) {
        requireNonNull(onSuccess);
        Subscriber<T> subscriber = new Subscriber<T>() {
            @Override
            public void onSubscribe(Cancellable cancellable) {
                // NOOP
            }

            @Override
            public void onSuccess(@Nullable T result) {
                onSuccess.accept(result);
            }

            @Override
            public void onError(Throwable t) {
                // NOOP
            }
        };
        return doBeforeSubscriber(() -> subscriber);
    }

    /**
     * Invokes the {@code onError} {@link Consumer} argument <strong>before</strong> {@link Subscriber#onError(Throwable)} is called for {@link Subscriber}s of the returned {@link Single}.
     *
     * @param onError Invoked <strong>before</strong> {@link Subscriber#onError(Throwable)} is called for {@link Subscriber}s of the returned {@link Single}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Single}.
     */
    public final Single<T> doBeforeError(Consumer<Throwable> onError) {
        requireNonNull(onError);
        Subscriber<T> subscriber = new Subscriber<T>() {
            @Override
            public void onSubscribe(Cancellable cancellable) {
                // NOOP
            }

            @Override
            public void onSuccess(@Nullable T result) {
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
     * Invokes the {@code onCancel} {@link Runnable} argument <strong>before</strong> {@link Cancellable#cancel()} is called for Subscriptions of the returned {@link Single}.
     *
     * @param onCancel Invoked <strong>before</strong> {@link Cancellable#cancel()} is called for Subscriptions of the returned {@link Single}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Single}.
     */
    public final Single<T> doBeforeCancel(Runnable onCancel) {
        return new DoCancellableSingle<>(this, onCancel::run, true);
    }

    /**
     * Invokes the {@code doFinally} {@link Runnable} argument <strong>before</strong> any of the following terminal methods are called:
     * <ul>
     *     <li>{@link Subscriber#onSuccess(Object)}</li>
     *     <li>{@link Subscriber#onError(Throwable)}</li>
     *     <li>{@link Cancellable#cancel()}</li>
     * </ul>
     * for Subscriptions/{@link Subscriber}s of the returned {@link Single}.
     *
     * @param doFinally Invoked <strong>before</strong> any of the following terminal methods are called:
     * <ul>
     *     <li>{@link Subscriber#onSuccess(Object)}</li>
     *     <li>{@link Subscriber#onError(Throwable)}</li>
     *     <li>{@link Cancellable#cancel()}</li>
     * </ul>
     * for Subscriptions/{@link Subscriber}s of the returned {@link Single}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Single}.
     */
    public final Single<T> doBeforeFinally(Runnable doFinally) {
        return new DoBeforeFinallySingle<>(this, doFinally);
    }

    /**
     * Creates a new {@link Subscriber} (via the {@code subscriberSupplier} argument) on each call to {@link #subscribe(Subscriber)} and invokes
     * all the {@link Subscriber} methods <strong>before</strong> the {@link Subscriber}s of the returned {@link Single}.
     *
     * @param subscriberSupplier Creates a new {@link Subscriber} on each call to {@link #subscribe(Subscriber)} and invokes
     * all the {@link Subscriber} methods <strong>before</strong> the {@link Subscriber}s of the returned {@link Single}. {@link Subscriber} methods <strong>MUST NOT</strong> throw.
     * @return The new {@link Single}.
     */
    public final Single<T> doBeforeSubscriber(Supplier<Subscriber<? super T>> subscriberSupplier) {
        return new DoBeforeSubscriberSingle<>(this, subscriberSupplier);
    }

    /**
     * Invokes the {@code onSubscribe} {@link Consumer} argument <strong>after</strong> {@link Subscriber#onSubscribe(Cancellable)} is called for {@link Subscriber}s of the returned {@link Single}.
     *
     * @param onSubscribe Invoked <strong>after</strong> {@link Subscriber#onSubscribe(Cancellable)} is called for {@link Subscriber}s of the returned {@link Single}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Single}.
     */
    public final Single<T> doAfterSubscribe(Consumer<Cancellable> onSubscribe) {
        requireNonNull(onSubscribe);
        Subscriber<T> subscriber = new Subscriber<T>() {
            @Override
            public void onSubscribe(Cancellable cancellable) {
                onSubscribe.accept(cancellable);
            }

            @Override
            public void onSuccess(@Nullable T result) {
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
     * Invokes the {@code onSuccess} {@link Consumer} argument <strong>after</strong> {@link Subscriber#onSuccess(Object)} is called for {@link Subscriber}s of the returned {@link Single}.
     *
     * @param onSuccess Invoked <strong>after</strong> {@link Subscriber#onSuccess(Object)} is called for {@link Subscriber}s of the returned {@link Single}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Single}.
     */
    public final Single<T> doAfterSuccess(Consumer<T> onSuccess) {
        requireNonNull(onSuccess);
        Subscriber<T> subscriber = new Subscriber<T>() {
            @Override
            public void onSubscribe(Cancellable cancellable) {
                // NOOP
            }

            @Override
            public void onSuccess(@Nullable T result) {
                onSuccess.accept(result);
            }

            @Override
            public void onError(Throwable t) {
                // NOOP
            }
        };
        return doAfterSubscriber(() -> subscriber);
    }

    /**
     * Invokes the {@code onError} {@link Consumer} argument <strong>after</strong> {@link Subscriber#onError(Throwable)} is called for {@link Subscriber}s of the returned {@link Single}.
     *
     * @param onError Invoked <strong>after</strong> {@link Subscriber#onError(Throwable)} is called for {@link Subscriber}s of the returned {@link Single}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Single}.
     */
    public final Single<T> doAfterError(Consumer<Throwable> onError) {
        requireNonNull(onError);
        Subscriber<T> subscriber = new Subscriber<T>() {
            @Override
            public void onSubscribe(Cancellable cancellable) {
                // NOOP
            }

            @Override
            public void onSuccess(@Nullable T result) {
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
     * Invokes the {@code onCancel} {@link Runnable} argument <strong>after</strong> {@link Cancellable#cancel()} is called for Subscriptions of the returned {@link Single}.
     *
     * @param onCancel Invoked <strong>after</strong> {@link Cancellable#cancel()} is called for Subscriptions of the returned {@link Single}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Single}.
     */
    public final Single<T> doAfterCancel(Runnable onCancel) {
        return new DoCancellableSingle<>(this, onCancel::run, false);
    }

    /**
     * Invokes the {@code doFinally} {@link Runnable} argument <strong>after</strong> any of the following terminal methods are called:
     * <ul>
     *     <li>{@link Subscriber#onSuccess(Object)}</li>
     *     <li>{@link Subscriber#onError(Throwable)}</li>
     *     <li>{@link Cancellable#cancel()}</li>
     * </ul>
     * for Subscriptions/{@link Subscriber}s of the returned {@link Single}.
     *
     * @param doFinally Invoked <strong>after</strong> any of the following terminal methods are called:
     * <ul>
     *     <li>{@link Subscriber#onSuccess(Object)}</li>
     *     <li>{@link Subscriber#onError(Throwable)}</li>
     *     <li>{@link Cancellable#cancel()}</li>
     * </ul>
     * for Subscriptions/{@link Subscriber}s of the returned {@link Single}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Single}.
     */
    public final Single<T> doAfterFinally(Runnable doFinally) {
        return new DoAfterFinallySingle<>(this, doFinally);
    }

    /**
     * Creates a new {@link Subscriber} (via the {@code subscriberSupplier} argument) on each call to {@link #subscribe(Subscriber)} and invokes
     * all the {@link Subscriber} methods <strong>after</strong> the {@link Subscriber}s of the returned {@link Single}.
     *
     * @param subscriberSupplier Creates a new {@link Subscriber} on each call to {@link #subscribe(Subscriber)} and invokes
     * all the {@link Subscriber} methods <strong>after</strong> the {@link Subscriber}s of the returned {@link Single}. {@link Subscriber} methods <strong>MUST NOT</strong> throw.
     * @return The new {@link Single}.
     */
    public final Single<T> doAfterSubscriber(Supplier<Subscriber<? super T>> subscriberSupplier) {
        return new DoAfterSubscriberSingle<>(this, subscriberSupplier);
    }

    /**
     * Re-subscribes to this {@link Single} if an error is emitted and the passed {@link BiIntPredicate} returns {@code true}.
     *
     * @param shouldRetry {@link BiIntPredicate} that given the retry count and the most recent {@link Throwable} emitted from this
     * {@link Single} determines if the operation should be retried.
     * @return A {@link Single} that emits the result from this {@link Single} or re-subscribes if an error is emitted and if the passed {@link BiIntPredicate} returned {@code true}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/retry.html">ReactiveX retry operator.</a>
     */
    public final Single<T> retry(BiIntPredicate<Throwable> shouldRetry) {
        return new RetrySingle<>(this, shouldRetry);
    }

    /**
     * Re-subscribes to this {@link Single} if an error is emitted and the {@link Completable} returned by the supplied {@link BiIntFunction} completes successfully.
     * If the returned {@link Completable} emits an error, the returned {@link Single} terminates with that error.
     *
     * @param retryWhen {@link BiIntFunction} that given the retry count and the most recent {@link Throwable} emitted from this
     * {@link Single} returns a {@link Completable}. If this {@link Completable} emits an error, that error is emitted from the returned {@link Single},
     * otherwise, original {@link Single} is re-subscribed when this {@link Completable} completes.
     *
     * @return A {@link Single} that emits the result from this {@link Single} or re-subscribes if an error is emitted
     * and {@link Completable} returned by {@link BiIntFunction} completes successfully.
     *
     * @see <a href="http://reactivex.io/documentation/operators/retry.html">ReactiveX retry operator.</a>
     */
    public final Single<T> retryWhen(BiIntFunction<Throwable, Completable> retryWhen) {
        return new RetryWhenSingle<>(this, retryWhen);
    }

    /**
     * Re-subscribes to this {@link Single} when it completes and the passed {@link IntPredicate} returns {@code true}.
     *
     * @param shouldRepeat {@link IntPredicate} that given the repeat count determines if the operation should be repeated.
     * @return A {@link Single} that emits all items from this {@link Single} and from all re-subscriptions whenever the operation is repeated.
     *
     * @see <a href="http://reactivex.io/documentation/operators/repeat.html">ReactiveX repeat operator.</a>
     */
    public final Publisher<T> repeat(IntPredicate shouldRepeat) {
        return toPublisher().repeat(shouldRepeat);
    }

    /**
     * Re-subscribes to this {@link Single} when it completes and the {@link Completable} returned by the supplied {@link IntFunction} completes successfully.
     * If the returned {@link Completable} emits an error, the returned {@link Single} emits an error.
     *
     * @param repeatWhen {@link IntFunction} that given the repeat count returns a {@link Completable}.
     * If this {@link Completable} emits an error repeat is terminated, otherwise, original {@link Single} is re-subscribed
     * when this {@link Completable} completes.
     *
     * @return A {@link Publisher} that emits all items from this {@link Single} and from all re-subscriptions whenever the operation is repeated.
     *
     * @see <a href="http://reactivex.io/documentation/operators/retry.html">ReactiveX retry operator.</a>
     */
    public final Publisher<T> repeatWhen(IntFunction<Completable> repeatWhen) {
        return toPublisher().repeatWhen(repeatWhen);
    }

    /**
     * Creates a realized {@link Single} which always completes successfully with the provided {@code value}.
     *
     * @param value result of the {@link Single}.
     * @param <T>   Type of the {@link Single}.
     * @return A new {@link Single}.
     */
    public static <T> Single<T> success(@Nullable T value) {
        return new SucceededSingle<>(value);
    }

    /**
     * Creates a realized {@link Single} which always completes with the provided error {@code cause}.
     *
     * @param <T>   Type of the {@link Single}.
     * @param cause result of the {@link Single}.
     * @return A new {@link Single}.
     */
    public static <T> Single<T> error(Throwable cause) {
        return new FailedSingle<>(cause);
    }

    /**
     * Creates a {@link Single} that never terminates.
     *
     * @param <T> Type of the {@link Single}.
     * @return A new {@link Single}.
     */
    @SuppressWarnings("unchecked")
    public static <T> Single<T> never() {
        return (Single<T>) NeverSingle.INSTANCE;
    }

    /**
     * Defer creation of a {@link Single} till it is subscribed to.
     *
     * @param singleFactory {@link Supplier} to create a new {@link Single} for every call to {@link #subscribe(Subscriber)} to the returned {@link Single}.
     * @param <T> Type of the {@link Single}.
     * @return A new {@link Single} that creates a new {@link Single} using {@code singleFactory} for every call to {@link #subscribe(Subscriber)} and forwards
     * the result or error from the newly created {@link Single} to its {@link Subscriber}.
     */
    public static <T> Single<T> defer(Supplier<Single<T>> singleFactory) {
        return new SingleDefer<>(singleFactory);
    }
}
