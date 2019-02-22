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
import io.servicetalk.transport.api.PayloadWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import static io.servicetalk.concurrent.api.Publisher.error;
import static io.servicetalk.concurrent.internal.EmptySubscription.EMPTY_SUBSCRIPTION;
import static io.servicetalk.concurrent.internal.PlatformDependent.newUnboundedMpscQueue;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

/**
 * A {@link PayloadWriter} that can be {@link #connect() connected} to a sink such that any data written on the
 * {@link PayloadWriter} is eventually emitted to the connected {@link Publisher} {@link Subscriber}.
 * @param <T> The type of data for the {@link PayloadWriter}.
 */
public final class ConnectablePayloadWriter<T> implements PayloadWriter<T> {
    private static final AtomicLongFieldUpdater<ConnectablePayloadWriter> requestedUpdater =
            AtomicLongFieldUpdater.newUpdater(ConnectablePayloadWriter.class, "requested");
    private static final AtomicIntegerFieldUpdater<ConnectablePayloadWriter> closedUpdater =
            AtomicIntegerFieldUpdater.newUpdater(ConnectablePayloadWriter.class, "closed");
    private static final AtomicReferenceFieldUpdater<ConnectablePayloadWriter, Object> stateUpdater =
            newUpdater(ConnectablePayloadWriter.class, Object.class, "state");

    /**
     * Values used in {@link #state} below.
     */
    private static final Object DISCONNECTED = new Object(),
            CONNECTED = new Object(),
            EMITTING = new Object(),
            TERMINATED = new Object();

    /**
     * A field that assumes various states:
     * <ul>
     *      <li>{@link #DISCONNECTED} - waiting for {@link #connect()} to be called</li>
     *      <li>{@link #CONNECTED} - {@link Publisher} created, logically connected but awaiting {@link Subscriber}</li>
     *      <li>{@link #EMITTING} - emitting from {@link Subscription#request(long)} or {@link #flush()}</li>
     *      <li>{@link #TERMINATED} - the {@link Subscriber} has been terminated</li>
     *      <li>{@link Subscriber} - connected to the outer, waiting for items to be emitted or outer termination</li>
     * </ul>
     */
    @SuppressWarnings("unused")
    private volatile Object state = DISCONNECTED;
    @SuppressWarnings("unused")
    private volatile long requested;
    @SuppressWarnings("unused")
    private volatile int closed;

    /**
     * Stores objects of type {@link T} from {@link #write(Object)}, or a {@link TerminalNotification}.
     * <p>
     * MultiProducer queue because the Subscription may insert elements into the queue to force a close and terminate.
     */
    private final Queue<Object> dataQueue = newUnboundedMpscQueue(4);

    @Override
    public void write(final T t) {
        verifyOpen();
        dataQueue.add(t);
    }

    @Override
    public void flush() {
        verifyOpen();
        trySendData();
    }

    @Override
    public void close() {
        if (closedUpdater.compareAndSet(this, 0, 1)) {
            dataQueue.add(TerminalNotification.complete());
            trySendData();
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
        return stateUpdater.compareAndSet(this, DISCONNECTED, CONNECTED) ? new ConnectedPublisher<>(this) :
                error(new IllegalStateException("Stream is not ready for connect."));
    }

    private void trySendData() {
        final Object currentState = state;
        if (currentState instanceof Subscriber) {
            @SuppressWarnings("unchecked")
            final Subscriber<? super T> s = (Subscriber<? super T>) currentState;
            trySendData(s);
        }
    }

    private void trySendData(final Subscriber<? super T> s) {
        do {
            if (!stateUpdater.compareAndSet(this, s, EMITTING)) {
                break;
            }
            // Since we have acquired the lock, we reserve all the requested demand in this loop and decrement after
            // we have delivered as much data as possible.
            long requestedTotal = requestedUpdater.getAndSet(this, 0);
            long drainedCount = 0;
            try {
                while (drainedCount < requestedTotal) {
                    final Object next = dataQueue.poll();
                    if (next == null) {
                        break;
                    } else if (next instanceof TerminalNotification) {
                        state = TERMINATED;
                        ((TerminalNotification) next).terminate(s);
                        return;
                    } else {
                        ++drainedCount;
                        try {
                            @SuppressWarnings("unchecked")
                            final T nextT = (T) next;
                            s.onNext(nextT);
                        } catch (final Throwable t) {
                            closed = 1;
                            state = TERMINATED;
                            dataQueue.clear();
                            s.onError(t);
                            return;
                        }
                    }
                }
                // If requestN is exhausted, we should still check to see if there is a terminal event pending on the
                // the queue and deliver it.
                final Object next = dataQueue.peek();
                if (next instanceof TerminalNotification) {
                    dataQueue.poll();
                    state = TERMINATED;
                    ((TerminalNotification) next).terminate(s);
                }
            } finally {
                // Restore the amount we were unable to deliver from requested.
                if (drainedCount != requestedTotal) {
                    requestedUpdater.accumulateAndGet(this, requestedTotal - drainedCount,
                            FlowControlUtil::addWithOverflowProtection);
                }

                // Do a CaS because we may have set the state above, or someone else may have terminated the state.
                stateUpdater.compareAndSet(this, EMITTING, s);
            }
        } while (requested != 0 && !dataQueue.isEmpty());
    }

    private void verifyOpen() {
        if (closed != 0) {
            throw new IllegalStateException("Already closed.");
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
            if (!stateUpdater.compareAndSet(outer, CONNECTED, subscriber)) {
                subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
                subscriber.onError(new DuplicateSubscribeException(outer.state, subscriber));
                return;
            }

            subscriber.onSubscribe(new Subscription() {
                @Override
                public void request(final long n) {
                    if (isRequestNValid(n)) {
                        requestedUpdater.accumulateAndGet(outer, n, FlowControlUtil::addWithOverflowProtection);
                    } else if (closedUpdater.compareAndSet(outer, 0, 1)) {
                        outer.dataQueue.add(TerminalNotification.error(newExceptionForInvalidRequestN(n)));
                    } else {
                        LOGGER.warn("invalid request({}), but already closed.", n, newExceptionForInvalidRequestN(n));
                    }
                    outer.trySendData(subscriber);
                }

                @Override
                public void cancel() {
                    if (closedUpdater.compareAndSet(outer, 0, 1)) {
                        outer.dataQueue.add(TerminalNotification.complete());
                        outer.trySendData(subscriber);
                    }
                }
            });
        }
    }
}
