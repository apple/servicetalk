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
import io.servicetalk.concurrent.internal.TerminalNotification;
import io.servicetalk.oio.api.PayloadWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.LockSupport;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.failed;
import static io.servicetalk.concurrent.internal.FlowControlUtils.addWithOverflowProtection;
import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverCompleteFromSource;
import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverErrorFromSource;
import static io.servicetalk.concurrent.internal.SubscriberUtils.handleExceptionFromOnSubscribe;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

/**
 * A {@link PayloadWriter} that can be {@link #connect() connected} to a sink such that any data written on the
 * {@link PayloadWriter} is eventually emitted to the connected {@link Publisher} {@link Subscriber}.
 *
 * @param <T> The type of data for the {@link PayloadWriter}.
 */
public final class ConnectablePayloadWriter<T> implements PayloadWriter<T> {
    private static final long REQUESTN_ABOUT_TO_PARK = Long.MIN_VALUE;
    private static final long REQUESTN_TERMINATED = REQUESTN_ABOUT_TO_PARK + 1;
    @SuppressWarnings("rawtypes")
    private static final AtomicLongFieldUpdater<ConnectablePayloadWriter> requestedUpdater =
            AtomicLongFieldUpdater.newUpdater(ConnectablePayloadWriter.class, "requested");
    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<ConnectablePayloadWriter, TerminalNotification> closedUpdater =
            newUpdater(ConnectablePayloadWriter.class, TerminalNotification.class, "closed");
    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<ConnectablePayloadWriter, Object> stateUpdater =
            newUpdater(ConnectablePayloadWriter.class, Object.class, "state");

    /**
     * A field that assumes various states:
     * <ul>
     * <li>{@link State#DISCONNECTED} - waiting for {@link #connect()} to be called</li>
     * <li>{@link State#CONNECTING} - {@link #connect()} has been called, but no {@link Subscriber} yet.</li>
     * <li>{@link State#WAITING_FOR_CONNECTED} - the writer thread is waiting for the {@link Subscriber}.</li>
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
    /**
     * The writer thread that maybe blocked on {@link LockSupport#park()}.
     * This does not need to be volatile because visibility is provided by modifications to {@link #state} or
     * {@link #requested}.
     */
    @Nullable
    private Thread writerThread;

