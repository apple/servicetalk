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
package io.servicetalk.concurrent.api.internal;

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.DuplicateSubscribeException;
import io.servicetalk.concurrent.internal.FlowControlUtil;
import io.servicetalk.concurrent.internal.TerminalNotification;
import io.servicetalk.oio.api.PayloadWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.LockSupport;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.error;
import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverTerminalFromSource;
import static io.servicetalk.concurrent.internal.SubscriberUtils.handleExceptionFromOnSubscribe;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

/**
 * A {@link PayloadWriter} that can be {@link #connect() connected} to a sink such that any data written on the
 * {@link PayloadWriter} is eventually emitted to the connected {@link Publisher} {@link Subscriber}.
 *
 * @param <T> The type of data for the {@link PayloadWriter}.
 */
public final class ConnectablePayloadWriter<T> implements PayloadWriter<T> {
    private static final AtomicLongFieldUpdater<ConnectablePayloadWriter> requestedUpdater =
            AtomicLongFieldUpdater.newUpdater(ConnectablePayloadWriter.class, "requested");
    private static final AtomicReferenceFieldUpdater<ConnectablePayloadWriter, TerminalNotification> closedUpdater =
            AtomicReferenceFieldUpdater.newUpdater(
                    ConnectablePayloadWriter.class, TerminalNotification.class, "closed");
    private static final AtomicReferenceFieldUpdater<ConnectablePayloadWriter, Object> stateUpdater =
            newUpdater(ConnectablePayloadWriter.class, Object.class, "state");

    /**
     * A field that assumes various states:
     * <ul>
     * <li>{@link State#DISCONNECTED} - waiting for {@link #connect()} to be called</li>
     * <li>{@link State#CONNECTING} - {@link #connect()} has been called, but no {@link Subscriber} yet.</li>
     * <li>{@link State#CONNECTED} - {@link Publisher} created, logically connected but awaiting {@link Subscriber}</li>
     * <li>{@link Subscriber} - connected to the {@link Subscriber}, waiting for items to be emitted or termination</li>
     * <li>{@link State#TERMINATING} - we have {@link #close()}, but not yet delivered to the {@link Subscriber}</li>
     * <li>{@link State#TERMINATED} - we have delivered a terminal signal to the {@link Subscriber}</li>
     * </ul>
     */
    private volatile Object state = State.DISCONNECTED;
    private volatile long requested;
    @Nullable
    private volatile TerminalNotification closed;
    @Nullable
    private volatile Thread writerThread;

    @Override
    public void write(final T t) throws IOException {
        verifyOpen();
        // This can only be called from a single thread, so optimistically decrement and not worry about underflow
        // because there will be no other thread decrementing this value. If we go negative we increment and wait.
        final long currRequested = requestedUpdater.decrementAndGet(this);
        if (currRequested < 0) {
            requestedUpdater.incrementAndGet(this);
            waitForRequestNDemand();
        }

        final Subscriber<? super T> s = waitForSubscriber();
        try {
            s.onNext(t);
        } catch (final Throwable cause) {
            closed = TerminalNotification.error(cause);
            state = State.TERMINATED;
            s.onError(cause);
            throw cause;
        }
    }

    @Override
    public void flush() throws IOException {
        // We currently don't queue any data at this layer, so there is nothing to do here.
        verifyOpen();
    }

    @Override
    public void close() throws IOException {
        // Set closed before state, because the Subscriber thread depends upon this ordering in the event it needs to
        // terminate the Subscriber.
        if (closedUpdater.compareAndSet(this, null, TerminalNotification.complete())) {
            for (;;) {
                Object currState = state;
                if (currState == State.TERMINATED || currState == State.CONNECTED) {
                    break;
                } else if (currState instanceof Subscriber) {
                    if (stateUpdater.compareAndSet(this, currState, State.TERMINATED)) {
                        ((Subscriber) currState).onComplete();
                        break;
                    }
                } else if (stateUpdater.compareAndSet(this, currState, State.TERMINATING)) {
                    break;
                }
            }
        } else {
            Object currState = stateUpdater.getAndSet(this, State.TERMINATED);
            if (currState instanceof Subscriber) {
                final TerminalNotification currClosed = closed;
                assert currClosed != null;
                currClosed.terminate((Subscriber<?>) currState);
            }
        }
    }

    /**
     * Connects this {@link PayloadWriter} to the returned {@link Publisher} such that any data written to this
     * {@link PayloadWriter} is eventually delivered to a {@link Subscriber} of the returned {@link Publisher}.
     *
     * @return {@link Publisher} that will emit all data written to this {@link PayloadWriter} to its
     * {@link Subscriber}. Only a single active {@link Subscriber} is allowed for this {@link Publisher}.
     */
    public Publisher<T> connect() {
        return stateUpdater.compareAndSet(this, State.DISCONNECTED, State.CONNECTING) ? new ConnectedPublisher<>(this) :
                error(new IllegalStateException("Stream state " + state + " is not valid for connect."));
    }

