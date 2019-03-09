/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.api.Publisher;

import java.io.IOException;
import java.io.OutputStream;

import static java.util.Objects.requireNonNull;

/**
 * An {@link OutputStream} that can be connected to a sink such that any data written on the {@link OutputStream} is
 * eventually emitted to the connected {@link Publisher} {@link Subscriber}.
 */
public final class ConnectableOutputStream extends OutputStream {
    private final ConnectablePayloadWriter<Buffer> payloadWriter = new ConnectablePayloadWriter<>();
    private final BufferAllocator allocator;

    /**
     * Creates a new instance.
     *
     * @param allocator {@link BufferAllocator} to convert {code byte[]} to {@link Buffer}
     */
    public ConnectableOutputStream(final BufferAllocator allocator) {
        this.allocator = requireNonNull(allocator);
    }

    @Override
    public void write(final int b) throws IOException {
        payloadWriter.write(allocator.newBuffer(1).writeByte(b));
    }

    @Override
    public void write(final byte[] b) throws IOException {
        payloadWriter.write(allocator.wrap(b));
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException("Unexpected offset " + off + " (expected > 0) or length " + len
                    + " (expected >= 0 and should fit in the source array). Source array length " + b.length);
        }
        if (len == 0) {
            return;
        }

        payloadWriter.write(allocator.wrap(b, off, len));
    }

    @Override
    public void flush() throws IOException {
        payloadWriter.flush();
    }

    @Override
    public void close() throws IOException {
        payloadWriter.close();
    }

    /**
     * Connects this {@link OutputStream} to the returned {@link Publisher} such that any data written to this
     * {@link OutputStream} is eventually delivered to a {@link Subscriber} of the returned {@link Publisher}.
     *
     * @return {@link Publisher} that will emit all data written to this {@link OutputStream} to its {@link Subscriber}.
     * Only a single active {@link Subscriber} is allowed for this {@link Publisher}.
     */
    public Publisher<Buffer> connect() {
        return payloadWriter.connect();
    }
}
