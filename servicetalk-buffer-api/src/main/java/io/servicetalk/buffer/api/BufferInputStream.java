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
package io.servicetalk.buffer.api;

import java.io.InputStream;

import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;

final class BufferInputStream extends InputStream {
    private final Buffer buffer;

    BufferInputStream(Buffer buffer) {
        this.buffer = requireNonNull(buffer);
    }

    @Override
    public int read() {
        if (buffer.readableBytes() == 0) {
            return -1;
        }
        return buffer.readByte() & 0xff;
    }

    @Override
    public int read(byte[] b, int off, int len) {
        int readableBytes = buffer.readableBytes();
        if (readableBytes == 0) {
            return -1;
        }
        int bytes = min(readableBytes, len);
        buffer.readBytes(b, off, bytes);
        return bytes;
    }

    @Override
    public long skip(long n) {
        int skipped = min(buffer.readableBytes(), (int) min(Integer.MAX_VALUE, n));
        if (skipped <= 0) {
            return 0;
        }

        buffer.skipBytes(skipped);
        return skipped;
    }

    @Override
    public int available() {
        return buffer.readableBytes();
    }
}
