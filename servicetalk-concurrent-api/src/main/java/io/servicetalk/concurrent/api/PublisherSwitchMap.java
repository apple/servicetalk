/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.internal.TerminalNotification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.CompositeExceptionUtils.addPendingError;
import static io.servicetalk.concurrent.api.CompositeExceptionUtils.maxDelayedErrors;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.EmptySubscriptions.EMPTY_SUBSCRIPTION;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static io.servicetalk.concurrent.internal.TerminalNotification.error;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

final class PublisherSwitchMap<T, R> extends AbstractAsynchronousPublisherOperator<T, R> {
    private static final Logger LOGGER = LoggerFactory.getLogger(PublisherSwitchMap.class);
    private final int maxDelayedErrors;
    private final Function<? super T, ? extends Publisher<? extends R>> mapper;

    PublisherSwitchMap(final Publisher<T> original,
                       final boolean delayError,
                       final Function<? super T, ? extends Publisher<? extends R>> mapper) {
        this(original, maxDelayedErrors(delayError), mapper);
    }

    PublisherSwitchMap(final Publisher<T> original,
                       final int maxDelayedErrors,
                       final Function<? super T, ? extends Publisher<? extends R>> mapper) {
        super(original);
        if (maxDelayedErrors < 0) {
            throw new IllegalArgumentException("maxDelayedErrors: " + maxDelayedErrors + " (expected >=0)");
        }
        this.maxDelayedErrors = maxDelayedErrors;
        this.mapper = requireNonNull(mapper);
    }

    @Override
    public Subscriber<? super T> apply(final Subscriber<? super R> subscriber) {
        return new SwitchSubscriber<>(subscriber, this);
    }

    private static final class SwitchSubscriber<T, R> implements Subscriber<T> {
        @SuppressWarnings("rawtypes")
        private static final AtomicIntegerFieldUpdater<SwitchSubscriber.RSubscriber> stateUpdater =
                newUpdater(SwitchSubscriber.RSubscriber.class, "state");
        @SuppressWarnings("rawtypes")
        private static final AtomicIntegerFieldUpdater<SwitchSubscriber> pendingErrorCountUpdater =
                AtomicIntegerFieldUpdater.newUpdater(SwitchSubscriber.class, "pendingErrorCount");
        @SuppressWarnings("rawtypes")
        private static final AtomicReferenceFieldUpdater<SwitchSubscriber, Throwable> pendingErrorUpdater =
                AtomicReferenceFieldUpdater.newUpdater(SwitchSubscriber.class, Throwable.class, "pendingError");
        private static final int INNER_STATE_IDLE = 0;
        private static final int INNER_STATE_EMITTING = 1;
        private static final int INNER_STATE_DISPOSED = 2;
        private static final int INNER_STATE_COMPLETE = 3;
        private static final int INNER_STATE_ERROR = 4;
        private static final int OUTER_STATE_SHIFT = 3;
        private static final int OUTER_STATE_MASK = -8;
        private static final int INNER_STATE_MASK = ~OUTER_STATE_MASK;
        private static final int OUTER_STATE_COMPLETE = 1;
        private static final int OUTER_STATE_ERROR = 2;
        private final SequentialSubscription rSubscription = new SequentialSubscription();
        private final PublisherSwitchMap<T, R> parent;
        private final Subscriber<? super R> target;
        @Nullable
        private Subscription tSubscription;
        @Nullable
        private RSubscriber currPublisher;
        @SuppressWarnings("unused")
        private volatile int pendingErrorCount;
        @SuppressWarnings("unused")
        @Nullable
        private volatile Throwable pendingError;

        private SwitchSubscriber(final Subscriber<? super R> target,
                                 final PublisherSwitchMap<T, R> parent) {
            this.target = target;
            this.parent = parent;
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            // No need to wrap in ConcurrentSubscription because only the latest RSubscriber interacts with the
            // tSubscription which is atomically protected by the state variable.
            tSubscription = requireNonNull(subscription);
            target.onSubscribe(rSubscription);
            tSubscription.request(1);
        }

        @Override
        public void onNext(@Nullable T t) {
            final Publisher<? extends R> nextPub = requireNonNull(parent.mapper.apply(t),
                    () -> "Mapper " + parent.mapper + " returned null");
            currPublisher = new RSubscriber(currPublisher);
            toSource(nextPub).subscribe(currPublisher);
        }

        @Override
        public void onError(Throwable t) {
            if (currPublisher != null) {
                try {
                    if (parent.maxDelayedErrors <= 0) {
                        currPublisher.dispose(EMPTY_SUBSCRIPTION);
                    }
                } finally {
                    final Throwable cause = outerErrorUpdateState(t);
                    if (cause != null) {
                        target.onError(cause);
                    }
                }
            } else {
                target.onError(t);
            }
        }

