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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.Cancellable;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Executors.newOffloader;
import static io.servicetalk.concurrent.api.NeverSingle.neverSingle;
import static io.servicetalk.concurrent.api.Publisher.empty;
import static java.util.Objects.requireNonNull;

/**
 * An asynchronous computation that either completes with success giving the result or completes with an error.
 *
 * @param <T> Type of the result of the single.
 */
public abstract class Single<T> implements io.servicetalk.concurrent.Single<T> {
    private static final AtomicReference<BiConsumer<? super Subscriber, Consumer<? super Subscriber>>> SUBSCRIBE_PLUGIN_REF = new AtomicReference<>();

    private final Executor executor;

    /**
     * New instance.
     */
    protected Single() {
        this(immediate());
    }

    /**
     * New instance.
     *
     * @param executor {@link Executor} to use for this {@link Single}.
     */
    Single(Executor executor) {
        this.executor = requireNonNull(executor);
    }

    /**
     * Add a plugin that will be invoked on each {@link #subscribe(Subscriber)} call. This can be used for visibility or to
     * extend functionality to all {@link Subscriber}s which pass through {@link #subscribe(Subscriber)}.
     * @param subscribePlugin the plugin that will be invoked on each {@link #subscribe(Subscriber)} call.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void addSubscribePlugin(BiConsumer<? super Subscriber, Consumer<? super Subscriber>> subscribePlugin) {
        requireNonNull(subscribePlugin);
        SUBSCRIBE_PLUGIN_REF.updateAndGet(currentPlugin -> currentPlugin == null ? subscribePlugin :
                (subscriber, handleSubscribe) -> subscribePlugin.accept(subscriber,
                        subscriber2 -> subscribePlugin.accept(subscriber2, handleSubscribe))
        );
    }

    @Override
    public final void subscribe(Subscriber<? super T> subscriber) {
        // This is a user-driven subscribe i.e. there is no SignalOffloader override, so create a new SignalOffloader
        // to use.
        final SignalOffloader signalOffloader = newOffloader(executor);
        // Since this is a user-driven subscribe (end of the execution chain), offload Cancellable
        subscribe(signalOffloader.offloadCancellable(subscriber), signalOffloader);
    }

    /**
     * Handles a subscriber to this {@link Single}.
     *
     * @param subscriber the subscriber.
     */
    protected abstract void handleSubscribe(Subscriber<? super T> subscriber);

    /**
     * A special subscribe mode that uses the passed {@link SignalOffloader} instead of creating a new
     * {@link SignalOffloader} like {@link #subscribe(Subscriber)}. This will call
     * {@link #handleSubscribe(Subscriber, SignalOffloader)} to handle this subscribe instead of
     * {@link #handleSubscribe(Subscriber)}.<p>
     *
     *     This method is used by operator implementations to inherit a chosen {@link SignalOffloader} per
     *     {@link Subscriber} where possible.
     *     This method does not wrap the passed {@link Subscriber} or {@link Cancellable} to offload processing to
     *     {@link SignalOffloader}.
     *     That is done by {@link #handleSubscribe(Subscriber, SignalOffloader)} and hence can be overridden by
     *     operators that do not require this wrapping.
     *
     * @param subscriber {@link Subscriber} to this {@link Single}.
     * @param signalOffloader {@link SignalOffloader} to use for this {@link Subscriber}.
     */
    final void subscribe(Subscriber<? super T> subscriber, SignalOffloader signalOffloader) {
        requireNonNull(subscriber);
        BiConsumer<? super Subscriber, Consumer<? super Subscriber>> plugin = SUBSCRIBE_PLUGIN_REF.get();
        if (plugin != null) {
            plugin.accept(subscriber, sub -> handleSubscribe(sub, signalOffloader));
        } else {
            handleSubscribe(subscriber, signalOffloader);
        }
    }

