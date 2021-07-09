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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static io.servicetalk.concurrent.internal.TerminalNotification.error;
import static io.servicetalk.concurrent.test.internal.AwaitUtils.awaitUninterruptibly;
import static io.servicetalk.concurrent.test.internal.AwaitUtils.pollUninterruptibly;
import static io.servicetalk.concurrent.test.internal.AwaitUtils.takeUninterruptibly;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

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
    private final CountDownLatch onTerminalLatch = new CountDownLatch(1);
    private final CountDownLatch onSubscribeLatch = new CountDownLatch(1);
    @Nullable
    private TerminalNotification onTerminal;
    @Nullable
    private Subscription subscription;

    /**
     * Create a new instance.
     */
    public TestPublisherSubscriber() {
        this(0);
    }

    TestPublisherSubscriber(long initialDemand) {
        outstandingDemand = new AtomicLong(initialDemand);
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
        onSubscribeLatch.countDown();
    }

    @Override
    public void onNext(@Nullable final T t) {
        verifyOnSubscribedAndNoTerminal("onNext", t, true);
        if (outstandingDemand.decrementAndGet() < 0) {
            throw new IllegalStateException("Too many onNext signals relative to Subscription request(n). " +
                    "https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#1.1");
        }
        items.add(wrapNull(t));
    }

    @Override
    public void onError(final Throwable t) {
        verifyOnSubscribedAndNoTerminal("onError", t, true);
        onTerminal = error(t);
        onTerminalLatch.countDown();
    }

    @Override
    public void onComplete() {
        verifyOnSubscribedAndNoTerminal("onComplete", null, false);
        onTerminal = complete();
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
     * Consume all currently available {@link #onNext(Object)} signals.
     *
     * @return {@link List} containing all currently available {@link #onNext(Object)} signals.
     */
    public List<T> pollAllOnNext() {
        List<Object> consumedItems = new ArrayList<>();
        items.drainTo(consumedItems);
        return consumedItems.stream().map((Function<Object, T>) TestPublisherSubscriber::unwrapNull).collect(toList());
    }

    /**
     * Blocks for at most {@code timeout} time until the next {@link #onNext(Object)} method invocation.
     *
     * @param timeout The amount of time to wait.
     * @param unit The units of {@code timeout}.
     * @return A {@link Supplier} that returns the signal delivered to {@link #onNext(Object)},
     * or {@code null} if a timeout occurred or a terminal signal has been received.
     */
    @Nullable
    public Supplier<T> pollOnNext(long timeout, TimeUnit unit) {
        Object item = pollUninterruptibly(items, timeout, unit);
        return item == null ? null : () -> unwrapNull(item);
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
            throw new IllegalStateException("wanted onError but Subscriber terminated with onComplete");
        }
        assert onTerminal.cause() != null;
        if (verifyOnNextConsumed) {
            verifyAllOnNextProcessed();
        }
        return onTerminal.cause();
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
            throw new IllegalStateException("wanted onComplete but Subscriber terminated with onError",
                    onTerminal.cause());
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
        if (awaitUninterruptibly(onTerminalLatch, timeout, unit)) {
            assert onTerminal != null;
            return onTerminal == complete() ? () -> null : onTerminal::cause;
        }
        return null;
    }

    private void verifyAllOnNextProcessed() {
        if (!items.isEmpty()) {
            StringBuilder b = new StringBuilder();
            int itemCount = 0;
            Object item;
            while ((item = items.poll()) != null) {
                ++itemCount;
                b.append("[").append(TestPublisherSubscriber.<T>unwrapNull(item)).append("] ");
            }
            throw new IllegalStateException(itemCount + " onNext items were not processed: " + b.toString());
        }
    }

    private static Object wrapNull(@Nullable Object item) {
        return item == null ? NULL_ON_NEXT : item;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static <T> T unwrapNull(Object item) {
        return item == NULL_ON_NEXT ? null : (T) item;
    }
}
