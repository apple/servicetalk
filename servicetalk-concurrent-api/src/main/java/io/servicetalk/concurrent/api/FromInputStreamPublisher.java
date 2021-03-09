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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.internal.DuplicateSubscribeException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.FlowControlUtils.addWithOverflowProtection;
import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverErrorFromSource;
import static io.servicetalk.concurrent.internal.SubscriberUtils.handleExceptionFromOnSubscribe;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;
import static java.lang.Math.min;
import static java.lang.System.arraycopy;
import static java.util.Objects.requireNonNull;

/**
 * A {@link Publisher} created from an {@link InputStream} such that any data requested from the {@link Publisher} is
 * read from the {@link InputStream} until it terminates.
 *
 * Given that {@link InputStream} is a blocking API, requesting data from the {@link Publisher} can block on {@link
 * Subscription#request(long)} until there is sufficient data available. The implementation attempts to minimize
 * blocking, however by reading data faster than the writer is sending, blocking is inevitable.
 */
final class FromInputStreamPublisher extends Publisher<byte[]> implements PublisherSource<byte[]> {
    private static final Logger LOGGER = LoggerFactory.getLogger(FromInputStreamPublisher.class);
    private static final int DEFAULT_READ_CHUNK_SIZE = 65536;
    private static final AtomicIntegerFieldUpdater<FromInputStreamPublisher> subscribedUpdater =
            AtomicIntegerFieldUpdater.newUpdater(FromInputStreamPublisher.class, "subscribed");

    /**
     * A field together with {@link InputStreamPublisherSubscription#requested} that contains the application state:
     * <ul>
     *      <li>{@code 0} - waiting for subscriber</li>
     *      <li>{@code 1} - subscribed, waiting for items to be emitted or stream termination</li>
     *      <li>({@code requested == -1}) - see below - when {@link InputStream} and {@link Subscription} are terminated
     * </ul>
     * @see InputStreamPublisherSubscription#requested
     * @see InputStreamPublisherSubscription#TERMINAL_SENT
     */
    @SuppressWarnings("unused")
    private volatile int subscribed;

    private final InputStream stream;
    private final int readChunkSize;

    /**
     * A new instance.
     *
     * @param stream the {@link InputStream} to expose as a {@link Publisher}
     */
    FromInputStreamPublisher(final InputStream stream) {
        this(stream, DEFAULT_READ_CHUNK_SIZE);
    }

    /**
     * A new instance.
     *
     * @param stream the {@link InputStream} to expose as a {@link Publisher}
     * @param readChunkSize the maximum length of {@code byte[]} chunks which will be read from the {@link InputStream}
     * and emitted by the {@link Publisher}.
     */
    FromInputStreamPublisher(final InputStream stream, final int readChunkSize) {
        this.stream = requireNonNull(stream);
        if (readChunkSize <= 0) {
            throw new IllegalArgumentException("readChunkSize: " + readChunkSize + " (expected: >0)");
        }
    }

    @Override
    public void subscribe(final Subscriber<? super byte[]> subscriber) {
        subscribeInternal(subscriber);
    }

    @Override
    protected void handleSubscribe(final Subscriber<? super byte[]> subscriber) {
        if (subscribedUpdater.compareAndSet(this, 0, 1)) {
            try {
                subscriber.onSubscribe(new InputStreamPublisherSubscription(stream, subscriber, readChunkSize));
            } catch (Throwable t) {
                handleExceptionFromOnSubscribe(subscriber, t);
            }
        } else {
            deliverErrorFromSource(subscriber, new DuplicateSubscribeException(null, subscriber));
        }
    }

    private static final class InputStreamPublisherSubscription implements Subscription {

        private static final int END_OF_FILE = -1;
        /**
         * Value assigned to {@link #requested} when terminal event was sent.
         */
        private static final int TERMINAL_SENT = -1;

        private final InputStream stream;
        private final Subscriber<? super byte[]> subscriber;
        private final int readChunkSize;
        /**
         * Contains the outstanding demand or {@link #TERMINAL_SENT} indicating when {@link InputStream} and {@link
         * Subscription} are terminated.
         */
        private long requested;
        @Nullable
        private byte[] buffer;
        private int writeIdx;
        private boolean ignoreRequests;

