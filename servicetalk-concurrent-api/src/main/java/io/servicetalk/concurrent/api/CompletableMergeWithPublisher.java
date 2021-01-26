/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.internal.ConcurrentSubscription;
import io.servicetalk.concurrent.internal.ConcurrentTerminalSubscriber;
import io.servicetalk.concurrent.internal.DelayedCancellable;
import io.servicetalk.concurrent.internal.DelayedSubscription;
import io.servicetalk.concurrent.internal.SignalOffloader;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * {@link Publisher} as returned by {@link Completable#merge(Publisher)}.
 *
 * @param <T> Type of data returned from the {@link Publisher}
 */
final class CompletableMergeWithPublisher<T> extends AbstractNoHandleSubscribePublisher<T> {
    private final Completable original;
    private final Publisher<? extends T> mergeWith;
    private final boolean delayError;

    CompletableMergeWithPublisher(Completable original, Publisher<? extends T> mergeWith, boolean delayError,
                                  Executor executor) {
        super(executor);
        this.mergeWith = mergeWith;
        this.original = original;
        this.delayError = delayError;
    }

    @Override
    void handleSubscribe(final Subscriber<? super T> subscriber, final SignalOffloader signalOffloader,
                         final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
        if (delayError) {
            new MergerDelayError<>(subscriber, signalOffloader, contextMap, contextProvider)
                    .merge(original, mergeWith, signalOffloader, contextMap, contextProvider);
        } else {
            new Merger<>(subscriber, signalOffloader, contextMap, contextProvider)
                    .merge(original, mergeWith, signalOffloader, contextMap, contextProvider);
        }
    }

    private static final class MergerDelayError<T> implements Subscriber<T> {
        @SuppressWarnings("rawtypes")
        private static final AtomicReferenceFieldUpdater<MergerDelayError, TerminalSignal> terminalUpdater =
                AtomicReferenceFieldUpdater.newUpdater(MergerDelayError.class, TerminalSignal.class, "terminal");
        private final CompletableSubscriber completableSubscriber;
        private final Subscriber<? super T> offloadedSubscriber;
        private final DelayedSubscription subscription = new DelayedSubscription();
        @Nullable
        private volatile TerminalSignal terminal;

        MergerDelayError(Subscriber<? super T> subscriber, SignalOffloader signalOffloader, AsyncContextMap contextMap,
               AsyncContextProvider contextProvider) {
            // This is used only to deliver signals that originate from the mergeWith Publisher. Since, we need to
            // preserve the threading semantics of the original Completable, we offload the subscriber so that we do not
            // invoke it from the mergeWith Publisher Executor thread.
            this.offloadedSubscriber = signalOffloader.offloadSubscriber(contextProvider.wrapPublisherSubscriber(
                    subscriber, contextMap));
            completableSubscriber = new CompletableSubscriber();
        }

        void merge(Completable original, Publisher<? extends T> mergeWith, SignalOffloader signalOffloader,
                   AsyncContextMap contextMap, AsyncContextProvider contextProvider) {
            offloadedSubscriber.onSubscribe(
                    new MergedCancellableWithSubscription(subscription, completableSubscriber));
            original.delegateSubscribe(completableSubscriber, signalOffloader, contextMap,
                    contextProvider);
            // SignalOffloader is associated with the original Completable. Since mergeWith Publisher is provided by
            // the user, it will have its own Executor, hence we should not pass this signalOffloader to subscribe to
            // mergeWith.
            // Any signal originating from mergeWith Publisher should be offloaded before they are sent to the
            // Subscriber of the resulting Publisher of CompletableMergeWithPublisher as the Executor associated with
            // the original Completable defines the threading semantics for that Subscriber.
            mergeWith.subscribeInternal(this);
        }

        @Override
        public void onSubscribe(final Subscription subscription) {
            this.subscription.delayedSubscription(subscription);
        }

        @Override
        public void onNext(@Nullable final T t) {
            offloadedSubscriber.onNext(t);
        }

        @Override
        public void onError(final Throwable t) {
            terminateSubscriber(new TerminalSignal(t, true));
        }

        @Override
        public void onComplete() {
            terminateSubscriber(TerminalSignal.PUB_COMPLETED);
        }

        private void terminateSubscriber(final TerminalSignal terminalSignal) {
            for (;;) {
                final TerminalSignal currState = terminal;
                if (currState != null) {
                    if (currState.fromPublisher == terminalSignal.fromPublisher) {
                        // The goal of this check is to prevent concurrency on the Subscriber and require both sources
                        // terminate before terminating downstream. We don't need to enforce each source terminates
                        // exactly once for the correctness of this operator (so we don't).
                        throw duplicateTerminalException(currState);
                    }
                    if (currState.cause == null) {
                        if (terminalSignal.cause == null) {
                            offloadedSubscriber.onComplete();
                        } else {
                            offloadedSubscriber.onError(terminalSignal.cause);
                        }
                    } else {
                        offloadedSubscriber.onError(currState.cause);
                    }
                    break;
                } else if (terminalUpdater.compareAndSet(this, null, terminalSignal)) {
                    break;
                }
            }
        }

        private final class CompletableSubscriber extends DelayedCancellable implements CompletableSource.Subscriber {
            @Override
            public void onSubscribe(Cancellable cancellable) {
                delayedCancellable(cancellable);
            }

            @Override
            public void onComplete() {
                terminateSubscriber(TerminalSignal.COM_COMPLETED);
            }

            @Override
            public void onError(Throwable t) {
                terminateSubscriber(new TerminalSignal(t, false));
            }
        }

        private static final class TerminalSignal {
            private static final TerminalSignal PUB_COMPLETED = new TerminalSignal(true);
            private static final TerminalSignal COM_COMPLETED = new TerminalSignal(false);
            @Nullable
            final Throwable cause;
            final boolean fromPublisher;

            TerminalSignal(boolean fromPublisher) {
                cause = null;
                this.fromPublisher = fromPublisher;
            }

            TerminalSignal(Throwable cause, boolean fromPublisher) {
                this.cause = requireNonNull(cause);
                this.fromPublisher = fromPublisher;
            }
        }

        private static IllegalStateException duplicateTerminalException(TerminalSignal currState) {
            throw new IllegalStateException("duplicate terminal event from " + (currState.fromPublisher ?
                    Publisher.class.getSimpleName() : Completable.class.getSimpleName()), currState.cause);
        }
    }

    private static final class Merger<T> implements Subscriber<T> {
        @SuppressWarnings("rawtypes")
        private static final AtomicIntegerFieldUpdater<Merger> completionCountUpdater =
                AtomicIntegerFieldUpdater.newUpdater(Merger.class, "completionCount");

        @SuppressWarnings("unused")
        private volatile int completionCount;

        private final CompletableSubscriber completableSubscriber;
        private final Subscriber<? super T> offloadedSubscriber;
        private final DelayedSubscription subscription = new DelayedSubscription();

        Merger(Subscriber<? super T> subscriber, SignalOffloader signalOffloader, AsyncContextMap contextMap,
               AsyncContextProvider contextProvider) {
            // This is used only to deliver signals that originate from the mergeWith Publisher. Since, we need to
            // preserve the threading semantics of the original Completable, we offload the subscriber so that we do not
            // invoke it from the mergeWith Publisher Executor thread.
            this.offloadedSubscriber = new ConcurrentTerminalSubscriber<>(signalOffloader.offloadSubscriber(
                    contextProvider.wrapPublisherSubscriber(subscriber, contextMap)), false);
            completableSubscriber = new CompletableSubscriber();
        }

        void merge(Completable original, Publisher<? extends T> mergeWith, SignalOffloader signalOffloader,
                   AsyncContextMap contextMap, AsyncContextProvider contextProvider) {
            offloadedSubscriber.onSubscribe(
                    new MergedCancellableWithSubscription(subscription, completableSubscriber));
            original.delegateSubscribe(completableSubscriber, signalOffloader, contextMap,
                    contextProvider);
            // SignalOffloader is associated with the original Completable. Since mergeWith Publisher is provided by
            // the user, it will have its own Executor, hence we should not pass this signalOffloader to subscribe to
            // mergeWith.
            // Any signal originating from mergeWith Publisher should be offloaded before they are sent to the
            // Subscriber of the resulting Publisher of CompletableMergeWithPublisher as the Executor associated with
            // the original Completable defines the threading semantics for that Subscriber.
            mergeWith.subscribeInternal(this);
        }

        @Override
        public void onSubscribe(Subscription targetSubscription) {
            // We may cancel from multiple threads and DelayedSubscription will atomically swap if a cancel occurs but
            // it will not prevent concurrent access between request(n) and cancel() on the targetSubscription.
            subscription.delayedSubscription(ConcurrentSubscription.wrap(targetSubscription));
        }

        @Override
        public void onNext(@Nullable T t) {
            offloadedSubscriber.onNext(t);
        }

        @Override
        public void onError(Throwable t) {
            completableSubscriber.cancel();
            offloadedSubscriber.onError(t);
        }

        @Override
        public void onComplete() {
            if (completionCountUpdater.incrementAndGet(this) == 2) {
                offloadedSubscriber.onComplete();
            }
        }

        private final class CompletableSubscriber extends DelayedCancellable implements CompletableSource.Subscriber {
            @Override
            public void onSubscribe(Cancellable cancellable) {
                delayedCancellable(cancellable);
            }

            @Override
            public void onComplete() {
                if (completionCountUpdater.incrementAndGet(Merger.this) == 2) {
                    // This CompletableSource.Subscriber will be notified on the correct Executor, but we need to use
                    // the same offloaded Subscriber as the Publisher to ensure that all events are sequenced properly.
                    offloadedSubscriber.onComplete();
                }
            }

            @Override
            public void onError(Throwable t) {
                subscription.cancel();
                // This CompletableSource.Subscriber will be notified on the correct Executor, but we need to use
                // the same offloaded Subscriber as the Publisher to ensure that all events are sequenced properly.
                offloadedSubscriber.onError(t);
            }
        }
    }
}
