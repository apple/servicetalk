/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.buffer.netty;

import io.servicetalk.buffer.api.Buffer;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

import static io.servicetalk.buffer.netty.BufferAllocators.PREFER_DIRECT_ALLOCATOR;
import static io.servicetalk.buffer.netty.BufferAllocators.PREFER_HEAP_ALLOCATOR;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

class NettyBufferTest {

    @ParameterizedTest(name = "{displayName} [{index}] write={0}")
    @ValueSource(booleans = {true, false})
    void writeBytesInputStreamZeroLength(boolean write) throws IOException {
        Buffer buffer = buffer(true);
        byte[] bytes = new byte[100];
        InputStream is = inputStream(bytes, false);
        Buffer dup = buffer.duplicate();
        int readBytes;
        if (write) {
            readBytes = buffer.writeBytes(is, 0);
        } else {
            readBytes = buffer.setBytes(buffer.writerIndex(), is, 0);
        }
        assertThat("Read unexpected number of bytes", readBytes, is(0));
        assertThat("Unexpected changes for the buffer", buffer, equalTo(dup));
    }

    @ParameterizedTest(name = "{displayName} [{index}] heapBuffer={0} limitRead={1} write={2}")
    @CsvSource(value = {"false,false,false", "false,false,true", "false,true,false", "false,true,true",
            "true,false,false", "true,false,true", "true,true,false", "true,true,true"})
    void writeBytesInputStreamExactLength(boolean heapBuffer, boolean limitRead, boolean write) throws IOException {
        Buffer buffer = buffer(heapBuffer);
        byte[] bytes = new byte[100];
        InputStream is = inputStream(bytes, limitRead);
        writeOrSetBytes(buffer, is, bytes.length, write);
        assertBytes(buffer, bytes, is, bytes.length);
        assertEOF(buffer, is, write);
    }

    @ParameterizedTest(name = "{displayName} [{index}] heapBuffer={0} limitRead={1} write={2}")
    @CsvSource(value = {"false,false,false", "false,false,true", "false,true,false", "false,true,true",
            "true,false,false", "true,false,true", "true,true,false", "true,true,true"})
    void writeBytesInputStreamHalfAvailable(boolean heapBuffer, boolean limitRead, boolean write) throws IOException {
        Buffer buffer = buffer(heapBuffer);
        byte[] bytes = new byte[100];
        InputStream is = inputStream(bytes, limitRead);
        writeOrSetBytes(buffer, is, bytes.length / 2, write);
        assertBytes(buffer, bytes, is, bytes.length / 2);
    }

    @ParameterizedTest(name = "{displayName} [{index}] heapBuffer={0} limitRead={1} write={2}")
    @CsvSource(value = {"false,false,false", "false,false,true", "false,true,false", "false,true,true",
            "true,false,false", "true,false,true", "true,true,false", "true,true,true"})
    void writeBytesInputStreamDoubleLength(boolean heapBuffer, boolean limitRead, boolean write) throws IOException {
        Buffer buffer = buffer(heapBuffer);
        byte[] bytes = new byte[100];
        InputStream is = inputStream(bytes, limitRead);
        writeOrSetBytes(buffer, is, bytes.length * 2, write);
        assertBytes(buffer, bytes, is, bytes.length);
        assertEOF(buffer, is, write);
    }

    private static void writeOrSetBytes(Buffer buffer, InputStream is, int length, boolean write) throws IOException {
        if (write) {
            buffer.writeBytes(is, length);
        } else {
            buffer.ensureWritable(length);
            int idx = buffer.writerIndex();
            int written = buffer.setBytes(idx, is, length);
            assertThat("Unexpected buffer.writerIndex()", buffer.writerIndex(), is(idx));
            buffer.writerIndex(buffer.writerIndex() + written);
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] heapBuffer={0} limitRead={1} write={2}")
    @CsvSource(value = {"false,false,false", "false,false,true", "false,true,false", "false,true,true",
            "true,false,false", "true,false,true", "true,true,false", "true,true,true"})
    void writeBytesUntilEndStream(boolean heapBuffer, boolean limitRead, boolean write) throws IOException {
        Buffer buffer = buffer(heapBuffer);
        byte[] bytes = new byte[100];
        InputStream is = inputStream(bytes, limitRead);
        if (write) {
            buffer.writeBytesUntilEndStream(is, bytes.length / 10);
        } else {
            int idx = buffer.writerIndex();
            int written = buffer.setBytesUntilEndStream(idx, is, bytes.length / 10);
            assertThat("Unexpected buffer.writerIndex()", buffer.writerIndex(), is(idx));
            buffer.writerIndex(buffer.writerIndex() + written);
        }
        assertBytes(buffer, bytes, is, bytes.length);
        assertEOF(buffer, is, write);
    }

    private static Buffer buffer(boolean heapBuffer) {
        return (heapBuffer ? PREFER_HEAP_ALLOCATOR : PREFER_DIRECT_ALLOCATOR).newBuffer();
    }

    private static InputStream inputStream(byte[] bytes, boolean limitRead) {
        ThreadLocalRandom.current().nextBytes(bytes);
        InputStream is = new ByteArrayInputStream(bytes);
        return limitRead ? new TestInputStream(is, bytes.length / 20) : is;
    }

    private static void assertBytes(Buffer buffer, byte[] bytes, InputStream is, int length) throws IOException {
        assertThat("Unexpected buffer.readableBytes()", buffer.readableBytes(), is(length));
        byte[] tmp = new byte[buffer.readableBytes()];
        buffer.readBytes(tmp);
        assertThat("Unexpected bytes read", tmp, is(Arrays.copyOf(bytes, length)));
        assertThat("Unexpected available bytes", is.available(), is(bytes.length - length));
    }

    private static void assertEOF(Buffer buffer, InputStream is, boolean write) throws IOException {
        assertThat("Unexpected data from InputStream", is.read(), is(-1));
        if (write) {
            assertThat("No EOF signal", buffer.writeBytes(is, 1), is(-1));
        } else {
            assertThat("No EOF signal", buffer.setBytes(buffer.writerIndex(), is, 1), is(-1));
        }
    }

    private static final class TestInputStream extends InputStream {

        private final InputStream delegate;
        private final int readLimit;

        TestInputStream(InputStream delegate, int readLimit) {
            this.delegate = delegate;
            this.readLimit = readLimit;
        }

        @Override
        public int read() throws IOException {
            return delegate.read();
        }

        @Override
        public int read(final byte[] b) throws IOException {
            return delegate.read(b);
        }

        @Override
        public int read(final byte[] b, final int off, final int len) throws IOException {
            // Intentionally limit number of bytes that can be read in one invocation
            return delegate.read(b, off, Math.min(len, readLimit));
        }

        @Override
        public long skip(final long n) throws IOException {
            return delegate.skip(n);
        }

        @Override
        public int available() throws IOException {
            return delegate.available();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        @Override
        public synchronized void mark(final int readlimit) {
            delegate.mark(readlimit);
        }

        @Override
        public synchronized void reset() throws IOException {
            delegate.reset();
        }

        @Override
        public boolean markSupported() {
            return delegate.markSupported();
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + '(' + delegate.toString() + ')';
        }
    }
}
