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

import io.servicetalk.concurrent.CloseableIterator;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.function.Function;

import static io.servicetalk.concurrent.internal.AutoClosableUtils.closeAndReThrowIoException;
import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;

/**
 * Conversion from a {@link CloseableIterator} to a {@link InputStream} given a {@link Function} to serialize to bytes.
 *
 * @param <T> Type of items emitted by the {@link CloseableIterator}.
 */
public abstract class AbstractCloseableIteratorAsInputStream<T> extends InputStream {
    private final CloseableIterator<T> iterator;

    /**
     * Create a new instance.
     * @param source The {@link CloseableIterator} providing data.
     */
    protected AbstractCloseableIteratorAsInputStream(final CloseableIterator<T> source) {
        iterator = requireNonNull(source);
    }

    /**
     * Get the number of readable bytes in the left over buffer.
     * @return the number of readable bytes in the left over buffer.
     */
    protected abstract int leftOverReadableBytes();

    /**
     * Read bytes from the left over buffer into {@code b}.
     * @param dst The destination to read to.
     * @param offset The offset to read into for {@code dst}.
     * @param length The amount of bytes to read from the left over buffer.
     */
    protected abstract void leftOverReadBytes(byte[] dst, int offset, int length);

    /**
     * Determine if there are left over bytes buffered.
     * @return {@code true} if there are left over bytes buffered.
     */
    protected abstract boolean hasLeftOver();

    /**
     * Check if the left over buffer needs to be reset.
     */
    protected abstract void leftOverCheckReset();

    /**
     * Reset the left over buffer.
     */
    protected abstract void leftOverReset();

    /**
     * Read the next element from the {@link Iterator}.
     * @param iterator The {@link CloseableIterator} to get the next element from.
     */
    protected abstract void nextLeftOver(CloseableIterator<T> iterator);

    /**
     * Read a single byte from the left over buffer.
     * @return a single byte from the left over buffer.
     */
    protected abstract byte leftOverReadSingleByte();

    /**
     * Determine if {@link #close()} has been called.
     * @return {@code true} if {@link #close()} has been called.
     */
    protected abstract boolean isClosed();

    @Override
    public final int read(final byte[] b, int off, int len) throws IOException {
        checkAlreadyClosed();
        requireNonNull(b);
        if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException("Unexpected offset " + off + " (expected > 0) or length " + len
                    + " (expected >= 0 and should fit in the destination array). Destination array length " + b.length);
        }

        final int initialLen = len;
        for (;;) {
            if (hasLeftOver()) {
                // We have leftOver bytes from a previous read, so try to satisfy read from leftOver.
                final int toRead = min(len, leftOverReadableBytes());
                leftOverReadBytes(b, off, toRead);
                if (toRead != len) { // drained left over.
                    leftOverReset();
                    off += toRead;
                    len -= toRead;
                } else { // toRead == len, i.e. we filled the buffer.
                    leftOverCheckReset();
                    return initialLen - (len - toRead);
                }
            }
            // Avoid fetching a new element if we have no more space to read to. This prevents serializing and
            // retaining an object that we may never actually need.
            if (len == 0) {
                return initialLen;
            }
            if (!iterator.hasNext()) {
                final int bytesRead = initialLen - len;
                return bytesRead == 0 ? -1 : bytesRead;
            }
            nextLeftOver(iterator);
        }
    }

    @Override
    public final int available() {
        return hasLeftOver() ? leftOverReadableBytes() : 0;
    }

    @Override
    public void close() throws IOException {
        closeAndReThrowIoException(iterator);
    }

    @Override
    public final boolean markSupported() {
        // To reduce complexity at this layer, we do not support marks. If required, someone can wrap this in a stream
        // that supports marks like a BufferedInputStream.
        return false;
    }

    @Override
    public final int read() throws IOException {
        checkAlreadyClosed();
        if (hasLeftOver()) {
            return leftOverReadSingleByte() & 0xff;
        }
        for (;;) {
            if (!iterator.hasNext()) {
                return -1;
            }
            nextLeftOver(iterator);
            if (hasLeftOver()) {
                if (leftOverReadableBytes() != 0) {
                    return leftOverReadSingleByte() & 0xff;
                }
                leftOverReset();
            }
        }
    }

    private void checkAlreadyClosed() throws IOException {
        if (isClosed()) {
            throw new IOException("Stream is already closed.");
        }
    }
}
