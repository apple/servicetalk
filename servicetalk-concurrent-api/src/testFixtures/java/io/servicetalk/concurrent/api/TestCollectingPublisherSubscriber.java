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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.internal.TerminalNotification;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static io.servicetalk.concurrent.internal.TerminalNotification.error;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

/**
 * A {@link Subscriber} that enqueues {@link #onNext(Object)} and terminal signals while providing blocking methods
 * to consume these events. There are two approaches to using this class:
 * <pre>
 *     TestCollectingPublisherSubscriber&lt;String&gt; sub = new TestCollectingPublisherSubscriber&lt;&gt;();
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
public final class TestCollectingPublisherSubscriber<T> implements Subscriber<T> {
    private static final Object NULL_ON_NEXT = new Object();
    private final BlockingQueue<Object> items = new LinkedBlockingQueue<>();
    private final CountDownLatch onTerminalLatch = new CountDownLatch(1);
    private final CountDownLatch onSubscribeLatch = new CountDownLatch(1);
    @Nullable
    private TerminalNotification onTerminal;
    @Nullable
    private Subscription subscription;

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
        this.subscription = subscription;
        onSubscribeLatch.countDown();
    }

    @Override
    public void onNext(@Nullable final T t) {
        verifyNoTerminal("onNext", t, true);
        verifyOnSubscribed();
        items.add(t == null ? NULL_ON_NEXT : t);
    }

    @Override
    public void onError(final Throwable t) {
        verifyTerminalConditions("onError", t, true);
        onTerminal = error(t);
        onTerminalLatch.countDown();
    }

    @Override
    public void onComplete() {
        verifyTerminalConditions("onComplete", null, false);
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

    private void verifyTerminalConditions(String method, @Nullable Object param, boolean useParam) {
        verifyNoTerminal(method, param, useParam);
        verifyOnSubscribed();
    }

    private void verifyOnSubscribed() {
        if (subscription == null) {
            throw new IllegalStateException("onSubscribe must be called before any other signals. " +
                    "https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#1.9");
        }
    }

    /**
     * Block until {@link #onSubscribe(Subscription)}.
     *
     * @return The {@link Subscription} from {@link #onSubscribe(Subscription)}.
     * @throws InterruptedException if an interrupt occurs while blocking for waiting for
     * {@link #onSubscribe(Subscription)}.
     */
    public Subscription awaitSubscription() throws InterruptedException {
        onSubscribeLatch.await();
        assert subscription != null;
        return subscription;
    }

    /**
     * Blocks until the next {@link #onNext(Object)} method invocation.
     *
     * @return item delivered to {@link #onNext(Object)}.
     * @throws InterruptedException if an interrupt occurs while blocking for the next item.
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public T takeOnNext() throws InterruptedException {
        Object item = items.take();
        return item == NULL_ON_NEXT ? null : (T) item;
    }

    /**
     * Consume all currently available {@link #onNext(Object)} signals.
     *
     * @return {@link List} containing all currently available {@link #onNext(Object)} signals.
     */
    @SuppressWarnings("unchecked")
    public List<T> pollAllOnNext() {
        List<Object> consumedItems = new ArrayList<>();
        items.drainTo(consumedItems);
        return consumedItems.stream().map(item -> item == NULL_ON_NEXT ? null : (T) item).collect(toList());
    }

    /**
     * Block until a terminal signal is received, throws if {@link #onComplete()} and returns normally if
     * {@link #onError(Throwable)}. This method will verify that all {@link #onNext(Object)} signals have been
     * consumed.
     *
     * @return the exception received by {@link #onError(Throwable)}.
     * @throws InterruptedException If an interrupt occurs while blocking for the terminal event.
     */
    public Throwable awaitOnError() throws InterruptedException {
        return awaitOnError(true);
    }

    /**
     * Block until a terminal signal is received, throws if {@link #onComplete()} and returns normally if
     * {@link #onError(Throwable)}.
     *
     * @param verifyOnNextConsumed {@code true} to verify that all {@link #onNext(Object)} signals have been consumed
     * and throw if not. {@code false} to ignore if {@link #onNext(Object)} signals have been consumed or not.
     * @return the exception received by {@link #onError(Throwable)}.
     * @throws InterruptedException If an interrupt occurs while blocking for the terminal event.
     */
    public Throwable awaitOnError(boolean verifyOnNextConsumed) throws InterruptedException {
        onTerminalLatch.await();
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
     *
     * @throws InterruptedException If an interrupt occurs while blocking for the terminal event.
     */
    public void awaitOnComplete() throws InterruptedException {
        awaitOnComplete(true);
    }

    /**
     * Block until a terminal signal is received, throws if {@link #onError(Throwable)} and returns normally if
     * {@link #onComplete()}.
     *
     * @param verifyOnNextConsumed {@code true} to verify that all {@link #onNext(Object)} signals have been consumed
     * and throw if not. {@code false} to ignore if {@link #onNext(Object)} signals have been consumed or not.
     * @throws InterruptedException If an interrupt occurs while blocking for the terminal event.
     */
    public void awaitOnComplete(boolean verifyOnNextConsumed) throws InterruptedException {
        onTerminalLatch.await();
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
     * @return {@code true} if a terminal event has been received before the timeout duration.
     * @throws InterruptedException If an interrupt occurs while blocking for the terminal event.
     */
    public boolean pollTerminal(long timeout, TimeUnit unit) throws InterruptedException {
        return onTerminalLatch.await(timeout, unit);
    }

    private void verifyAllOnNextProcessed() {
        if (!items.isEmpty()) {
            StringBuilder b = new StringBuilder();
            int itemCount = 0;
            Object item;
            while ((item = items.poll()) != null) {
                ++itemCount;
                b.append("[").append(item == NULL_ON_NEXT ? null : item).append("] ");
            }
            throw new IllegalStateException(itemCount + " onNext items were not processed: " + b.toString());
        }
    }
}
