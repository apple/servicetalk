/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.internal;

import io.servicetalk.concurrent.PublisherSource;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.PublisherSource.Subscriber;
import static io.servicetalk.concurrent.PublisherSource.Subscription;
import static java.util.Objects.requireNonNull;

/**
 * A {@link Subscriber} that allows for concurrent delivery of terminal events.
 *
 * @param <T> The type of {@link Subscriber}.
 */
public final class ConcurrentTerminalSubscriber<T> implements Subscriber<T> {
    private static final int SUBSCRIBER_STATE_INVALID = Integer.MIN_VALUE;
    private static final int SUBSCRIBER_STATE_WAITING_ON_SUBSCRIBE = -1;
    private static final int SUBSCRIBER_STATE_IDLE = 0;
    private static final int SUBSCRIBER_STATE_ON_NEXT = 1;
    private static final int SUBSCRIBER_STATE_TERMINATING = 2;
    private static final int SUBSCRIBER_STATE_TERMINATED = 3;

    private static final AtomicIntegerFieldUpdater<ConcurrentTerminalSubscriber> stateUpdater =
            AtomicIntegerFieldUpdater.newUpdater(ConcurrentTerminalSubscriber.class, "state");

    private final Subscriber<T> delegate;
    @Nullable
    private TerminalNotification terminalNotification;
    private volatile int state;

    /**
     * Create a new instance.
     *
     * @param delegate The {@link Subscriber} to delegate all signals to.
     */
    public ConcurrentTerminalSubscriber(Subscriber<T> delegate) {
        this(delegate, true);
    }

    /**
     * Create a new instance.
     *
     * @param delegate The {@link Subscriber} to delegate all signals to.
     * @param concurrentOnSubscribe {@code false} to not guard for concurrency on
     * {@link Subscriber#onSubscribe(PublisherSource.Subscription)}. {@code true} means that
     * {@link Subscriber#onSubscribe(PublisherSource.Subscription)} will be protected against concurrent invocation with
     * terminal methods.
     */
    public ConcurrentTerminalSubscriber(Subscriber<T> delegate, boolean concurrentOnSubscribe) {
        this.delegate = requireNonNull(delegate);
        state = concurrentOnSubscribe ? SUBSCRIBER_STATE_WAITING_ON_SUBSCRIBE : SUBSCRIBER_STATE_IDLE;
    }

    @Override
    public void onSubscribe(final Subscription subscription) {
        final boolean wasWaiting = state == SUBSCRIBER_STATE_WAITING_ON_SUBSCRIBE;

        try {
            delegate.onSubscribe(subscription);
        } finally {
            if (wasWaiting) {
                for (;;) {
                    final int localState = state;
                    if (localState == SUBSCRIBER_STATE_WAITING_ON_SUBSCRIBE) {
                        if (stateUpdater.compareAndSet(this, SUBSCRIBER_STATE_WAITING_ON_SUBSCRIBE,
                                SUBSCRIBER_STATE_IDLE)) {
                            break;
                        }
                    } else if (localState == SUBSCRIBER_STATE_TERMINATING) {
                        if (stateUpdater.compareAndSet(this, SUBSCRIBER_STATE_TERMINATING,
                                SUBSCRIBER_STATE_TERMINATED)) {
                            assert terminalNotification != null;
                            terminalNotification.terminate(delegate);
                            break;
                        }
                    } else {
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void onNext(@Nullable final T t) {
        int originalState = SUBSCRIBER_STATE_INVALID;
        for (;;) {
            final int localState = state;
            if (localState == SUBSCRIBER_STATE_IDLE || localState == SUBSCRIBER_STATE_WAITING_ON_SUBSCRIBE) {
                if (stateUpdater.compareAndSet(this, localState, SUBSCRIBER_STATE_ON_NEXT)) {
                    originalState = localState;
                    break;
                }
            } else if (localState == SUBSCRIBER_STATE_ON_NEXT) {
                // Allow reentry because we don't want to drop data.
                break;
            } else {
                // The only possible state is TERMINATED. We don't have to worry about concurrency for
                // Subscriber#onNext.
                return;
            }
        }
        try {
            delegate.onNext(t);
        } finally {
            if (originalState != SUBSCRIBER_STATE_INVALID) {
                for (;;) {
                    final int localState = state;
                    if (localState == SUBSCRIBER_STATE_ON_NEXT) {
                        if (stateUpdater.compareAndSet(this, SUBSCRIBER_STATE_ON_NEXT, originalState)) {
                            break;
                        }
                    } else if (localState == SUBSCRIBER_STATE_TERMINATING) {
                        if (stateUpdater.compareAndSet(this, SUBSCRIBER_STATE_TERMINATING,
                                SUBSCRIBER_STATE_TERMINATED)) {
                            assert terminalNotification != null;
                            terminalNotification.terminate(delegate);
                            break;
                        }
                    } else {
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void onError(final Throwable t) {
        processOnError(t);
    }

    /**
     * Attempt to process {@link #onError(Throwable)}.
     *
     * @param t The error to process.
     * @return {@code true} if the terminal signal was propagated to the delegate {@link Subscriber}.
     */
    public boolean processOnError(final Throwable t) {
        for (;;) {
            final int localState = state;
            if (localState == SUBSCRIBER_STATE_TERMINATED || localState == SUBSCRIBER_STATE_TERMINATING) {
                return false;
            } else {
                // We may overwrite the terminalNotification if there is concurrency on this method, but there is no
                // guarantee about what terminal notification will be propagated in the event of concurrency anyways.
                terminalNotification = TerminalNotification.error(t);
                if (stateUpdater.compareAndSet(this, localState, SUBSCRIBER_STATE_TERMINATING)) {
                    // We only propagate the terminal event here if the localState was SUBSCRIBER_STATE_IDLE, because
                    // otherwise this means we maybe interacting with the Subscriber on another thread.
                    if (localState == SUBSCRIBER_STATE_IDLE &&
                            stateUpdater.compareAndSet(this, SUBSCRIBER_STATE_TERMINATING,
                                    SUBSCRIBER_STATE_TERMINATED)) {
                        delegate.onError(t);
                        return true;
                    }
                    return false;
                }
            }
        }
    }

    @Override
    public void onComplete() {
        processOnComplete();
    }

    /**
     * Attempt to process {@link #onComplete()}.
     *
     * @return {@code true} if the terminal signal was propagated to the delegate {@link Subscriber}.
     */
    public boolean processOnComplete() {
        for (;;) {
            final int localState = state;
            if (localState == SUBSCRIBER_STATE_TERMINATED || localState == SUBSCRIBER_STATE_TERMINATING) {
                return false;
            } else {
                // We may overwrite the terminalNotification if there is concurrency on this method, but there is no
                // guarantee about what terminal notification will be propagated in the event of concurrency anyways.
                terminalNotification = TerminalNotification.complete();
                if (stateUpdater.compareAndSet(this, localState, SUBSCRIBER_STATE_TERMINATING)) {
                    // We only propagate the terminal event here if the localState was SUBSCRIBER_STATE_IDLE, because
                    // otherwise this means we maybe interacting with the Subscriber on another thread.
                    if (localState == SUBSCRIBER_STATE_IDLE &&
                            stateUpdater.compareAndSet(this, SUBSCRIBER_STATE_TERMINATING,
                                    SUBSCRIBER_STATE_TERMINATED)) {
                        delegate.onComplete();
                        return true;
                    }
                    return false;
                }
            }
        }
    }
}
