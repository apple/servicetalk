/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.servicetalk.http.api;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.Publisher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.Buffer.asInputStream;
import static io.servicetalk.buffer.api.Buffer.asOutputStream;
import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HeaderUtils.addContentEncoding;
import static java.lang.Math.min;

abstract class AbstractZipContentCodec implements ContentCodec {

    protected static final int ONE_KB = 1 << 10;
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractZipContentCodec.class);
    private static final Object END_OF_STREAM = new Object();

    private final CharSequence encoding;

    AbstractZipContentCodec(final CharSequence encoding) {
        this.encoding = encoding;
    }

    abstract boolean supportsChecksum();

    abstract Inflater newRawInflater();

    abstract DeflaterOutputStream newDeflaterOutputStream(OutputStream out) throws IOException;

    abstract InflaterInputStream newInflaterInputStream(InputStream in);

    @Override
    public final Buffer encode(final HttpHeaders headers, final Buffer src, final int offset, final int length,
                               final BufferAllocator allocator) {
        addContentEncoding(headers, encoding);

        final Buffer dst = allocator.newBuffer(ONE_KB);
        DeflaterOutputStream output = null;
        try {
            output = newDeflaterOutputStream(asOutputStream(dst));

            if (src.hasArray()) {
                output.write(src.array(), offset, length);
            } else {
                while (src.readableBytes() > 0) {
                    byte[] onHeap = new byte[min(src.readableBytes(), ONE_KB)];
                    src.readBytes(onHeap);
                    output.write(onHeap);
                }
            }

            output.finish();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            closeQuietly(output);
        }

        return dst;
    }

    @Override
    public final Publisher<Buffer> encode(final HttpHeaders headers, final Publisher<Buffer> from,
                                          final BufferAllocator allocator) {
        addContentEncoding(headers, encoding);
        return from
                .map((it) -> (Object) it)
                .concat(succeeded(END_OF_STREAM))
                .liftSync(subscriber -> new PublisherSource.Subscriber<Object>() {

                    @Nullable
                    Buffer dst;
                    @Nullable
                    DeflaterOutputStream output;

                    @Override
                    public void onSubscribe(PublisherSource.Subscription subscription) {
                        try {
                            dst = allocator.newBuffer(ONE_KB);
                            output = newDeflaterOutputStream(asOutputStream(dst));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        subscriber.onSubscribe(subscription);
                    }

                    @Override
                    public void onNext(Object next) {
                        assert output != null;
                        assert dst != null;

                        try {
                            if (next == END_OF_STREAM) {
                                try {
                                    output.finish();
                                    subscriber.onNext(dst.readSlice(dst.readableBytes()));
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                                return;
                            }

                            Buffer src = (Buffer) next;
                            if (src.hasArray()) {
                                output.write(src.array(), src.readerIndex(), src.readableBytes());
                            } else {
                                while (src.readableBytes() > 0) {
                                    byte[] onHeap = new byte[min(src.readableBytes(), ONE_KB)];
                                    src.readBytes(onHeap);
                                    output.write(onHeap);
                                }
                            }

                            output.flush();
                            subscriber.onNext(dst.readSlice(dst.readableBytes()));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        closeQuietly(output);
                        subscriber.onError(t);
                    }

                    @Override
                    public void onComplete() {
                        closeQuietly(output);
                        subscriber.onComplete();
                    }
                });
    }

    @Override
    public final Buffer decode(final Buffer src, final int offset, final int length, final BufferAllocator allocator) {
        final Buffer dst = allocator.newBuffer(ONE_KB);
        InflaterInputStream input = null;
        try {
            input = newInflaterInputStream(asInputStream(src));

            int read = dst.setBytesUntilEndStream(0, input, ONE_KB);
            dst.writerIndex(read);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            closeQuietly(input);
        }

        return dst;
    }

    @Override
    public final Publisher<Buffer> decode(final Publisher<Buffer> from, final BufferAllocator allocator) {
        return from.liftSync(subscriber -> new PublisherSource.Subscriber<Buffer>() {

            @Nullable
            Buffer dst;
            @Nullable
            Inflater inflater;
            @Nullable
            ZLibStreamDecoder streamDecoder;

            @Override
            public void onSubscribe(final PublisherSource.Subscription subscription) {
                dst = allocator.newBuffer(ONE_KB);
                inflater = newRawInflater();
                streamDecoder = new ZLibStreamDecoder(dst, inflater, supportsChecksum());
                subscriber.onSubscribe(subscription);
            }

            @Override
            public void onNext(@Nullable final Buffer src) {
                assert streamDecoder != null;
                assert src != null;

                Buffer part;
                try {
                    if (streamDecoder.isFinished()) {
                        throw new IllegalStateException("Stream encoder previously closed but more input arrived ");
                    }

                    part = streamDecoder.decode(src);
                    subscriber.onNext(part != null ? part : EMPTY_BUFFER);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void onError(final Throwable t) {
                assert inflater != null;

                inflater.end();
                subscriber.onError(t);
            }

            @Override
            public void onComplete() {
                assert inflater != null;

                inflater.end();
                subscriber.onComplete();
            }
        });
    }

    private void closeQuietly(@Nullable final Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException e) {
            LOGGER.error("Unexpected IO exception while closing buffer streams", e);
        }
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
        private final Buffer decompressed;

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

        private boolean finished;

        ZLibStreamDecoder(Buffer destination, Inflater inflater, boolean supportsChksum) {
            this.decompressed = destination;
            this.inflater = inflater;
            crc = supportsChksum ? new CRC32() : null;
        }

        public boolean isFinished() {
            return finished;
        }

        @Nullable
        protected Buffer decode(Buffer in) throws Exception {
            if (finished) {
                // Skip data received after finished.
                in.skipBytes(in.readableBytes());
                return null;
            }

            int readableBytes = in.readableBytes();
            if (readableBytes == 0) {
                return null;
            }

            if (crc != null) {
                switch (state) {
                    case FOOTER_START:
                        if (readGZIPFooter(in)) {
                            finished = true;
                        }
                        return null;
                    default:
                        if (state != State.HEADER_END && !readGZIPHeader(in)) {
                            return null;
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
                    byte[] outArray = decompressed.array();
                    int writerIndex = decompressed.writerIndex();
                    int outIndex = decompressed.arrayOffset() + writerIndex;
                    int outputLength = inflater.inflate(outArray, outIndex, decompressed.writableBytes());
                    if (outputLength > 0) {
                        decompressed.writerIndex(writerIndex + outputLength);
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
                        decompressed.ensureWritable(ONE_KB);
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

            if (decompressed.readableBytes() > 0) {
                return decompressed.readSlice(decompressed.readableBytes());
            }

            return null;
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
}