        InputStreamPublisherSubscription(final InputStream stream, final Subscriber<? super byte[]> subscriber,
                                         final int readChunkSize) {
            this.stream = stream;
            this.subscriber = subscriber;
            this.readChunkSize = readChunkSize;
        }

        @Override
        public void request(final long n) {
            // No need to protect against concurrency between request and cancel
            // https://github.com/reactive-streams/reactive-streams-jvm#2.7
            if (requested == TERMINAL_SENT) {
                return;
            }
            if (!isRequestNValid(n)) {
                sendOnError(subscriber, closeStreamOnError(newExceptionForInvalidRequestN(n)));
                return;
            }
            requested = addWithOverflowProtection(requested, n);
            if (ignoreRequests) {
                return;
            }
            ignoreRequests = true;
            readAndDeliver(subscriber);
            if (requested != TERMINAL_SENT) {
                ignoreRequests = false;
            }
        }

        @Override
        public void cancel() {
            if (trySetTerminalSent()) {
                closeStream(subscriber);
            }
        }

        private void readAndDeliver(final Subscriber<? super byte[]> subscriber) {
            try {
                do {
                    // Can't fully trust available(), but it's a reasonable hint to mitigate blocking on read().
                    int available = stream.available();
                    if (available == 0) {
                        // Work around InputStreams that don't strictly honor the 0 == EOF contract.
                        available = buffer != null ? buffer.length : 1;
                    }
                    available = fillBufferAvoidingBlocking(available);
                    emitSingleBuffer(subscriber);
                    if (available == END_OF_FILE) {
                        sendOnComplete(subscriber);
                        return;
                    }
                } while (requested > 0);
            } catch (Throwable t) {
                sendOnError(subscriber, closeStreamOnError(t));
            }
        }

        // This method honors the estimated available bytes that can be read without blocking
        private int fillBufferAvoidingBlocking(int available) throws IOException {
            if (buffer == null) {
                buffer = new byte[min(available, readChunkSize)];
            }
            while (writeIdx != buffer.length && available > 0) {
                int len = min(buffer.length - writeIdx, available);
                int readActual = stream.read(buffer, writeIdx, len); // may block if len > available
                if (readActual == END_OF_FILE) {
                    return END_OF_FILE;
                }
                available -= readActual;
                writeIdx += readActual;
            }
            return available;
        }

        private void emitSingleBuffer(final Subscriber<? super byte[]> subscriber) {
            if (writeIdx < 1) {
                return;
            }
            assert buffer != null : "should have a buffer when writeIdx > 0";
            final byte[] b;
            if (writeIdx == buffer.length) {
                b = buffer;
                buffer = null;
            } else {
                b = new byte[writeIdx];
                arraycopy(buffer, 0, b, 0, writeIdx);
            }
            requested--;
            writeIdx = 0;
            subscriber.onNext(b);
        }

        private void sendOnComplete(final Subscriber<? super byte[]> subscriber) {
            closeStream(subscriber);
            if (trySetTerminalSent()) {
                try {
                    subscriber.onComplete();
                } catch (Throwable t) {
                    LOGGER.info("Ignoring exception from onComplete of Subscriber {}.", subscriber, t);
                }
            }
        }

        private <T extends Throwable> void sendOnError(final Subscriber<? super byte[]> subscriber, final T t) {
            if (trySetTerminalSent()) {
                try {
                    subscriber.onError(t);
                } catch (Throwable tt) {
                    LOGGER.info("Ignoring exception from onError of Subscriber {}.", subscriber, tt);
                }
            }
        }

        private Throwable closeStreamOnError(Throwable t) {
            try {
                stream.close();
            } catch (Throwable e) {
                // ignored, we are already closing with an error.
            }
            return t;
        }

        private void closeStream(final Subscriber<? super byte[]> subscriber) {
            try {
                stream.close();
            } catch (Throwable e) {
                if (trySetTerminalSent()) {
                    sendOnError(subscriber, e);
                }
            }
        }

        /**
         * @return {@code true} if terminal event wasn't sent and marks the state as sent, {@code false} otherwise
         */
        private boolean trySetTerminalSent() {
            if (requested == TERMINAL_SENT) {
                return false;
            }
            requested = TERMINAL_SENT;
            return true;
        }
    }
}
