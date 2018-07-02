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
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Executors.newOffloader;
import static java.util.Objects.requireNonNull;

/**
 * An asynchronous computation that does not emit any data. It just completes or emits an error.
 */
public abstract class Completable implements io.servicetalk.concurrent.Completable {
    private static final AtomicReference<BiConsumer<? super Subscriber, Consumer<? super Subscriber>>> SUBSCRIBE_PLUGIN_REF = new AtomicReference<>();
    private static final Object CONVERSION_VALUE = new Object();

    private final Executor executor;

    /**
     * New instance.
     */
    protected Completable() {
        this(immediate());
    }

    /**
     * New instance.
     *
     * @param executor {@link Executor} to use for this {@link Completable}.
     */
    Completable(final Executor executor) {
        this.executor = requireNonNull(executor);
    }

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

    @Override
    public final void subscribe(Subscriber subscriber) {
        // This is a user-driven subscribe i.e. there is no SignalOffloader override, so create a new SignalOffloader
        // to use.
        final SignalOffloader signalOffloader = newOffloader(executor);
        // Since this is a user-driven subscribe (end of the execution chain), offload Cancellable
        subscribe(signalOffloader.offloadCancellable(subscriber), signalOffloader);
    }

    /**
     * Handles a subscriber to this {@code Completable}.
     * <p>
     * This method is invoked internally by {@link Completable} for every call to the
     * {@link Completable#subscribe(Subscriber)} method.
     *
     * @param subscriber the subscriber.
     */
    protected abstract void handleSubscribe(Subscriber subscriber);

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
     * @param subscriber {@link Subscriber} to this {@link Completable}.
     * @param signalOffloader {@link SignalOffloader} to use for this {@link Subscriber}.
     */
    final void subscribe(Subscriber subscriber, SignalOffloader signalOffloader) {
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
    void handleSubscribe(Subscriber subscriber, SignalOffloader signalOffloader) {
        Subscriber safeSubscriber = signalOffloader.offloadSubscriber(subscriber);
        signalOffloader.offloadSignal(safeSubscriber, this::handleSubscribe);
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
        return new MergeCompletable(false, this, executor, other);
    }

    /**
     * Merges this {@link Completable} with the {@code other} {@link Completable}s so that the resulting
     * {@link Completable} terminates successfully when all of these complete or terminates with an error when any one terminates with an error.
     *
     * @param other {@link Completable}s to merge.
     * @return {@link Completable} that terminates successfully when this and all {@code other} {@link Completable}s complete or terminates with an error when any one terminates with an error.
     */
    public final Completable merge(Iterable<? extends Completable> other) {
        return new IterableMergeCompletable(false, this, other, executor);
    }

    /**
     * Merges the passed {@link Publisher} with this {@link Completable}.
     * <p>
     * The resulting {@link Publisher} emits all items emitted by the passed {@link Publisher} and terminates
     * successfully when both this {@link Completable} and the passed {@link Publisher} terminates successfully.
     * It terminates with an error when any one of this {@link Completable} or passed {@link Publisher} terminates with
     * an error.
     *
     * @param mergeWith the {@link Publisher} to merge in
     * @param <T>       The value type of the resulting {@link Publisher}.
     * @return {@link Publisher} that emits all items emitted by the passed {@link Publisher} and terminates
     * successfully when both this {@link Completable} and the passed {@link Publisher} terminates successfully.
     * It terminates with an error when any one of this {@link Completable} or passed {@link Publisher} terminates with
     * an error.
     *
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX merge operator.</a>
     */
    public final <T> Publisher<T> merge(Publisher<T> mergeWith) {
        return new CompletableMergeWithPublisher<>(this, mergeWith, executor);
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
        return new MergeCompletable(true, this, executor, other);
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
        return new IterableMergeCompletable(true, this, other, executor);
    }

    /**
     * Converts this {@code Completable} to a {@link Publisher}.
     * @param value The value to deliver to {@link org.reactivestreams.Subscriber#onNext(Object)} when this {@link Completable} completes.
     *              {@code null} is not allowed.
     * @param <T> The value type of the resulting {@link Publisher}.
     * @return A {@link Publisher} that mirrors the terminal signal from this {@link Completable}.
     */
    public final <T> Publisher<T> toPublisher(@Nullable T value) {
        return toPublisher(() -> value);
    }

    /**
     * Converts this {@code Completable} to a {@link Publisher}.
     * @param valueSupplier A {@link Supplier} that produces the value to deliver to
     * {@link org.reactivestreams.Subscriber#onNext(Object)} when this {@link Completable} completes.
     * @param <T> The value type of the resulting {@link Publisher}.
     * @return A {@link Publisher} that mirrors the terminal signal from this {@link Completable}.
     */
    public final <T> Publisher<T> toPublisher(Supplier<T> valueSupplier) {
        return new CompletableToPublisher<>(this, valueSupplier, executor);
    }

    /**
     * Creates a new {@link Completable} that will mimic the signals of this {@link Completable} but will terminate
     * with a {@link TimeoutException} if time {@code duration} elapses between {@link #subscribe(Subscriber)} and
     * termination. The timer starts when the returned {@link Completable} is {@link #subscribe(Subscriber) subscribed}
     * to.
     * <p>
     * In the event of timeout any {@link Cancellable} from {@link Subscriber#onSubscribe(Cancellable)} will be
     * {@link Cancellable#cancel() cancelled} and the associated {@link Subscriber} will be
     * {@link Subscriber#onError(Throwable) terminated}.
     * @param duration The time duration which is allowed to elapse before {@link Subscriber#onComplete()}.
     * @param unit The units for {@code duration}.
     * @return a new {@link Completable} that will mimic the signals of this {@link Completable} but will terminate with
     * a {@link TimeoutException} if time {@code duration} elapses before {@link Subscriber#onComplete()}.
     * @see <a href="http://reactivex.io/documentation/operators/timeout.html">ReactiveX timeout operator.</a>
     */
    public final Completable timeout(long duration, TimeUnit unit) {
        return timeout(duration, unit, executor);
    }

    /**
     * Creates a new {@link Completable} that will mimic the signals of this {@link Completable} but will terminate
     * with a {@link TimeoutException} if time {@code duration} elapses between {@link #subscribe(Subscriber)} and
     * termination. The timer starts when the returned {@link Completable} is {@link #subscribe(Subscriber) subscribed}
     * to.
     * <p>
     * In the event of timeout any {@link Cancellable} from {@link Subscriber#onSubscribe(Cancellable)} will be
     * {@link Cancellable#cancel() cancelled} and the associated {@link Subscriber} will be
     * {@link Subscriber#onError(Throwable) terminated}.
     * @param duration The time duration which is allowed to elapse before {@link Subscriber#onComplete()}.
     * @param unit The units for {@code duration}.
     * @param timeoutExecutor The {@link Executor} to use for managing the timer notifications.
     * @return a new {@link Completable} that will mimic the signals of this {@link Completable} but will terminate with
     * a {@link TimeoutException} if time {@code duration} elapses before {@link Subscriber#onComplete()}.
     * @see <a href="http://reactivex.io/documentation/operators/timeout.html">ReactiveX timeout operator.</a>
     */
    public final Completable timeout(long duration, TimeUnit unit, Executor timeoutExecutor) {
        return new TimeoutCompletable(this, duration, unit, timeoutExecutor);
    }

    /**
     * Creates a new {@link Completable} that will mimic the signals of this {@link Completable} but will terminate
     * with a {@link TimeoutException} if time {@code duration} elapses between {@link #subscribe(Subscriber)} and
     * termination. The timer starts when the returned {@link Completable} is {@link #subscribe(Subscriber) subscribed}
     * to.
     * <p>
     * In the event of timeout any {@link Cancellable} from {@link Subscriber#onSubscribe(Cancellable)} will be
     * {@link Cancellable#cancel() cancelled} and the associated {@link Subscriber} will be
     * {@link Subscriber#onError(Throwable) terminated}.
     * @param duration The time duration which is allowed to elapse before {@link Subscriber#onComplete()}.
     * @return a new {@link Completable} that will mimic the signals of this {@link Completable} but will terminate with
     * a {@link TimeoutException} if time {@code duration} elapses before {@link Subscriber#onComplete()}.
     * @see <a href="http://reactivex.io/documentation/operators/timeout.html">ReactiveX timeout operator.</a>
     */
    public final Completable timeout(Duration duration) {
        return timeout(duration, executor);
    }

    /**
     * Creates a new {@link Completable} that will mimic the signals of this {@link Completable} but will terminate
     * with a {@link TimeoutException} if time {@code duration} elapses between {@link #subscribe(Subscriber)} and
     * termination. The timer starts when the returned {@link Completable} is {@link #subscribe(Subscriber) subscribed}
     * to.
     * <p>
     * In the event of timeout any {@link Cancellable} from {@link Subscriber#onSubscribe(Cancellable)} will be
     * {@link Cancellable#cancel() cancelled} and the associated {@link Subscriber} will be
     * {@link Subscriber#onError(Throwable) terminated}.
     * @param duration The time duration which is allowed to elapse before {@link Subscriber#onComplete()}.
     * @param timeoutExecutor The {@link Executor} to use for managing the timer notifications.
     * @return a new {@link Completable} that will mimic the signals of this {@link Completable} but will terminate with
     * a {@link TimeoutException} if time {@code duration} elapses before {@link Subscriber#onComplete()}.
     * @see <a href="http://reactivex.io/documentation/operators/timeout.html">ReactiveX timeout operator.</a>
     */
    public final Completable timeout(Duration duration, Executor timeoutExecutor) {
        return new TimeoutCompletable(this, duration, timeoutExecutor);
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
        return new CompletableAndThenCompletable(this, next, executor);
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
        return new CompletableAndThenSingle<>(this, next, executor);
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
        return toSingle().flatMapPublisher(aVoid -> next);
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
        return new CompletableToSingle<>(this, valueSupplier, executor);
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
        return new DoCancellableCompletable(this, onCancel::run, true, executor);
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
        return new DoBeforeSubscriberCompletable(this, subscriberSupplier, executor);
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
        return new DoCancellableCompletable(this, onCancel::run, false, executor);
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
        return new DoAfterFinallyCompletable(this, doFinally, executor);
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
        return new DoAfterSubscriberCompletable(this, subscriberSupplier, executor);
    }

    /**
     * Ignores any error returned by this {@link Completable} and resume to a new {@link Completable}.
     *
     * @param nextFactory Returns the next {@link Completable}, if this {@link Completable} emits an error.
     * @return The new {@link Completable}.
     */
    public final Completable onErrorResume(Function<Throwable, Completable> nextFactory) {
        return new ResumeCompletable(this, nextFactory, executor);
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
     * Re-subscribes to this {@link Completable} when it completes and the {@link Completable} returned by the supplied
     * {@link IntFunction} completes successfully.
     * If the returned {@link Completable} emits an error, the returned {@link Completable} emits an error.
     *
     * @param repeatWhen {@link IntFunction} that given the repeat count returns a {@link Completable}.
     * If this {@link Completable} emits an error repeat is terminated, otherwise, original {@link Completable} is
     * re-subscribed when this {@link Completable} completes.
     *
     * @return A {@link Completable} that completes after all re-subscriptions completes.
     *
     * @see <a href="http://reactivex.io/documentation/operators/retry.html">ReactiveX retry operator.</a>
     */
    public final Completable repeatWhen(IntFunction<Completable> repeatWhen) {
        return toPublisher(CONVERSION_VALUE).repeatWhen(repeatWhen).ignoreElements();
    }

    /**
     * <strong>This method requires advanced knowledge of building operators. Before using this method please attempt
     * to compose existing operator(s) to satisfy your use case.</strong>
     * <p>
     * Returns a {@link Completable} that when {@link Completable#subscribe(Subscriber)} is called the {@code operator}
     * argument will be used to wrap the {@link Subscriber} before subscribing to this {@link Completable}.
     * <pre>{@code
     *     Completable<X> pub = ...;
     *     pub.map(..) // A
     *        .liftSynchronous(original -> modified)
     *        .doAfterFinally(..) // B
     * }</pre>
     * The {@code original -> modified} "operator" <strong>MUST</strong> be "synchronous" in that it does not interact
     * with the original {@link Subscriber} from outside the modified {@link Subscriber} or {@link Cancellable}
     * threads. That is to say this operator will not impact the {@link Executor} constraints already in place between
     * <i>A</i> and <i>B</i> above. If you need asynchronous behavior, or are unsure, see
     * {@link #liftAsynchronous(CompletableOperator)}.
     * @param operator The custom operator logic. The input is the "original" {@link Subscriber} to this
     * {@link Completable} and the return is the "modified" {@link Subscriber} that provides custom operator business
     * logic.
     * @return a {@link Completable} that when {@link Completable#subscribe(Subscriber)} is called the {@code operator}
     * argument will be used to wrap the {@link Subscriber} before subscribing to this {@link Completable}.
     * @see #liftAsynchronous(CompletableOperator)
     */
    public final Completable liftSynchronous(CompletableOperator operator) {
        return new LiftSynchronousCompletableOperator(this, operator, executor);
    }

    /**
     * <strong>This method requires advanced knowledge of building operators. Before using this method please attempt
     * to compose existing operator(s) to satisfy your use case.</strong>
     * <p>
     * Returns a {@link Completable} that when {@link Completable#subscribe(Subscriber)} is called the {@code operator}
     * argument will be used to wrap the {@link Subscriber} before subscribing to this {@link Completable}.
     * <pre>{@code
     *     Publisher<X> pub = ...;
     *     pub.map(..) // A
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
     * {@link Completable} and the return is the "modified" {@link Subscriber} that provides custom operator business
     * logic.
     * @return a {@link Completable} that when {@link Completable#subscribe(Subscriber)} is called the {@code operator}
     * argument will be used to wrap the {@link Subscriber} before subscribing to this {@link Completable}.
     * @see #liftSynchronous(CompletableOperator)
     */
    public final Completable liftAsynchronous(CompletableOperator operator) {
        return new LiftAsynchronousCompletableOperator(this, operator, executor);
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
     * Defer creation of a {@link Completable} till it is subscribed to.
     *
     * @param completableSupplier {@link Supplier} to create a new {@link Completable} for every call to
     * {@link #subscribe(Subscriber)} to the returned {@link Completable}.
     * @return A new {@link Completable} that creates a new {@link Completable} using {@code completableFactory}
     * for every call to {@link #subscribe(Subscriber)} and forwards
     * the termination signal from the newly created {@link Completable} to its {@link Subscriber}.
     */
    public static Completable defer(Supplier<Completable> completableSupplier) {
        return new CompletableDefer(completableSupplier);
    }
}
