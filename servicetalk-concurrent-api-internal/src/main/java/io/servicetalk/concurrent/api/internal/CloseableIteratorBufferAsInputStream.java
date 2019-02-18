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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.CloseableIterator;
import io.servicetalk.concurrent.internal.AbstractCloseableIteratorAsInputStream;

import java.io.IOException;
import java.io.InputStream;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.DEFAULT_RO_ALLOCATOR;

/**
 * Conversion from a {@link CloseableIterator} of {@link Buffer}s to a {@link InputStream}.
 */
public final class CloseableIteratorBufferAsInputStream extends AbstractCloseableIteratorAsInputStream<Buffer> {
    private static final Buffer CLOSED = DEFAULT_RO_ALLOCATOR.fromAscii("");
    @Nullable
    private Buffer leftover;

    /**
     * Create a new instance.
     * @param iterator The {@link CloseableIterator} providing data.
     */
    public CloseableIteratorBufferAsInputStream(CloseableIterator<Buffer> iterator) {
        super(iterator);
    }

    @Override
    protected int leftOverReadableBytes() {
        assert leftover != null;
        return leftover.readableBytes();
    }

    @Override
    protected void leftOverReadBytes(final byte[] dst, final int offset, final int length) {
        assert leftover != null;
        leftover.readBytes(dst, offset, length);
    }

    @Override
    protected boolean hasLeftOver() {
        return leftover != null;
    }

    @Override
    protected void leftOverCheckReset() {
        assert leftover != null;
        if (leftover.readableBytes() == 0) {
            leftover = null;
        }
    }

    @Override
    protected void leftOverReset() {
        leftover = null;
    }

    @Override
    protected void nextLeftOver(final CloseableIterator<Buffer> iterator) {
        leftover = iterator.next();
    }

    @Override
    protected byte leftOverReadSingleByte() {
        assert leftover != null;
        final byte b = leftover.readByte();
        leftOverCheckReset();
        return b;
    }

    @Override
    protected boolean closed() {
        return leftover == CLOSED;
    }

    @Override
    public void close() throws IOException {
        leftover = CLOSED;
        super.close();
    }
}
