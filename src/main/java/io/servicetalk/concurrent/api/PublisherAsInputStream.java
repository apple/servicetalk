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

import io.servicetalk.concurrent.api.PublisherAsIterable.CancellableIterator;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Function;
import javax.annotation.Nullable;

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
    private final CancellableIterator<T> iterator;

    @Nullable
    private byte[] singleByteRead;

    @Nullable
    private byte[] leftover;
    private int leftoverReadIndex;

    PublisherAsInputStream(final CancellableIterator<T> source, final Function<T, byte[]> serializer) {
        this.serializer = requireNonNull(serializer);
        iterator = source;
    }

    @Override
    public int read(final byte[] b, int off, final int len) throws IOException {
        if (leftover == CLOSED) {
            throw new IOException("Stream is already closed.");
        }
        requireNonNull(b);
        if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException("Unexpected offset " + off + " (expected > 0) or length " + len
                    + " (expected >= 0 and should fit in the destination array). Destination array length " + b.length);
        } else if (len == 0) {
            return 0;
        }

        int bytesRead = 0;
        int leftToRead = len;
        do {
            if (leftover != null && leftover.length > leftoverReadIndex) {
                // We have leftOver bytes from a previous read, so try to satisfy read from leftOver.
                final int toRead = min(leftToRead, (leftover.length - leftoverReadIndex));
                arraycopy(leftover, leftoverReadIndex, b, off, toRead);
                bytesRead += toRead;
                if (toRead != len) { // drained left over.
                    leftover = null;
                    leftoverReadIndex = 0;
                    off += toRead;
                    leftToRead -= toRead;
                } else { // toRead == len, i.e. we filled the buffer.
                    leftoverReadIndex += toRead;
                    return bytesRead;
                }
            }
            if (!iterator.hasNext()) {
                return bytesRead == 0 ? -1 : bytesRead;
            }
            leftover = serializer.apply(iterator.next());
        } while (bytesRead < len);

        return bytesRead;
    }

    @Override
    public int available() {
        return leftover == null ? 0 : leftover.length - leftoverReadIndex;
    }

    @Override
    public void close() {
        leftover = CLOSED;
        iterator.cancel();
    }

    @Override
    public boolean markSupported() {
        // To reduce complexity at this layer, we do not support marks. If required, someone can wrap this in a stream
        // that supports marks like a BufferedInputStream.
        return false;
    }

    @Override
    public int read() throws IOException {
        if (singleByteRead == null) {
            singleByteRead = new byte[1];
        }
        if (read(singleByteRead, 0, 1) > 0) {
            return singleByteRead[0];
        }
        return -1;
    }
}
