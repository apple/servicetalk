/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.IntBinaryOperator;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.error;
import static io.servicetalk.concurrent.internal.EmptySubscription.EMPTY_SUBSCRIPTION;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;
import static java.lang.System.arraycopy;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

/**
 * An {@link OutputStream} that can be connected to a sink such that any data written on the {@link OutputStream} is
 * eventually emitted to the connected {@link Publisher} {@link Subscriber}.
 */
public final class ConnectableOutputStream extends OutputStream {

    private static final AtomicReferenceFieldUpdater<ConnectableOutputStream, byte[]> pendingOnNextUpdater =
            newUpdater(ConnectableOutputStream.class, byte[].class, "pendingOnNext");
    private static final AtomicLongFieldUpdater<ConnectableOutputStream> requestedUpdater =
            AtomicLongFieldUpdater.newUpdater(ConnectableOutputStream.class, "requested");
    private static final AtomicReferenceFieldUpdater<ConnectableOutputStream, Object> stateUpdater =
            newUpdater(ConnectableOutputStream.class, Object.class, "state");

    /**
     * Values used in {@link #state} below.
     */
    private static final Object DISCONNECTED = new Object(),
                                CONNECTED = new Object(),
                                CLOSE_ON_SUB = new Object(),
                                CLOSED = new Object(),
                                EMITTING = new Object(),
                                CANCEL_AFTER_EMIT = new Object(),
                                TERMINAL_SENT = new Object();

    /**
     * A field that assumes various states:
     * <ul>
     *      <li>{@link #DISCONNECTED} - waiting for {@link #connect()} to be called</li>
     *      <li>{@link #CONNECTED} - {@link Publisher} created, logically connected but awaiting {@link Subscriber}</li>
     *      <li>{@link #CLOSE_ON_SUB} - stream closed, terminates on subscribe</li>
     *      <li>{@link #CLOSED} - stream closed, draining buffers, delivering terminal event to {@link Subscriber}</li>
     *      <li>{@link #EMITTING} - emitting from {@link Subscription#request(long)} or {@link #flush()}</li>
     *      <li>{@link #CANCEL_AFTER_EMIT} - {@link Subscription#cancel()} to be executed after emitting</li>
     *      <li>{@link #TERMINAL_SENT} - {@link Subscription} in terminal state, either completed or with error</li>
     *      <li>{@link Subscriber} - connected to the stream, waiting for items to be emitted or stream termination</li>
     *      <li>{@link Throwable} - when {@link Subscription#request(long)} invalid</li>
     * </ul>
     */
    @SuppressWarnings("unused")
    @Nullable
    private volatile Object state = DISCONNECTED;

    @SuppressWarnings("unused")
    private volatile long requested;

    // Data buffer till flush() is called.
    @Nullable
    private byte[] unflushed;

    // All data written before the last flush() call and not yet sent to the sink.
    @SuppressWarnings("unused")
    @Nullable
    private volatile byte[] pendingOnNext;
    private int writeIndex;

    /**
     * Error thrown by a {@link Subscriber} which leads to termination of the stream but is not visible otherwise.
     * Visibility is provided by volatile store with {@link #stateUpdater}.
     */
    @Nullable
    private Throwable prematureSubscriberTermination;

    private final IntBinaryOperator nextSizeSupplier;
    /**
     * Determines whether {@link Subscription#cancel()} should close the {@link OutputStream} or reset {@link #state} to
     * {@link #CONNECTED} and allow re-subscribe.
     */
    private final boolean closeOnCancel;

    /**
     * New instance with default intermediate buffer sizing and default close on cancel behavior.
     * <p>
     * Use {@link #ConnectableOutputStream(IntBinaryOperator, boolean)} to control internal buffer size and cancel
     * behavior.
     */
    public ConnectableOutputStream() {
        this((current, minSize) -> minSize, true);
    }

    /**
     * New instance with default intermediate buffer sizing.
     *
     * @param closeOnCancel if {@code TRUE} {@link Subscription#cancel()} closes the {@link OutputStream} else it allows
     * the {@link Publisher} to be re-subscribed
     */
    public ConnectableOutputStream(final boolean closeOnCancel) {
        this((current, minSize) -> minSize, closeOnCancel);
    }

    /**
     * New instance with default close on cancel behavior.
     *
     * @param nextSizeSupplier An {@link IntBinaryOperator} that controls the size of the intermediate buffer. This
     * {@link IntBinaryOperator} will be invoked every time a new intermediate buffer is to be created. First argument
     * is the current size of the buffer and second argument is the minimum size required for the next buffer. Second
     * argument is guaranteed to be greater than the first. Returned value should be greater than or equal to the second
     * argument to this {@link IntBinaryOperator}
     */
    public ConnectableOutputStream(final IntBinaryOperator nextSizeSupplier) {
        this(nextSizeSupplier, true);
    }

