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

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;
import javax.annotation.Nullable;

abstract class ZipGrpcMessageCodec implements GrpcMessageCodec {

    private static final int ONE_KB = 1 << 10;

    abstract DeflaterOutputStream newCodecOutputStream(OutputStream out) throws IOException;

    abstract InflaterInputStream newCodecInputStream(InputStream in) throws IOException;

    @Override
    public final ByteBuffer encode(final ByteBuffer src, final BufferAllocator allocator) {

        final Buffer buffer = allocator.newBuffer(ONE_KB);
        DeflaterOutputStream output = null;
        try {
            output = newCodecOutputStream(Buffer.asOutputStream(buffer));
            output.write(src.array(), src.arrayOffset() + src.position(), src.remaining());
            output.finish();
            // Mark original as consumed
            src.position(src.position() + src.remaining());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            closeQuietly(output);
        }

        return buffer.toNioBuffer();
    }

    @Override
    public final ByteBuffer decode(final ByteBuffer src, final BufferAllocator allocator) {
        final Buffer buffer = allocator.newBuffer(ONE_KB);
        InflaterInputStream input = null;
        try {
            input = newCodecInputStream(new ByteArrayInputStream(src.array(),
                    src.arrayOffset() + src.position(), src.remaining()));

            int read = buffer.setBytesUntilEndStream(0, input, ONE_KB);
            buffer.writerIndex(read);

            // Mark original as consumed
            src.position(src.position() + src.remaining());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            closeQuietly(input);
        }

        return buffer.toNioBuffer();
    }

    private void closeQuietly(@Nullable final Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException e) {
            // Ignore
        }
    }
}
