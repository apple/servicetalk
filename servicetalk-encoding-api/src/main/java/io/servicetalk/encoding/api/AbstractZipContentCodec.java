/*
 * Copyright © 2020, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.encoding.api;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.ConcurrentSubscription;
import io.servicetalk.concurrent.internal.FlowControlUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicLongFieldUpdater.newUpdater;

abstract class AbstractZipContentCodec extends AbstractContentCodec {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractZipContentCodec.class);

    protected final int chunkSize;
    private final int maxPayloadSize;

    AbstractZipContentCodec(final CharSequence name, final int chunkSize, final int maxPayloadSize) {
        super(name);
        this.chunkSize = chunkSize;
        this.maxPayloadSize = maxPayloadSize;
    }

    abstract boolean supportsChecksum();

    abstract Inflater newRawInflater();

    abstract DeflaterOutputStream newDeflaterOutputStream(OutputStream out) throws IOException;

    abstract InflaterInputStream newInflaterInputStream(InputStream in) throws IOException;

    @Override
    public final Buffer encode(final Buffer src, final int offset, final int length, final BufferAllocator allocator) {
        if (offset < 0) {
            throw new IllegalArgumentException("Invalid offset: " + offset + " (expected >= 0)");
        }

        final Buffer dst = allocator.newBuffer(chunkSize);
        DeflaterOutputStream output = null;
        try {
            src.readerIndex(src.readerIndex() + offset);
            output = newDeflaterOutputStream(Buffer.asOutputStream(dst));

            if (src.hasArray()) {
                output.write(src.array(), src.arrayOffset() + src.readerIndex(), length);
                src.readerIndex(src.readerIndex() + length);
            } else {
                while (src.readableBytes() > 0) {
                    byte[] onHeap = new byte[min(src.readableBytes(), min(chunkSize, length))];
                    src.readBytes(onHeap);
                    output.write(onHeap);
                }
            }

            output.finish();
        } catch (Exception e) {
            LOGGER.error("Error while encoding with {}", name(), e);
            throw new RuntimeException(e);
        } finally {
            closeIgnoreException(output);
        }

        return dst;
    }

    @Override
    public final Publisher<Buffer> encode(final Publisher<Buffer> from,
                                          final BufferAllocator allocator) {
        return from.liftSync(subscriber -> new EncodingOperator(subscriber, allocator, this));
    }

    @Override
    public final Buffer decode(final Buffer src, final int offset, final int length, final BufferAllocator allocator) {
        if (offset < 0) {
            throw new IllegalArgumentException("Invalid offset: " + offset + " (expected >= 0)");
        }

        src.readerIndex(src.readerIndex() + offset);
        final Buffer dst = allocator.newBuffer(chunkSize, maxPayloadSize);
        InflaterInputStream input = null;
        try {
            input = newInflaterInputStream(new BufferBoundedInputStream(src, length));

            int read = dst.setBytesUntilEndStream(0, input, chunkSize);
            dst.writerIndex(read);
        } catch (Exception e) {
            LOGGER.error("Error while decoding with {}", name(), e);
            throw new RuntimeException(e);
        } finally {
            closeIgnoreException(input);
        }

        return dst;
    }

    @Override
    public final Publisher<Buffer> decode(final Publisher<Buffer> from, final BufferAllocator allocator) {
        return from.liftSync(subscriber -> new DecodingOperator(subscriber, allocator, this));
    }

    private static class EncodingOperator implements PublisherSource.Subscriber<Buffer> {

        private static final int FOOTER_LEN = 10;
        private static final OutputStream NULL_OUTPUT = newEmptyStream("NULL");
        private static final OutputStream SIGNAL_IN_PROGRESS_OUTPUT = newEmptyStream("SIGNAL IN PROGRESS");
        private static final OutputStream TERMINATED_OUTPUT = newEmptyStream("TERMINATED");

        private static final AtomicLongFieldUpdater<EncodingOperator> demandUpdater =
                newUpdater(EncodingOperator.class, "demand");

        private static final AtomicReferenceFieldUpdater<EncodingOperator, OutputStream> outputUpdater =
                AtomicReferenceFieldUpdater.newUpdater(EncodingOperator.class, OutputStream.class, "output");

        private final SwappableBufferOutputStream stream = new SwappableBufferOutputStream();
        private final PublisherSource.Subscriber<? super Buffer> subscriber;
        private final BufferAllocator allocator;
        private final AbstractZipContentCodec codec;

        private volatile long demand;

        @Nullable
        private volatile OutputStream output = NULL_OUTPUT;

        EncodingOperator(final PublisherSource.Subscriber<? super Buffer> subscriber, final BufferAllocator allocator,
                         final AbstractZipContentCodec codec) {
            this.subscriber = subscriber;
            this.allocator = allocator;
            this.codec = codec;
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            final Subscription sub = new Subscription() {
                @Override
                public void request(final long n) {
                    if (!isRequestNValid(n)) {
                        subscription.request(n);
                    } else if (demandUpdater.accumulateAndGet(EncodingOperator.this, n,
                            FlowControlUtils::addWithOverflowProtectionIfNotNegative) > 0) {
                        subscription.request(n);
                    }
                }

                @Override
                public void cancel() {
                    subscription.cancel();

                    for (;;) {
                        OutputStream theState = outputUpdater.get(EncodingOperator.this);
                        if (theState == TERMINATED_OUTPUT) {
                            return;
                        }

                        if (outputUpdater.compareAndSet(EncodingOperator.this,
                                theState, TERMINATED_OUTPUT)) {
                            // Clean up only if the state was not RESERVED,
                            // otherwise let the signal in-progress handle the cleaning
                            if (theState != SIGNAL_IN_PROGRESS_OUTPUT) {
                                closeIgnoreException(theState);
                            }

                            return;
                        }
                    }
                }
            };
            subscriber.onSubscribe(sub);
        }

        @Override
        public void onNext(Buffer next) {
            if (next == null) {
                return;
            }

            // onNext will produce AT-MOST N items (from upstream)
            runSeriallyWithTermHandoff((output) -> {
                demandUpdater.decrementAndGet(this);
                Buffer buffer = allocator.newBuffer(codec.chunkSize);
                stream.swap(buffer);

                try {
                    if (output == NULL_OUTPUT) {
                        // This will produce header bytes
                        output = codec.newDeflaterOutputStream(stream);
                    }

                    consume(output, next);
                } catch (IOException e) {
                    throwException(e);
                }

                subscriber.onNext(buffer);
                return output;
            });
        }

        @Override
        public void onError(Throwable t) {
            runSeriallyWithTermHandoff((__) -> TERMINATED_OUTPUT);
            subscriber.onError(t);
        }

        @Override
        public void onComplete() {
            // onComplete will deliver 1 more element (encoding footer) if there is enough demand.
            runSeriallyWithTermHandoff((output) -> {
                if (demand > 0) {
                    Buffer buffer = allocator.newBuffer(output == NULL_OUTPUT ? codec.chunkSize : FOOTER_LEN);
                    stream.swap(buffer);

                    try {
                        if (output == NULL_OUTPUT) {
                            // This will produce header bytes
                            output = codec.newDeflaterOutputStream(stream);
                        }

                        // This will produce footer bytes
                        output.close();
                        subscriber.onNext(buffer);
                        subscriber.onComplete();
                    } catch (IOException e) {
                        LOGGER.error("Error while encoding with {}", codec.name(), e);
                        subscriber.onError(e);
                    }
                } else {
                    subscriber.onComplete();
                }

                return TERMINATED_OUTPUT;
            });
        }

        // Execute a signal while maintaining ability to receive a cancellation handoff that will trigger the clean-up
        // phase of the resources.
        private void runSeriallyWithTermHandoff(final Function<OutputStream, OutputStream> work) {
            OutputStream theOutput = outputUpdater.get(EncodingOperator.this);
            if (theOutput == TERMINATED_OUTPUT) {
                return;
            }

            Exception toThrow = null;

            // No need to loop since the signals are guaranteed to be signalled serially,
            // thus a CAS fail means cancellation won, so we can safely ignore it.
            if (outputUpdater.compareAndSet(EncodingOperator.this, theOutput, SIGNAL_IN_PROGRESS_OUTPUT)) {
                try {
                    theOutput = work.apply(theOutput);
                } catch (Exception e) {
                    LOGGER.error("Error while encoding with {}", codec.name(), e);
                    theOutput = TERMINATED_OUTPUT;
                    toThrow = e;
                }

                if (!outputUpdater.compareAndSet(EncodingOperator.this, SIGNAL_IN_PROGRESS_OUTPUT, theOutput) ||
                        theOutput == TERMINATED_OUTPUT) {
                    // state changed while we were in progress OR newState is TERMINATED
                    // therefore, cleaning is our responsibility
                    closeIgnoreException(theOutput);
                }
            }

            if (toThrow != null) {
                throwException(toThrow);
            }
        }

        private void consume(final OutputStream output, final Buffer next) throws IOException {
            if (next.hasArray()) {
                output.write(next.array(), next.arrayOffset() + next.readerIndex(),
                        next.readableBytes());
            } else {
                while (next.readableBytes() > 0) {
                    byte[] onHeap = new byte[min(next.readableBytes(), codec.chunkSize)];
                    next.readBytes(onHeap);
                    output.write(onHeap);
                }
            }

            output.flush();
        }
    }

    private static final class DecodingOperator implements PublisherSource.Subscriber<Buffer> {

        private static final AtomicIntegerFieldUpdater<DecodingOperator> stateUpdater =
                AtomicIntegerFieldUpdater.newUpdater(DecodingOperator.class, "state");

        private static final int AVAILABLE = 1;
        private static final int SIGNAL_IN_PROGRESS = 2;
        private static final int TERMINATED = 3;

        private final PublisherSource.Subscriber<? super Buffer> subscriber;
        private final BufferAllocator allocator;
        private final AbstractZipContentCodec codec;
        private final ZLibStreamDecoder streamDecoder;
        private final Inflater inflater;

        @Nullable
        private Subscription subscription;

        private volatile int state = AVAILABLE;

        DecodingOperator(final PublisherSource.Subscriber<? super Buffer> subscriber, final BufferAllocator allocator,
                         final AbstractZipContentCodec codec) {
            this.subscriber = subscriber;
            this.allocator = allocator;
            this.inflater = codec.newRawInflater();
            this.streamDecoder = new ZLibStreamDecoder(inflater, codec.supportsChecksum(), codec.maxPayloadSize);
            this.codec = codec;
        }

        @Override
        public void onSubscribe(final Subscription subscription) {
            this.subscription = ConcurrentSubscription.wrap(new Subscription() {
                @Override
                public void request(final long n) {
                    subscription.request(n);
                }

                @Override
                public void cancel() {
                    subscription.cancel();

                    for (;;) {
                        int theState = stateUpdater.get(DecodingOperator.this);
                        if (theState == TERMINATED) {
                            return;
                        }

                        if (stateUpdater.compareAndSet(DecodingOperator.this,
                                theState, TERMINATED)) {
                            // Clean up only if the state was not RESERVED,
                            // otherwise let the signal in-progress handle the cleaning
                            if (theState != SIGNAL_IN_PROGRESS) {
                                inflater.end();
                            }

                            return;
                        }
                    }
                }
            });

            subscriber.onSubscribe(this.subscription);
        }

        @Override
        public void onNext(@Nullable final Buffer src) {
            assert subscription != null;

            if (src == null) {
                return;
            }

            runSeriallyWithTermHandoff(() -> {
                // onNext will produce AT-MOST N items (as received)
                try {
                    if (streamDecoder.isFinished()) {
                        throw new IllegalStateException("Stream encoder previously closed but more input arrived ");
                    }

                    Buffer part = allocator.newBuffer(codec.chunkSize);
                    streamDecoder.decode(src, part);
                    if (part.readableBytes() > 0) {
                        subscriber.onNext(part);
                    } else {
                        // Not enough data to decompress, ask for more
                        subscription.request(1);
                    }
                } catch (Exception e) {
                    throwException(e);
                }

                return AVAILABLE;
            });
        }

        @Override
        public void onError(final Throwable t) {
            runSeriallyWithTermHandoff(() -> TERMINATED);
            subscriber.onError(t);
        }

        @Override
        public void onComplete() {
            runSeriallyWithTermHandoff(() -> TERMINATED);
            subscriber.onComplete();
        }

        // Execute a signal while maintaining ability to receive a cancellation handoff that will trigger the clean-up
        // phase of the resources.
        private void runSeriallyWithTermHandoff(final IntSupplier work) {
            int theState = stateUpdater.get(DecodingOperator.this);
            if (theState == TERMINATED) {
                return;
            }

            Exception toThrow = null;

            // No need to loop since the signals are guaranteed to be signalled serially,
            // thus a CAS fail means cancellation won, so we can safely ignore it.
            if (stateUpdater.compareAndSet(DecodingOperator.this, theState, SIGNAL_IN_PROGRESS)) {
                try {
                    theState = work.getAsInt();
                } catch (Exception e) {
                    LOGGER.error("Error while decoding with {}", codec.name(), e);
                    theState = TERMINATED;
                    toThrow = e;
                }

                if (!stateUpdater.compareAndSet(DecodingOperator.this, SIGNAL_IN_PROGRESS, theState) ||
                        theState == TERMINATED) {
                    // state changed while we were in progress OR newState is TERMINATED
                    // therefore, cleaning is our responsibility
                    inflater.end();
                }
            }

            if (toThrow != null) {
                throwException(toThrow);
            }
        }
    }

    private static void closeIgnoreException(@Nullable final Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException e) {
            LOGGER.error("Unexpected IO exception while closing buffer streams", e);
        }
    }

    private static OutputStream newEmptyStream(final String type) {
        return new OutputStream() {

            @Override
            public void write(int b) {
                throw new IllegalStateException(type + " output stream.");
            }

            @Override
            public void write(byte[] b, int off, int len) {
                throw new IllegalStateException(type + " output stream.");
            }

            @Override
            public void close() {
            }
        };
    }

    // Code forked from Netty's JdkZlibDecoder
    static class ZLibStreamDecoder {
        private static final int FHCRC = 0x02;
        private static final int FEXTRA = 0x04;
        private static final int FNAME = 0x08;
        private static final int FCOMMENT = 0x10;
        private static final int FRESERVED = 0xE0;

        @Nullable
        private final CRC32 crc;
        private final Inflater inflater;
        private final int maxPayloadSize;

        private enum State {
            HEADER_START,
            HEADER_END,
            FLG_READ,
            XLEN_READ,
            SKIP_FNAME,
            SKIP_COMMENT,
            PROCESS_FHCRC,
            FOOTER_START,
        }

        private State state = State.HEADER_START;
        private int flags = -1;
        private int xlen = -1;

        private int payloadSizeAcc;
        private boolean finished;

        ZLibStreamDecoder(Inflater inflater, boolean supportsChksum, int maxPayloadSize) {
            this.inflater = inflater;
            this.maxPayloadSize = maxPayloadSize;
            crc = supportsChksum ? new CRC32() : null;
        }

        public boolean isFinished() {
            return finished;
        }

        @Nullable
        protected void decode(Buffer in, Buffer out) throws Exception {
            if (finished) {
                // Skip data received after finished.
                in.skipBytes(in.readableBytes());
                return;
            }

            int readableBytes = in.readableBytes();
            if (readableBytes == 0) {
                return;
            }

            if (crc != null) {
                switch (state) {
                    case FOOTER_START:
                        if (readGZIPFooter(in)) {
                            finished = true;
                        }
                        return;
                    default:
                        if (state != State.HEADER_END && !readGZIPHeader(in)) {
                            return;
                        }
                }
                // Some bytes may have been consumed, and so we must re-set the number of readable bytes.
                readableBytes = in.readableBytes();
            }

            if (in.hasArray()) {
                inflater.setInput(in.array(), in.arrayOffset() + in.readerIndex(), readableBytes);
            } else {
                byte[] array = new byte[readableBytes];
                in.getBytes(in.readerIndex(), array);
                inflater.setInput(array);
            }

            try {
                boolean readFooter = false;
                while (!inflater.needsInput()) {
                    byte[] outArray = out.array();
                    int writerIndex = out.writerIndex();
                    int outIndex = out.arrayOffset() + writerIndex;
                    int outputLength = inflater.inflate(outArray, outIndex, out.writableBytes());
                    payloadSizeAcc += outputLength;
                    if (payloadSizeAcc > maxPayloadSize) {
                        throw new IllegalStateException("Max decompressed payload limit has been reached: " +
                                payloadSizeAcc + " (expected <= " + maxPayloadSize + ") bytes");
                    }

                    if (outputLength > 0) {
                        out.writerIndex(writerIndex + outputLength);
                        if (crc != null) {
                            crc.update(outArray, outIndex, outputLength);
                        }
                    } else {
                        if (inflater.needsDictionary()) {
                            throw new IOException(
                                    "decompression failure, unable to set dictionary as non was specified");
                        }
                    }

                    if (inflater.finished()) {
                        if (crc == null) {
                            finished = true; // Do not decode anymore.
                        } else {
                            readFooter = true;
                        }
                        break;
                    } else {
                        out.ensureWritable(inflater.getRemaining() << 1);
                    }
                }

                in.skipBytes(readableBytes - inflater.getRemaining());

                if (readFooter) {
                    state = State.FOOTER_START;
                    if (readGZIPFooter(in)) {
                        finished = true;
                        inflater.end();
                    }
                }
            } catch (DataFormatException e) {
                throw new IOException("decompression failure", e);
            }
        }

        private boolean readGZIPHeader(Buffer in) throws IOException {
            switch (state) {
                case HEADER_START:
                    if (in.readableBytes() < 10) {
                        return false;
                    }
                    // read magic numbers
                    int magic0 = in.readByte();
                    int magic1 = in.readByte();

                    if (magic0 != 31) {
                        throw new IOException("Input is not in the GZIP format");
                    }
                    crc.update(magic0);
                    crc.update(magic1);

                    int method = in.readUnsignedByte();
                    if (method != Deflater.DEFLATED) {
                        throw new IOException("Unsupported compression method "
                                + method + " in the GZIP header");
                    }
                    crc.update(method);

                    flags = in.readUnsignedByte();
                    crc.update(flags);

                    if ((flags & FRESERVED) != 0) {
                        throw new IOException(
                                "Reserved flags are set in the GZIP header");
                    }

                    // mtime (int)
                    crc.update(in.readUnsignedByte());
                    crc.update(in.readUnsignedByte());
                    crc.update(in.readUnsignedByte());
                    crc.update(in.readUnsignedByte());

                    crc.update(in.readUnsignedByte()); // extra flags
                    crc.update(in.readUnsignedByte()); // operating system

                    state = State.FLG_READ;
                    // fall through
                case FLG_READ:
                    if ((flags & FEXTRA) != 0) {
                        if (in.readableBytes() < 2) {
                            return false;
                        }
                        int xlen1 = in.readUnsignedByte();
                        int xlen2 = in.readUnsignedByte();
                        crc.update(xlen1);
                        crc.update(xlen2);

                        xlen |= xlen1 << 8 | xlen2;
                    }
                    state = State.XLEN_READ;
                    // fall through
                case XLEN_READ:
                    if (xlen != -1) {
                        if (in.readableBytes() < xlen) {
                            return false;
                        }
                        for (int i = 0; i < xlen; i++) {
                            crc.update(in.readUnsignedByte());
                        }
                    }
                    state = State.SKIP_FNAME;
                    // fall through
                case SKIP_FNAME:
                    if ((flags & FNAME) != 0) {
                        if (in.readableBytes() > 0) {
                            return false;
                        }
                        do {
                            int b = in.readUnsignedByte();
                            crc.update(b);
                            if (b == 0x00) {
                                break;
                            }
                        } while (in.readableBytes() > 0);
                    }
                    state = State.SKIP_COMMENT;
                    // fall through
                case SKIP_COMMENT:
                    if ((flags & FCOMMENT) != 0) {
                        if (in.readableBytes() > 0) {
                            return false;
                        }
                        do {
                            int b = in.readUnsignedByte();
                            crc.update(b);
                            if (b == 0x00) {
                                break;
                            }
                        } while (in.readableBytes() > 0);
                    }
                    state = State.PROCESS_FHCRC;
                    // fall through
                case PROCESS_FHCRC:
                    if ((flags & FHCRC) != 0) {
                        if (in.readableBytes() < 4) {
                            return false;
                        }
                        verifyCrc(in);
                    }
                    crc.reset();
                    state = State.HEADER_END;
                    // fall through
                case HEADER_END:
                    return true;
                default:
                    throw new IllegalStateException();
            }
        }

        private boolean readGZIPFooter(Buffer buf) throws IOException {
            if (buf.readableBytes() < 8) {
                return false;
            }

            verifyCrc(buf);

            // read ISIZE and verify
            int dataLength = 0;
            for (int i = 0; i < 4; ++i) {
                dataLength |= buf.readUnsignedByte() << i * 8;
            }
            int readLength = inflater.getTotalOut();
            if (dataLength != readLength) {
                throw new IOException(
                        "Number of bytes mismatch. Expected: " + dataLength + ", Got: " + readLength);
            }
            return true;
        }

        private void verifyCrc(Buffer in) throws IOException {
            long crcValue = 0;
            for (int i = 0; i < 4; ++i) {
                crcValue |= (long) in.readUnsignedByte() << i * 8;
            }
            long readCrc = crc.getValue();
            if (crcValue != readCrc) {
                throw new IOException(
                        "CRC value mismatch. Expected: " + crcValue + ", Got: " + readCrc);
            }
        }
    }

    private static class SwappableBufferOutputStream extends OutputStream {
        @Nullable
        private Buffer buffer;

        private void swap(final Buffer buffer) {
            this.buffer = requireNonNull(buffer);
        }

        @Override
        public void write(final int b) {
            assert buffer != null;
            buffer.writeInt(b);
        }

        @Override
        public void write(byte[] b) {
            assert buffer != null;
            buffer.writeBytes(b);
        }

        @Override
        public void write(byte[] b, int off, int len) {
            assert buffer != null;
            buffer.writeBytes(b, off, len);
        }
    }

    private static final class BufferBoundedInputStream extends InputStream {
        private final Buffer buffer;
        private int count;

        BufferBoundedInputStream(Buffer buffer, int limit) {
            this.buffer = requireNonNull(buffer);
            this.count = limit;
        }

        @Override
        public int read() {
            if (buffer.readableBytes() == 0 || --count <= 0) {
                return -1;
            }

            return buffer.readByte() & 0xff;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            int bytes = min(buffer.readableBytes(), min(count, len));
            if (bytes <= 0) {
                return -1;
            }

            count -= bytes;
            buffer.readBytes(b, off, bytes);
            return bytes;
        }

        @Override
        public long skip(long n) {
            int skipped = (int) min(buffer.readableBytes(), n);
            if (skipped <= 0) {
                return 0;
            }

            count -= skipped;
            buffer.skipBytes(skipped);
            return skipped;
        }

        @Override
        public int available() {
            return buffer.readableBytes();
        }
    }
}
