/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.HttpPayloadChunk;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.annotation.Nullable;

// An InputStream built around a Publisher<HttpPayloadChunk>, which can either be read OIO style or provide its wrapped
// publisher. Not threadsafe and intended to be used only in this module, where no concurrency occurs between read and
// getChunkPublisher.
class ChunkPublisherInputStream extends FilterInputStream {
    private static final InputStream EMPTY_INPUT_STREAM = new InputStream() {
        @Override
        public int read() {
            return -1;
        }
    };

    private final Publisher<HttpPayloadChunk> publisher;
    private final int queueCapacity;

    ChunkPublisherInputStream(final Publisher<HttpPayloadChunk> publisher, final int queueCapacity) {
        super(EMPTY_INPUT_STREAM);
        this.publisher = publisher;
        this.queueCapacity = queueCapacity;
    }

    public Publisher<HttpPayloadChunk> getChunkPublisher() {
        if (in != EMPTY_INPUT_STREAM) {
            throw new IllegalStateException("Publisher is being consumed via InputStream");
        }
        return publisher;
    }

    @Override
    public int read() throws IOException {
        publisherToInputStream();
        return in.read();
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        publisherToInputStream();
        return in.read(b, off, len);
    }

    private void publisherToInputStream() {
        if (in == EMPTY_INPUT_STREAM) {
            in = publisher.toInputStream(ChunkPublisherInputStream::getBytes, queueCapacity);
        }
    }

    @Nullable
    private static byte[] getBytes(final HttpPayloadChunk chunk) {
        final Buffer content = chunk.getContent();
        final int readableBytes = content.getReadableBytes();

        if (readableBytes == 0) {
            return null;
        }

        if (content.hasArray() && content.getArrayOffset() == 0 && content.getArray().length == readableBytes) {
            return content.getArray();
        }

        final byte[] bytes = new byte[readableBytes];
        content.readBytes(bytes);
        return bytes;
    }
}