    /**
     * New instance.
     *
     * @param nextSizeSupplier An {@link IntBinaryOperator} that controls the size of the intermediate buffer. This
     * {@link IntBinaryOperator} will be invoked every time a new intermediate buffer is to be created. First argument
     * is the current size of the buffer and second argument is the minimum size required for the next buffer. Second
     * argument is guaranteed to be greater than the first. Returned value should be greater than or equal to the second
     * argument to this {@link IntBinaryOperator}
     * @param closeOnCancel if {@code TRUE} {@link Subscription#cancel()} closes the {@link OutputStream} else it allows
     * the {@link Publisher} to be re-subscribed
     */
    public ConnectableOutputStream(final IntBinaryOperator nextSizeSupplier, final boolean closeOnCancel) {
        this.nextSizeSupplier = requireNonNull(nextSizeSupplier);
        this.closeOnCancel = closeOnCancel;
    }

    @Override
    public void write(final int b) throws IOException {
        verifyOpen();
        final byte[] dst = expandUnflushedIfNecessary(1);
        dst[writeIndex++] = (byte) b;
    }

    @Override
    public void write(final byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        verifyOpen();
        if (b == null) {
            throw new NullPointerException("b");
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException("Unexpected offset " + off + " (expected > 0) or length " + len
                    + " (expected >= 0 and should fit in the source array). Source array length " + b.length);
        } else if (len == 0) {
            return;
        }
        arraycopy(b, off, expandUnflushedIfNecessary(len), writeIndex, len);
        writeIndex += len;
    }

    private byte[] expandUnflushedIfNecessary(final int extraCapacity) throws IOException {
        final int minCap = writeIndex + extraCapacity;
        // check for int-overflow without casting to long for addition
        // http://docs.oracle.com/javase/specs/jls/se8/html/jls-15.html#jls-15.18.2
        if (minCap < 0) {
            throw new IOException("Buffer overflow, stream not being flushed or consumed?");
        }
        if (unflushed == null || minCap > unflushed.length) {
            final int newSize = nextSizeSupplier.applyAsInt(unflushed == null ? 0 : unflushed.length, minCap);
            if (newSize < minCap) {
                throw new IOException("Supplied Buffer size insufficient");
            }
            final byte[] newData = new byte[newSize];
            if (unflushed != null) {
                arraycopy(unflushed, 0, newData, 0, writeIndex);
            }
            unflushed = newData;
        }
        return unflushed;
    }

    @Override
    public void flush() throws IOException {
        if (writeIndex == 0) {
            // Nothing written, ignore flush, ensures early return before checking for open for idempotent flush+close
            return;
        }
        verifyOpen();
        assert unflushed != null;
        byte[] toFlush;
        for (;;) {
            final byte[] currentOnNext = pendingOnNext;
            if (currentOnNext == null) {
                // No previously flushed data, either this is the first flush() or the previously flushed data is
                // already sent.
                if (writeIndex == unflushed.length) {
                    if (pendingOnNextUpdater.compareAndSet(this, null, unflushed)) {
                        // Skip copy when unflushed buffer is completely full and can be sent as-is, the downside is
                        // potentially more calls to nextSizeSupplier, trading CPU for allocation/GC.
                        toFlush = unflushed;
                        unflushed = null;
                        writeIndex = 0;
                        break;
                    }
                    continue; // CAS(currentOnNext, toFlush) below will fail, skip ahead to reload pendingOnNext
                }
                toFlush = new byte[writeIndex];
                arraycopy(unflushed, 0, toFlush, 0, writeIndex);
            } else {
                // Last flushed data has not yet been sent, so accumulate into a single buffer.
                toFlush = new byte[currentOnNext.length + writeIndex];
                arraycopy(currentOnNext, 0, toFlush, 0, currentOnNext.length);
                arraycopy(unflushed, 0, toFlush, currentOnNext.length, writeIndex);
            }
            if (pendingOnNextUpdater.compareAndSet(this, currentOnNext, toFlush)) {
                writeIndex = 0; // Keep scratch buffer and only reset writeIndex to avoid new allocation
                break;
            }
        }

        final Object maybeSubscriber = state;
        if (maybeSubscriber instanceof Subscriber) {
            @SuppressWarnings("unchecked")
            final Subscriber<? super byte[]> s = (Subscriber<? super byte[]>) maybeSubscriber;
            trySendData(s, requested, toFlush);
            // Check if subscriber failed to process all data and rethrow, re-read state volatile to guarantee
            // visibility for prematureSubscriberTermination
            checkPrematureTermination(state);
        }
    }

