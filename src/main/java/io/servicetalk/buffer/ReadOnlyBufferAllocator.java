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
package io.servicetalk.buffer;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;

import static java.nio.ByteBuffer.allocate;
import static java.nio.ByteBuffer.allocateDirect;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * An {@link BufferAllocator} that creates {@link Buffer} instances which cannot be modified and are read only.
 */
final class ReadOnlyBufferAllocator implements BufferAllocator {
    static final BufferAllocator PREFER_DIRECT_ALLOCATOR = new ReadOnlyBufferAllocator(true);
    static final BufferAllocator PREFER_HEAP_ALLOCATOR = new ReadOnlyBufferAllocator(false);
    private boolean preferDirect;

    private ReadOnlyBufferAllocator(boolean preferDirect) {
        this.preferDirect = preferDirect;
    }

    @Override
    public Buffer newBuffer(int initialCapacity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Buffer newBuffer(int initialCapacity, boolean direct) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompositeBuffer newCompositeBuffer() {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompositeBuffer newCompositeBuffer(int maxComponents) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Buffer fromSequence(CharSequence data, Charset charset) {
        return fromSequence(data, charset, preferDirect);
    }

    @Override
    public Buffer fromSequence(CharSequence data, Charset charset, boolean direct) {
        // TODO(scott): cache the encoder in a thread local?
        CharsetEncoder encoder = charset.newEncoder();
        ByteBuffer byteBuffer = direct ? allocateDirect((int) (data.length() * encoder.maxBytesPerChar())) :
                allocate((int) (data.length() * encoder.maxBytesPerChar()));
        CoderResult cr = encoder.encode(CharBuffer.wrap(data), byteBuffer, true);
        try {
            if (!cr.isUnderflow()) {
                cr.throwException();
            }
            cr = encoder.flush(byteBuffer);
            if (!cr.isUnderflow()) {
                cr.throwException();
            }
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException(e);
        }
        byteBuffer.flip();
        return new ReadOnlyByteBuffer(byteBuffer);
    }

    @Override
    public Buffer fromUtf8(CharSequence data) {
        return fromUtf8(data, preferDirect);
    }

    @Override
    public Buffer fromUtf8(CharSequence data, boolean direct) {
        return fromSequence(data, UTF_8, direct);
    }

    @Override
    public Buffer fromAscii(CharSequence data) {
        return fromAscii(data, preferDirect);
    }

    @Override
    public Buffer fromAscii(CharSequence data, boolean direct) {
        ByteBuffer byteBuffer = direct ? allocateDirect(data.length()) : allocate(data.length());
        // Just do a raw cast. If the character is not within the valid ascii range we preserve the data as much as
        // possible.
        data.codePoints().forEach(c -> byteBuffer.put((byte) c));
        byteBuffer.flip();
        return new ReadOnlyByteBuffer(byteBuffer);
    }

    @Override
    public Buffer wrap(byte[] bytes) {
        return new ReadOnlyByteBuffer(ByteBuffer.wrap(bytes));
    }

    @Override
    public Buffer wrap(ByteBuffer buffer) {
        return new ReadOnlyByteBuffer(buffer);
    }
}
