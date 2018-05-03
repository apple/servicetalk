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
package io.servicetalk.http.api;

import io.servicetalk.buffer.Buffer;
import io.servicetalk.buffer.BufferAllocator;
import io.servicetalk.buffer.CompositeBuffer;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;

import javax.annotation.Nullable;

/**
 * Factory methods for creating {@link HttpPayloadChunk}s.
 */
public final class HttpPayloadChunks {

    private HttpPayloadChunks() {
        // No instances.
    }

    /**
     * Create an {@link HttpPayloadChunk} instance with the specified {@code content}.
     *
     * @param content the content.
     * @return a new {@link HttpPayloadChunk}.
     */
    public static HttpPayloadChunk newPayloadChunk(final Buffer content) {
        return new DefaultHttpPayloadChunk(content);
    }

    /**
     * Create an {@link HttpPayloadChunk} instance with the specified {@code content}.
     *
     * @param content the content.
     * @param trailers the <a href="https://tools.ietf.org/html/rfc7230#section-4.4">trailer headers</a>.
     * @return a new {@link LastHttpPayloadChunk}.
     */
    public static LastHttpPayloadChunk newLastPayloadChunk(final Buffer content, final HttpHeaders trailers) {
        return new DefaultLastHttpPayloadChunk(content, trailers);
    }

    /**
     * Aggregates a {@link Publisher} of {@link HttpPayloadChunk} into a {@link Single} of {@link LastHttpPayloadChunk}.
     *
     * @param content {@link Publisher} to be aggregated.
     * @param allocator {@link BufferAllocator} to create a {@link Buffer} for content accumulation.
     * @return A {@link Single} which contains the aggregated HTTP payload and optional trailers.
     */
    public static Single<LastHttpPayloadChunk> aggregateChunks(final Publisher<HttpPayloadChunk> content,
                                                               final BufferAllocator allocator) {
        return content.reduce(() -> newAggregatingChunk(allocator), (aggregatingChunk, chunk) -> {
            aggregatingChunk.addChunk(chunk);
            return aggregatingChunk;
        }).map(aggregatingChunk -> aggregatingChunk);
    }

    /**
     * Aggregates an {@link Iterable} of {@link HttpPayloadChunk} into a {@link LastHttpPayloadChunk}.
     *
     * @param content {@link Iterable} to be aggregated.
     * @param allocator {@link BufferAllocator} to create a {@link Buffer} for content accumulation.
     * @return Aggregated {@link LastHttpPayloadChunk} which contains the aggregated HTTP payload and optional trailers.
     */
    public static LastHttpPayloadChunk aggregateChunks(final Iterable<HttpPayloadChunk> content,
                                                       final BufferAllocator allocator) {
        AggregatingChunk aggregatingChunk = newAggregatingChunk(allocator);
        for (HttpPayloadChunk chunk : content) {
            aggregatingChunk.addChunk(chunk);
        }
        return aggregatingChunk;
    }

    /**
     * Creates a new instance of {@link AggregatingChunk} to be used to aggregate an HTTP payload {@link Publisher}.
     *
     * @param allocator {@link BufferAllocator} to create a {@link Buffer} for content accumulation.
     *
     * @return A new instance of {@link AggregatingChunk}.
     */
    public static AggregatingChunk newAggregatingChunk(final BufferAllocator allocator) {
        return newAggregatingChunk(Integer.MAX_VALUE, allocator);
    }

    /**
     * Creates a new instance of {@link AggregatingChunk} to be used to aggregate an HTTP payload {@link Publisher}.
     *
     * @param allocator {@link BufferAllocator} to create a {@link Buffer} for content accumulation.
     * @param trailerFactory {@link HttpHeadersFactory} to create a trailer for the returned {@link AggregatingChunk}.
     * This will be used to create a trailer, only if {@link AggregatingChunk#getTrailers()} is called before
     * aggregation is completed.
     *
     * @return A new instance of {@link AggregatingChunk}.
     */
    public static AggregatingChunk newAggregatingChunk(final BufferAllocator allocator,
                                                       final HttpHeadersFactory trailerFactory) {
        return newAggregatingChunk(Integer.MAX_VALUE, trailerFactory, allocator);
    }

