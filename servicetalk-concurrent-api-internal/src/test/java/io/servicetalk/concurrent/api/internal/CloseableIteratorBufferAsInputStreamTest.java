/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.DEFAULT_RO_ALLOCATOR;
import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.internal.BlockingIterables.from;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CloseableIteratorBufferAsInputStreamTest {

    @Test
    void streamEmitsAllDataInSingleRead() throws IOException {
        Buffer src = DEFAULT_RO_ALLOCATOR.fromAscii("1234");
        try (InputStream stream = new CloseableIteratorBufferAsInputStream(createIterator(src))) {
            byte[] data = new byte[4];
            int read = stream.read(data, 0, 4);
            assertThat("Unexpected number of bytes read.", read, is(4));
            assertThat("Unexpected bytes read.", bytesToBuffer(data, read), equalTo(src));
            assertThat("Bytes read after complete.", stream.read(), is(-1));
        }
    }

    @Test
    void streamEmitsAllDataInMultipleReads() throws IOException {
        Buffer src = DEFAULT_RO_ALLOCATOR.fromAscii("1234");
        try (InputStream stream = new CloseableIteratorBufferAsInputStream(createIterator(src))) {
            byte[] data = new byte[2];

            int read = stream.read(data, 0, 2);
            assertThat("Unexpected number of bytes read.", read, is(2));
            assertThat("Unexpected bytes read.", bytesToBuffer(data, 2),
                    equalTo(src.slice(src.readerIndex(), 2)));
            read = stream.read(data, 0, 2);
            assertThat("Unexpected number of bytes read.", read, is(2));
            assertThat("Unexpected bytes read.", bytesToBuffer(data, 2),
                    equalTo(src.slice(src.readerIndex() + 2, 2)));

            assertThat("Bytes read after complete.", stream.read(), is(-1));
        }
    }

    @Test
    void incrementallyFillAnArray() throws IOException {
        Buffer src = DEFAULT_RO_ALLOCATOR.fromAscii("1234");
        try (InputStream stream = new CloseableIteratorBufferAsInputStream(createIterator(src))) {
            byte[] data = new byte[4];

            int read = stream.read(data, 0, 2);
            assertThat("Unexpected number of bytes read.", read, is(2));
            assertThat("Unexpected bytes read.", bytesToBuffer(data, 2),
                    equalTo(src.slice(src.readerIndex(), 2)));
            read = stream.read(data, 2, 2);
            assertThat("Unexpected number of bytes read.", read, is(2));
            assertThat("Unexpected bytes read.", bytesToBuffer(data, 4), equalTo(src));

            assertThat("Bytes read after complete.", stream.read(), is(-1));
        }
    }

    @Test
    void readRequestMoreThanDataBuffer() throws IOException {
        Buffer src = DEFAULT_RO_ALLOCATOR.fromAscii("1234");
        try (InputStream stream = new CloseableIteratorBufferAsInputStream(createIterator(src))) {
            byte[] data = new byte[16];
            int read = stream.read(data, 0, 16);
            assertThat("Unexpected number of bytes read.", read, is(4));
            assertThat("Unexpected bytes read.", bytesToBuffer(data, read), equalTo(src));
            assertThat("Bytes read after complete.", stream.read(), is(-1));
        }
    }

    @Test
    void readRequestLessThanDataBuffer() throws IOException {
        Buffer src = DEFAULT_RO_ALLOCATOR.fromAscii("1234");
        byte[] data;
        int read;
        try (InputStream stream = new CloseableIteratorBufferAsInputStream(createIterator(src))) {
            data = new byte[2];
            read = stream.read(data, 0, 2);
        }
        assertThat("Unexpected number of bytes read.", read, is(2));
        assertThat("Unexpected bytes read.", bytesToBuffer(data, read), equalTo(src.slice(src.readerIndex(), 2)));
    }

    @Test
    void largerSizeItems() throws IOException {
        Buffer src1 = DEFAULT_RO_ALLOCATOR.fromAscii("1234");
        Buffer src2 = DEFAULT_RO_ALLOCATOR.fromAscii("45678");
        byte[] data;
        int read;
        try (InputStream stream = new CloseableIteratorBufferAsInputStream(
                from(asList(src1.duplicate(), src2.duplicate())).iterator())) {

            data = new byte[4];
            read = stream.read(data, 0, 4);
        }
        assertThat("Unexpected number of bytes read.", read, is(4));
        assertThat("Unexpected bytes read.", bytesToBuffer(data, read), equalTo(src1));
    }

    @Test
    void closeThenReadShouldBeInvalid() throws IOException {
        Buffer src = DEFAULT_RO_ALLOCATOR.fromAscii("1234");
        try (InputStream stream = new CloseableIteratorBufferAsInputStream(createIterator(src))) {
            stream.close();
            assertThrows(IOException.class, stream::read);
        }
    }

    @Test
    void singleByteRead() throws IOException {
        Buffer src = DEFAULT_RO_ALLOCATOR.fromAscii("1");
        try (InputStream stream = new CloseableIteratorBufferAsInputStream(createIterator(src))) {
            int read = stream.read();
            assertThat("Unexpected bytes read.", (char) read, equalTo('1'));
            assertThat("Bytes read after complete.", stream.read(), is(-1));
        }
    }

    @Test
    void singleByteReadWithingRange() throws IOException {
        singleByteReadWithingRange((byte) 0, 0);
        singleByteReadWithingRange((byte) 255, 255);
        singleByteReadWithingRange((byte) -1, 255);
        singleByteReadWithingRange((byte) -117, 139);
    }

    private static void singleByteReadWithingRange(byte actual, int expected) throws IOException {
        Buffer src = DEFAULT_ALLOCATOR.newBuffer(1)
                .writeByte(actual);
        try (InputStream stream = new CloseableIteratorBufferAsInputStream(createIterator(src))) {
            int read = stream.read();
            assertThat("Unexpected bytes read.", read, is(expected));
            assertThat("Bytes read after complete.", stream.read(), is(-1));
        }
    }

    @Test
    void singleByteReadWithEmptyIterable() throws IOException {
        Buffer src = DEFAULT_RO_ALLOCATOR.fromAscii("");
        try (InputStream stream = new CloseableIteratorBufferAsInputStream(createIterator(src))) {
            assertThat("Unexpected bytes read.", stream.read(), is(-1));
            // We have left over state across reads, do a second read to make sure we do not have such state.
            // Since, we only ever emit 1 item from the source,
            // we are sure that no other state will change after this read.
            assertThat("Unexpected bytes from second read.", stream.read(), is(-1));
        }
    }

    @Test
    void readWithEmptyIterable() throws IOException {
        Buffer src = DEFAULT_RO_ALLOCATOR.fromAscii("");
        try (InputStream stream = new CloseableIteratorBufferAsInputStream(createIterator(src))) {
            byte[] r = new byte[1];
            assertThat("Unexpected bytes read.", stream.read(r, 0, 1), is(-1));
            // We have left over state across reads, do a second read to make sure we do not have such state.
            // Since, we only ever emit 1 item from the source,
            // we are sure that no other state will change after this read.
            assertThat("Unexpected bytes from second read.", stream.read(r), is(-1));
        }
    }

    @Test
    void zeroLengthReadShouldBeValid() throws IOException {
        Buffer src = DEFAULT_RO_ALLOCATOR.fromAscii("1");
        try (InputStream stream = new CloseableIteratorBufferAsInputStream(createIterator(src))) {
            byte[] data = new byte[0];
            int read = stream.read(data, 0, 0);
            assertThat("Unexpected bytes read.", read, equalTo(0));
            assertThat("Bytes read after complete.", (char) stream.read(), equalTo('1'));
        }
    }

    @Test
    void checkAvailableReturnsCorrectlyWithPrefetch() throws IOException {
        Buffer src = DEFAULT_RO_ALLOCATOR.fromAscii("1234");
        try (InputStream stream = new CloseableIteratorBufferAsInputStream(createIterator(src))) {
            assertThat("Unexpected available return type.", stream.available(), is(0));
            byte[] data = new byte[2];
            int read = stream.read(data, 0, 2);
            assertThat("Unexpected number of bytes read.", read, is(2));
            assertThat("Unexpected bytes read.", bytesToBuffer(data, read), equalTo(src.slice(src.readerIndex(), 2)));
            assertThat("Unexpected available return type.", stream.available(), is(2));
        }
    }

    @Test
    void completionAndEmptyReadShouldIndicateEOF() throws IOException {
        Buffer src = DEFAULT_RO_ALLOCATOR.fromAscii("");
        int read;
        try (InputStream stream = new CloseableIteratorBufferAsInputStream(createIterator(src))) {
            byte[] data = new byte[32];
            read = stream.read(data, 0, 32);
        }
        assertThat("Unexpected bytes read.", read, equalTo(-1));
    }

    @Test
    void testNullAndEmptyIteratorValues() throws IOException {
        Buffer src1 = DEFAULT_RO_ALLOCATOR.fromAscii("hel");
        Buffer src2 = DEFAULT_RO_ALLOCATOR.fromAscii("");
        Buffer src3 = DEFAULT_RO_ALLOCATOR.fromAscii("lo!");
        String realStringData;
        byte[] data;
        try (InputStream stream = new CloseableIteratorBufferAsInputStream(
                from(asList(src1.duplicate(), null, src2, null, src3)).iterator())) {

            realStringData = "hello!";
            final int midWayPoint = 3;
            data = new byte[realStringData.length()];

            // Split the real data up into 2 chunks and send null/empty in between
            assertThat(stream.read(data, 0, midWayPoint), is(midWayPoint));

            // send the second chunk
            final int len = realStringData.length() - midWayPoint;
            assertThat(stream.read(data, midWayPoint, len), is(len));

            assertThat(data, is(realStringData.getBytes(US_ASCII)));
        }
    }

    private static CloseableIterator<Buffer> createIterator(Buffer src) {
        return from(singletonList(src.duplicate())).iterator();
    }

    private static Buffer bytesToBuffer(final byte[] data, final int length) {
        return DEFAULT_RO_ALLOCATOR.wrap(data, 0, length);
    }
}
