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
package io.servicetalk.concurrent.test.internal;

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.internal.FlowControlUtils;
import io.servicetalk.concurrent.internal.TerminalNotification;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static io.servicetalk.concurrent.internal.TerminalNotification.error;
import static java.lang.System.nanoTime;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * A {@link Subscriber} that enqueues {@link #onNext(Object)} and terminal signals while providing blocking methods
 * to consume these events. There are two approaches to using this class:
 * <pre>
 *     TestPublisherSubscriber&lt;String&gt; sub = new TestPublisherSubscriber&lt;&gt;();
 *
 *     // Approach 1 - verify individual items sequentially.
 *     String s = sub.takeOnNext();
 *     // verify s
 *     sub.awaitOnComplete(); // this will verify that all onNext signals have been consumed
 *
 *     // Approach 2 - wait for terminal, verify items in bulk.
 *     sub.awaitOnComplete(false); // wait for the terminal signal, ignore if there are unconsumed onNext signals.
 *     List&lt;String&gt; onNextSignals = sub.pollAllOnNext();
 *     // verify all onNextSignals occurred in the expected order
 * </pre>
 * @param <T> The type of data in {@link #onNext(Object)}.
 */
public final class TestPublisherSubscriber<T> implements Subscriber<T> {
    private static final Object NULL_ON_NEXT = new Object();
    private final AtomicLong outstandingDemand;
    private final BlockingQueue<Object> items = new LinkedBlockingQueue<>();
    private final CountDownLatch onSubscribeLatch = new CountDownLatch(1);
    private final CountDownLatch onTerminalLatch = new CountDownLatch(1);
    @Nullable
    private TerminalNotification onTerminal;
    @Nullable
    private Subscription subscription;
    @Nullable
    private Consumer<T> onNextConsumer;
    @Nullable
    private Consumer<Subscription> onSubscribeConsumer;

    /**
     * Create a new instance.
     */
    public TestPublisherSubscriber() {
        this(0);
    }

    TestPublisherSubscriber(long initialDemand) {
        outstandingDemand = new AtomicLong(initialDemand);
    }

    /**
     * Set a {@link Consumer} that is invoked in {@link PublisherSource.Subscriber#onSubscribe(Subscription)}.
     * @param onSubscribeConsumer a {@link Consumer} that is invoked in
     * {@link PublisherSource.Subscriber#onSubscribe(Subscription)}.
     */
    public void onSubscribeConsumer(@Nullable Consumer<Subscription> onSubscribeConsumer) {
        this.onSubscribeConsumer = onSubscribeConsumer;
    }

    /**
     * Set a {@link Consumer} that is invoked in {@link #onNext(Object)}.
     * @param onNextConsumer a {@link Consumer} that is invoked in {@link #onNext(Object)}.
     */
    public void onNextConsumer(@Nullable Consumer<T> onNextConsumer) {
        this.onNextConsumer = onNextConsumer;
    }

    @Override
    public void onSubscribe(final Subscription subscription) {
        requireNonNull(subscription,
                "Null Subscription is not permitted " +
                        "https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#2.13");
        verifyNoTerminal("onSubscribe", null, false);
        if (this.subscription != null) {
            throw new IllegalStateException("The Subscription has already been set to " + this.subscription +
                    ". New Subscription " + subscription + " is not supported.");
        }
        this.subscription = new Subscription() {
            @Override
            public void request(final long n) {
                try {
                    if (n > 0) { // let negative demand through, we may want to test if the source delivers an error.
                        outstandingDemand.accumulateAndGet(n, FlowControlUtils::addWithOverflowProtection);
                    }
                } finally {
                    subscription.request(n);
                }
            }

            @Override
            public void cancel() {
                subscription.cancel();
            }
        };
        if (onSubscribeConsumer != null) {
            onSubscribeConsumer.accept(this.subscription);
        }
        onSubscribeLatch.countDown();
    }

    @Override
    public void onNext(@Nullable final T t) {
        verifyOnSubscribedAndNoTerminal("onNext", t, true);
        if (outstandingDemand.decrementAndGet() < 0) {
            throw new IllegalStateException("Too many onNext signals relative to Subscription request(n). " +
                    "https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#1.1");
        }
        if (onNextConsumer != null) {
            onNextConsumer.accept(t);
        }
        items.add(wrapNull(t));
    }

    @Override
    public void onError(final Throwable t) {
        verifyOnSubscribedAndNoTerminal("onError", t, true);
        onTerminal = error(t);
        items.add(onTerminal);
        onTerminalLatch.countDown();
    }

    @Override
    public void onComplete() {
        verifyOnSubscribedAndNoTerminal("onComplete", null, false);
        onTerminal = complete();
        items.add(onTerminal);
        onTerminalLatch.countDown();
    }

    private void verifyNoTerminal(String method, @Nullable Object param, boolean useParam) {
        if (onTerminal != null) {
            throw new IllegalStateException("Subscriber has already terminated [" + onTerminal +
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

    /**
     * Block until {@link PublisherSource.Subscriber#onSubscribe(Subscription)}.
     *
     * @return The {@link Subscription} from {@link PublisherSource.Subscriber#onSubscribe(Subscription)}.
     */
    public Subscription awaitSubscription() {
        awaitUninterruptibly(onSubscribeLatch);
        assert subscription != null;
        return subscription;
    }

    /**
     * Block until {@link PublisherSource.Subscriber#onSubscribe(Subscription)}.
     *
     * @return The {@link Subscription} from {@link PublisherSource.Subscriber#onSubscribe(Subscription)}.
     * @throws InterruptedException if the calling thread is interrupted while waiting for signals.
     */
    public Subscription awaitSubscriptionInterruptible() throws InterruptedException {
        onSubscribeLatch.await();
        assert subscription != null;
        return subscription;
    }

    /**
     * Block until {@link PublisherSource.Subscriber#onSubscribe(Subscription)}.
     * @param timeout The amount of time to wait.
     * @param unit The units of {@code timeout}.
     * @return The {@link Subscription} from {@link PublisherSource.Subscriber#onSubscribe(Subscription)}.
     * @throws InterruptedException if the calling thread is interrupted while waiting for signals.
     * @throws TimeoutException if a timeout occurs while waiting.
     */
    public Subscription awaitSubscriptionInterruptible(long timeout, TimeUnit unit) throws InterruptedException,
            TimeoutException {
        if (!onSubscribeLatch.await(timeout, unit)) {
            throwTimeoutException(timeout, unit);
        }
        assert subscription != null;
        return subscription;
    }

    /**
     * Blocks until the next {@link #onNext(Object)} method invocation.
     *
     * @return item delivered to {@link #onNext(Object)}.
     */
    @Nullable
    public T takeOnNext() {
        Object item = takeUninterruptibly(items);
        return unwrapNull(item);
    }

    /**
     * Blocks until the next {@link #onNext(Object)} method invocation.
     *
     * @return item delivered to {@link #onNext(Object)}.
     * @throws InterruptedException if the calling thread is interrupted while waiting for signals.
     */
    @Nullable
    public T takeOnNextInterruptible() throws InterruptedException {
        return unwrapNull(items.take());
    }

    /**
     * Blocks until {@code n} {@link #onNext(Object)} method invocations.
     *
     * @param n The number of {@link #onNext(Object)} to consume.
     * @return A {@link List} containing {@code n} {@link #onNext(Object)} signals.
     */
    public List<T> takeOnNext(final int n) {
        List<T> list = new ArrayList<>(n);
        boolean interrupted = false;
        try {
            for (int i = 0; i < n; ++i) {
                T item;
                do {
                    try {
                        item = unwrapNull(items.take());
                        break;
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
                } while (true);

                list.add(item);
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        return list;
    }

    /**
     * Blocks until {@code n} {@link #onNext(Object)} method invocations.
     *
     * @param n The number of {@link #onNext(Object)} to consume.
     * @return A {@link List} containing {@code n} {@link #onNext(Object)} signals.
     * @throws InterruptedException if the calling thread is interrupted while waiting for signals.
     */
    public List<T> takeOnNextInterruptible(final int n) throws InterruptedException {
        List<T> list = new ArrayList<>(n);
        for (int i = 0; i < n; ++i) {
            list.add(unwrapNull(items.take()));
        }
        return list;
    }

    /**
     * Blocks for at most {@code timeout} time until the next {@link #onNext(Object)} method invocation.
     *
     * @param timeout The amount of time to wait.
     * @param unit The units of {@code timeout}.
     * @return item delivered to {@link #onNext(Object)}, or {@code null} if a timeout occurred.
     * @throws TimeoutException if a timeout occurs while waiting.
     */
    @Nullable
    public T takeOnNext(long timeout, TimeUnit unit) throws TimeoutException {
        Object item = pollUninterruptibly(items, timeout, unit);
        if (item == null) {
            throwTimeoutException(timeout, unit);
        }
        return unwrapNull(item);
    }

    /**
     * Blocks for at most {@code timeout} time until the next {@link #onNext(Object)} method invocation.
     *
     * @param timeout The amount of time to wait.
     * @param unit The units of {@code timeout}.
     * @return item delivered to {@link #onNext(Object)}, or {@code null} if a timeout occurred.
     * @throws InterruptedException if the calling thread is interrupted while waiting for signals.
     * @throws TimeoutException if a timeout occurs while waiting.
     */
    @Nullable
    public T takeOnNextInterruptible(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        Object item = items.poll(timeout, unit);
        if (item == null) {
            throwTimeoutException(timeout, unit);
        }
        return unwrapNull(item);
    }

    /**
     * Blocks until {@code n} {@link #onNext(Object)} method invocations.
     *
     * @param n The number of {@link #onNext(Object)} to consume.
     * @param timeout The amount of time to wait.
     * @param unit The units of {@code timeout}.
     * @return A {@link List} containing {@code n} {@link #onNext(Object)} signals.
     * @throws InterruptedException if the calling thread is interrupted while waiting for signals.
     * @throws TimeoutException if a timeout occurs while waiting.
     */
    public List<T> takeOnNextInterruptible(final int n, final long timeout, final TimeUnit unit)
            throws InterruptedException, TimeoutException {
        List<T> list = new ArrayList<>(n);
        final long startTime = nanoTime();
        final long timeoutNanos = NANOSECONDS.convert(timeout, unit);
        long waitTime = timeout;
        for (int i = 0; i < n; ++i) {
            Object next = items.poll(waitTime, NANOSECONDS);
            if (next == null) {
                throw new TimeoutException("timeout after: " + timeout + " " + unit +
                        ". pending signals: " + list);
            }
            list.add(unwrapNull(next));
            waitTime = timeoutNanos - (nanoTime() - startTime);
        }
        return list;
    }

    /**
     * Consume all currently available {@link #onNext(Object)} signals.
     *
     * @return {@link List} containing all currently available {@link #onNext(Object)} signals.
     */
    public List<T> pollAllOnNext() {
        List<T> filteredItems = new ArrayList<>();
        Object item;
        while ((item = items.peek()) != null) {
            if (item instanceof TerminalNotification) {
                break;
            }
            item = items.poll();
            assert item != null;
            filteredItems.add(unwrapNull(item));
        }
        return filteredItems;
    }

    /**
     * Blocks for at most {@code timeout} time until the next {@link #onNext(Object)} method invocation.
     *
     * @param timeout The amount of time to wait.
     * @param unit The units of {@code timeout}.
     * @return item delivered to {@link #onNext(Object)}, or {@code null} if a timeout occurred or a terminal signal
     * has been received.
     */
    @Nullable
    public Supplier<T> pollOnNext(long timeout, TimeUnit unit) {
        return pollOnNextFilter(pollUninterruptibly(items, timeout, unit));
    }

    /**
     * Blocks for at most {@code timeout} time until the next {@link #onNext(Object)} method invocation.
     *
     * @param timeout The amount of time to wait.
     * @param unit The units of {@code timeout}.
     * @return item delivered to {@link #onNext(Object)}, or {@code null} if a timeout occurred or a terminal signal
     * has been received.
     * @throws InterruptedException if the calling thread is interrupted while waiting for signals.
     */
    @Nullable
    public Supplier<T> pollOnNextInterruptible(long timeout, TimeUnit unit) throws InterruptedException {
        return pollOnNextFilter(items.poll(timeout, unit));
    }

    @Nullable
    private Supplier<T> pollOnNextFilter(@Nullable Object item) {
        if (item instanceof TerminalNotification) {
            items.add(item);
            return null;
        }
        return pollWrapSupplier(item);
    }

    /**
     * Block until a terminal signal is received, throws if {@link #onComplete()} and returns normally if
     * {@link #onError(Throwable)}. This method will verify that all {@link #onNext(Object)} signals have been
     * consumed.
     *
     * @return the exception received by {@link #onError(Throwable)}.
     */
    public Throwable awaitOnError() {
        return awaitOnError(true);
    }

    /**
     * Block until a terminal signal is received, throws if {@link #onComplete()} and returns normally if
     * {@link #onError(Throwable)}.
     *
     * @param verifyOnNextConsumed {@code true} to verify that all {@link #onNext(Object)} signals have been consumed
     * and throw if not. {@code false} to ignore if {@link #onNext(Object)} signals have been consumed or not.
     * @return the exception received by {@link #onError(Throwable)}.
     */
    Throwable awaitOnError(boolean verifyOnNextConsumed) {
        awaitUninterruptibly(onTerminalLatch);
        assert onTerminal != null;
        if (onTerminal == complete()) {
            throw new IllegalStateException("expected onError, actual onComplete");
        }
        final Throwable cause = onTerminal.cause();
        assert cause != null;
        if (verifyOnNextConsumed) {
            verifyAllOnNextProcessed();
        }
        return cause;
    }

    /**
     * Block until a terminal signal is received, throws if {@link #onComplete()} and returns normally if
     * {@link #onError(Throwable)}. This method will verify that all {@link #onNext(Object)} signals have been
     * consumed.
     *
     * @return the exception received by {@link #onError(Throwable)}.
     * @throws InterruptedException if the calling thread is interrupted while waiting for signals.
     */
    public Throwable takeOnErrorInterruptible() throws InterruptedException {
        return takeOnErrorInterruptible(true);
    }

    /**
     * Block until a terminal signal is received, throws if {@link #onComplete()} and returns normally if
     * {@link #onError(Throwable)}. This method will verify that all {@link #onNext(Object)} signals have been
     * consumed.
     *
     * @param timeout The amount of time to wait.
     * @param unit The units of {@code timeout}.
     * @return the exception received by {@link #onError(Throwable)}.
     * @throws InterruptedException if the calling thread is interrupted while waiting for signals.
     * @throws TimeoutException if a timeout occurs while waiting.
     */
    public Throwable takeOnErrorInterruptible(long timeout, TimeUnit unit)
            throws InterruptedException, TimeoutException {
        return takeOnErrorInterruptible(true, timeout, unit);
    }

    /**
     * Block until a terminal signal is received, throws if {@link #onComplete()} and returns normally if
     * {@link #onError(Throwable)}.
     * @param verifyOnNextConsumed {@code true} to verify that all {@link #onNext(Object)} signals have been consumed
     * and throw if not. {@code false} to ignore if {@link #onNext(Object)} signals have been consumed or not.
     * @return the exception received by {@link #onError(Throwable)}.
     * @throws InterruptedException if the calling thread is interrupted while waiting for signals.
     */
    public Throwable takeOnErrorInterruptible(boolean verifyOnNextConsumed) throws InterruptedException {
        if (verifyOnNextConsumed) {
            return takeOnErrorAssertConditions(items.take(), true);
        }
        onTerminalLatch.await();
        assert onTerminal != null;
        return takeOnErrorAssertConditions(onTerminal, false);
    }

    /**
     * Block until a terminal signal is received, throws if {@link #onComplete()} and returns normally if
     * {@link #onError(Throwable)}.
     * @param timeout The amount of time to wait.
     * @param unit The units of {@code timeout}.
     * @param verifyOnNextConsumed {@code true} to verify that all {@link #onNext(Object)} signals have been consumed
     * and throw if not. {@code false} to ignore if {@link #onNext(Object)} signals have been consumed or not.
     * @return the exception received by {@link #onError(Throwable)}.
     * @throws InterruptedException if the calling thread is interrupted while waiting for signals.
     * @throws TimeoutException if a timeout occurs while waiting.
     */
    public Throwable takeOnErrorInterruptible(boolean verifyOnNextConsumed, long timeout, TimeUnit unit)
            throws InterruptedException, TimeoutException {
        if (verifyOnNextConsumed) {
            Object item = items.poll(timeout, unit);
            if (item == null) {
                throwTimeoutException(timeout, unit);
            }
            return takeOnErrorAssertConditions(item, true);
        }
        if (!onTerminalLatch.await(timeout, unit)) {
            throwTimeoutException(timeout, unit);
        }
        assert onTerminal != null;
        return takeOnErrorAssertConditions(onTerminal, false);
    }

    private Throwable takeOnErrorAssertConditions(Object item, boolean verifyOnNextConsumed) {
        if (item == complete()) {
            throw new IllegalStateException("expected onError, actual onComplete");
        } else if (!(item instanceof TerminalNotification)) {
            throw new IllegalStateException("expected onError, actual onNext: " + item);
        }
        final Throwable cause = ((TerminalNotification) item).cause();
        assert cause != null;
        if (verifyOnNextConsumed) {
            verifyAllOnNextProcessed();
        }
        return cause;
    }

    /**
     * Block until a terminal signal is received, throws if {@link #onError(Throwable)} and returns normally if
     * {@link #onComplete()}. This method will verify that all {@link #onNext(Object)} signals have been consumed.
     */
    public void awaitOnComplete() {
        awaitOnComplete(true);
    }

    /**
     * Block until a terminal signal is received, throws if {@link #onError(Throwable)} and returns normally if
     * {@link #onComplete()}.
     *
     * @param verifyOnNextConsumed {@code true} to verify that all {@link #onNext(Object)} signals have been consumed
     * and throw if not. {@code false} to ignore if {@link #onNext(Object)} signals have been consumed or not.
     */
    void awaitOnComplete(boolean verifyOnNextConsumed) {
        awaitUninterruptibly(onTerminalLatch);
        assert onTerminal != null;
        if (onTerminal != complete()) {
            throw new IllegalStateException("expected onComplete, actual onError", onTerminal.cause());
        }
        if (verifyOnNextConsumed) {
            verifyAllOnNextProcessed();
        }
    }

    /**
     * Block until a terminal signal is received, throws if {@link #onError(Throwable)} and returns normally if
     * {@link #onComplete()}. This method will verify that all {@link #onNext(Object)} signals have been consumed.
     * @throws InterruptedException if the calling thread is interrupted while waiting for signals.
     */
    public void takeOnCompleteInterruptible() throws InterruptedException {
        takeOnCompleteInterruptible(true);
    }

    /**
     * Block until a terminal signal is received, throws if {@link #onError(Throwable)} and returns normally if
     * {@link #onComplete()}. This method will verify that all {@link #onNext(Object)} signals have been consumed.
     * @param timeout The amount of time to wait.
     * @param unit The units of {@code timeout}.
     * @throws InterruptedException if the calling thread is interrupted while waiting for signals.
     * @throws TimeoutException if a timeout occurs while waiting.
     */
    public void takeOnCompleteInterruptible(long timeout, TimeUnit unit)
            throws InterruptedException, TimeoutException {
        takeOnCompleteInterruptible(true, timeout, unit);
    }

    /**
     * Block until a terminal signal is received, throws if {@link #onError(Throwable)} and returns normally if
     * {@link #onComplete()}. This method will verify that all {@link #onNext(Object)} signals have been consumed.
     * @param verifyOnNextConsumed {@code true} to verify that all {@link #onNext(Object)} signals have been consumed
     * and throw if not. {@code false} to ignore if {@link #onNext(Object)} signals have been consumed or not.
     * @throws InterruptedException if the calling thread is interrupted while waiting for signals.
     */
    public void takeOnCompleteInterruptible(boolean verifyOnNextConsumed) throws InterruptedException {
        if (verifyOnNextConsumed) {
            takeOnCompleteInterruptible(items.take(), true);
        } else {
            onTerminalLatch.await();
            assert onTerminal != null;
            takeOnCompleteInterruptible(onTerminal, false);
        }
    }

    /**
     * Block until a terminal signal is received, throws if {@link #onError(Throwable)} and returns normally if
     * {@link #onComplete()}.
     * @param verifyOnNextConsumed {@code true} to verify that all {@link #onNext(Object)} signals have been consumed
     * and throw if not. {@code false} to ignore if {@link #onNext(Object)} signals have been consumed or not.
     * @param timeout The amount of time to wait.
     * @param unit The units of {@code timeout}.
     * @throws InterruptedException if the calling thread is interrupted while waiting for signals.
     * @throws TimeoutException if a timeout occurs while waiting.
     */
    public void takeOnCompleteInterruptible(boolean verifyOnNextConsumed, long timeout, TimeUnit unit)
            throws InterruptedException, TimeoutException {
        if (verifyOnNextConsumed) {
            Object item = items.poll(timeout, unit);
            if (item == null) {
                throwTimeoutException(timeout, unit);
            }
            takeOnCompleteInterruptible(item, true);
        } else {
            if (!onTerminalLatch.await(timeout, unit)) {
                throwTimeoutException(timeout, unit);
            }
            assert onTerminal != null;
            takeOnCompleteInterruptible(onTerminal, false);
        }
    }

    private void takeOnCompleteInterruptible(Object item, boolean verifyOnNextConsumed) {
        if (!(item instanceof TerminalNotification)) {
            throw new IllegalStateException("expected onComplete, actual onNext: " + item);
        } else if (item != complete()) {
            throw new IllegalStateException("expected onComplete, actual onError",
                    ((TerminalNotification) item).cause());
        }

        if (verifyOnNextConsumed) {
            verifyAllOnNextProcessed();
        }
    }

    /**
     * Block for a terminal event.
     *
     * @param timeout The duration of time to wait.
     * @param unit The unit of time to apply to the duration.
     * @return {@code null} if a the timeout expires before a terminal event is received. A non-{@code null}
     * {@link Supplier} that returns {@code null} if {@link #onComplete()}, or the {@link Throwable} from
     * {@link #onError(Throwable)}.
     */
    @Nullable
    public Supplier<Throwable> pollTerminal(long timeout, TimeUnit unit) {
        return awaitUninterruptibly(onTerminalLatch, timeout, unit) ? wrapTerminal() : null;
    }

    /**
     * Block for a terminal event.
     *
     * @param timeout The duration of time to wait.
     * @param unit The unit of time to apply to the duration.
     * @return {@code null} if a the timeout expires before a terminal event is received. A non-{@code null}
     * {@link Supplier} that returns {@code null} if {@link #onComplete()}, or the {@link Throwable} from
     * {@link #onError(Throwable)}.
     * @throws InterruptedException if the calling thread is interrupted while waiting for signals.
     */
    @Nullable
    public Supplier<Throwable> pollTerminalInterruptible(long timeout, TimeUnit unit) throws InterruptedException {
        return onTerminalLatch.await(timeout, unit) ? wrapTerminal() : null;
    }

    private Supplier<Throwable> wrapTerminal() {
        assert onTerminal != null;
        return onTerminal == complete() ? () -> null : onTerminal::cause;
    }

    private void verifyAllOnNextProcessed() {
        if (!items.isEmpty() && !(items.peek() instanceof TerminalNotification)) {
            StringBuilder b = new StringBuilder();
            int itemCount = 0;
            Object item;
            while ((item = items.poll()) != null) {
                if (item instanceof TerminalNotification) {
                    continue;
                }
                ++itemCount;
                b.append("[").append(TestPublisherSubscriber.<T>unwrapNull(item)).append("] ");
            }
            throw new IllegalStateException(itemCount + " onNext items were not processed: " + b.toString());
        }
    }

    @Nullable
    private static <T> Supplier<T> pollWrapSupplier(@Nullable Object item) {
        return item == null ? null : () -> unwrapNull(item);
    }

    private static Object wrapNull(@Nullable Object item) {
        return item == null ? NULL_ON_NEXT : item;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static <T> T unwrapNull(Object item) {
        if (item instanceof TerminalNotification) {
            if (item == complete()) {
                throw new IllegalStateException("expecting onNext, actual onComplete");
            }
            throw new IllegalStateException("expecting onNext, actual onError", ((TerminalNotification) item).cause());
        }
        return item == NULL_ON_NEXT ? null : (T) item;
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        try {
            do {
                try {
                    latch.await();
                    return;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            } while (true);
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static boolean awaitUninterruptibly(CountDownLatch latch, long timeout, TimeUnit unit) {
        final long startTime = nanoTime();
        final long timeoutNanos = NANOSECONDS.convert(timeout, unit);
        long waitTime = timeoutNanos;
        boolean interrupted = false;
        try {
            do {
                try {
                    return latch.await(waitTime, NANOSECONDS);
                } catch (InterruptedException e) {
                    interrupted = true;
                }
                waitTime = timeoutNanos - (nanoTime() - startTime);
                if (waitTime <= 0) {
                    return true;
                }
            } while (true);
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static <T> T takeUninterruptibly(BlockingQueue<T> queue) {
        boolean interrupted = false;
        try {
            do {
                try {
                    return queue.take();
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            } while (true);
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Nullable
    private static <T> T pollUninterruptibly(BlockingQueue<T> queue, long timeout, TimeUnit unit) {
        final long startTime = nanoTime();
        final long timeoutNanos = NANOSECONDS.convert(timeout, unit);
        long waitTime = timeout;
        boolean interrupted = false;
        try {
            do {
                try {
                    return queue.poll(waitTime, NANOSECONDS);
                } catch (InterruptedException e) {
                    interrupted = true;
                }
                waitTime = timeoutNanos - (nanoTime() - startTime);
                if (waitTime <= 0) {
                    return null;
                }
            } while (true);
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void throwTimeoutException(long timeout, TimeUnit unit) throws TimeoutException {
        throw new TimeoutException("timeout after: " + timeout + " " + unit);
    }
}
