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
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.Executor;
import io.servicetalk.concurrent.test.internal.TestCompletableSubscriber;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

final class DefaultCompletableFirstStep implements CompletableFirstStep {
    private final CompletableSource source;
    private final Queue<Event<TestCompletableSubscriber>> events;

    DefaultCompletableFirstStep(final CompletableSource source) {
        this.source = requireNonNull(source);
        this.events = new ArrayDeque<>();
    }

    @Override
    public CompletableLastStep expectCancellable(final Consumer<? super Cancellable> predicate) {
        events.add(new Event<TestCompletableSubscriber>() {
            @Override
            void verify(final TestCompletableSubscriber subscriber) throws Exception {
                predicate.accept(subscriber.awaitSubscriptionInterruptible());
            }

            @Override
            void verify(final TestCompletableSubscriber subscriber, final long timeout, final TimeUnit unit)
                    throws Exception {
                predicate.accept(subscriber.awaitSubscriptionInterruptible(timeout, unit));
            }
        });
        return this;
    }

    @Override
    public CompletableFirstStep expectNoTerminal(Duration duration) {
        events.add(new Event<TestCompletableSubscriber>() {
            @Override
            void verify(TestCompletableSubscriber subscriber) throws Exception {
                doVerify(subscriber, duration.toNanos());
            }

            @Override
            void verify(TestCompletableSubscriber subscriber, long timeout, TimeUnit unit) throws Exception {
                doVerify(subscriber, min(NANOSECONDS.convert(timeout, unit), duration.toNanos()));
            }

            private void doVerify(TestCompletableSubscriber subscriber, long timeoutNs) throws Exception {
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
        requireNonNull(errorConsumer);
        events.add(new Event<TestCompletableSubscriber>() {
            @Override
            void verify(final TestCompletableSubscriber subscriber) throws Exception {
                errorConsumer.accept(subscriber.takeOnErrorInterruptible());
            }

            @Override
            void verify(final TestCompletableSubscriber subscriber, final long timeout, final TimeUnit unit)
                    throws Exception {
                errorConsumer.accept(subscriber.takeOnErrorInterruptible(timeout, unit));
            }
        });
        return new CompletableStepVerifier(source, events);
    }

    @Override
    public StepVerifier expectComplete() {
        events.add(new Event<TestCompletableSubscriber>() {
            @Override
            void verify(final TestCompletableSubscriber subscriber) throws Exception {
                subscriber.takeOnCompleteInterruptible();
            }

            @Override
            void verify(final TestCompletableSubscriber subscriber, final long timeout, final TimeUnit unit)
                    throws Exception {
                subscriber.takeOnCompleteInterruptible(timeout, unit);
            }
        });
        return new CompletableStepVerifier(source, events);
    }

    @Override
    public StepVerifier thenCancel() {
        events.add(new Event<TestCompletableSubscriber>() {
            @Override
            void verify(TestCompletableSubscriber subscriber) {
                subscriber.awaitSubscription().cancel();
            }

            @Override
            void verify(TestCompletableSubscriber subscriber, long timeout, TimeUnit unit) {
                subscriber.awaitSubscription().cancel();
            }
        });
        return new CompletableStepVerifier(source, events);
    }

    private static final class CompletableStepVerifier extends
                                                     DefaultStepVerifier<CompletableSource, TestCompletableSubscriber> {
        CompletableStepVerifier(final CompletableSource source, final Queue<Event<TestCompletableSubscriber>> events) {
            super(source, events, DefaultCompletableFirstStep.class.getName());
        }

        @Override
        TestCompletableSubscriber doSubscribe(final CompletableSource source) {
            TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
            source.subscribe(subscriber);
            return subscriber;
        }

        @Override
        TestCompletableSubscriber doSubscribe(final CompletableSource source, final Duration duration,
                                              final Executor executor) {
            TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
            toSource(fromSource(source).idleTimeout(duration, executor)).subscribe(subscriber);
            return subscriber;
        }
    }
}
