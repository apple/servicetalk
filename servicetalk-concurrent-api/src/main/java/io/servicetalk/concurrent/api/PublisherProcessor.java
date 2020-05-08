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

import io.servicetalk.concurrent.PublisherSource.Processor;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.internal.ConcurrentSubscription;
import io.servicetalk.concurrent.internal.DelayedSubscription;
import io.servicetalk.concurrent.internal.DuplicateSubscribeException;
import io.servicetalk.concurrent.internal.FlowControlUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.ConcurrentUtils.releaseLock;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.tryAcquireLock;
import static io.servicetalk.concurrent.internal.SubscriberUtils.handleExceptionFromOnSubscribe;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;
import static io.servicetalk.concurrent.internal.SubscriberUtils.safeOnComplete;
import static io.servicetalk.concurrent.internal.SubscriberUtils.safeOnError;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

final class PublisherProcessor<T> extends Publisher<T> implements Processor<T, T>, Subscription {
    private static final Logger LOGGER = LoggerFactory.getLogger(PublisherProcessor.class);
    @SuppressWarnings("rawtypes")
    private static final ProcessorSignalsConsumer CANCELLED = new NoopProcessorSignalsConsumer();
    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<PublisherProcessor, ProcessorSignalsConsumer> consumerUpdater =
            AtomicReferenceFieldUpdater.newUpdater(PublisherProcessor.class,
                    ProcessorSignalsConsumer.class, "consumer");
    @SuppressWarnings("rawtypes")
    private static final AtomicIntegerFieldUpdater<PublisherProcessor> emittingUpdater =
            newUpdater(PublisherProcessor.class, "emitting");
    @SuppressWarnings("rawtypes")
    private static final AtomicLongFieldUpdater<PublisherProcessor> pendingUpdater =
            AtomicLongFieldUpdater.newUpdater(PublisherProcessor.class, "pending");

    private final DelayedSubscription delayedSubscription;
    private final PublisherProcessorSignalsHolder<T> buffer;

    @Nullable
    private Throwable fatalError; // visible via emitting
    @Nullable
    private volatile ProcessorSignalsConsumer<T> consumer;
    @SuppressWarnings("unused")
    private volatile int emitting;
    private volatile long pending;

    PublisherProcessor(final PublisherProcessorSignalsHolder<T> buffer) {
        this.buffer = requireNonNull(buffer);
        delayedSubscription = new DelayedSubscription();
    }

    @Override
    public void onSubscribe(final Subscription subscription) {
        delayedSubscription.delayedSubscription(ConcurrentSubscription.wrap(subscription));
    }

    @Override
    public void onNext(@Nullable final T t) {
        buffer.add(t);
        tryEmitSignals();
    }

    @Override
    public void onError(final Throwable t) {
        buffer.terminate(t);
        tryEmitSignals();
    }

    @Override
    public void onComplete() {
        buffer.terminate();
        tryEmitSignals();
    }

    @Override
    protected void handleSubscribe(final Subscriber<? super T> subscriber) {
        final DelayedSubscription delayedSubscription = new DelayedSubscription();
        try {
            subscriber.onSubscribe(delayedSubscription);
        } catch (Throwable t) {
            handleExceptionFromOnSubscribe(subscriber, t);
            return;
        }

        if (consumerUpdater.compareAndSet(this, null, new SubscriberProcessorSignalsConsumer<>(subscriber))) {
            try {
                delayedSubscription.delayedSubscription(this);
                tryEmitSignals();
            } catch (Throwable t) {
                LOGGER.error("Unexpected error while delivering signals to the subscriber {}", subscriber, t);
            }
        } else {
            ProcessorSignalsConsumer<? super T> existingConsumer = this.consumer;
            assert existingConsumer != null;
            @SuppressWarnings("unchecked")
            final Subscriber<? super T> existingSubscriber =
                    existingConsumer instanceof PublisherProcessor.SubscriberProcessorSignalsConsumer ?
                            ((SubscriberProcessorSignalsConsumer<T>) existingConsumer).subscriber : null;
            safeOnError(subscriber, new DuplicateSubscribeException(existingSubscriber, subscriber));
        }
    }

    @Override
    public void subscribe(final Subscriber<? super T> subscriber) {
        subscribeInternal(subscriber);
    }

