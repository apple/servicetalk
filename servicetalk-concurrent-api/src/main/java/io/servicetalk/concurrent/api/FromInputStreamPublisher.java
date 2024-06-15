/*
 * Copyright © 2018-2021, 2023-2024 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.utils.internal.NumberUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static io.servicetalk.concurrent.internal.FlowControlUtils.addWithOverflowProtection;
import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverErrorFromSource;
import static io.servicetalk.concurrent.internal.SubscriberUtils.handleExceptionFromOnSubscribe;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;
import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;

/**
 * A {@link Publisher} created from an {@link InputStream} such that any data requested from the {@link Publisher} is
 * read from the {@link InputStream} until it terminates.
 *
 * Given that {@link InputStream} is a blocking API, requesting data from the {@link Publisher} can block on {@link
 * Subscription#request(long)} until there is sufficient data available. The implementation attempts to minimize
 * blocking, however by reading data faster than the writer is sending, blocking is inevitable.
 */
final class FromInputStreamPublisher<T> extends Publisher<T> implements PublisherSource<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(FromInputStreamPublisher.class);

    static {
        // FIXME: remove
        System.out.println("FromInputStreamPublisher with BufferAllocator");
    }

    // While sun.nio.ch.FileChannelImpl and java.io.InputStream.transferTo(...) use 8Kb chunks,
    // we use 16Kb-32B because 16Kb is:
    //  - the max data size of a TLS record;
    //  - the initial value of max HTTP/2 DATA frame size (SETTINGS_MAX_FRAME_SIZE);
    //  - default allocation quantum to use for the HTTP/2 remote flow controller (see H2ProtocolConfigBuilder);
    // and adjust by 32B to let either HTTP/1.1 chunked encoding or HTTP/2 DATA frame encoding to wrap data before the
    // write hits SslHandler. This helps utilize the full potential of the transport without fragmentation at TLS/HTTP/2
    // layers or introducing too many flushes (they are expensive!) for large payloads. Benchmarks confirmed that
    // subtraction of 32B significantly improves throughput and latency for TLS and has no effect on plaintext traffic.
    private static final int DEFAULT_READ_CHUNK_SIZE = 16 * 1024 - 32;
    @SuppressWarnings("rawtypes")
    private static final AtomicIntegerFieldUpdater<FromInputStreamPublisher> subscribedUpdater =
            AtomicIntegerFieldUpdater.newUpdater(FromInputStreamPublisher.class, "subscribed");

    private static final class TrimmingMapper implements BiIntFunction<byte[], byte[]> {

        static final BiIntFunction<byte[], byte[]> INSTANCE = new TrimmingMapper();

        private TrimmingMapper() {
            // Singleton
        }

        @Override
        public byte[] apply(final int length, final byte[] buffer) {
            if (buffer.length == length) {
                return buffer;
            } else {
                // This extra copy is necessary when we read the last chunk and total number of bytes read before EOF
                // is less than guesstimated buffer length.
                return Arrays.copyOf(buffer, length);
            }
        }
    }

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
    private final BiIntFunction<? super byte[], ? extends T> mapper;

    /**
     * A new instance.
     *
     * @param stream the {@link InputStream} to expose as a {@link Publisher}
     */
    static Publisher<byte[]> newFromInputStreamPublisher(final InputStream stream) {
        return newFromInputStreamPublisher(stream, DEFAULT_READ_CHUNK_SIZE);
    }

    FromInputStreamPublisher(final InputStream stream, BiIntFunction<? super byte[], ? extends T> mapper) {
        this(stream, DEFAULT_READ_CHUNK_SIZE, mapper);
    }

    /**
     * A new instance.
     *
     * @param stream the {@link InputStream} to expose as a {@link Publisher}
     * @param readChunkSize the maximum length of {@code byte[]} chunks which will be read from the {@link InputStream}
     * and emitted by the {@link Publisher}.
     */
    static Publisher<byte[]> newFromInputStreamPublisher(final InputStream stream, final int readChunkSize) {
        return new FromInputStreamPublisher<>(stream, readChunkSize, TrimmingMapper.INSTANCE);
    }

    FromInputStreamPublisher(final InputStream stream, final int readChunkSize,
                             BiIntFunction<? super byte[], ? extends T> mapper) {
        this.stream = requireNonNull(stream);
        this.readChunkSize = NumberUtils.ensurePositive(readChunkSize, "readChunkSize");
        this.mapper = requireNonNull(mapper);
    }

    @Override
    public void subscribe(final Subscriber<? super T> subscriber) {
        subscribeInternal(subscriber);
    }

    @Override
    protected void handleSubscribe(final Subscriber<? super T> subscriber) {
        if (subscribedUpdater.compareAndSet(this, 0, 1)) {
            try {
                subscriber.onSubscribe(new InputStreamPublisherSubscription(stream, subscriber, readChunkSize, mapper));
            } catch (Throwable t) {
                handleExceptionFromOnSubscribe(subscriber, t);
            }
        } else {
            deliverErrorFromSource(subscriber, new DuplicateSubscribeException(null, subscriber));
        }
    }

    private static final class InputStreamPublisherSubscription<T> implements Subscription {

        private static final int END_OF_FILE = -1;
        /**
         * Value assigned to {@link #requested} when terminal event was sent.
         */
        private static final int TERMINAL_SENT = -1;

        private final InputStream stream;
        private final Subscriber<? super T> subscriber;
        private final int readChunkSize;
        /**
         * Contains the outstanding demand or {@link #TERMINAL_SENT} indicating when {@link InputStream} and {@link
         * Subscription} are terminated.
         */
        private long requested;
        private int writeIdx;
        private boolean ignoreRequests;
        private final BiIntFunction<? super byte[], ? extends T> mapper;

        InputStreamPublisherSubscription(final InputStream stream, final Subscriber<? super T> subscriber,
                                         final int readChunkSize,
                                         final BiIntFunction<? super byte[], ? extends T> mapper) {
            this.stream = stream;
            this.subscriber = subscriber;
            this.readChunkSize = readChunkSize;
            this.mapper = mapper;
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

        private void readAndDeliver(final Subscriber<? super T> subscriber) {
            try {
                do {
                    // Initialize readByte with a negative value different from END_OF_FILE as an indicator that it was
                    // not initialized.
                    int readByte = Integer.MIN_VALUE;
                    // Can't fully trust available(), but it's a reasonable hint to mitigate blocking on read().
                    int available = stream.available();
                    if (available == 0) {
                        // This can be an indicator of EOF or a signal that no bytes are available to read without
                        // blocking. To avoid unnecessary allocation, we first probe for EOF:
                        readByte = stream.read();
                        if (readByte == END_OF_FILE) {
                            sendOnComplete(subscriber);
                            return;
                        }
                        // There is a chance a single read triggered availability of more bytes, let's check:
                        available = stream.available();
                        if (available == 0) {
                            // This InputStream either does not implement available() method at all, or does not honor
                            // the 0 == EOF contract, or does not prefetch data in larger chunks.
                            // In this case, we attempt to read based on the configured readChunkSize:
                            available = readChunkSize;
                        }
                    }
                    available = readAvailableAndEmit(available, readByte);
                    if (available == END_OF_FILE) {
                        sendOnComplete(subscriber);
                        return;
                    }
                } while (requested > 0);
            } catch (Throwable t) {
                sendOnError(subscriber, closeStreamOnError(t));
            }
        }

        private int readAvailableAndEmit(final int available, final int readByte) throws IOException {
            final byte[] buffer;
            if (readByte >= 0) {
                buffer = new byte[available < readChunkSize ? available + 1 : readChunkSize];
                buffer[writeIdx++] = (byte) readByte;
            } else {
                buffer = new byte[min(available, readChunkSize)];
            }
            final int remainingLength = fillBuffer(buffer, available);
            emitSingleBuffer(subscriber, buffer, remainingLength);
            return remainingLength;
        }

        // This method honors the estimated available bytes that can be read without blocking
        private int fillBuffer(final byte[] buffer, int available) throws IOException {
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

        private void emitSingleBuffer(final Subscriber<? super T> subscriber,
                                      final byte[] buffer, final int remainingLength) {
            if (writeIdx < 1) {
                assert remainingLength == END_OF_FILE :
                        "unexpected writeIdx == 0 while we still have some remaining data to read";
                return;
            }
            assert writeIdx <= buffer.length : "writeIdx can not be grater than buffer.length";
            final T item = mapper.apply(writeIdx, buffer);
            requested--;
            writeIdx = 0;
            subscriber.onNext(item);
        }

        private void sendOnComplete(final Subscriber<? super T> subscriber) {
            closeStream(subscriber);
            if (trySetTerminalSent()) {
                try {
                    subscriber.onComplete();
                } catch (Throwable t) {
                    LOGGER.info("Ignoring exception from onComplete of Subscriber {}.", subscriber, t);
                }
            }
        }

        private void sendOnError(final Subscriber<? super T> subscriber, final Throwable t) {
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

        private void closeStream(final Subscriber<? super T> subscriber) {
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
