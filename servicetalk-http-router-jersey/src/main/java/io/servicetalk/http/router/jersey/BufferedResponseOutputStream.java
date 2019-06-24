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
package io.servicetalk.http.router.jersey;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;

import java.io.IOException;
import java.io.OutputStream;
import java.util.function.Consumer;

final class BufferedResponseOutputStream extends OutputStream {
    private final BufferAllocator allocator;
    private final Consumer<Buffer> responseBodyConsumer;

    BufferedResponseOutputStream(final BufferAllocator allocator, final Consumer<Buffer> responseBodyConsumer) {
        this.allocator = allocator;
        this.responseBodyConsumer = responseBodyConsumer;
    }

    @Override
    public void write(final int b) {
        throw new UnsupportedOperationException("Only intended to be used with buffered responses");
    }

    /*
     * No need to protect against multiple call because this is used only in the case when a single byte[] has been
     * used to buffer the response body.
     */
    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        // Do not wrap b in case Jersey decides to reuse it
        final Buffer buf = allocator.newBuffer(len);
        buf.writeBytes(b, off, len);
        responseBodyConsumer.accept(buf);
    }
}
