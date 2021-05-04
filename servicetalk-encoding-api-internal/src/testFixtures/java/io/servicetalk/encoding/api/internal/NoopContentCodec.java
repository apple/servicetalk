/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

package io.servicetalk.encoding.api.internal;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.encoding.api.ContentCodec;

public class NoopContentCodec implements ContentCodec {

    private final CharSequence name;

    public NoopContentCodec(CharSequence name) {
        this.name = name;
    }

    @Override
    public CharSequence name() {
        return name;
    }

    @Override
    public Buffer encode(Buffer src, BufferAllocator allocator) {
        return src;
    }

    @Override
    public Buffer encode(Buffer src, int offset, int length, BufferAllocator allocator) {
        return src;
    }

    @Override
    public Buffer decode(Buffer src, BufferAllocator allocator) {
        return src;
    }

    @Override
    public Buffer decode(Buffer src, int offset, int length, BufferAllocator allocator) {
        return src;
    }

    @Override
    public Publisher<Buffer> encode(Publisher<Buffer> from, BufferAllocator allocator) {
        return from;
    }

    @Override
    public Publisher<Buffer> decode(Publisher<Buffer> from, BufferAllocator allocator) {
        return from;
    }
}