    /**
     * Override for {@link #handleSubscribe(Subscriber)} to offload the {@link #handleSubscribe(Subscriber)} call to the
     * passed {@link SignalOffloader}. <p>
     *
     *     This method wraps the passed {@link Subscriber} using {@link SignalOffloader#offloadSubscriber(Subscriber)}
     *     and then calls {@link #handleSubscribe(Subscriber)} using
     *     {@link SignalOffloader#offloadSignal(Object, Consumer)}.
     *     Operators that do not wish to wrap the passed {@link Subscriber} can override this method and omit the
     *     wrapping.
     *
     * @param subscriber the subscriber.
     * @param signalOffloader {@link SignalOffloader} to use for this {@link Subscriber}.
     */
    void handleSubscribe(Subscriber<? super T> subscriber, SignalOffloader signalOffloader) {
        Subscriber<? super T> safeSubscriber = signalOffloader.offloadSubscriber(subscriber);
        signalOffloader.offloadSignal(safeSubscriber, this::handleSubscribe);
    }

    /**
     * Subscribe to this {@link Single}, emits the result to the passed {@link Consumer} and log any
     * {@link Subscriber#onError(Throwable)}.
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
        return new SingleToPublisher<>(this, executor);
    }

    /**
     * Ignores the result of this {@link Single} and forwards the termination signal to the returned {@link Completable}.
     *
     * @return A {@link Completable} that mirrors the terminal signal from this {@code Single}.
     */
    public final Completable ignoreResult() {
        return new SingleToCompletable<>(this, executor);
    }

    /**
     * Creates a new {@link Single} that will mimic the signals of this {@link Single} but will terminate with a
     * with a {@link TimeoutException} if time {@code duration} elapses between {@link #subscribe(Subscriber)} and
     * termination. The timer starts when the returned {@link Single} is {@link #subscribe(Subscriber) subscribed}
     * to.
     * <p>
     * In the event of timeout any {@link Cancellable} from {@link Subscriber#onSubscribe(Cancellable)} will be
     * {@link Cancellable#cancel() cancelled} and the associated {@link Subscriber} will be
     * {@link Subscriber#onError(Throwable) terminated}.
     * @param duration The time duration which is allowed to elapse before {@link Subscriber#onSuccess(Object)}.
     * @param unit The units for {@code duration}.
     * @return a new {@link Single} that will mimic the signals of this {@link Single} but will terminate with a
     * {@link TimeoutException} if time {@code duration} elapses before {@link Subscriber#onSuccess(Object)}.
     * @see <a href="http://reactivex.io/documentation/operators/timeout.html">ReactiveX timeout operator.</a>
     */
    public final Single<T> timeout(long duration, TimeUnit unit) {
        return timeout(duration, unit, executor);
    }

    /**
     * Creates a new {@link Single} that will mimic the signals of this {@link Single} but will terminate with a
     * with a {@link TimeoutException} if time {@code duration} elapses between {@link #subscribe(Subscriber)} and
     * termination. The timer starts when the returned {@link Single} is {@link #subscribe(Subscriber) subscribed}
     * to.
     * <p>
     * In the event of timeout any {@link Cancellable} from {@link Subscriber#onSubscribe(Cancellable)} will be
     * {@link Cancellable#cancel() cancelled} and the associated {@link Subscriber} will be
     * {@link Subscriber#onError(Throwable) terminated}.
     * @param duration The time duration which is allowed to elapse before {@link Subscriber#onSuccess(Object)}.
     * @param unit The units for {@code duration}.
     * @param timeoutExecutor The {@link Executor} to use for managing the timer notifications.
     * @return a new {@link Single} that will mimic the signals of this {@link Single} but will terminate with a
     * {@link TimeoutException} if time {@code duration} elapses before {@link Subscriber#onSuccess(Object)}.
     * @see <a href="http://reactivex.io/documentation/operators/timeout.html">ReactiveX timeout operator.</a>
     */
    public final Single<T> timeout(long duration, TimeUnit unit, Executor timeoutExecutor) {
        return new TimeoutSingle<>(this, duration, unit, timeoutExecutor);
    }

