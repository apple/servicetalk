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
import io.servicetalk.concurrent.internal.DelayedCancellable;
import io.servicetalk.concurrent.internal.DelayedSubscription;
import io.servicetalk.concurrent.internal.SignalOffloader;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

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
        private static final AtomicIntegerFieldUpdater<Merger> stateUpdater = newUpdater(Merger.class, "state");
        private static final int PUBLISHER_TERMINATED = 1;
        private static final int COMPLETABLE_TERMINATED = 2;
        private static final int COMPLETABLE_ERROR = 4;
        private static final int IN_ON_NEXT = 8;
        private static final int ALL_TERMINATED = PUBLISHER_TERMINATED | COMPLETABLE_TERMINATED;
        private static final int COMPLETABLE_ALL_TERM = ALL_TERMINATED | COMPLETABLE_ERROR;
        private final CompletableSubscriber completableSubscriber;
        private final Subscriber<? super T> offloadedSubscriber;
        private final DelayedSubscription subscription = new DelayedSubscription();
        @Nullable
        private Throwable completableError;
        private volatile int state;

        Merger(Subscriber<? super T> subscriber, SignalOffloader signalOffloader, AsyncContextMap contextMap,
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
        public void onSubscribe(Subscription targetSubscription) {
            // We may cancel from multiple threads and DelayedSubscription will atomically swap if a cancel occurs but
            // it will not prevent concurrent access between request(n) and cancel() on the targetSubscription.
            subscription.delayedSubscription(ConcurrentSubscription.wrap(targetSubscription));
        }

        @Override
        public void onNext(@Nullable T t) {
            for (;;) {
                final int currState = state;
                int newState;
                if (isState(currState, ALL_TERMINATED)) {
                    // If downstream subscriber has been terminated, we can't deliver any more signals.
                    break;
                } else if (isState(currState, COMPLETABLE_TERMINATED)) {
                    // No need to acquire lock if the Completable has terminated, there will be no concurrency.
                    offloadedSubscriber.onNext(t);
                    break;
                } else if (stateUpdater.compareAndSet(this, currState, (newState = setState(currState, IN_ON_NEXT)))) {
                    try {
                        offloadedSubscriber.onNext(t);
                    } finally {
                        // Only attempt unlock if this method invocation acquired the lock. If this call is re-entry we
                        // don't want to unlock.
                        if (!isState(currState, IN_ON_NEXT) &&
                                !stateUpdater.compareAndSet(this, newState, clearState(newState, IN_ON_NEXT))) {
                            onNextUnLockFail();
                        }
                    }
                    break;
                }
            }
        }

        private void onNextUnLockFail() {
            for (;;) {
                final int currState = state;
                if (isState(currState, COMPLETABLE_ERROR)) {
                    assert completableError != null;
                    offloadedSubscriber.onError(completableError);
                    break;
                } else if (stateUpdater.compareAndSet(this, currState, clearState(currState, IN_ON_NEXT))) {
                    // Clear the state because we may have re-entry terminated with onComplete and if the Completable
                    // fails in the future it should propagate the failure.
                    break;
                }
            }
        }

        @Override
        public void onError(Throwable t) {
            for (;;) {
                final int currState = state;
                if (isState(currState, PUBLISHER_TERMINATED)) {
                    // If Publisher is terminated that means the Completable propagated an onError
                    break;
                } else if (stateUpdater.compareAndSet(this, currState, setState(currState, ALL_TERMINATED))) {
                    try {
                        if (!isState(currState, COMPLETABLE_TERMINATED)) {
                            completableSubscriber.cancel();
                        }
                    } finally {
                        offloadedSubscriber.onError(t);
                    }
                    break;
                }
            }
        }

        @Override
        public void onComplete() {
            for (;;) {
                final int currState = state;
                if (isState(currState, PUBLISHER_TERMINATED)) {
                    // If Publisher is terminated that means the Completable propagated an onError
                    break;
                } else if (stateUpdater.compareAndSet(this, currState, setState(currState, PUBLISHER_TERMINATED))) {
                    if (isState(currState, COMPLETABLE_TERMINATED)) {
                        offloadedSubscriber.onComplete();
                    }
                    break;
                }
            }
        }

        private static boolean isState(int state, int stateToCheck) {
            return (state & stateToCheck) == stateToCheck;
        }

        private static int clearState(int state, int stateToClear) {
            return state & ~stateToClear;
        }

        private static int setState(int state, int stateToSet) {
            return state | stateToSet;
        }

        private final class CompletableSubscriber extends DelayedCancellable implements CompletableSource.Subscriber {
            @Override
            public void onSubscribe(Cancellable cancellable) {
                delayedCancellable(cancellable);
            }

            @Override
            public void onComplete() {
                for (;;) {
                    final int currState = state;
                    if (isState(currState, COMPLETABLE_TERMINATED)) {
                        // If Completable is terminated that means the Publisher propagated an onError
                        break;
                    } else if (stateUpdater.compareAndSet(Merger.this, currState,
                            setState(currState, COMPLETABLE_TERMINATED))) {
                        if (isState(currState, PUBLISHER_TERMINATED)) {
                            // This CompletableSource.Subscriber will be notified on the correct Executor, but we need
                            // to use the same offloaded Subscriber as the Publisher to ensure that all events are
                            // sequenced properly.
                            offloadedSubscriber.onComplete();
                        }
                        break;
                    }
                }
            }

            @Override
            public void onError(Throwable t) {
                // store a reference to the error in case we are in onNext and not able to deliver here.
                completableError = t;
                for (;;) {
                    final int currState = state;
                    if (isState(currState, COMPLETABLE_TERMINATED)) {
                        // If Completable is terminated that means the Publisher propagated an onError
                        break;
                    } else if (stateUpdater.compareAndSet(Merger.this, currState,
                            setState(currState, COMPLETABLE_ALL_TERM))) {
                        try {
                            if (!isState(currState, PUBLISHER_TERMINATED)) {
                                subscription.cancel();
                            }
                        } finally {
                            if (!isState(currState, IN_ON_NEXT)) {
                                // This CompletableSource.Subscriber will be notified on the correct Executor, but we
                                // need to use the same offloaded Subscriber as the Publisher to ensure that all events
                                // are sequenced properly.
                                offloadedSubscriber.onError(t);
                            }
                        }
                        break;
                    }
                }
            }
        }
    }
}
