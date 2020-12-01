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

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Provides the ability to express expectations for the terminal signals (e.g.
 * {@link Subscriber#onComplete() onComplete} or {@link Subscriber#onError(Throwable) onError}) of a {@link Subscriber}.
 */
public interface PublisherLastStep {
    /**
     * Declare an expectation that {@link Subscriber#onError(Throwable) onError} will be the next signal and evaluate it
     * with {@code errorPredicate}.
     * @param errorPredicate Will be invoked when {@link Subscriber#onError(Throwable) onError} is called and will raise
     * a {@link AssertionError} if the predicate returns {@code false}.
     * @return An object which allows to verify all expectations.
     */
    StepVerifier expectError(Predicate<Throwable> errorPredicate);

    /**
     * Declare an expectation that {@link Subscriber#onError(Throwable) onError} will be the next signal and evaluate it
     * with {@code errorPredicate}.
     * @param errorPredicate Will be invoked when {@link Subscriber#onError(Throwable) onError} is called and will raise
     * a {@link AssertionError} if the predicate returns {@code false}.
     * @param verifySignalsConsumed {@code true} will verify that all prior {@link Subscriber#onNext(Object) onNext}
     * signals have been consumed before evaluating the terminal signal. {@code false} will ignore/discard any prior
     * {@link Subscriber#onNext(Object) onNext} signals and verify the terminal event.
     * @return An object which allows to verify all expectations.
     */
    StepVerifier expectError(Predicate<Throwable> errorPredicate, boolean verifySignalsConsumed);

    /**
     * Declare an expectation that {@link Subscriber#onError(Throwable) onError} will be the next signal and it will be
     * of type {@code errorClass}.
     * @param errorClass The type of error which is expected.
     * @return An object which allows to verify all expectations.
     */
    StepVerifier expectError(Class<? extends Throwable> errorClass);

    /**
     * Declare an expectation that {@link Subscriber#onError(Throwable) onError} will be the next signal and it will be
     * of type {@code errorClass}.
     * @param errorClass The type of error which is expected.
     * @param verifySignalsConsumed {@code true} will verify that all prior
     * {@link Subscriber#onNext(Object) onNext} signals have been consumed before evaluating the terminal signal.
     * {@code false} will ignore/discard any prior
     * {@link Subscriber#onNext(Object) onNext} signals and verify the terminal event.
     * @return An object which allows to verify all expectations.
     */
    StepVerifier expectError(Class<? extends Throwable> errorClass, boolean verifySignalsConsumed);

    /**
     * Declare an expectation that {@link Subscriber#onError(Throwable) onError} will be the next signal and evaluate it
     * with {@code errorConsumer}.
     * @param errorConsumer Will be invoked when {@link Subscriber#onError(Throwable) onError} is called.
     * @return An object which allows to verify all expectations.
     */
    StepVerifier expectError(Consumer<Throwable> errorConsumer);

    /**
     * Declare an expectation that {@link Subscriber#onError(Throwable) onError} will be the next signal and evaluate it
     * with {@code errorConsumer}.
     * @param errorConsumer Will be invoked when {@link Subscriber#onError(Throwable) onError} is called.
     * @param verifySignalsConsumed {@code true} will verify that all prior
     * {@link Subscriber#onNext(Object) onNext} signals have been consumed before evaluating the terminal signal.
     * {@code false} will ignore/discard any prior {@link Subscriber#onNext(Object) onNext} signals and verify the
     * terminal event.
     * @return An object which allows to verify all expectations.
     */
    StepVerifier expectError(Consumer<Throwable> errorConsumer, boolean verifySignalsConsumed);

    /**
     * Declare an expectation that {@link Subscriber#onComplete()} onComplete} will be the next signal.
     * @return An object which allows to verify all expectations.
     */
    StepVerifier expectComplete();

    /**
     * Declare an expectation that {@link Subscriber#onComplete()} onComplete} will be the next signal.
     * @param verifySignalsConsumed {@code true} will verify that all prior
     * {@link Subscriber#onNext(Object) onNext} signals have been consumed before evaluating the terminal signal.
     * {@code false} will ignore/discard any prior {@link Subscriber#onNext(Object) onNext} signals and verify the
     * terminal event.
     * @return An object which allows to verify all expectations.
     */
    StepVerifier expectComplete(boolean verifySignalsConsumed);

    /**
     * Manually invoke {@link Subscription#cancel()} on the {@link Subscription} from
     * {@link PublisherSource.Subscriber#onSubscribe(Subscription)}.
     * @return An object which allows to verify all expectations.
     */
    StepVerifier thenCancel();
}
