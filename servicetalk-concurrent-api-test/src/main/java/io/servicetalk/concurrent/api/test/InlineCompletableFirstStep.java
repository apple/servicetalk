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
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.test.InlinePublisherSubscriber.NoSignalForDurationEvent;
import io.servicetalk.concurrent.api.test.InlinePublisherSubscriber.OnSubscriptionEvent;
import io.servicetalk.concurrent.api.test.InlinePublisherSubscriber.OnTerminalCompleteEvent;
import io.servicetalk.concurrent.api.test.InlinePublisherSubscriber.OnTerminalErrorClassChecker;
import io.servicetalk.concurrent.api.test.InlinePublisherSubscriber.OnTerminalErrorEvent;
import io.servicetalk.concurrent.api.test.InlinePublisherSubscriber.OnTerminalErrorPredicate;
import io.servicetalk.concurrent.api.test.InlinePublisherSubscriber.SubscriptionEvent;
import io.servicetalk.concurrent.api.test.InlinePublisherSubscriber.VerifyThreadAwaitEvent;
import io.servicetalk.concurrent.api.test.InlinePublisherSubscriber.VerifyThreadRunEvent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static io.servicetalk.concurrent.api.test.InlineStepVerifier.PublisherEvent;
import static java.util.Objects.requireNonNull;

public class InlineCompletableFirstStep implements CompletableFirstStep {
    private final CompletableSource source;
    private final NormalizedTimeSource timeSource;
    private final List<PublisherEvent> events;

    public InlineCompletableFirstStep(CompletableSource source, NormalizedTimeSource timeSource) {
        this.source = requireNonNull(source);
        this.timeSource = requireNonNull(timeSource);
        this.events = new ArrayList<>();
    }

    @Override
    public CompletableLastStep expectCancellable(Consumer<? super Cancellable> consumer) {
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
    public CompletableLastStep then(Runnable r) {
        events.add(new VerifyThreadRunEvent(r));
        return this;
    }

    @Override
    public CompletableLastStep thenAwait(Duration duration) {
        events.add(new VerifyThreadAwaitEvent(duration));
        return this;
    }

    @Override
    public CompletableLastStep expectNoSignals(Duration duration) {
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
        return new CompletableInlineStepVerifier(source, timeSource, events);
    }

    @Override
    public StepVerifier expectComplete() {
        events.add(new OnTerminalCompleteEvent());
        return new CompletableInlineStepVerifier(source, timeSource, events);
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
        return new CompletableInlineStepVerifier(source, timeSource, events);
    }

    private static final class CompletableInlineStepVerifier extends
            InlineStepVerifier<CompletableSource, InlineCompletableSubscriber> {
        CompletableInlineStepVerifier(CompletableSource completableSource, NormalizedTimeSource timeSource,
                                      List<PublisherEvent> events) {
            super(completableSource, timeSource, events);
        }

        @Override
        InlineCompletableSubscriber newSubscriber(NormalizedTimeSource timeSource, List<PublisherEvent> events) {
            return new InlineCompletableSubscriber(timeSource, events, exceptionPrefixFilter());
        }

        @Override
        void subscribe(CompletableSource source, InlineCompletableSubscriber subscriber) {
            source.subscribe(subscriber);
        }

        @Override
        String exceptionPrefixFilter() {
            return InlineCompletableFirstStep.class.getName();
        }
    }
}
