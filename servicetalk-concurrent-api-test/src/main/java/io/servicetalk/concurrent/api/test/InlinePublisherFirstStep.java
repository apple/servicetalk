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

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.test.InlinePublisherSubscriber.NoSignalForDurationEvent;
import io.servicetalk.concurrent.api.test.InlinePublisherSubscriber.OnNextAggregateEvent;
import io.servicetalk.concurrent.api.test.InlinePublisherSubscriber.OnNextEvent;
import io.servicetalk.concurrent.api.test.InlinePublisherSubscriber.OnNextIgnoreEvent;
import io.servicetalk.concurrent.api.test.InlinePublisherSubscriber.OnNextIterableEvent;
import io.servicetalk.concurrent.api.test.InlinePublisherSubscriber.OnSubscriptionEvent;
import io.servicetalk.concurrent.api.test.InlinePublisherSubscriber.OnTerminalCompleteEvent;
import io.servicetalk.concurrent.api.test.InlinePublisherSubscriber.OnTerminalErrorClassChecker;
import io.servicetalk.concurrent.api.test.InlinePublisherSubscriber.OnTerminalErrorEvent;
import io.servicetalk.concurrent.api.test.InlinePublisherSubscriber.OnTerminalErrorPredicate;
import io.servicetalk.concurrent.api.test.InlinePublisherSubscriber.SubscriptionEvent;
import io.servicetalk.concurrent.api.test.InlinePublisherSubscriber.VerifyThreadAwaitEvent;
import io.servicetalk.concurrent.api.test.InlinePublisherSubscriber.VerifyThreadRunEvent;
import io.servicetalk.concurrent.api.test.InlineStepVerifier.PublisherEvent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;

final class InlinePublisherFirstStep<T> implements PublisherFirstStep<T> {
    private final PublisherSource<T> source;
    private final NormalizedTimeSource timeSource;
    private final List<PublisherEvent> events;

    InlinePublisherFirstStep(final PublisherSource<T> source, final NormalizedTimeSource timeSource) {
        this.source = requireNonNull(source);
        this.timeSource = requireNonNull(timeSource);
        this.events = new ArrayList<>();
    }

    @Override
    public PublisherStep<T> expectSubscription(Consumer<? super Subscription> consumer) {
        requireNonNull(consumer);
        events.add(new OnSubscriptionEvent() {
            @Override
            void subscription(Subscription subscription) {
                consumer.accept(subscription);
            }

            @Override
            String description() {
                return "expectSubscription(" + consumer + ")";
            }
        });
        return this;
    }

    @Override
    public PublisherStep<T> expectNext(@Nullable T signal) {
        events.add(new OnNextEvent<T>() {
            @Override
            void onNext(@Nullable T next) {
                if (notEqualsOnNext(signal, next)) {
                    throw new AssertionError("expectNext(T) failed. expected: " + signal + " actual: " + next);
                }
            }

            @Override
            String description() {
                return "expectNext(" + signal + ")";
            }
        });
        return this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public PublisherStep<T> expectNext(T... signals) {
        return expectNext((Iterable<? extends T>) asList(signals)); // avoid aggregating data in the Subscriber
    }

    @Override
    public PublisherStep<T> expectNext(Iterable<? extends T> signals) {
        events.add(new OnNextIterableEvent<T>(signals));
        return this;
    }

    @Override
    public PublisherStep<T> expectNext(Collection<? extends T> signals) {
        return expectNext((Iterable<? extends T>) signals); // avoid aggregating data in the Subscriber
    }

    @Override
    public PublisherStep<T> expectNext(Consumer<? super T> signalConsumer) {
        requireNonNull(signalConsumer);
        events.add(new OnNextEvent<T>() {
            @Override
            void onNext(@Nullable T next) {
                signalConsumer.accept(next);
            }

            @Override
            String description() {
                return "expectNext(" + signalConsumer + ")";
            }
        });
        return this;
    }

    @Override
    public PublisherStep<T> expectNext(int n, Consumer<? super Collection<? extends T>> signalsConsumer) {
        return expectNext(n, n, signalsConsumer);
    }

    @Override
    public PublisherStep<T> expectNext(int min, int max, Consumer<? super Collection<? extends T>> signalsConsumer) {
        events.add(new OnNextAggregateEvent<>(min, max, signalsConsumer));
        return this;
    }

    @Override
    public PublisherStep<T> expectNextCount(long n) {
        events.add(new OnNextIgnoreEvent(n));
        return this;
    }

    @Override
    public PublisherStep<T> thenRequest(long n) {
        events.add(new SubscriptionEvent() {
            @Override
            void subscription(Subscription subscription) {
                subscription.request(n);
            }

            @Override
            String description() {
                return "thenRequest(" + n + ")";
            }
        });
        return this;
    }

    @Override
    public PublisherStep<T> then(Runnable r) {
        events.add(new VerifyThreadRunEvent(r));
        return this;
    }

    @Override
    public PublisherStep<T> thenAwait(Duration duration) {
        events.add(new VerifyThreadAwaitEvent(duration));
        return this;
    }

    @Override
    public PublisherStep<T> expectNoSignals(Duration duration) {
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
        return new PublisherInlineStepVerifier<>(source, timeSource, events);
    }

    @Override
    public StepVerifier expectComplete() {
        events.add(new OnTerminalCompleteEvent());
        return new PublisherInlineStepVerifier<>(source, timeSource, events);
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
        return new PublisherInlineStepVerifier<>(source, timeSource, events);
    }

    private static final class PublisherInlineStepVerifier<T> extends
            InlineStepVerifier<PublisherSource<T>, InlinePublisherSubscriber<T>> {
        PublisherInlineStepVerifier(PublisherSource<T> source, NormalizedTimeSource timeSource,
                                    List<PublisherEvent> events) {
            super(source, timeSource, events);
        }

        @Override
        InlinePublisherSubscriber<T> newSubscriber(NormalizedTimeSource timeSource, List<PublisherEvent> events) {
            return new InlinePublisherSubscriber<>(0, timeSource, events, exceptionPrefixFilter());
        }

        @Override
        void subscribe(PublisherSource<T> source, InlinePublisherSubscriber<T> subscriber) {
            source.subscribe(subscriber);
        }

        @Override
        String exceptionPrefixFilter() {
            return InlinePublisherFirstStep.class.getName();
        }
    }
}