    @Override
    public void request(final long n) {
        if (!isRequestNValid(n)) {
            fatalError = newExceptionForInvalidRequestN(n);
        } else {
            pendingUpdater.accumulateAndGet(this, n, FlowControlUtils::addWithOverflowProtectionIfNotNegative);
            delayedSubscription.request(n);
        }
        tryEmitSignals();
    }

    @Override
    public void cancel() {
        if (pendingUpdater.getAndSet(this, Long.MIN_VALUE) >= 0) {
            @SuppressWarnings("unchecked")
            ProcessorSignalsConsumer<T> cancelled = CANCELLED;
            // Release reference to the subscriber as per rule 3.13
            // https://github.com/reactive-streams/reactive-streams-jvm#3.13
            this.consumer = cancelled;
            delayedSubscription.cancel();
        }
    }

    private void tryEmitSignals() {
        boolean tryAcquire = true;
        while (tryAcquire && tryAcquireLock(emittingUpdater, this)) {
            final ProcessorSignalsConsumer<T> consumer = this.consumer;
            try {
                if (consumer instanceof PublisherProcessor.SubscriberProcessorSignalsConsumer) {
                    SubscriberProcessorSignalsConsumer<T> target = (SubscriberProcessorSignalsConsumer<T>) consumer;
                    if (fatalError != null) {
                        earlyTerminateConsumerHoldingLock(target, fatalError);
                        return;
                    } else {
                        emitSignalsHoldingLock(target);
                    }
                }
            } finally {
                tryAcquire = !releaseLock(emittingUpdater, this);
            }
        }
    }

    private void emitSignalsHoldingLock(final SubscriberProcessorSignalsConsumer<T> target) {
        for (;;) {
            final long cPending = pending;
            if (cPending > 0 && pendingUpdater.compareAndSet(this, cPending, cPending - 1)) {
                final boolean consumed;
                try {
                    consumed = buffer.tryConsume(target);
                } catch (Throwable t) {
                    earlyTerminateConsumerHoldingLock(target, t);
                    return;
                }

                if (target.isTerminated()) {
                    pending = Long.MIN_VALUE;
                } else if (!consumed) {
                    // we optimistically decremented pending, so increment back again.
                    pendingUpdater.accumulateAndGet(this, 1,
                            FlowControlUtils::addWithOverflowProtectionIfNotNegative);
                    return;
                }
            } else if (cPending < 0) {
                // cancelled or already terminated
                return;
            } else if (cPending == 0) {
                try {
                    if (buffer.tryConsumeTerminal(target)) {
                        pending = Long.MIN_VALUE;
                    }
                } catch (Throwable t) {
                    // Assume that we did not deliver terminal to the consumer.
                    earlyTerminateConsumerHoldingLock(target, t);
                }
                return;
            }
        }
    }

    private void earlyTerminateConsumerHoldingLock(final SubscriberProcessorSignalsConsumer<T> consumer,
                                                   final Throwable cause) {
        pending = Long.MIN_VALUE;
        try {
            delayedSubscription.cancel();
        } finally {
            consumer.consumeTerminal(cause);
        }
    }

    private static final class SubscriberProcessorSignalsConsumer<T> implements ProcessorSignalsConsumer<T> {
        private final Subscriber<? super T> subscriber;
        private boolean terminated;

        SubscriberProcessorSignalsConsumer(final Subscriber<? super T> subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public void consumeItem(@Nullable final T item) {
            subscriber.onNext(item);
        }

        @Override
        public void consumeTerminal(final Throwable cause) {
            terminated = true;
            safeOnError(subscriber, cause);
        }

        @Override
        public void consumeTerminal() {
            terminated = true;
            safeOnComplete(subscriber);
        }

        boolean isTerminated() {
            return terminated;
        }
    }

    @SuppressWarnings("rawtypes")
    private static final class NoopProcessorSignalsConsumer implements ProcessorSignalsConsumer {

        @Override
        public void consumeItem(@Nullable final Object item) {
            // noop
        }

        @Override
        public void consumeTerminal(final Throwable cause) {
            // noop
        }

        @Override
        public void consumeTerminal() {
            // noop
        }
    }
}