    @Override
    public void write(final T t) throws IOException {
        for (;;) {
            final long requested = this.requested;
            if (requested > 0) {
                if (requestedUpdater.compareAndSet(this, requested, requested - 1)) {
                    break;
                }
            } else if (requested >= 0) {
                waitForRequestNDemand();
                break;
            } else {
                // In the event that we have been terminated don't bother trying to wait. More importantly we don't
                // want to reset the requested state to REQUESTN_ABOUT_TO_PARK because it may have been set to
                // REQUESTN_TERMINATED.
                processClosed();
            }
        }

        final Subscriber<? super T> s = waitForSubscriber();
        try {
            s.onNext(t);
        } catch (final Throwable cause) {
            closed = TerminalNotification.error(cause);
            requested = REQUESTN_TERMINATED;
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
        close0(null);
    }

    @Override
    public void close(final Throwable cause) throws IOException {
        close0(requireNonNull(cause));
    }

    private void close0(@Nullable Throwable cause) {
        // Set closed before state, because the Subscriber thread depends upon this ordering in the event it needs to
        // terminate the Subscriber.
        if (closedUpdater.compareAndSet(this, null, TerminalNotification.complete())) {
            // We need to terminate requested or else we may block indefinitely on subsequent calls to write in
            // waitForRequestNDemand.
            requested = REQUESTN_TERMINATED;
            for (;;) {
                final Object currState = state;
                if (currState instanceof Subscriber) {
                    if (stateUpdater.compareAndSet(this, currState, State.TERMINATED)) {
                        @SuppressWarnings("unchecked")
                        final Subscriber<T> subscriber = (Subscriber<T>) currState;
                        if (cause == null) {
                            subscriber.onComplete();
                        } else {
                            subscriber.onError(cause);
                        }
                        break;
                    }
                } else if (currState == State.TERMINATED ||
                        stateUpdater.compareAndSet(this, currState, State.TERMINATING)) {
                    assert currState != State.WAITING_FOR_CONNECTED;
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
                failed(new IllegalStateException("Stream state " + state + " is not valid for connect."));
    }

    private void verifyOpen() throws IOException {
        TerminalNotification currClosed = closed;
        if (currClosed != null) {
            processClosed(currClosed);
        }
    }

    private void processClosed() throws IOException {
        TerminalNotification currClosed = closed;
        assert currClosed != null;
        processClosed(currClosed);
    }

    private void processClosed(TerminalNotification currClosed) throws IOException {
        Object currState = stateUpdater.getAndSet(this, State.TERMINATED);
        if (currState instanceof Subscriber) {
            currClosed.terminate((Subscriber<?>) currState);
        }
        throw new IOException("Already closed " + currClosed);
    }

    private void waitForRequestNDemand() throws IOException {
        writerThread = Thread.currentThread();
        final long oldRequested = requestedUpdater.getAndSet(this, REQUESTN_ABOUT_TO_PARK);
        if (oldRequested == 0) {
            for (;;) {
                LockSupport.park();
                final long requested = this.requested;
                if (requested > 0) {
                    if (requestedUpdater.compareAndSet(this, requested, requested - 1)) {
                        writerThread = null;
                        break;
                    }
                } else if (requested != REQUESTN_ABOUT_TO_PARK) {
                    writerThread = null;
                    processClosed();
                }
            }
        } else if (oldRequested > 0) {
            writerThread = null;
            waitForRequestNDemandAvoidPark(oldRequested);
        } else {
            writerThread = null;
            processClosed();
        }
    }

    private void waitForRequestNDemandAvoidPark(final long oldRequested) throws IOException {
        for (;;) {
            final long requested = this.requested;
            if (requested == REQUESTN_ABOUT_TO_PARK) {
                if (requestedUpdater.compareAndSet(this, REQUESTN_ABOUT_TO_PARK, oldRequested - 1)) {
                    break;
                }
            } else if (requested < 0) {
                processClosed();
            } else if (requestedUpdater.compareAndSet(this, requested,
                    addWithOverflowProtection(oldRequested - 1, requested))) {
                break;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Subscriber<? super T> waitForSubscriber() throws IOException {
        final Object currState = state;
        return currState instanceof Subscriber ? (Subscriber<? super T>) currState : waitForSubscriberSlowPath();
    }

    @SuppressWarnings("unchecked")
    private Subscriber<? super T> waitForSubscriberSlowPath() throws IOException {
        writerThread = Thread.currentThread();
        for (;;) {
            final Object currState = state;
            if (currState instanceof Subscriber) {
                writerThread = null;
                return (Subscriber<? super T>) currState;
            } else if (currState == State.TERMINATED || currState == State.TERMINATING) {
                // If the subscriber is not handed off the the writer thread then the writer thread is not responsible
                // for delivering the terminal event to the Subscriber (because it never has a reference to it), and
                // the thread processing the subscribe(..) call will terminate the Subscriber instead of handing it off.
                writerThread = null;
                throw new IOException("Already closed " + closed);
            } else if (stateUpdater.compareAndSet(this, currState, State.WAITING_FOR_CONNECTED)) {
                LockSupport.park();
            }
        }
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
                    deliverCompleteFromSource(subscriber);
                } else {
                    deliverErrorFromSource(subscriber, new DuplicateSubscribeException(outer.state, subscriber));
                }
                return;
            }

            try {
                subscriber.onSubscribe(new Subscription() {
                    @Override
                    public void request(final long n) {
                        if (isRequestNValid(n)) {
                            for (;;) {
                                final long requested = outer.requested;
                                if (requested >= 0) {
                                    if (requestedUpdater.compareAndSet(outer, requested,
                                            addWithOverflowProtection(requested, n))) {
                                        break;
                                    }
                                } else if (requested == REQUESTN_ABOUT_TO_PARK) {
                                    // It is possible the writer thread did a GaS to make REQUESTN_ABOUT_TO_PARK visible
                                    // but if the Get returned some positive demand it will write something and
                                    // atomically decrement the demand. So we should do a CaS here to be sure we don't
                                    // overwrite this value and lose demand.
                                    if (requestedUpdater.compareAndSet(outer, REQUESTN_ABOUT_TO_PARK, n)) {
                                        tryWakeupWriterThread();
                                        break;
                                    }
                                } else {
                                    break;
                                }
                            }
                        } else if (closedUpdater.compareAndSet(outer, null,
                                TerminalNotification.error(newExceptionForInvalidRequestN(n)))) {
                            terminateRequestN();
                        } else {
                            LOGGER.warn("invalid request({}), but already closed.", n);
                        }
                    }

                    @Override
                    public void cancel() {
                        if (closedUpdater.compareAndSet(outer, null, TerminalNotification.complete())) {
                            terminateRequestN();
                        }
                    }
                });
            } catch (Throwable cause) {
                handleExceptionFromOnSubscribe(subscriber, cause);
            } finally {
                // Make the Subscriber available after this thread is done interacting with it to avoid concurrent
                // invocation.
                for (;;) {
                    final Object currState = outer.state;
                    if (currState == State.CONNECTED) {
                        if (stateUpdater.compareAndSet(outer, State.CONNECTED, subscriber)) {
                            break;
                        }
                    } else if (currState == State.WAITING_FOR_CONNECTED) {
                        if (stateUpdater.compareAndSet(outer, State.WAITING_FOR_CONNECTED, subscriber)) {
                            final Thread writerThread = outer.writerThread;
                            assert writerThread != null;
                            LockSupport.unpark(writerThread);
                            break;
                        }
                    } else {
                        TerminalNotification currClosed = outer.closed;
                        assert currClosed != null;
                        currClosed.terminate(subscriber);
                        break;
                    }
                }
            }
        }

        private void terminateRequestN() {
            outer.requested = REQUESTN_TERMINATED;
            tryWakeupWriterThread();
        }

        private void tryWakeupWriterThread() {
            final Thread writerThread = outer.writerThread;
            if (writerThread != null) {
                LockSupport.unpark(writerThread);
            }
        }
    }

    private enum State {
        DISCONNECTED, CONNECTING, WAITING_FOR_CONNECTED, CONNECTED, TERMINATING, TERMINATED
    }
}