    private void verifyOpen() throws IOException {
        TerminalNotification currClosed = closed;
        if (currClosed != null) {
            Object currState = stateUpdater.getAndSet(this, State.TERMINATED);
            if (currState instanceof Subscriber) {
                currClosed.terminate((Subscriber<?>) currState);
            }
            throw new IOException("Already closed " + currClosed);
        }
    }

    private void waitForRequestNDemand() throws IOException {
        writerThread = Thread.currentThread();
        // After we set the writerThread, and before we park we have to check if there has been some requested demand in
        // the mean time or else we may deadlock because the Subscription thread may not have seen the writerThread.
        if (requested > 0) {
            writerThread = null;
            // There will only ever be a single thread decrementing, so no need to use a CaS, we can just decrement.
            requestedUpdater.decrementAndGet(this);
        } else {
            for (;;) {
                LockSupport.park();
                // There will only ever be a single thread decrementing, so no need to use a CaS, we can just decrement.
                final long currRequested = requestedUpdater.decrementAndGet(this);
                if (currRequested >= 0) {
                    writerThread = null;
                    break;
                } else {
                    requestedUpdater.incrementAndGet(this);
                    // While we are waiting for interaction with the Subscription, if the Subscription contract is
                    // violated that may result in a terminal notification, which doesn't require any demand to deliver.
                    TerminalNotification currClosed = closed;
                    if (currClosed != null) {
                        writerThread = null;
                        final Subscriber<? super T> s = waitForSubscriber();
                        state = State.TERMINATED;
                        currClosed.terminate(s);
                        throw new IOException("Already closed " + currClosed);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Subscriber<? super T> waitForSubscriber() throws IOException {
        Object currState = state;
        if (!(currState instanceof Subscriber)) {
            writerThread = Thread.currentThread();
            for (;;) {
                LockSupport.park();
                currState = state;
                if (currState instanceof Subscriber) {
                    writerThread = null;
                    break;
                } else if (currState == State.TERMINATED) {
                    writerThread = null;
                    throw new IOException("Already closed " + closed);
                }
            }
        }
        return (Subscriber<? super T>) currState;
    }

    private static final class ConnectedPublisher<T> extends Publisher<T> {
        private static final Logger LOGGER = LoggerFactory.getLogger(ConnectedPublisher.class);
        private final ConnectablePayloadWriter<T> outer;

        ConnectedPublisher(final ConnectablePayloadWriter<T> outer) {
            this.outer = outer;
        }

        @Override
        protected void handleSubscribe(final Subscriber<? super T> subscriber) {
            if (!stateUpdater.compareAndSet(outer, State.CONNECTING, State.CONNECTED)) {
                if (stateUpdater.compareAndSet(outer, State.TERMINATING, State.TERMINATED)) {
                    deliverTerminalFromSource(subscriber);
                } else {
                    deliverTerminalFromSource(subscriber, new DuplicateSubscribeException(outer.state, subscriber));
                }
                return;
            }

            try {
                subscriber.onSubscribe(new Subscription() {
                    @Override
                    public void request(final long n) {
                        if (isRequestNValid(n)) {
                            requestedUpdater.accumulateAndGet(outer, n, FlowControlUtil::addWithOverflowProtection);
                            unparkWriterThread();
                        } else if (closedUpdater.compareAndSet(outer, null,
                                TerminalNotification.error(newExceptionForInvalidRequestN(n)))) {
                            unparkWriterThread();
                        } else {
                            LOGGER.warn("invalid request({}), but already closed.", n);
                        }
                    }

                    @Override
                    public void cancel() {
                        if (closedUpdater.compareAndSet(outer, null, TerminalNotification.complete())) {
                            unparkWriterThread();
                        }
                    }
                });
            } catch (Throwable cause) {
                handleExceptionFromOnSubscribe(subscriber, cause);
            } finally {
                // Make the Subscriber available after this thread is done interacting with it to avoid concurrent
                // invocation.
                if (stateUpdater.compareAndSet(outer, State.CONNECTED, subscriber)) {
                    // We need to unpark the writer thread here because it is possible there was synchronous
                    // calls on the Subscription above, and those wakeups would be interpreted as spurious because the
                    // Subscriber was not made available to the writer thread yet.
                    unparkWriterThread();
                } else {
                    TerminalNotification currClosed = outer.closed;
                    assert currClosed != null;
                    currClosed.terminate(subscriber);
                }
            }
        }

        private void unparkWriterThread() {
            final Thread maybeWriterThread = outer.writerThread;
            if (maybeWriterThread != null) {
                LockSupport.unpark(maybeWriterThread);
            }
        }
    }

    private enum State {
        DISCONNECTED, CONNECTING, CONNECTED, TERMINATING, TERMINATED
    }
}
