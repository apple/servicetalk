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

abstract class DelegatingToBufferHttpPayloadWriter<T> implements HttpPayloadWriter<T> {

    protected final HttpPayloadWriter<Buffer> delegate;
    protected final BufferAllocator allocator;

    protected DelegatingToBufferHttpPayloadWriter(final HttpPayloadWriter<Buffer> delegate,
                                                  final BufferAllocator allocator) {
        this.delegate = delegate;
        this.allocator = allocator;
    }

    @Override
    public void flush() throws IOException {
        delegate.flush();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    @Override
    public void close(Throwable cause) throws IOException {
        delegate.close(cause);
    }

    @Override
    public HttpHeaders trailers() {
        return delegate.trailers();
    }
}