    /**
     * Creates a new {@link Single} that will mimic the signals of this {@link Single} but will terminate with a
     * with a {@link TimeoutException} if time {@code duration} elapses between {@link #subscribe(Subscriber)} and
     * termination. The timer starts when the returned {@link Single} is {@link #subscribe(Subscriber) subscribed}
     * to.
     * <p>
     * In the event of timeout any {@link Cancellable} from {@link Subscriber#onSubscribe(Cancellable)} will be
     * {@link Cancellable#cancel() cancelled} and the associated {@link Subscriber} will be
     * {@link Subscriber#onError(Throwable) terminated}.
     * {@link Subscriber} will via {@link Subscriber#onError(Throwable) terminated}.
     * @param duration The time duration which is allowed to elapse before {@link Subscriber#onSuccess(Object)}.
     * @return a new {@link Single} that will mimic the signals of this {@link Single} but will terminate with a
     * {@link TimeoutException} if time {@code duration} elapses before {@link Subscriber#onSuccess(Object)}.
     * @see <a href="http://reactivex.io/documentation/operators/timeout.html">ReactiveX timeout operator.</a>
     */
    public final Single<T> timeout(Duration duration) {
        return timeout(duration, executor);
    }

    /**
     * Creates a new {@link Single} that will mimic the signals of this {@link Single} but will terminate with a
     * with a {@link TimeoutException} if time {@code duration} elapses between {@link #subscribe(Subscriber)} and
     * termination. The timer starts when the returned {@link Single} is {@link #subscribe(Subscriber) subscribed}
     * to.
     * <p>
     * In the event of timeout any {@link Cancellable} from {@link Subscriber#onSubscribe(Cancellable)} will be
     * {@link Cancellable#cancel() cancelled} and the associated {@link Subscriber} will be
     * {@link Subscriber#onError(Throwable) terminated}.
     * @param duration The time duration which is allowed to elapse before {@link Subscriber#onSuccess(Object)}.
     * @param timeoutExecutor The {@link Executor} to use for managing the timer notifications.
     * @return a new {@link Single} that will mimic the signals of this {@link Single} but will terminate with a
     * {@link TimeoutException} if time {@code duration} elapses before {@link Subscriber#onSuccess(Object)}.
     * @see <a href="http://reactivex.io/documentation/operators/timeout.html">ReactiveX timeout operator.</a>
     */
    public final Single<T> timeout(Duration duration, Executor timeoutExecutor) {
        return new TimeoutSingle<>(this, duration, timeoutExecutor);
    }

    /**
     * Ignores any error returned by this {@link Single} and resume to a new {@link Single}.
     *
     * @param nextFactory Returns the next {@link Single}, when this {@link Single} emits an error.
     * @return A {@link Single} that ignores error from this {@code Single} and resume with the {@link Single} produced by {@code nextFactory}.
     */
    public final Single<T> onErrorResume(Function<Throwable, Single<T>> nextFactory) {
        return new ResumeSingle<>(this, nextFactory, executor);
    }

    /**
     * Maps the result of this single to a different type. Error, if any is forwarded to the returned {@link Single}.
     *
     * @param mapper To convert this result to other.
     * @param <R>    Type of the returned {@code Single}.
     * @return A new {@link Single} that will now have the result of type {@link R}.
     */
    public final <R> Single<R> map(Function<T, R> mapper) {
        return new MapSingle<>(this, mapper, executor);
    }

    /**
     * Returns a {@link Single} that mirrors emissions from the {@link Single} returned by {@code next}.
     * Any error emitted by this {@link Single} is forwarded to the returned {@link Single}.
     *
     * @param next Function to give the next {@link Single}.
     * @param <R>  Type of the result of the resulting {@link Single}.
     * @return New {@link Single} that switches to the {@link Single} returned by {@code next} after this {@link Single} completes successfully.
     */
    public final <R> Single<R> flatMap(Function<T, Single<R>> next) {
        return new SingleFlatMapSingle<>(this, next, executor);
    }

    /**
     * Returns a {@link Completable} that mirrors emissions from the {@link Completable} returned by {@code next}.
     * Any error emitted by this {@link Single} is forwarded to the returned {@link Completable}.
     *
     * @param next Function to give the next {@link Completable}.
     * @return New {@link Completable} that switches to the {@link Completable} returned by {@code next} after this {@link Single} completes successfully.
     */
    public final Completable flatMapCompletable(Function<T, Completable> next) {
        return new SingleFlatMapCompletable<>(this, next, executor);
    }

