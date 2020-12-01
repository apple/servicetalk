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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.Executor;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.test.internal.TestSingleSubscriber;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

final class DefaultSingleFirstStep<T> implements SingleFirstStep<T> {
    private final SingleSource<T> source;
    private final Queue<Event<TestSingleSubscriber<T>>> events;

    DefaultSingleFirstStep(final SingleSource<T> source) {
        this.source = requireNonNull(source);
        this.events = new ArrayDeque<>();
    }

    @Override
    public SingleLastStep<T> expectCancellable(final Consumer<? super Cancellable> predicate) {
        events.add(new Event<TestSingleSubscriber<T>>() {
            @Override
            void verify(final TestSingleSubscriber<T> subscriber) throws Exception {
                predicate.accept(subscriber.awaitSubscriptionInterruptible());
            }

            @Override
            void verify(final TestSingleSubscriber<T> subscriber, final long timeout, final TimeUnit unit)
                    throws Exception {
                predicate.accept(subscriber.awaitSubscriptionInterruptible(timeout, unit));
            }
        });
        return this;
    }

    @Override
    public SingleFirstStep<T> expectNoTerminal(Duration duration) {
        events.add(new Event<TestSingleSubscriber<T>>() {
            @Override
            void verify(TestSingleSubscriber<T> subscriber) throws Exception {
                doVerify(subscriber, duration.toNanos());
            }

            @Override
            void verify(TestSingleSubscriber<T> subscriber, long timeout, TimeUnit unit) throws Exception {
                doVerify(subscriber, min(NANOSECONDS.convert(timeout, unit), duration.toNanos()));
            }

            private void doVerify(TestSingleSubscriber<T> subscriber, long timeoutNs) throws Exception {
                verifyNoTerminal(subscriber.pollTerminalInterruptible(timeoutNs, NANOSECONDS));
            }
        });
        return this;
    }

    @Override
    public StepVerifier expectError(final Predicate<Throwable> errorPredicate) {
        return expectError(t -> {
            if (!errorPredicate.test(t)) {
                throw new AssertionError("errorPredicate failed on: " + t);
            }
        });
    }

    @Override
    public StepVerifier expectError(final Class<? extends Throwable> errorClass) {
        return expectError(t -> {
            if (!errorClass.isInstance(t)) {
                throw new AssertionError("expected class: " + errorClass + " actual: " + t.getClass());
            }
        });
    }

    @Override
    public StepVerifier expectError(final Consumer<Throwable> errorConsumer) {
        events.add(new Event<TestSingleSubscriber<T>>() {
            @Override
            void verify(final TestSingleSubscriber<T> subscriber) throws Exception {
                errorConsumer.accept(subscriber.takeOnErrorInterruptible());
            }

            @Override
            void verify(final TestSingleSubscriber<T> subscriber, final long timeout, final TimeUnit unit)
                    throws Exception {
                errorConsumer.accept(subscriber.takeOnErrorInterruptible(timeout, unit));
            }
        });
        return new SingleStepVerifier<>(source, events);
    }

    @Override
    public StepVerifier expectSuccess(@Nullable final T onSuccess) {
        return expectSuccess(actual -> verifyOnSuccess(onSuccess, actual));
    }

    private static <T> void verifyOnSuccess(@Nullable final T expected, @Nullable final T actual) {
        if (Event.notEqualsOnNext(expected, actual)) {
            throw new AssertionError("onSuccess(T) failed. expected: " + expected + " actual: " + actual);
        }
    }

    @Override
    public StepVerifier expectSuccess(final Consumer<? super T> onSuccess) {
        events.add(new Event<TestSingleSubscriber<T>>() {
            @Override
            void verify(final TestSingleSubscriber<T> subscriber) throws Exception {
                onSuccess.accept(subscriber.takeOnSuccessInterruptible());
            }

            @Override
            void verify(final TestSingleSubscriber<T> subscriber, final long timeout, final TimeUnit unit)
                    throws Exception {
                onSuccess.accept(subscriber.takeOnSuccessInterruptible(timeout, unit));
            }
        });
        return new SingleStepVerifier<>(source, events);
    }

    @Override
    public StepVerifier thenCancel() {
        events.add(new Event<TestSingleSubscriber<T>>() {
            @Override
            void verify(TestSingleSubscriber<T> subscriber) {
                subscriber.awaitSubscription().cancel();
            }

            @Override
            void verify(TestSingleSubscriber<T> subscriber, long timeout, TimeUnit unit) {
                subscriber.awaitSubscription().cancel();
            }
        });
        return new SingleStepVerifier<>(source, events);
    }

    private static final class SingleStepVerifier<T> extends
                                        DefaultStepVerifier<SingleSource<T>, TestSingleSubscriber<T>> {
        SingleStepVerifier(final SingleSource<T> source, final Queue<Event<TestSingleSubscriber<T>>> events) {
            super(source, events, DefaultSingleFirstStep.class.getName());
        }

        @Override
        TestSingleSubscriber<T> doSubscribe(final SingleSource<T> source) {
            TestSingleSubscriber<T> subscriber = new TestSingleSubscriber<>();
            source.subscribe(subscriber);
            return subscriber;
        }

        @Override
        TestSingleSubscriber<T> doSubscribe(final SingleSource<T> source, final Duration duration,
                                            final Executor executor) {
            TestSingleSubscriber<T> subscriber = new TestSingleSubscriber<>();
            toSource(fromSource(source).idleTimeout(duration, executor)).subscribe(subscriber);
            return subscriber;
        }
    }
}
