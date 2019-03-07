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

import io.servicetalk.concurrent.CloseableIterator;
import io.servicetalk.concurrent.internal.AbstractCloseableIteratorAsInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Function;
import javax.annotation.Nullable;

import static java.lang.System.arraycopy;
import static java.util.Objects.requireNonNull;

/**
 * Conversion from a {@link CloseableIterator} to a {@link InputStream} given a {@link Function} to serialize to bytes.
 *
 * @param <T> Type of items emitted by the {@link CloseableIterator}.
 */
final class CloseableIteratorAsInputStream<T> extends AbstractCloseableIteratorAsInputStream<T> {
    private static final byte[] CLOSED = new byte[0];
    private final Function<? super T, byte[]> serializer;

    @Nullable
    private byte[] leftover;
    private int leftoverReadIndex;

    /**
     * Create a new instance.
     * @param source The {@link CloseableIterator} providing data.
     * @param serializer A means to serialize the data from {@code source} to bytes.
     */
    CloseableIteratorAsInputStream(final CloseableIterator<T> source, final Function<? super T, byte[]> serializer) {
        super(source);
        this.serializer = requireNonNull(serializer);
    }

    @Override
    protected int leftOverReadableBytes() {
        assert leftover != null;
        return leftover.length - leftoverReadIndex;
    }

    @Override
    protected void leftOverReadBytes(final byte[] dst, final int offset, final int length) {
        assert leftover != null;
        arraycopy(leftover, leftoverReadIndex, dst, offset, length);
        leftoverReadIndex += length;
    }

    @Override
    protected boolean hasLeftOver() {
        return leftover != null;
    }

    @Override
    protected void leftOverCheckReset() {
        assert leftover != null;
        if (leftoverReadIndex == leftover.length) {
            leftOverReset();
        }
    }

    @Override
    protected void leftOverReset() {
        leftover = null;
        leftoverReadIndex = 0;
    }

    @Override
    protected void nextLeftOver(final CloseableIterator<T> iterator) {
        leftover = serializer.apply(iterator.next());
    }

    @Override
    protected byte leftOverReadSingleByte() {
        assert leftover != null;
        final byte value = leftover[leftoverReadIndex++];
        leftOverCheckReset();
        return value;
    }

    @Override
    protected boolean isClosed() {
        return leftover == CLOSED;
    }

    @Override
    public void close() throws IOException {
        leftover = CLOSED;
        super.close();
    }
}