        @Override
        public void onComplete() {
            // If current publisher isn't null defer terminal signals to that publisher, otherwise terminate here.
            TerminalNotification terminalNotification = complete();
            if (currPublisher == null || (terminalNotification = outerCompleteUpdateState()) != null) {
                terminalNotification.terminate(target);
            }
        }

        @Nullable
        private Throwable outerErrorUpdateState(Throwable t) {
            assert currPublisher != null;
            t = addPendingError(pendingErrorUpdater, pendingErrorCountUpdater, this, parent.maxDelayedErrors, t);
            for (;;) {
                final int cState = currPublisher.state;
                if (stateUpdater.compareAndSet(currPublisher, cState, setOuterState(cState, OUTER_STATE_ERROR))) {
                    final int innerState = getInnerState(cState);
                    return parent.maxDelayedErrors <= 0 && innerState != INNER_STATE_ERROR ||
                            (parent.maxDelayedErrors > 0 &&
                            (innerState == INNER_STATE_ERROR || innerState == INNER_STATE_COMPLETE)) ? t : null;
                }
            }
        }

        @Nullable
        private TerminalNotification outerCompleteUpdateState() {
            assert currPublisher != null;
            for (;;) {
                final int cState = currPublisher.state;
                if (stateUpdater.compareAndSet(currPublisher, cState, setOuterState(cState, OUTER_STATE_COMPLETE))) {
                    final int innerState = getInnerState(cState);
                    if (parent.maxDelayedErrors <= 0) {
                        return innerState == INNER_STATE_COMPLETE ? complete() : null;
                    } else if (innerState == INNER_STATE_ERROR || innerState == INNER_STATE_COMPLETE) {
                        final Throwable cPendingError = pendingError;
                        return cPendingError != null ? error(cPendingError) : complete();
                    }
                    return null;
                }
            }
        }

        private static int setOuterState(int currState, int newState) {
            return (newState << OUTER_STATE_SHIFT) | (currState & INNER_STATE_MASK);
        }

        private static int setInnerState(int currState, int newState) {
            return (currState & OUTER_STATE_MASK) | newState;
        }

        private static int getOuterState(int state) {
            return state >> OUTER_STATE_SHIFT;
        }

        private static int getInnerState(int state) {
            return state & INNER_STATE_MASK;
        }

        private final class RSubscriber implements Subscriber<R> {
            volatile int state;
            @Nullable
            private RSubscriber prevPublisher;
            @Nullable
            private Subscription localSubscription;
            @Nullable
            private Subscription nextSubscriptionIfDisposePending;

            private RSubscriber(@Nullable RSubscriber prevPublisher) {
                this.prevPublisher = prevPublisher;
            }

            @Override
            public void onSubscribe(Subscription subscription) {
                localSubscription = requireNonNull(subscription);
                if (prevPublisher != null) {
                    final RSubscriber localPrev = prevPublisher;
                    prevPublisher = null; // Set the reference to null to avoid memory leak.
                    localPrev.dispose(subscription);
                } else {
                    switchTo(subscription);
                }
            }

            @Override
            public void onNext(@Nullable R result) {
                int innerState;
                for (;;) {
                    final int cState = state;
                    innerState = getInnerState(cState);
                    final int outerState = getOuterState(cState);
                    if (outerState == OUTER_STATE_ERROR && parent.maxDelayedErrors <= 0) {
                        return;
                    } else if (innerState == INNER_STATE_IDLE) {
                        if (stateUpdater.compareAndSet(this, cState, setInnerState(cState, INNER_STATE_EMITTING))) {
                            break;
                        }
                    } else if (innerState == INNER_STATE_EMITTING) {
                        // Allow reentry because we don't want to drop data.
                        break;
                    } else {
                        LOGGER.debug("Disposed Subscriber ignoring signal state={} subscriber='{}' onNext='{}'",
                                cState, SwitchSubscriber.this, result);
                        // Only states are COMPLETED, ERROR, and DISPOSED.
                        // DISPOSED -> Subscriber is no longer the newest subscriber, and it is OK to drop data
                        // because the "newest"/"active" Subscriber is assumed to get the "current" state as the
                        // first onNext signal and indicated a "switch" and downstream will do a delta between "old"
                        // and "current" state.
                        // COMPLETED / ERROR -> This is a terminal state, and re-try/subscribe must happen to reset
                        // state.

                        // It is OK to not call dataSubscription.itemReceived() because we won't be propagating it
                        // downstream when we switch to a newer subscriber we want to request more items to preserve
                        // the demand from downstream Subscription.
                        return;
                    }
                }
                try {
                    rSubscription.itemReceived();
                    target.onNext(result);
                } finally {
                    // Only attempt "unlock" if we acquired the lock, otherwise this is reentry and when the stack
                    // unwinds the lock will be released.
                    if (innerState == INNER_STATE_IDLE) {
                        for (;;) {
                            final int cState = state;
                            innerState = getInnerState(cState);
                            if (innerState == INNER_STATE_DISPOSED) {
                                assert nextSubscriptionIfDisposePending != null;
                                assert localSubscription != null;
                                switchWhenDisposed(localSubscription, nextSubscriptionIfDisposePending);
                            } else if (innerState == INNER_STATE_ERROR || innerState == INNER_STATE_COMPLETE ||
                                    stateUpdater.compareAndSet(this, cState, setInnerState(cState, INNER_STATE_IDLE))) {
                                break;
                            }
                        }
                    }
                }
            }

