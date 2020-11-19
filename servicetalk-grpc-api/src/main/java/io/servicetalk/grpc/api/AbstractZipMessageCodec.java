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
package io.servicetalk.grpc.api;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.Buffer.asInputStream;
import static io.servicetalk.buffer.api.Buffer.asOutputStream;
import static java.lang.Math.min;

abstract class AbstractZipMessageCodec implements MessageCodec {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractZipMessageCodec.class);
    protected static final int ONE_KB = 1 << 10;

    abstract DeflaterOutputStream newDeflaterOutputStream(OutputStream out) throws IOException;

    abstract InflaterInputStream newInflaterInputStream(InputStream in) throws IOException;

    @Override
    public final Buffer encode(final Buffer src, final int offset, final int length, final BufferAllocator allocator) {
        final Buffer dst = allocator.newBuffer(ONE_KB);
        DeflaterOutputStream output = null;
        try {
            output = newDeflaterOutputStream(asOutputStream(dst));

            if (src.hasArray()) {
                output.write(src.array(), offset, length);
            } else {
                while (src.readableBytes() > 0) {
                    byte[] onHeap = new byte[min(src.readableBytes(), ONE_KB)];
                    src.readBytes(onHeap);
                    output.write(onHeap);
                }
            }

            output.finish();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            closeQuietly(output);
        }

        return dst;
    }

    @Override
    public final Buffer decode(final Buffer src, final int offset, final int length, final BufferAllocator allocator) {
        final Buffer dst = allocator.newBuffer(ONE_KB);
        InflaterInputStream input = null;
        try {
            input = newInflaterInputStream(asInputStream(src));

            int read = dst.setBytesUntilEndStream(0, input, ONE_KB);
            dst.writerIndex(read);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            closeQuietly(input);
        }

        return dst;
    }

    private void closeQuietly(@Nullable final Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException e) {
            LOGGER.error("Unexpected IO exception while closing buffer streams", e);
        }
    }
}
