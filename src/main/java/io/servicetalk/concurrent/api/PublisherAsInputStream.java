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

import io.servicetalk.concurrent.BlockingIterator;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AutoClosableUtils.closeAndReThrowIoException;
import static java.lang.Math.min;
import static java.lang.System.arraycopy;
import static java.util.Objects.requireNonNull;

/**
 * As returned by {@link Publisher#toInputStream(Function)} and {@link Publisher#toInputStream(Function, int)}.
 *
 * @param <T> Type of items emitted by the {@link Publisher} from which this {@link InputStream} is created.
 */
final class PublisherAsInputStream<T> extends InputStream {

    private static final byte[] CLOSED = new byte[0];
    private final Function<T, byte[]> serializer;
    private final BlockingIterator<T> iterator;

    @Nullable
    private byte[] leftover;
    private int leftoverReadIndex;

    PublisherAsInputStream(final BlockingIterator<T> source, final Function<T, byte[]> serializer) {
        this.serializer = requireNonNull(serializer);
        iterator = source;
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
                final int toRead = min(len, (leftover.length - leftoverReadIndex));
                arraycopy(leftover, leftoverReadIndex, b, off, toRead);
                if (toRead != len) { // drained left over.
                    resetLeftover();
                    off += toRead;
                    len -= toRead;
                } else { // toRead == len, i.e. we filled the buffer.
                    leftoverReadIndex += toRead;
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
            leftover = serializer.apply(iterator.next());
        }
    }

    @Override
    public int available() {
        return leftover == null ? 0 : leftover.length - leftoverReadIndex;
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
            leftover = serializer.apply(iterator.next());
            if (leftover != null) {
                return readSingleByteFromLeftover(leftover);
            }
        }
    }

    private int readSingleByteFromLeftover(byte[] leftover) {
        final int value = leftover[leftoverReadIndex++];
        checkResetLeftover(leftover);
        return value;
    }

    private void checkResetLeftover(byte[] leftover) {
        if (leftoverReadIndex == leftover.length) {
            resetLeftover();
        }
    }

    private void resetLeftover() {
        leftover = null;
        leftoverReadIndex = 0;
    }

    private void checkAlreadyClosed() throws IOException {
        if (leftover == CLOSED) {
            throw new IOException("Stream is already closed.");
        }
    }
}