            @Override
            public void onError(Throwable t) {
                Throwable currPendingError = null;
                for (;;) {
                    final int cState = state;
                    final int innerState = getInnerState(cState);
                    if (innerState == INNER_STATE_DISPOSED) {
                        break;
                    } else if (parent.maxDelayedErrors <= 0) {
                        if (stateUpdater.compareAndSet(this, cState, setInnerState(cState, INNER_STATE_ERROR))) {
                            final int outerState = getOuterState(cState);
                            if (outerState != OUTER_STATE_ERROR) {
                                try {
                                    if (outerState != OUTER_STATE_COMPLETE) {
                                        assert tSubscription != null;
                                        tSubscription.cancel();
                                    }
                                } finally {
                                    target.onError(t);
                                }
                            }
                            break;
                        }
                    } else {
                        if (currPendingError == null) {
                            currPendingError = addPendingError(pendingErrorUpdater, pendingErrorCountUpdater,
                                    SwitchSubscriber.this, parent.maxDelayedErrors, t);
                        }

                        if (stateUpdater.compareAndSet(this, cState, setInnerState(cState, INNER_STATE_ERROR))) {
                            final int outerState = getOuterState(cState);
                            if (outerState == OUTER_STATE_ERROR || outerState == OUTER_STATE_COMPLETE) {
                                target.onError(currPendingError);
                            } else {
                                // It is possible the outer Publisher will concurrently terminate with this method and
                                // requesting more is not necessary, but still safe (e.g. no concurrent invocation on
                                // tSubscription or target).
                                assert tSubscription != null;
                                tSubscription.request(1);
                            }
                            break;
                        }
                    }
                }
            }

            @Override
            public void onComplete() {
                for (;;) {
                    final int cState = state;
                    final int innerState = getInnerState(cState);
                    if (innerState == INNER_STATE_DISPOSED) {
                        break;
                    } else if (stateUpdater.compareAndSet(this, cState, setInnerState(cState, INNER_STATE_COMPLETE))) {
                        final int outerState = getOuterState(cState);
                        if (outerState == OUTER_STATE_COMPLETE) {
                            target.onComplete();
                        } else if (outerState == OUTER_STATE_ERROR && parent.maxDelayedErrors > 0) {
                            final Throwable cause = pendingError;
                            assert cause != null;
                            target.onError(cause);
                        } else if (outerState != OUTER_STATE_ERROR) {
                            // It is possible the outer Publisher will concurrently terminate with this method and
                            // requesting more is not necessary, but still safe (e.g. no concurrent invocation on
                            // tSubscription or target).
                            assert tSubscription != null;
                            tSubscription.request(1);
                        }
                        break;
                    }
                }
            }

            void dispose(Subscription nextSubscription) {
                nextSubscriptionIfDisposePending = nextSubscription;
                for (;;) {
                    final int cState = state;
                    final int innerState = getInnerState(cState);
                    if (innerState == INNER_STATE_DISPOSED ||
                            // Don't change state if no delayedErrors, and we have already terminated downstream with an
                            // error. This prevents duplicated termination if upstream terminates with an error later.
                            (innerState == INNER_STATE_ERROR && parent.maxDelayedErrors <= 0)) {
                        break;
                    } else if (stateUpdater.compareAndSet(this, cState, setInnerState(cState, INNER_STATE_DISPOSED))) {
                        // if emitting -> onNext will handle after it is done to avoid concurrency
                        // if idle     -> next element arrived, switch to new Publisher
                        // if complete / error -> completed before the next element, need to switch when it arrives
                        if (innerState != INNER_STATE_EMITTING) {
                            assert localSubscription != null;
                            switchWhenDisposed(localSubscription, nextSubscription);
                        }
                        break;
                    }
                }
            }

            private void switchWhenDisposed(Subscription mySubscription, Subscription nextSubscription) {
                try {
                    mySubscription.cancel();
                } finally {
                    switchTo(nextSubscription);
                }
            }

            private void switchTo(Subscription nextSubscription) {
                try {
                    rSubscription.switchTo(nextSubscription);
                } finally {
                    assert tSubscription != null;
                    tSubscription.request(1);
                }
            }
        }
    }
}