    @Override
    public void close() throws IOException {
        flush();
        for (;;) {
            final Object currentState = state;
            if (currentState == TERMINAL_SENT || currentState == CLOSED || currentState == CLOSE_ON_SUB ||
                    currentState == CANCEL_AFTER_EMIT || currentState instanceof Throwable) {
                // Subscriber already terminated or terminating
                return;
            }
            if (currentState == EMITTING && stateUpdater.compareAndSet(this, EMITTING, CLOSED)) {
                // emitting thread will take care of sending onComplete
                return;
            }
            if (currentState == CONNECTED && stateUpdater.compareAndSet(this, CONNECTED, CLOSE_ON_SUB)) {
                // no subscriber, defer close until subscribe()
                return;
            }
            if (currentState == DISCONNECTED && stateUpdater.compareAndSet(this, DISCONNECTED, CLOSED)) {
                // not connected, close immediately
                return;
            }
            if (currentState instanceof Subscriber && stateUpdater.compareAndSet(this, currentState, CLOSED)) {
                @SuppressWarnings("unchecked")
                final Subscriber<? super byte[]> s = (Subscriber<? super byte[]>) currentState;
                trySendDataOrCompleteAfterClose(s);
                return;
            }
        }
    }

    /**
     * Connects this {@link OutputStream} to the returned {@link Publisher} such that any data written to this
     * {@link OutputStream} is eventually delivered to a {@link Subscriber} of the returned {@link Publisher}.
     *
     * @return {@link Publisher} that will emit all data written to this {@link OutputStream} to its {@link Subscriber}.
     * Only a single active {@link Subscriber} is allowed for this {@link Publisher}.
     */
    public Publisher<byte[]> connect() {
        for (;;) {
            if (stateUpdater.compareAndSet(this, DISCONNECTED, CONNECTED)) {
                return new ConnectedPublisher(this);
            }
            final Object current = state;
            if (current == CLOSE_ON_SUB || current == CLOSED) {
                return error(new IllegalStateException("Stream already closed."));
            }
            if (current == CONNECTED || current == EMITTING || current == CANCEL_AFTER_EMIT ||
                    current == TERMINAL_SENT ||
                    current instanceof Subscriber ||
                    current instanceof Throwable) {
                return error(new IllegalStateException("Stream already connected."));
            }
        }
    }

    private void trySendData(final Subscriber<? super byte[]> s, long requestN, @Nullable byte[] next) {
        for (;;) {
            if (requestN <= 0 || next == null || !stateUpdater.compareAndSet(this, s, EMITTING)) {
                return;
            }
            try {
                // We need to re-read the requested value from inside the lock to ensure no other thread has consumed
                // all outstanding demand before acquiring the lock. We optimistically assume our pendingOnNext value
                // has not been consumed, CAS below ensures that assumption.
                if (requested <= 0) {
                    continue; // do another iteration, including re-reading requested into requestN from finally
                }
                if (pendingOnNextUpdater.compareAndSet(this, next, null)) {
                    // Invariants:
                    // -- requested was last seen as positive
                    // -- We are exclusively emitting so requested can not decrement between last check and now
                    //
                    // Based on the above, it is guaranteed that the below statement will keep requested
                    // positive.
                    final long reqN = requestedUpdater.getAndDecrement(this);
                    assert reqN > 0 : "Insufficient demand";
                    try {
                        s.onNext(next);
                    } catch (final Throwable t) {
                        prematureSubscriberTermination = t;
                        final Object maybeThrowable = stateUpdater.getAndSet(this, TERMINAL_SENT);
                        if (maybeThrowable instanceof Throwable) {
                            t.addSuppressed((Throwable) maybeThrowable);
                        }
                        s.onError(t);
                    }
                }
            } finally {
                for (;;) {
                    final Object current = state;
                    if (current == TERMINAL_SENT ||
                            (current == EMITTING && stateUpdater.compareAndSet(this, EMITTING, s))) {
                        // Done emitting or failed to deliver an event
                        break;
                    }
                    if (current == CANCEL_AFTER_EMIT && stateUpdater.compareAndSet(this, CANCEL_AFTER_EMIT,
                            closeOnCancel ? TERMINAL_SENT : CONNECTED)) {
                        // cancel() concurrent while emitting, either reset or abort the connection
                        break;
                    }
                    if (current == CLOSED) {
                        // close() called while emitting, flush() may have accumulated data while we ran out of demand
                        trySendDataOrCompleteAfterClose(s);
                        break;
                    }
                    if (current instanceof Throwable && stateUpdater.compareAndSet(this, current, TERMINAL_SENT)) {
                        s.onError((Throwable) current);
                        break;
                    }
                }
                // When `requestN` was positive coming into the method and the current `requested` value drops to 0, we
                // could enter a livelock if we don't refresh `requestN` before looping again. So when `continuing` from
                // the precondition in the try-block above we need to make sure we refresh `next` and `requestN` from
                // inside the finally-block.
                next = pendingOnNext;
                requestN = requested;
            }
        }
    }