    /**
     * Creates a new instance of {@link AggregatingChunk} to be used to aggregate an HTTP payload {@link Publisher}.
     *
     * @param maxChunksCount Maximum expected {@link HttpPayloadChunk}s.
     * @param allocator {@link BufferAllocator} to create a {@link Buffer} for content accumulation.
     *
     * @return A new instance of {@link AggregatingChunk}.
     */
    public static AggregatingChunk newAggregatingChunk(final int maxChunksCount, final BufferAllocator allocator) {
        return newAggregatingChunk(maxChunksCount, DefaultHttpHeadersFactory.INSTANCE, allocator);
    }

    /**
     * Creates a new instance of {@link AggregatingChunk} to be used to aggregate an HTTP payload {@link Publisher}.
     *
     * @param maxChunksCount Maximum expected {@link HttpPayloadChunk}s.
     * @param trailerFactory {@link HttpHeadersFactory} to create a trailer for the returned {@link AggregatingChunk}.
     * This will be used to create a trailer, only if {@link AggregatingChunk#getTrailers()} is called before
     * aggregation is completed.
     * @param allocator {@link BufferAllocator} to create a {@link Buffer} for content accumulation.
     *
     * @return A new instance of {@link AggregatingChunk}.
     */
    public static AggregatingChunk newAggregatingChunk(final int maxChunksCount,
                                                       final HttpHeadersFactory trailerFactory,
                                                       final BufferAllocator allocator) {
        return new AggregatingChunkImpl(allocator, maxChunksCount, trailerFactory);
    }

    /**
     * A special {@link LastHttpPayloadChunk} that allows to incrementally aggregate multiple {@link HttpPayloadChunk}.
     *
     * <h2>Incremental aggregation</h2>
     * This interface expects to incrementally aggregate {@link HttpPayloadChunk}s from a stream.
     * Data from {@link LastHttpPayloadChunk}, viz., {@link #getContent()} and {@link #getTrailers()} MAY be different
     * before the aggregation is completed.
     */
    public interface AggregatingChunk extends LastHttpPayloadChunk {

        /**
         * Adds an {@link HttpPayloadChunk} to this {@link AggregatingChunk}.
         * If this {@link HttpPayloadChunk} is a {@link LastHttpPayloadChunk} then the trailers will be available via
         * {@link #getTrailers()} after this method returns. Otherwise, the payload as returned by
         * {@link HttpPayloadChunk#getContent()} is added to this {@link AggregatingChunk}.
         *
         * @param chunk {@link HttpPayloadChunk} to add.
         */
        void addChunk(HttpPayloadChunk chunk);
    }

    private static class AggregatingChunkImpl implements AggregatingChunk {

        private final HttpHeadersFactory trailerFactory;
        private final CompositeBuffer buffer;
        @Nullable
        private HttpHeaders trailers;

        AggregatingChunkImpl(final BufferAllocator allocator, final int maxChunksCount,
                             HttpHeadersFactory trailerFactory) {
            this.trailerFactory = trailerFactory;
            buffer = allocator.newCompositeBuffer(maxChunksCount);
        }

        @Override
        public void addChunk(final HttpPayloadChunk chunk) {
            if (chunk instanceof LastHttpPayloadChunk) {
                if (trailers == null) {
                    trailers = ((LastHttpPayloadChunk) chunk).getTrailers();
                } else {
                    trailers.add(((LastHttpPayloadChunk) chunk).getTrailers());
                }
            }
            buffer.addBuffer(chunk.getContent(), true);
        }

        @Override
        public HttpHeaders getTrailers() {
            if (trailers == null) {
                trailers = trailerFactory.newEmptyTrailers();
            }
            return trailers;
        }

        @Override
        public LastHttpPayloadChunk duplicate() {
            return newLastPayloadChunk(buffer.duplicate(), getTrailers());
        }

        @Override
        public LastHttpPayloadChunk replace(final Buffer content) {
            return newLastPayloadChunk(content, getTrailers());
        }

        @Override
        public Buffer getContent() {
            return buffer;
        }
    }
}
