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
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.test.InlinePublisherSubscriber.NoSignalForDurationEvent;
import io.servicetalk.concurrent.api.test.InlinePublisherSubscriber.OnNextEvent;
import io.servicetalk.concurrent.api.test.InlinePublisherSubscriber.OnSubscriptionEvent;
import io.servicetalk.concurrent.api.test.InlinePublisherSubscriber.OnTerminalErrorClassChecker;
import io.servicetalk.concurrent.api.test.InlinePublisherSubscriber.OnTerminalErrorEvent;
import io.servicetalk.concurrent.api.test.InlinePublisherSubscriber.OnTerminalErrorPredicate;
import io.servicetalk.concurrent.api.test.InlinePublisherSubscriber.OnTerminalEvent;
import io.servicetalk.concurrent.api.test.InlinePublisherSubscriber.SubscriptionEvent;
import io.servicetalk.concurrent.api.test.InlinePublisherSubscriber.VerifyThreadAwaitEvent;
import io.servicetalk.concurrent.api.test.InlinePublisherSubscriber.VerifyThreadRunEvent;
import io.servicetalk.concurrent.api.test.InlineStepVerifier.PublisherEvent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.test.InlineStepVerifier.PublisherEvent.notEqualsOnNext;
import static java.util.Objects.requireNonNull;

final class InlineSingleFirstStep<T> implements SingleFirstStep<T> {
    private final SingleSource<T> source;
    private final NormalizedTimeSource timeSource;
    private final List<PublisherEvent> events;

    InlineSingleFirstStep(SingleSource<T> source, NormalizedTimeSource timeSource) {
        this.source = requireNonNull(source);
        this.timeSource = requireNonNull(timeSource);
        this.events = new ArrayList<>();
    }

    @Override
    public SingleLastStep<T> expectCancellable(Consumer<? super Cancellable> consumer) {
        requireNonNull(consumer);
        events.add(new OnSubscriptionEvent() {
            @Override
            void subscription(Subscription subscription) {
                consumer.accept(subscription);
            }

            @Override
            String description() {
                return "expectCancellable(" + consumer + ")";
            }
        });
        return this;
    }

    @Override
    public SingleLastStep<T> then(Runnable r) {
        events.add(new VerifyThreadRunEvent(r));
        return this;
    }

    @Override
    public SingleLastStep<T> thenAwait(Duration duration) {
        events.add(new VerifyThreadAwaitEvent(duration));
        return this;
    }

    @Override
    public SingleLastStep<T> expectNoSignals(Duration duration) {
        events.add(new NoSignalForDurationEvent(duration));
        return this;
    }

    @Override
    public StepVerifier expectError(Predicate<Throwable> errorPredicate) {
        return expectError(new OnTerminalErrorPredicate(errorPredicate));
    }

    @Override
    public StepVerifier expectError(Class<? extends Throwable> errorClass) {
        return expectError(new OnTerminalErrorClassChecker(errorClass));
    }

    @Override
    public StepVerifier expectError(Consumer<Throwable> errorConsumer) {
        events.add(new OnTerminalErrorEvent(errorConsumer));
        return new SingleInlineStepVerifier<>(source, timeSource, events);
    }

    @Override
    public StepVerifier expectSuccess(@Nullable T onSuccess) {
        return expectSuccess(new Consumer<T>() {
            @Override
            public void accept(T next) {
                if (notEqualsOnNext(onSuccess, next)) {
                    throw new AssertionError("expectSuccess(T) failed. expected: " + onSuccess + " actual: " + next);
                }
            }

            @Override
            public String toString() {
                return "expectSuccess(" + onSuccess + ")";
            }
        });
    }

    @Override
    public StepVerifier expectSuccess(Consumer<? super T> onSuccessConsumer) {
        events.add(new OnNextEvent<T>() {
            @Override
            void onNext(@Nullable T next) {
                onSuccessConsumer.accept(next);
            }

            @Override
            String description() {
                return "expectSuccess(" + onSuccessConsumer + ")";
            }
        });
        events.add(new OnTerminalEvent() {
            @Override
            void onError(Throwable cause) {
                throw new AssertionError("expectSuccess(" + onSuccessConsumer + ") failed. actual: onError", cause);
            }

            @Override
            void onComplete() {
            }

            @Override
            String description() {
                return "expectSuccess(" + onSuccessConsumer + ")";
            }
        });
        return new SingleInlineStepVerifier<>(source, timeSource, events);
    }

    @Override
    public StepVerifier thenCancel() {
        events.add(new SubscriptionEvent() {
            @Override
            void subscription(Subscription subscription) {
                subscription.cancel();
            }

            @Override
            String description() {
                return "thenCancel()";
            }
        });
        return new SingleInlineStepVerifier<>(source, timeSource, events);
    }

    private static final class SingleInlineStepVerifier<T> extends
            InlineStepVerifier<SingleSource<T>, InlineSingleSubscriber<T>> {
        SingleInlineStepVerifier(SingleSource<T> source, NormalizedTimeSource timeSource,
                                 List<PublisherEvent> events) {
            super(source, timeSource, events);
        }

        @Override
        InlineSingleSubscriber<T> newSubscriber(NormalizedTimeSource timeSource, List<PublisherEvent> events) {
            return new InlineSingleSubscriber<>(timeSource, events, exceptionPrefixFilter());
        }

        @Override
        void subscribe(SingleSource<T> source, InlineSingleSubscriber<T> subscriber) {
            source.subscribe(subscriber);
        }

        @Override
        String exceptionPrefixFilter() {
            return InlineSingleFirstStep.class.getName();
        }
    }
}
