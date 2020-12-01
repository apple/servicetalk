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
package io.servicetalk.concurrent.test;

import io.servicetalk.concurrent.Executor;
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static java.lang.Math.min;
import static java.lang.System.nanoTime;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

final class DefaultPublisherFirstStep<T> implements PublisherFirstStep<T> {
    private final PublisherSource<T> source;
    private final Queue<Event<TestPublisherSubscriber<T>>> events;

    DefaultPublisherFirstStep(final PublisherSource<T> source) {
        this.source = requireNonNull(source);
        this.events = new ArrayDeque<>();
    }

    @Override
    public PublisherStep<T> expectSubscription(final Consumer<? super Subscription> sPredicate) {
        events.add(new Event<TestPublisherSubscriber<T>>() {
            @Override
            void verify(final TestPublisherSubscriber<T> subscriber) throws Exception {
                sPredicate.accept(subscriber.awaitSubscriptionInterruptible());
            }

            @Override
            void verify(final TestPublisherSubscriber<T> subscriber, final long timeout, final TimeUnit unit)
                    throws Exception {
                sPredicate.accept(subscriber.awaitSubscriptionInterruptible(timeout, unit));
            }
        });
        return this;
    }

    @Override
    public PublisherStep<T> expectNext(@Nullable final T signal) {
        events.add(new Event<TestPublisherSubscriber<T>>() {
            @Override
            void verify(final TestPublisherSubscriber<T> subscriber) throws Exception {
                doVerify(subscriber, subscriber::takeOnNextInterruptible);
            }

            @Override
            void verify(final TestPublisherSubscriber<T> subscriber, final long timeout, final TimeUnit unit)
                    throws Exception {
                doVerify(subscriber, () -> subscriber.takeOnNext(timeout, unit));
            }

            private void doVerify(TestPublisherSubscriber<T> subscriber, CheckedSupplier<T> supplier) throws Exception {
                subscriber.awaitSubscriptionInterruptible().request(1);
                verifyOnNext(signal, supplier.get());
            }
        });
        return this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public PublisherStep<T> expectNext(final T... signals) {
        return expectNext(asList(signals));
    }

    private static <T> void verifyOnNext(@Nullable final T expected, @Nullable final T actual) {
        if (Event.notEqualsOnNext(expected, actual)) {
            throw new AssertionError("expectNext(T) failed. expected: " + expected + " actual: " + actual);
        }
    }

    @Override
    public PublisherStep<T> expectNext(final Iterable<? extends T> signals) {
        requireNonNull(signals);
        events.add(new Event<TestPublisherSubscriber<T>>() {
            @Override
            void verify(final TestPublisherSubscriber<T> subscriber) throws Exception {
                int i = 0;
                for (T signal : signals) {
                    subscriber.awaitSubscriptionInterruptible().request(1);
                    T next = subscriber.takeOnNext();
                    verifyOnNext(signal, next, i++);
                }
            }

            @Override
            void verify(final TestPublisherSubscriber<T> subscriber, final long timeout, final TimeUnit unit)
                    throws Exception {
                final long startTime = nanoTime();
                final long timeoutNanos = NANOSECONDS.convert(timeout, unit);
                long waitTime = timeout;
                int i = 0;
                for (T signal : signals) {
                    subscriber.awaitSubscriptionInterruptible().request(1);
                    T next = subscriber.takeOnNextInterruptible(waitTime, NANOSECONDS);
                    verifyOnNext(signal, next, i++);
                    waitTime = timeoutNanos - (nanoTime() - startTime);
                }
            }
        });
        return this;
    }

    @Override
    public PublisherStep<T> expectNext(final Collection<? extends T> signals) {
        requireNonNull(signals);
        if (signals.isEmpty()) {
            throw new IllegalArgumentException("signals must be non-empty");
        }
        events.add(new Event<TestPublisherSubscriber<T>>() {
            @Override
            void verify(final TestPublisherSubscriber<T> subscriber) throws Exception {
                doVerify(subscriber, () -> subscriber.takeOnNextInterruptible(signals.size()));
            }

            @Override
            void verify(final TestPublisherSubscriber<T> subscriber, final long timeout, final TimeUnit unit)
                    throws Exception {
                doVerify(subscriber, () -> subscriber.takeOnNextInterruptible(signals.size(), timeout, unit));
            }

            private void doVerify(TestPublisherSubscriber<T> subscriber, CheckedSupplier<List<T>> listSupplier)
                    throws Exception {
                subscriber.awaitSubscriptionInterruptible().request(signals.size());
                List<T> actualSignals = listSupplier.get();
                assert actualSignals != null;
                int i = 0;
                for (T signal : signals) {
                    T next = actualSignals.get(i);
                    verifyOnNext(signal, next, i++);
                }
            }
        });
        return this;
    }

    private static <T> void verifyOnNext(@Nullable final T expected, @Nullable final T actual, int i) {
        if (Event.notEqualsOnNext(expected, actual)) {
            throw new AssertionError("expectNext(T..) failed at signal " + i + " expected: " + expected +
                    " actual: " + actual);
        }
    }

    @Override
    public PublisherStep<T> expectNext(final Consumer<? super T> signalConsumer) {
        requireNonNull(signalConsumer);
        events.add(new Event<TestPublisherSubscriber<T>>() {
            @Override
            void verify(final TestPublisherSubscriber<T> subscriber) throws InterruptedException {
                subscriber.awaitSubscriptionInterruptible().request(1);
                T next = subscriber.takeOnNextInterruptible();
                signalConsumer.accept(next);
            }

            @Override
            void verify(final TestPublisherSubscriber<T> subscriber, final long timeout, final TimeUnit unit)
                    throws InterruptedException, TimeoutException {
                subscriber.awaitSubscriptionInterruptible().request(1);
                T next = subscriber.takeOnNext(timeout, unit);
                signalConsumer.accept(next);
            }
        });
        return this;
    }

    @Override
    public PublisherStep<T> expectNext(final int n, final Consumer<? super Iterable<? extends T>> signalsConsumer) {
        requireNonNull(signalsConsumer);
        if (n <= 0) {
            throw new IllegalArgumentException("n: " + n + " (expected >0)");
        }
        events.add(new Event<TestPublisherSubscriber<T>>() {
            @Override
            void verify(final TestPublisherSubscriber<T> subscriber) throws Exception {
                doVerify(subscriber, () -> subscriber.takeOnNextInterruptible(n));
            }

            @Override
            void verify(final TestPublisherSubscriber<T> subscriber, final long timeout, final TimeUnit unit)
                    throws Exception {
                doVerify(subscriber, () -> subscriber.takeOnNextInterruptible(n, timeout, unit));
            }

            private void doVerify(TestPublisherSubscriber<T> subscriber, CheckedSupplier<List<T>> listSupplier)
                    throws Exception {
                subscriber.awaitSubscriptionInterruptible().request(n);
                List<T> actualSignals = listSupplier.get();
                signalsConsumer.accept(actualSignals);
            }
        });
        return this;
    }

    @Override
    public PublisherStep<T> expectNextCount(long n) {
        do {
            expectNext((int) min(Integer.MAX_VALUE, n), iterable -> { });
            n -= Integer.MAX_VALUE;
        } while (n > 0);

        return this;
    }

    @Override
    public PublisherStep<T> thenRequest(long n) {
        events.add(new Event<TestPublisherSubscriber<T>>() {
            @Override
            void verify(TestPublisherSubscriber<T> subscriber) {
                subscriber.awaitSubscription().request(n);
            }

            @Override
            void verify(TestPublisherSubscriber<T> subscriber, long timeout, TimeUnit unit) {
                subscriber.awaitSubscription().request(n);
            }
        });
        return this;
    }

    @Override
    public PublisherStep<T> expectNoNextOrTerminal(Duration duration) {
        events.add(new Event<TestPublisherSubscriber<T>>() {
            @Override
            void verify(TestPublisherSubscriber<T> subscriber) throws Exception {
                doVerify(subscriber, duration.toNanos());
            }

            @Override
            void verify(TestPublisherSubscriber<T> subscriber, long timeout, TimeUnit unit) throws Exception {
                doVerify(subscriber, min(NANOSECONDS.convert(timeout, unit), duration.toNanos()));
            }

            private void doVerify(TestPublisherSubscriber<T> subscriber, long timeoutNs) throws Exception {
                subscriber.awaitSubscription().request(1);
                Supplier<T> onNextSupplier = subscriber.pollOnNextInterruptible(timeoutNs, NANOSECONDS);
                if (onNextSupplier != null) {
                    throw new IllegalStateException("expected no signals, actual onNext: " + onNextSupplier.get());
                }
                verifyNoTerminal(subscriber.pollTerminalInterruptible(0, NANOSECONDS));
            }
        });
        return this;
    }

    @Override
    public StepVerifier expectError(final Predicate<Throwable> errorPredicate) {
        return expectError(errorPredicate, true);
    }

    @Override
    public StepVerifier expectError(final Predicate<Throwable> errorPredicate, final boolean verifySignalsConsumed) {
        return expectError(t -> {
            if (!errorPredicate.test(t)) {
                throw new AssertionError("errorPredicate failed on: " + t);
            }
        }, verifySignalsConsumed);
    }

    @Override
    public StepVerifier expectError(final Class<? extends Throwable> errorClass) {
        return expectError(errorClass, true);
    }

    @Override
    public StepVerifier expectError(final Class<? extends Throwable> errorClass, final boolean verifySignalsConsumed) {
        return expectError(t -> {
            if (!errorClass.isInstance(t)) {
                throw new AssertionError("expected class: " + errorClass + " actual: " + t.getClass());
            }
        }, verifySignalsConsumed);
    }

    @Override
    public StepVerifier expectError(final Consumer<Throwable> errorConsumer) {
        return expectError(errorConsumer, true);
    }

    @Override
    public StepVerifier expectError(final Consumer<Throwable> errorConsumer, boolean verifySignalsConsumed) {
        requireNonNull(errorConsumer);
        events.add(new Event<TestPublisherSubscriber<T>>() {
            @Override
            void verify(final TestPublisherSubscriber<T> subscriber) throws Exception {
                doVerify(subscriber, () -> subscriber.takeOnErrorInterruptible(verifySignalsConsumed));
            }

            @Override
            void verify(final TestPublisherSubscriber<T> subscriber, final long timeout, final TimeUnit unit)
                    throws Exception {
                doVerify(subscriber, () -> subscriber.takeOnErrorInterruptible(verifySignalsConsumed, timeout, unit));
            }

            private void doVerify(TestPublisherSubscriber<T> subscriber, CheckedSupplier<Throwable> causeSupplier)
                    throws Exception {
                subscriber.awaitSubscriptionInterruptible().request(Long.MAX_VALUE);
                Throwable error = causeSupplier.get();
                errorConsumer.accept(error);
            }
        });
        return new PublisherStepVerifier<>(source, events);
    }

    @Override
    public StepVerifier expectComplete() {
        return expectComplete(true);
    }

    @Override
    public StepVerifier expectComplete(boolean verifySignalsConsumed) {
        events.add(new Event<TestPublisherSubscriber<T>>() {
            @Override
            void verify(final TestPublisherSubscriber<T> subscriber) throws Exception {
                subscriber.awaitSubscriptionInterruptible().request(Long.MAX_VALUE);
                subscriber.takeOnCompleteInterruptible(verifySignalsConsumed);
            }

            @Override
            void verify(final TestPublisherSubscriber<T> subscriber, final long timeout, final TimeUnit unit)
                    throws Exception {
                subscriber.awaitSubscriptionInterruptible().request(Long.MAX_VALUE);
                subscriber.takeOnCompleteInterruptible(verifySignalsConsumed, timeout, unit);
            }
        });
        return new PublisherStepVerifier<>(source, events);
    }

    @Override
    public StepVerifier thenCancel() {
        events.add(new Event<TestPublisherSubscriber<T>>() {
            @Override
            void verify(TestPublisherSubscriber<T> subscriber) {
                subscriber.awaitSubscription().cancel();
            }

            @Override
            void verify(TestPublisherSubscriber<T> subscriber, long timeout, TimeUnit unit) {
                subscriber.awaitSubscription().cancel();
            }
        });
        return new PublisherStepVerifier<>(source, events);
    }

    private static final class PublisherStepVerifier<T> extends
                                               DefaultStepVerifier<PublisherSource<T>, TestPublisherSubscriber<T>> {
        private PublisherStepVerifier(final PublisherSource<T> source,
                                      final Queue<Event<TestPublisherSubscriber<T>>> events) {
            super(source, events, DefaultPublisherFirstStep.class.getName());
        }

        TestPublisherSubscriber<T> doSubscribe(PublisherSource<T> source) {
            TestPublisherSubscriber<T> subscriber = new TestPublisherSubscriber<>();
            source.subscribe(subscriber);
            return subscriber;
        }

        @Override
        TestPublisherSubscriber<T> doSubscribe(final PublisherSource<T> source, final Duration duration,
                                               final Executor executor) {
            TestPublisherSubscriber<T> subscriber = new TestPublisherSubscriber<>();
            toSource(fromSource(source).idleTimeout(duration, executor)).subscribe(subscriber);
            return subscriber;
        }
    }
}