    private void trySendDataOrCompleteAfterClose(final Subscriber<? super byte[]> subscriber) {
        final byte[] next = pendingOnNext;
        if (next != null) {
            if (requested > 0 && stateUpdater.compareAndSet(this, CLOSED, TERMINAL_SENT) &&
                    pendingOnNextUpdater.compareAndSet(this, next, null)) {
                final long reqN = requestedUpdater.getAndDecrement(this);
                assert reqN > 0 : "Insufficient demand for final event, concurrent with trySendData()?";
                try {
                    subscriber.onNext(next);
                } catch (final Throwable t) {
                    subscriber.onError(t);
                    return;
                }
                subscriber.onComplete();
            }
        } else if (stateUpdater.compareAndSet(this, CLOSED, TERMINAL_SENT)) {
            subscriber.onComplete();
        }
    }

    private void verifyOpen() throws IOException {
        final Object currentState = state;
        checkPrematureTermination(currentState);
        if (currentState == CLOSE_ON_SUB || currentState == CLOSED || currentState == TERMINAL_SENT) {
            throw new IllegalStateException("Stream is closed.");
        }
    }

    private void checkPrematureTermination(@Nullable final Object currentState) throws IOException {
        if (currentState instanceof Throwable) {
            throw new IOException("Premature termination of stream.", (Throwable) currentState);
        }
        // If the terminal state is set by trySendData, we may have a premature exception to process. The
        // prematureSubscriberTermination variable will be visible in this case due to write->read on state.
        if (prematureSubscriberTermination != null) {
            throw new IOException("Premature termination of stream.", prematureSubscriberTermination);
        }
    }

    private static final class ConnectedPublisher extends SubscribablePublisher<byte[]> {

        private final ConnectableOutputStream stream;

        ConnectedPublisher(final ConnectableOutputStream stream) {
            this.stream = stream;
        }

        @Override
        protected void handleSubscribe(final Subscriber<? super byte[]> subscriber) {
            for (;;) {
                final Object currentState = stream.state;
                if ((currentState == CONNECTED && stateUpdater.compareAndSet(stream, CONNECTED, subscriber)) ||
                    (currentState == CLOSE_ON_SUB && stateUpdater.compareAndSet(stream, CLOSE_ON_SUB, CLOSED))) {
                    break;
                }
                if (currentState == CLOSED || currentState == EMITTING || currentState == CANCEL_AFTER_EMIT ||
                        currentState == TERMINAL_SENT || currentState instanceof Subscriber ||
                        currentState instanceof Throwable) {
                    subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
                    subscriber.onError(new DuplicateSubscribeException(currentState, subscriber));
                    return;
                }
            }

            subscriber.onSubscribe(new Subscription() {
                @Override
                public void request(final long n) {
                    if (isRequestNValid(n)) {
                        final long reqN = requestedUpdater.accumulateAndGet(stream, n,
                                FlowControlUtil::addWithOverflowProtection);
                        stream.trySendData(subscriber, reqN, stream.pendingOnNext);
                        if (stream.state == CLOSED) {
                            // drain the buffers and emit terminal events when close() happens before subscribe()
                            stream.trySendDataOrCompleteAfterClose(subscriber);
                        }
                    } else {
                        final IllegalArgumentException iae = newExceptionForInvalidRequestN(n);
                        final Object oldVal = stateUpdater.getAndSet(stream, iae);
                        if (oldVal == subscriber) {
                            // No in progress emissions, so terminate.
                            cancel();
                            subscriber.onError(iae);
                        }
                    }
                }

                @Override
                public void cancel() {
                    for (;;) {
                        final Object currentState = stream.state;
                        if (currentState == subscriber && stateUpdater.compareAndSet(stream, subscriber,
                                    stream.closeOnCancel ? TERMINAL_SENT : CONNECTED)) {
                            return;
                        }
                        if (currentState == EMITTING && // trySendData() called from flush()/close()
                                stateUpdater.compareAndSet(stream, EMITTING, CANCEL_AFTER_EMIT)) {
                            return;
                        }
                        if (currentState == CLOSED || currentState == TERMINAL_SENT ||
                                currentState instanceof Throwable) {
                            return;
                        }
                    }
                }
            });
        }
    }
}
