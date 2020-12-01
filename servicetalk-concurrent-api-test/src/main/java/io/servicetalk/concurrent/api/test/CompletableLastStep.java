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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource.Subscriber;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Provides the ability to express expectations for the terminal signals (e.g.
 * {@link Subscriber#onComplete() onComplete} or {@link Subscriber#onError(Throwable) onError}) of a {@link Subscriber}.
 */
public interface CompletableLastStep {
    /**
     * Expect no signals in {@code duration} time.
     * @param duration The amount of time to assert that no signals are received.
     * @return An object which allows for subsequent expectations to be defined.
     */
    CompletableLastStep expectNoSignals(Duration duration);

    /**
     * Declare an expectation that {@link Subscriber#onError(Throwable) onError} will be the next signal and evaluate it
     * with {@code errorPredicate}.
     * @param errorPredicate Will be invoked when {@link Subscriber#onError(Throwable) onError} is called and will raise
     * a {@link AssertionError} if the predicate returns {@code false}.
     * @return An object which allows to verify all expectations.
     */
    StepVerifier expectError(Predicate<Throwable> errorPredicate);

    /**
     * Declare an expectation that {@link Subscriber#onError(Throwable) onError} will be the next signal and it will be
     * of type {@code errorClass}.
     * @param errorClass The type of error which is expected.
     * @return An object which allows to verify all expectations.
     */
    StepVerifier expectError(Class<? extends Throwable> errorClass);

    /**
     * Declare an expectation that {@link Subscriber#onError(Throwable) onError} will be the next signal and evaluate it
     * with {@code errorConsumer}.
     * @param errorConsumer Will be invoked when {@link Subscriber#onError(Throwable) onError} is called.
     * @return An object which allows to verify all expectations.
     */
    StepVerifier expectError(Consumer<Throwable> errorConsumer);

    /**
     * Declare an expectation that {@link Subscriber#onComplete()} onComplete} will be the next signal.
     * @return An object which allows to verify all expectations.
     */
    StepVerifier expectComplete();

    /**
     * Manually invoke {@link Cancellable#cancel()} on the {@link Cancellable} from
     * {@link Subscriber#onSubscribe(Cancellable)}.
     * @return An object which allows to verify all expectations.
     */
    StepVerifier thenCancel();

    /**
     * Invoke {@link Runnable#run()} <strong>on the thread which invokes {@link StepVerifier#verify()}</strong>.
     * @param r the {@link Runnable} to invoke.
     * @return An object which allows for subsequent expectations to be defined.
     */
    CompletableLastStep then(Runnable r);

    /**
     * Wait for a time delay of {@code duration} <strong>on the thread which invokes
     * {@link StepVerifier#verify()}</strong>.
     * @param duration the duration to wait for.
     * @return An object which allows for subsequent expectations to be defined.
     */
    CompletableLastStep thenAwait(Duration duration);
}
