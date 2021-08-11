/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.encoding.api;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Publisher;

import static io.servicetalk.buffer.api.CharSequences.caseInsensitiveHashCode;
import static io.servicetalk.buffer.api.CharSequences.contentEqualsIgnoreCase;
import static io.servicetalk.buffer.api.CharSequences.newAsciiString;

/**
 * Default, always supported NOOP 'identity' {@link ContentCodec}.
 * @deprecated Will be removed along with {@link ContentCodec}.
 */
@Deprecated
final class IdentityContentCodec implements ContentCodec {

    private static final CharSequence NAME = newAsciiString("identity");
    private static final int HASH_CODE = caseInsensitiveHashCode(NAME);

    @Override
    public CharSequence name() {
        return NAME;
    }

    @Override
    public Buffer encode(final Buffer src, final BufferAllocator allocator) {
        return src;
    }

    @Override
    public Buffer decode(final Buffer src, final BufferAllocator allocator) {
        return src;
    }

    @Override
    public Publisher<Buffer> encode(final Publisher<Buffer> from, final BufferAllocator allocator) {
        return from;
    }

    @Override
    public Publisher<Buffer> decode(final Publisher<Buffer> from, final BufferAllocator allocator) {
        return from;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof ContentCodec)) {
            return false;
        }

        final ContentCodec that = (ContentCodec) o;
        return contentEqualsIgnoreCase(name(), that.name());
    }

    @Override
    public int hashCode() {
        return HASH_CODE;
    }
}
