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

import io.servicetalk.buffer.BufferAllocator;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.HttpPayloadChunk;

import org.reactivestreams.Subscriber;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

import static io.servicetalk.http.api.HttpPayloadChunks.newPayloadChunk;
import static java.lang.System.arraycopy;
import static java.util.Objects.requireNonNull;

final class DummyInputStreamChunkPublisher extends Publisher<HttpPayloadChunk> implements Closeable {
    private final Publisher<HttpPayloadChunk> source;

    private DummyInputStreamChunkPublisher(final Publisher<HttpPayloadChunk> source) {
        this.source = source;
    }

    static DummyInputStreamChunkPublisher asCloseableChunkPublisher(final InputStream is,
                                                                    final BufferAllocator allocator) {
        requireNonNull(is);
        requireNonNull(allocator);

        return new DummyInputStreamChunkPublisher(
                Publisher.from(() -> new DummyInputStreamIterator(is, allocator))
                        .doAfterFinally(() -> {
                            try {
                                is.close();
                            } catch (final IOException e) {
                                // Not much we can do at this point
                            }
                        }));
    }

    @Override
    protected void handleSubscribe(final Subscriber<? super HttpPayloadChunk> subscriber) {
        source.subscribe(subscriber);
    }

    @Override
    public void close() {
        // NOOP - Closeable is implemented to prevent Jersey to close the source inputstream
    }

    private static final class DummyInputStreamIterator implements Iterator<HttpPayloadChunk> {
        private final InputStream is;
        private final BufferAllocator allocator;
        private final byte[] buffer = new byte[4096];
        private int nextAvailable;

        private DummyInputStreamIterator(final InputStream is, final BufferAllocator allocator) {
            this.is = requireNonNull(is);
            this.allocator = requireNonNull(allocator);
            fetchNext();
        }

        @Override
        public boolean hasNext() {
            return nextAvailable > -1;
        }

        @Override
        public HttpPayloadChunk next() {
            try {
                final byte[] next = new byte[nextAvailable];
                arraycopy(buffer, 0, next, 0, nextAvailable);
                return newPayloadChunk(allocator.wrap(next));
            } finally {
                fetchNext();
            }
        }

        private void fetchNext() {
            try {
                nextAvailable = is.read(buffer);
            } catch (final IOException e) {
                throw new RuntimeException("Failed to read: " + is, e);
            }
        }
    }
}
