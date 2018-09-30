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
package io.servicetalk.concurrent.internal;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.CloseableIterator;

import java.io.IOException;
import java.io.InputStream;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.DEFAULT_RO_ALLOCATOR;
import static io.servicetalk.concurrent.internal.AutoClosableUtils.closeAndReThrowIoException;
import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;

/**
 * Conversion from a {@link CloseableIterator} of {@link Buffer}s to a {@link InputStream}.
 * @see CloseableIteratorAsInputStream
 */
public final class CloseableIteratorBufferAsInputStream extends InputStream {
    private static final Buffer CLOSED = DEFAULT_RO_ALLOCATOR.fromAscii("");
    private final CloseableIterator<Buffer> iterator;
    @Nullable
    private Buffer leftover;

    /**
     * Create a new instance.
     * @param iterator The {@link CloseableIterator} providing data.
     */
    public CloseableIteratorBufferAsInputStream(CloseableIterator<Buffer> iterator) {
        this.iterator = requireNonNull(iterator);
    }

    @Override
    public int read(final byte[] b, int off, int len) throws IOException {
        checkAlreadyClosed();
        requireNonNull(b);
        if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException("Unexpected offset " + off + " (expected > 0) or length " + len
                    + " (expected >= 0 and should fit in the destination array). Destination array length " + b.length);
        }

        final int initialLen = len;
        for (;;) {
            if (leftover != null) {
                // We have leftOver bytes from a previous read, so try to satisfy read from leftOver.
                final int toRead = min(len, leftover.readableBytes());
                leftover.readBytes(b, off, toRead);
                if (toRead != len) { // drained left over.
                    resetLeftover();
                    off += toRead;
                    len -= toRead;
                } else { // toRead == len, i.e. we filled the buffer.
                    checkResetLeftover(leftover);
                    return initialLen - (len - toRead);
                }
            }
            // Avoid fetching a new element if we have no more space to read to. This prevents serializing and
            // retaining an object that we may never actually need.
            if (len == 0) {
                return initialLen - len;
            }
            if (!iterator.hasNext()) {
                final int bytesRead = initialLen - len;
                return bytesRead == 0 ? -1 : bytesRead;
            }
            leftover = iterator.next();
        }
    }

    @Override
    public int available() {
        return leftover == null ? 0 : leftover.readableBytes();
    }

    @Override
    public void close() throws IOException {
        leftover = CLOSED;
        closeAndReThrowIoException(iterator);
    }

    @Override
    public boolean markSupported() {
        // To reduce complexity at this layer, we do not support marks. If required, someone can wrap this in a stream
        // that supports marks like a BufferedInputStream.
        return false;
    }

    @Override
    public int read() throws IOException {
        checkAlreadyClosed();
        if (leftover != null) {
            return readSingleByteFromLeftover(leftover);
        }
        for (;;) {
            if (!iterator.hasNext()) {
                return -1;
            }
            leftover = iterator.next();
            if (leftover != null) {
                if (leftover.readableBytes() != 0) {
                    return readSingleByteFromLeftover(leftover);
                }
                resetLeftover();
            }
        }
    }

    private int readSingleByteFromLeftover(Buffer leftover) {
        final int value = leftover.readByte();
        checkResetLeftover(leftover);
        return value;
    }

    private void checkResetLeftover(Buffer leftover) {
        if (leftover.readableBytes() == 0) {
            resetLeftover();
        }
    }

    private void resetLeftover() {
        leftover = null;
    }

    private void checkAlreadyClosed() throws IOException {
        if (leftover == CLOSED) {
            throw new IOException("Stream is already closed.");
        }
    }
}
