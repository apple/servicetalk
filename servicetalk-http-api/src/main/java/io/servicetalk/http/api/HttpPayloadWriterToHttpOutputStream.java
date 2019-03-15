/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.api;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;

import java.io.IOException;

final class HttpPayloadWriterToHttpOutputStream extends HttpOutputStream {

    private final HttpPayloadWriter<Buffer> writer;
    private final BufferAllocator allocator;

    HttpPayloadWriterToHttpOutputStream(final HttpPayloadWriter<Buffer> writer, final BufferAllocator allocator) {
        this.writer = writer;
        this.allocator = allocator;
    }

    @Override
    public void write(final int b) throws IOException {
        writer.write(allocator.newBuffer(1).writeByte(b));
    }

    @Override
    public void write(final byte[] b) throws IOException {
        writer.write(allocator.wrap(b));
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

        writer.write(allocator.wrap(b, off, len));
    }

    @Override
    public void flush() throws IOException {
        writer.flush();
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }

    @Override
    public HttpHeaders trailers() {
        return writer.trailers();
    }
}