    /**
     * Returns a {@link Publisher} that mirrors emissions from the {@link Publisher} returned by {@code next}.
     * Any error emitted by this {@link Single} is forwarded to the returned {@link Publisher}.
     *
     * @param next Function to give the next {@link Publisher}.
     * @param <R>  Type of objects emitted by the returned {@link Publisher}.
     * @return New {@link Publisher} that switches to the {@link Publisher} returned by {@code next} after this {@link Single} completes successfully.
     */
    public final <R> Publisher<R> flatMapPublisher(Function<T, Publisher<R>> next) {
        return new SingleFlatMapPublisher<>(this, next, executor);
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
        return new DoCancellableSingle<>(this, onCancel::run, true, executor);
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
        return new DoBeforeFinallySingle<>(this, doFinally, executor);
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
        return new DoBeforeSubscriberSingle<>(this, subscriberSupplier, executor);
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
        return new DoCancellableSingle<>(this, onCancel::run, false, executor);
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
        return new DoAfterSubscriberSingle<>(this, subscriberSupplier, executor);
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
        return new RetrySingle<>(this, shouldRetry, executor);
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
        return new RetryWhenSingle<>(this, retryWhen, executor);
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
     * <strong>This method requires advanced knowledge of building operators. Before using this method please attempt
     * to compose existing operator(s) to satisfy your use case.</strong>
     * <p>
     * Returns a {@link Single} that when {@link Single#subscribe(Subscriber)} is called the {@code operator}
     * argument will be used to wrap the {@link Subscriber} before subscribing to this {@link Single}.
     * <pre>{@code
     *     Single<X> pub = ...;
     *     pub.map(..) // A
     *        .liftSynchronous(original -> modified)
     *        .doAfterFinally(..) // B
     * }</pre>
     * The {@code original -> modified} "operator" <strong>MUST</strong> be "synchronous" in that it does not interact
     * with the original {@link Subscriber} from outside the modified {@link Subscriber} or {@link Cancellable}
     * threads. That is to say this operator will not impact the {@link Executor} constraints already in place between
     * <i>A</i> and <i>B</i> above. If you need asynchronous behavior, or are unsure, see
     * {@link #liftAsynchronous(SingleOperator)}.
     * @param operator The custom operator logic. The input is the "original" {@link Subscriber} to this
     * {@link Single} and the return is the "modified" {@link Subscriber} that provides custom operator business
     * logic.
     * @param <R> Type of the items emitted by the returned {@link Single}.
     * @return a {@link Single} that when {@link Single#subscribe(Subscriber)} is called the {@code operator}
     * argument will be used to wrap the {@link Subscriber} before subscribing to this {@link Single}.
     * @see #liftAsynchronous(SingleOperator)
     */
    public final <R> Single<R> liftSynchronous(SingleOperator<T, R> operator) {
        return new LiftSynchronousSingleOperator<>(this, operator, executor);
    }

    /**
     * <strong>This method requires advanced knowledge of building operators. Before using this method please attempt
     * to compose existing operator(s) to satisfy your use case.</strong>
     * <p>
     * Returns a {@link Single} that when {@link Single#subscribe(Subscriber)} is called the {@code operator}
     * argument will be used to wrap the {@link Subscriber} before subscribing to this {@link Single}.
     * <pre>{@code
     *     Publisher<X> pub = ...;
     *     pub.map(..) // Aw
     *        .liftAsynchronous(original -> modified)
     *        .doAfterFinally(..) // B
     * }</pre>
     * The {@code original -> modified} "operator" MAY be "asynchronous" in that it may interact with the original
     * {@link Subscriber} from outside the modified {@link Subscriber} or {@link Cancellable} threads. More
     * specifically:
     * <ul>
     *  <li>all of the {@link Subscriber} invocations going "downstream" (i.e. from <i>A</i> to <i>B</i> above) MAY be
     *  offloaded via an {@link Executor}</li>
     *  <li>all of the {@link Cancellable} invocations going "upstream" (i.e. from <i>B</i> to <i>A</i> above) MAY be
     *  offloaded via an {@link Executor}</li>
     * </ul>
     * This behavior exists to prevent blocking code negatively impacting the thread that powers the upstream source of
     * data (e.g. an EventLoop).
     * @param operator The custom operator logic. The input is the "original" {@link Subscriber} to this
     * {@link Single} and the return is the "modified" {@link Subscriber} that provides custom operator business
     * logic.
     * @param <R> Type of the items emitted by the returned {@link Single}.
     * @return a {@link Single} that when {@link Single#subscribe(Subscriber)} is called the {@code operator}
     * argument will be used to wrap the {@link Subscriber} before subscribing to this {@link Single}.
     * @see #liftSynchronous(SingleOperator)
     */
    public final <R> Single<R> liftAsynchronous(SingleOperator<T, R> operator) {
        return new LiftAsynchronousSingleOperator<>(this, operator, executor);
    }

    /**
     * Creates a new {@link Single} that will use the passed {@link Executor} to invoke the following methods:
     * <ul>
     *     <li>All {@link Subscriber} methods.</li>
     *     <li>All {@link Cancellable} methods.</li>
     *     <li>The {@link #handleSubscribe(Single.Subscriber)} method.</li>
     * </ul>
     * This method does <em>not</em> override preceding {@link Executor}s, if any, specified for {@code this}
     * {@link Single}. Only subsequent operations, if any, added in this execution chain will use this
     * {@link Executor}. If such an override is required, {@link #publishAndSubscribeOnOverride(Executor)} can be used.
     *
     * @param executor {@link Executor} to use.
     * @return A new {@link Single} that will use the passed {@link Executor} to invoke all methods
     * {@link Subscriber}, {@link Cancellable} and {@link #handleSubscribe(Single.Subscriber)}.
     */
    public final Single<T> publishAndSubscribeOn(Executor executor) {
        return PublishAndSubscribeOnSingles.publishAndSubscribeOn(this, executor);
    }

    /**
     * Creates a new {@link Single} that will use the passed {@link Executor} to invoke the following methods:
     * <ul>
     *     <li>All {@link Subscriber} methods.</li>
     *     <li>All {@link Cancellable} methods.</li>
     *     <li>The {@link #handleSubscribe(Single.Subscriber)} method.</li>
     * </ul>
     * This method overrides preceding {@link Executor}s, if any, specified for {@code this} {@link Single}.
     * That is to say preceding and subsequent operations for this execution chain will use this {@link Executor}.
     * If such an override is not required, {@link #publishAndSubscribeOn(Executor)} can be used.
     *
     * @param executor {@link Executor} to use.
     * @return A new {@link Single} that will use the passed {@link Executor} to invoke all methods of
     * {@link Subscriber}, {@link Cancellable} and {@link #handleSubscribe(Single.Subscriber)} both for the returned
     * {@link Single} as well as {@code this} {@link Single}.
     */
    public final Single<T> publishAndSubscribeOnOverride(Executor executor) {
        return PublishAndSubscribeOnSingles.publishAndSubscribeOnOverride(this, executor);
    }

    /**
     * Creates a realized {@link Single} which always completes successfully with the provided {@code value}.
     *
     * @param value result of the {@link Single}.
     * @param <T>   Type of the {@link Single}.
     *
     * @return A new {@link Single}.
     */
    public static <T> Single<T> success(@Nullable T value) {
        return new SucceededSingle<>(value);
    }

    /**
     * Creates a realized {@link Single} which always completes with the provided error {@code cause}.
     *
     * @param cause result of the {@link Single}.
     * @param <T>   Type of the {@link Single}.
     *
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
    public static <T> Single<T> never() {
        return neverSingle();
    }

    /**
     * Defer creation of a {@link Single} till it is subscribed to.
     *
     * @param singleSupplier {@link Supplier} to create a new {@link Single} for every call to
     * {@link #subscribe(Subscriber)} to the returned {@link Single}.
     * @param <T> Type of the {@link Single}.
     * @return A new {@link Single} that creates a new {@link Single} using {@code singleFactory} for every call to
     * {@link #subscribe(Subscriber)} and forwards the result or error from the newly created {@link Single} to its
     * {@link Subscriber}.
     */
    public static <T> Single<T> defer(Supplier<Single<T>> singleSupplier) {
        return new SingleDefer<>(singleSupplier);
    }

    /**
     * Returns the {@link Executor} used for this {@link Single}.
     *
     * @return {@link Executor} used for this {@link Single} via {@link #Single(Executor)}.
     */
    final Executor getExecutor() {
        return executor;
    }
}
