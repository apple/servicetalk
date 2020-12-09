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
package io.servicetalk.concurrent.api.test;

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/**
 * Provides the ability to express expectations for the {@link Subscriber#onNext(Object)} stages of a
 * {@link Subscriber Subscriber}'s lifecycle.
 * @param <T> The type of {@link Subscriber}.
 */
public interface PublisherStep<T> extends PublisherLastStep {
    /**
     * Declare an expectation that {@code signal} will be the next {@link Subscriber#onNext(Object) signal}.
     * @param signal The next signal which is expected.
     * @return An object which allows for subsequent expectations to be defined.
     */
    PublisherStep<T> expectNext(@Nullable T signal);

    /**
     * Declare an expectation that {@code signals} will be the next in-order sequence of
     * {@link Subscriber#onNext(Object) signals}.
     * @param signals The next signals which are expected in-order.
     * @return An object which allows for subsequent expectations to be defined.
     */
    @SuppressWarnings("unchecked")
    PublisherStep<T> expectNext(T... signals);

    /**
     * Declare an expectation that {@code signals} will be the next in-order sequence of
     * {@link Subscriber#onNext(Object) signals}.
     * @param signals The next signals which are expected in-order.
     * @return An object which allows for subsequent expectations to be defined.
     */
    PublisherStep<T> expectNextSequence(Iterable<? extends T> signals);

    /**
     * Declare an expectation that {@link Subscriber#onNext(Object) onNext} will be the next signal and evaluate it
     * with {@code signalPredicate}.
     * @param signalPredicate Will be invoked when {@link Subscriber#onNext(Object) onNext} is called and will raise
     * a {@link AssertionError} if the predicate returns {@code false}.
     * @return An object which allows for subsequent expectations to be defined.
     */
    PublisherStep<T> expectNextMatches(Predicate<? super T> signalPredicate);

    /**
     * Declare an expectation that {@link Subscriber#onNext(Object) onNext} will be the next signal and evaluate it
     * with {@code signalConsumer}.
     * @param signalConsumer Consumes the next {@link Subscriber#onNext(Object) onNext} signal.
     * @return An object which allows for subsequent expectations to be defined.
     */
    PublisherStep<T> expectNextConsumed(Consumer<? super T> signalConsumer);

    /**
     * Expect {@code n} {@link Subscriber#onNext(Object) onNext} signals, and assert their values via
     * {@code signalsConsumer}.
     * @param n The number of {@link Subscriber#onNext(Object) onNext} signals that are expected.
     * @param signalsConsumer A {@link Consumer} that accepts an {@link Iterable} which has {@code n} items.
     * @return An object which allows for subsequent expectations to be defined.
     */
    PublisherStep<T> expectNext(long n, Consumer<? super Iterable<? extends T>> signalsConsumer);

    /**
     * Expect between {@code [min, max]} {@link Subscriber#onNext(Object) onNext} signals, and assert their values via
     * {@code signalsConsumer}.
     * @param min The minimum number of {@link Subscriber#onNext(Object) onNext} signals that are required before
     * invoking {@code signalsConsumer}. If a terminal signal is processed and the number of accumulated
     * {@link Subscriber#onNext(Object) onNext} signals is {@code >=} this value the {@code signalsConsumer} will be
     * invoked for verification, otherwise the expectation will fail.
     * @param max The maximum number of {@link Subscriber#onNext(Object) onNext} signals that will be accumulated
     * before invoking {@code signalsConsumer}.
     * @param signalsConsumer A {@link Consumer} that accepts an {@link Iterable} which has between
     * {@code [min, max]} items and preforms verification.
     * @return An object which allows for subsequent expectations to be defined.
     */
    PublisherStep<T> expectNext(long min, long max, Consumer<? super Iterable<? extends T>> signalsConsumer);

    /**
     * Expect between {@code [min, max]} {@link Subscriber#onNext(Object) onNext} signals, and discard the values.
     * @param n The number of {@link Subscriber#onNext(Object) onNext} signals that are expected.
     * @return An object which allows for subsequent expectations to be defined.
     */
    PublisherStep<T> expectNextCount(long n);

    /**
     * Expect between {@code [min, max]} {@link Subscriber#onNext(Object) onNext} signals, and discard the values.
     * @param min The minimum number of {@link Subscriber#onNext(Object) onNext} signals that are required before. If a
     * terminal signal is processed and the number of accumulated {@link Subscriber#onNext(Object) onNext} signals is
     * {@code <} this value, the expectation will fail.
     * @param max The maximum number of {@link Subscriber#onNext(Object) onNext} signals that will be expected and
     * ignored before moving on to the next expectation.
     * @return An object which allows for subsequent expectations to be defined.
     */
    PublisherStep<T> expectNextCount(long min, long max);

    /**
     * Expect no {@link Subscriber#onNext(Object)}, {@link Subscriber#onError(Throwable)}, or
     * {@link Subscriber#onComplete()} signals in {@code duration} time.
     * @param duration The amount of time to wait for a signal.
     * @return An object which allows for subsequent expectations to be defined.
     */
    PublisherStep<T> expectNoSignals(Duration duration);

    /**
     * Manually request more from the {@link Subscription}.
     * @param n The amount to request.
     * @return An object which allows for subsequent expectations to be defined.
     */
    PublisherStep<T> thenRequest(long n);

    /**
     * Invoke {@link Runnable#run()} <strong>on the thread which invokes {@link StepVerifier#verify()}</strong>.
     * @param r the {@link Runnable} to invoke.
     * @return An object which allows for subsequent expectations to be defined.
     */
    PublisherStep<T> then(Runnable r);

    /**
     * Wait for a time delay of {@code duration} <strong>on the thread which invokes
     * {@link StepVerifier#verify()}</strong>.
     * @param duration the duration to wait for.
     * @return An object which allows for subsequent expectations to be defined.
     */
    PublisherStep<T> thenAwait(Duration duration);
}
