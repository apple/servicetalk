/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.internal.ArrayUtils;
import io.servicetalk.concurrent.internal.DelayedCancellable;
import io.servicetalk.concurrent.internal.TerminalNotification;
import io.servicetalk.context.api.ContextMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverErrorFromSource;
import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverSuccessFromSource;
import static io.servicetalk.concurrent.internal.SubscriberUtils.logDuplicateTerminal;
import static io.servicetalk.concurrent.internal.ThrowableUtils.catchUnexpected;
import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

final class CacheSingle<T> extends AbstractNoHandleSubscribeSingle<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CacheSingle.class);
    private static final Subscriber<?>[] EMPTY_SUBSCRIBERS = new Subscriber[0];
    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<CacheSingle.State, Subscriber[]>
            newSubscribersUpdater = newUpdater(CacheSingle.State.class, Subscriber[].class, "subscribers");
    @SuppressWarnings("rawtypes")
    private static final AtomicIntegerFieldUpdater<CacheSingle.State> subscribeCountUpdater =
            AtomicIntegerFieldUpdater.newUpdater(CacheSingle.State.class, "subscribeCount");
    private final Single<T> original;
    private final BiFunction<T, Throwable, Completable> terminalResubscribe;
    private final int minSubscribers;
    private final boolean cancelUpstream;
    private volatile State state = new State();

    CacheSingle(Single<T> original, int minSubscribers, boolean cancelUpstream,
                BiFunction<T, Throwable, Completable> terminalResubscribe) {
        if (minSubscribers < 1) {
            throw new IllegalArgumentException("minSubscribers: " + minSubscribers + " (expected >1)");
        }
        this.original = original;
        this.minSubscribers = minSubscribers;
        this.cancelUpstream = cancelUpstream;
        this.terminalResubscribe = requireNonNull(terminalResubscribe);
    }

    @Override
    void handleSubscribe(Subscriber<? super T> subscriber, ContextMap contextMap,
                         AsyncContextProvider contextProvider) {
        state.addSubscriber(subscriber, contextMap, contextProvider);
    }

    private final class State implements Subscriber<T> {
        @SuppressWarnings("unchecked")
        volatile Subscriber<? super T>[] subscribers = (Subscriber<? super T>[]) EMPTY_SUBSCRIBERS;
        volatile int subscribeCount;
        private final DelayedCancellable delayedCancellable = new DelayedCancellable();

        void addSubscriber(Subscriber<? super T> subscriber, ContextMap contextMap,
                           AsyncContextProvider contextProvider) {
            final int sCount = subscribeCountUpdater.incrementAndGet(this);
            final ConcurrentOnSubscribeSubscriber<T> multiSubscriber =
                    new ConcurrentOnSubscribeSubscriber<>(subscriber);
            for (;;) {
                final Subscriber<? super T>[] currSubs = subscribers;
                if (currSubs.length == 1 && currSubs[0] instanceof TerminalSubscriber) {
                    @SuppressWarnings("unchecked")
                    final TerminalSubscriber<T> terminalSubscriber = (TerminalSubscriber<T>) currSubs[0];
                    terminalSubscriber.safeTerminateFromSource(subscriber);
                    break;
                } else {
                    @SuppressWarnings("unchecked")
                    Subscriber<? super T>[] newSubs = (Subscriber<? super T>[])
                            Array.newInstance(Subscriber.class, currSubs.length + 1);
                    System.arraycopy(currSubs, 0, newSubs, 0, currSubs.length);
                    newSubs[currSubs.length] = multiSubscriber;
                    if (newSubscribersUpdater.compareAndSet(this, currSubs, newSubs)) {
                        // Note we invoke onSubscribe AFTER the subscribers array because the subscription methods
                        // depend upon this state (cancellation needs the subscriber to be visible in the array). This
                        // may result in onSuccess(t) or onError(t) being called before onSubscribe() which is
                        // unexpected for the Subscriber API, but MulticastSubscriber can tolerate this and internally
                        // queues signals.
                        multiSubscriber.onSubscribe(() -> removeSubscriber(multiSubscriber));
                        if (sCount == minSubscribers) {
                            // This operator has special behavior where it chooses to use the AsyncContext and signal
                            // offloader from the last subscribe operation.
                            original.delegateSubscribe(this, contextMap, contextProvider);
                        }
                        break;
                    }
                }
            }
        }

        void removeSubscriber(Subscriber<T> subscriber) {
            for (;;) {
                final Subscriber<? super T>[] currSubs = subscribers;
                final int i = ArrayUtils.indexOf(subscriber, currSubs);
                if (i < 0) {
                    return;
                }
                @SuppressWarnings("unchecked")
                Subscriber<? super T>[] newSubs = (Subscriber<? super T>[])
                        Array.newInstance(Subscriber.class, currSubs.length - 1);
                if (i == 0) {
                    System.arraycopy(currSubs, 1, newSubs, 0, newSubs.length);
                } else {
                    System.arraycopy(currSubs, 0, newSubs, 0, i);
                    System.arraycopy(currSubs, i + 1, newSubs, i, newSubs.length - i);
                }
                if (newSubscribersUpdater.compareAndSet(this, currSubs, newSubs)) {
                    if (cancelUpstream && newSubs.length == 0) {
                        // Reset the state when all subscribers have cancelled to allow for re-subscribe.
                        state = new State();
                        delayedCancellable.cancel();
                    }
                    break;
                }
            }
        }

        @Override
        public void onSubscribe(final Cancellable cancellable) {
            delayedCancellable.delayedCancellable(cancellable);
        }

        @Override
        public void onSuccess(@Nullable final T result) {
            safeTerminalStateReset(result, null);
            terminate(new TerminalSubscriber<>(null, result), sub -> sub.onSuccess(result));
        }

        @Override
        public void onError(final Throwable t) {
            safeTerminalStateReset(null, t);
            terminate(new TerminalSubscriber<>(t, null), sub -> sub.onError(t));
        }

        private void terminate(TerminalSubscriber<T> terminalSubscriber, Consumer<Subscriber<? super T>> terminator) {
            @SuppressWarnings("unchecked")
            final Subscriber<? super T>[] newSubs = (Subscriber<? super T>[]) Array.newInstance(Subscriber.class, 1);
            newSubs[0] = terminalSubscriber;
            for (;;) {
                final Subscriber<? super T>[] subs = subscribers;
                if (newSubscribersUpdater.compareAndSet(this, subs, newSubs)) {
                    Throwable delayedCause = null;
                    for (final Subscriber<? super T> subscriber : subs) {
                        try {
                            terminator.accept(subscriber);
                        } catch (Throwable cause) {
                            delayedCause = catchUnexpected(delayedCause, cause);
                        }
                    }
                    if (delayedCause != null) {
                        throwException(delayedCause);
                    }
                    break;
                }
            }
        }

        private void safeTerminalStateReset(@Nullable T value, @Nullable Throwable t) {
            Completable completable;
            try {
                completable = terminalResubscribe.apply(value, t);
            } catch (Throwable cause) {
                LOGGER.warn("terminalStateReset {} threw", terminalResubscribe, cause);
                completable = Completable.never();
            }
            completable.whenFinally(() -> state = new State()).subscribe();
        }
    }

    private static final class ConcurrentOnSubscribeSubscriber<T> implements Subscriber<T> {
        @SuppressWarnings("rawtypes")
        private static final AtomicReferenceFieldUpdater<ConcurrentOnSubscribeSubscriber, Object> stateUpdater =
                AtomicReferenceFieldUpdater.newUpdater(ConcurrentOnSubscribeSubscriber.class, Object.class, "state");
        private static final Object INIT = new Object();
        private static final Object INVOKING_ON_SUBSCRIBE = new Object();
        private static final Object WAITING_FOR_TERMINAL = new Object();
        private static final Object TERMINATED = new Object();
        private final Subscriber<? super T> delegate;
        /**
         * <ul>
         *     <li>{@code INIT} - no {@link Subscriber} methods invoked</li>
         *     <li>{@link #INVOKING_ON_SUBSCRIBE} - about to invoke {@link #onSubscribe(Cancellable)}</li>
         *     <li>{@link #WAITING_FOR_TERMINAL} - {@link #onSubscribe(Cancellable)} has been invoked</li>
         *     <li>{@link TerminalNotification} - {@link #onError(Throwable)} has been invoked</li>
         *     <li>{@link #TERMINATED} - a terminal signal has been delivered to {@link #delegate}</li>
         *     <li>else - {@link #onSuccess(Object)} has been invoked</li>
         * </ul>
         */
        @Nullable
        private volatile Object state = INIT;

        private ConcurrentOnSubscribeSubscriber(final Subscriber<? super T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onSubscribe(final Cancellable cancellable) {
            for (;;) {
                final Object currState = state;
                if (currState == INIT && stateUpdater.compareAndSet(this, INIT, INVOKING_ON_SUBSCRIBE)) {
                    try {
                        delegate.onSubscribe(cancellable);
                    } finally {
                        if (!stateUpdater.compareAndSet(this, INVOKING_ON_SUBSCRIBE, WAITING_FOR_TERMINAL)) {
                            sendTerminal(state); // re-read state which has been changed by another thread.
                        }
                    }
                    break;
                } else if (currState == WAITING_FOR_TERMINAL || currState == INVOKING_ON_SUBSCRIBE) {
                    duplicateOnSubscribe(cancellable);
                    break;
                } else if (currState == TERMINATED) {
                    // Either we raced with terminal signal, or this is duplicate onSubscribe after terminal.
                    // Usage of this class in multicast prevents the latter.
                    break;
                } else if (currState != INIT) {
                    delayedOnSubscribe(currState);
                    break;
                }
            }
        }

        private void duplicateOnSubscribe(Cancellable cancellable) {
            try {
                cancellable.cancel(); // duplicate onSubscribe not supported
            } finally {
                logDuplicateTerminal(this);
            }
        }

        private void delayedOnSubscribe(@Nullable Object currState) {
            try {
                delegate.onSubscribe(IGNORE_CANCEL);
            } finally {
                sendTerminal(currState);
            }
        }

        private void sendTerminal(@Nullable Object currState) {
            state = TERMINATED;
            if (currState instanceof TerminalNotification) {
                Throwable cause = ((TerminalNotification) currState).cause();
                assert cause != null;
                delegate.onError(cause);
            } else {
                @SuppressWarnings("unchecked")
                final T t = (T) currState;
                delegate.onSuccess(t);
            }
        }

        @Override
        public void onSuccess(@Nullable final T result) {
            for (;;) {
                final Object currState = state;
                if (currState == WAITING_FOR_TERMINAL) {
                    state = TERMINATED; // onSubscribe already invoked, no need for atomic as no concurrency allowed.
                    delegate.onSuccess(result);
                    break;
                } else if (currState == INIT) {
                    if (stateUpdater.compareAndSet(this, INIT, TERMINATED)) {
                        deliverSuccessFromSource(delegate, result);
                        break;
                    }
                } else if (currState == INVOKING_ON_SUBSCRIBE) { // hand off to the onSubscribe thread.
                    if (stateUpdater.compareAndSet(this, INVOKING_ON_SUBSCRIBE, result)) {
                        break;
                    }
                } else {
                    logDuplicateTerminal(this);
                    break;
                }
            }
        }

        @Override
        public void onError(final Throwable t) {
            final TerminalNotification terminalNotification = TerminalNotification.error(t);
            for (;;) {
                final Object currState = state;
                if (currState == WAITING_FOR_TERMINAL) {
                    state = TERMINATED; // onSubscribe already invoked, no need for atomic as no concurrency allowed.
                    delegate.onError(t);
                    break;
                } else if (currState == INIT) {
                    if (stateUpdater.compareAndSet(this, INIT, TERMINATED)) {
                        deliverErrorFromSource(delegate, t);
                        break;
                    }
                } else if (currState == INVOKING_ON_SUBSCRIBE) { // hand off to the onSubscribe thread.
                    if (stateUpdater.compareAndSet(this, INVOKING_ON_SUBSCRIBE, terminalNotification)) {
                        break;
                    }
                } else {
                    logDuplicateTerminal(this);
                    break;
                }
            }
        }
    }

    private static final class TerminalSubscriber<T> implements Subscriber<T> {
        @Nullable
        private final Object terminal;
        private final boolean isSuccess;

        private TerminalSubscriber(@Nullable final Throwable error, @Nullable final T value) {
            if (error == null) {
                terminal = value;
                isSuccess = true;
            } else {
                terminal = error;
                isSuccess = false;
            }
        }

        @SuppressWarnings("unchecked")
        private void safeTerminateFromSource(Subscriber<? super T> sub) {
            if (isSuccess) {
                deliverSuccessFromSource(sub, (T) terminal);
            } else {
                assert terminal != null;
                deliverErrorFromSource(sub, (Throwable) terminal);
            }
        }

        @Override
        public void onSubscribe(final Cancellable cancellable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onSuccess(@Nullable final T result) {
            if (isSuccess) {
                throw new IllegalStateException("terminal signal already received in onSuccess. new: " + result,
                        (Throwable) terminal);
            }
            throw new IllegalStateException("terminal signal already received in onSuccess. old: " + terminal +
                    " new: " + result);
        }

        @Override
        public void onError(final Throwable t) {
            throw new IllegalStateException("duplicate terminal signal in onError. old: " + terminal, t);
        }
    }
}
