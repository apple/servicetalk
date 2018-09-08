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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.serialization.api.DefaultSerializer;
import io.servicetalk.serialization.api.SerializationException;
import io.servicetalk.serialization.api.SerializationProvider;
import io.servicetalk.serialization.api.Serializer;
import io.servicetalk.serialization.api.TypeHolder;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_JSON;
import static io.servicetalk.http.api.HttpPayloadChunks.newPayloadChunk;

/**
 * Default implementation of {@link HttpSerializer} that uses a {@link Serializer} to do the actual
 * serialization/deserialization.
 */
public final class DefaultHttpSerializer implements HttpSerializer {

    private final Serializer serializer;
    private final Predicate<HttpMetaData> checkContentType;
    private final Consumer<HttpMetaData> addContentType;

    private DefaultHttpSerializer(final Serializer serializer, final Predicate<HttpMetaData> checkContentType,
                                  final Consumer<HttpMetaData> addContentType) {
        this.serializer = serializer;
        this.checkContentType = checkContentType;
        this.addContentType = addContentType;
    }

    @Override
    public <T> HttpRequest<HttpPayloadChunk> serialize(final HttpRequest<T> request,
                                                       final BufferAllocator allocator) {
        addContentType.accept(request);
        return request.transformPayloadBody(payload ->
                newPayloadChunk(serializer.serialize(payload, allocator)));
    }

    @Override
    public <T> HttpRequest<HttpPayloadChunk> serialize(final HttpRequest<T> request,
                                                       final BufferAllocator allocator,
                                                       final int bytesEstimate) {
        addContentType.accept(request);
        return request.transformPayloadBody(payload ->
                newPayloadChunk(serializer.serialize(payload, allocator, bytesEstimate)));
    }

    @Override
    public <T> HttpResponse<HttpPayloadChunk> serialize(final HttpResponse<T> response,
                                                        final BufferAllocator allocator) {
        addContentType.accept(response);
        return response.transformPayloadBody(payload ->
                newPayloadChunk(serializer.serialize(payload, allocator)));
    }

    @Override
    public <T> HttpResponse<HttpPayloadChunk> serialize(final HttpResponse<T> response,
                                                        final BufferAllocator allocator,
                                                        final int bytesEstimate) {
        addContentType.accept(response);
        return response.transformPayloadBody(payload ->
                newPayloadChunk(serializer.serialize(payload, allocator, bytesEstimate)));
    }

    @Override
    public <T> StreamingHttpRequest<HttpPayloadChunk> serialize(final StreamingHttpRequest<T> request, final BufferAllocator allocator,
                                                                final Class<T> type) {
        addContentType.accept(request);
        return request.transformPayloadBody(payload ->
                serializer.serialize(payload, allocator, type).map(HttpPayloadChunks::newPayloadChunk));
    }

    @Override
    public <T> StreamingHttpRequest<HttpPayloadChunk> serialize(final StreamingHttpRequest<T> request, final BufferAllocator allocator,
                                                                final TypeHolder<T> typeHolder) {
        addContentType.accept(request);
        return request.transformPayloadBody(payload ->
                serializer.serialize(payload, allocator, typeHolder)
                        .map(HttpPayloadChunks::newPayloadChunk));
    }

    @Override
    public <T> StreamingHttpRequest<HttpPayloadChunk> serialize(final StreamingHttpRequest<T> request, final BufferAllocator allocator,
                                                                final Class<T> type,
                                                                final IntUnaryOperator bytesEstimator) {
        addContentType.accept(request);
        return request.transformPayloadBody(payload ->
                serializer.serialize(payload, allocator, type, bytesEstimator)
                        .map(HttpPayloadChunks::newPayloadChunk));
    }

    @Override
    public <T> StreamingHttpRequest<HttpPayloadChunk> serialize(final StreamingHttpRequest<T> request, final BufferAllocator allocator,
                                                                final TypeHolder<T> typeHolder,
                                                                final IntUnaryOperator bytesEstimator) {
        addContentType.accept(request);
        return request.transformPayloadBody(payload ->
                serializer.serialize(payload, allocator, typeHolder, bytesEstimator)
                        .map(HttpPayloadChunks::newPayloadChunk));
    }

    @Override
    public <T> StreamingHttpResponse<HttpPayloadChunk> serialize(final StreamingHttpResponse<T> response, final BufferAllocator allocator,
                                                                 final Class<T> type) {
        addContentType.accept(response);
        return response.transformPayloadBody(payload -> serializer.serialize(payload, allocator, type)
                        .map(HttpPayloadChunks::newPayloadChunk));
    }

    @Override
    public <T> StreamingHttpResponse<HttpPayloadChunk> serialize(final StreamingHttpResponse<T> response, final BufferAllocator allocator,
                                                                 final TypeHolder<T> typeHolder) {
        addContentType.accept(response);
        return response.transformPayloadBody(payload ->
                serializer.serialize(payload, allocator, typeHolder)
                        .map(HttpPayloadChunks::newPayloadChunk));
    }

    @Override
    public <T> StreamingHttpResponse<HttpPayloadChunk> serialize(final StreamingHttpResponse<T> response, final BufferAllocator allocator,
                                                                 final Class<T> type,
                                                                 final IntUnaryOperator bytesEstimator) {
        addContentType.accept(response);
        return response.transformPayloadBody(payload ->
                serializer.serialize(payload, allocator, type, bytesEstimator)
                        .map(HttpPayloadChunks::newPayloadChunk));
    }

    @Override
    public <T> StreamingHttpResponse<HttpPayloadChunk> serialize(final StreamingHttpResponse<T> response, final BufferAllocator allocator,
                                                                 final TypeHolder<T> typeHolder,
                                                                 final IntUnaryOperator bytesEstimator) {
        addContentType.accept(response);
        return response.transformPayloadBody(payload ->
                serializer.serialize(payload, allocator, typeHolder, bytesEstimator)
                        .map(HttpPayloadChunks::newPayloadChunk));
    }

    @Override
    public <T> BlockingStreamingHttpRequest<HttpPayloadChunk> serialize(final BlockingStreamingHttpRequest<T> request,
                                                                        final BufferAllocator allocator, final Class<T> type) {
        addContentType.accept(request);
        return request.transformPayloadBody(payload ->
                toPayloadChunkIterable(serializer.serialize(payload, allocator, type)));
    }

    @Override
    public <T> BlockingStreamingHttpRequest<HttpPayloadChunk> serialize(final BlockingStreamingHttpRequest<T> request,
                                                                        final BufferAllocator allocator,
                                                                        final TypeHolder<T> typeHolder) {
        addContentType.accept(request);
        return request.transformPayloadBody(payload ->
                toPayloadChunkIterable(serializer.serialize(payload, allocator, typeHolder)));
    }

    @Override
    public <T> BlockingStreamingHttpRequest<HttpPayloadChunk> serialize(final BlockingStreamingHttpRequest<T> request,
                                                                        final BufferAllocator allocator, final Class<T> type,
                                                                        final IntUnaryOperator bytesEstimator) {
        addContentType.accept(request);
        return request.transformPayloadBody(payload ->
                toPayloadChunkIterable(serializer.serialize(payload, allocator, type, bytesEstimator)));
    }

    @Override
    public <T> BlockingStreamingHttpRequest<HttpPayloadChunk> serialize(final BlockingStreamingHttpRequest<T> request,
                                                                        final BufferAllocator allocator, final TypeHolder<T> typeHolder,
                                                                        final IntUnaryOperator bytesEstimator) {
        addContentType.accept(request);
        return request.transformPayloadBody(payload ->
                toPayloadChunkIterable(serializer.serialize(payload, allocator, typeHolder, bytesEstimator
                )));
    }

    @Override
    public <T> BlockingStreamingHttpResponse<HttpPayloadChunk> serialize(final BlockingStreamingHttpResponse<T> response,
                                                                         final BufferAllocator allocator, final Class<T> type) {
        addContentType.accept(response);
        return response.transformPayloadBody(payload ->
                toPayloadChunkIterable(serializer.serialize(payload, allocator, type)));
    }

    @Override
    public <T> BlockingStreamingHttpResponse<HttpPayloadChunk> serialize(final BlockingStreamingHttpResponse<T> response,
                                                                         final BufferAllocator allocator,
                                                                         final TypeHolder<T> typeHolder) {
        addContentType.accept(response);
        return response.transformPayloadBody(payload ->
                toPayloadChunkIterable(serializer.serialize(payload, allocator, typeHolder)));
    }

    @Override
    public <T> BlockingStreamingHttpResponse<HttpPayloadChunk> serialize(final BlockingStreamingHttpResponse<T> response,
                                                                         final BufferAllocator allocator, final Class<T> type,
                                                                         final IntUnaryOperator bytesEstimator) {
        addContentType.accept(response);
        return response.transformPayloadBody(payload ->
                toPayloadChunkIterable(serializer.serialize(payload, allocator, type, bytesEstimator)));
    }

    @Override
    public <T> BlockingStreamingHttpResponse<HttpPayloadChunk> serialize(final BlockingStreamingHttpResponse<T> response,
                                                                         final BufferAllocator allocator,
                                                                         final TypeHolder<T> typeHolder,
                                                                         final IntUnaryOperator bytesEstimator) {
        addContentType.accept(response);
        return response.transformPayloadBody(payload ->
                toPayloadChunkIterable(serializer.serialize(payload, allocator, typeHolder, bytesEstimator
                )));
    }

    @Override
    public <T> HttpRequest<T> deserialize(final HttpRequest<HttpPayloadChunk> request,
                                          final Class<T> type) {
        checkContentType(request);
        return request.transformPayloadBody(chunk ->
                serializer.deserializeAggregatedSingle(request.getPayloadBody().getContent(), type));
    }

    @Override
    public <T> HttpRequest<T> deserialize(final HttpRequest<HttpPayloadChunk> request,
                                          final TypeHolder<T> typeHolder) {
        checkContentType(request);
        return request.transformPayloadBody(chunk ->
                serializer.deserializeAggregatedSingle(request.getPayloadBody().getContent(), typeHolder));
    }

    @Override
    public <T> HttpResponse<T> deserialize(final HttpResponse<HttpPayloadChunk> response,
                                           final Class<T> type) {
        checkContentType(response);
        return response.transformPayloadBody(chunk ->
                serializer.deserializeAggregatedSingle(response.getPayloadBody().getContent(), type));
    }

    @Override
    public <T> HttpResponse<T> deserialize(final HttpResponse<HttpPayloadChunk> response,
                                           final TypeHolder<T> typeHolder) {
        checkContentType(response);
        return response.transformPayloadBody(chunk ->
                serializer.deserializeAggregatedSingle(response.getPayloadBody().getContent(), typeHolder));
    }

    @Override
    public <T> StreamingHttpRequest<T> deserialize(final StreamingHttpRequest<HttpPayloadChunk> request, final Class<T> type) {
        checkContentType(request);
        return request.transformPayloadBody(payload ->
                serializer.deserialize(payload.map(HttpPayloadChunk::getContent), type));
    }

    @Override
    public <T> StreamingHttpRequest<T> deserialize(final StreamingHttpRequest<HttpPayloadChunk> request, final TypeHolder<T> typeHolder) {
        checkContentType(request);
        return request.transformPayloadBody(payload ->
                serializer.deserialize(payload.map(HttpPayloadChunk::getContent), typeHolder));
    }

    @Override
    public <T> StreamingHttpResponse<T> deserialize(final StreamingHttpResponse<HttpPayloadChunk> response, final Class<T> type) {
        checkContentType(response);
        return response.transformPayloadBody(payload ->
                serializer.deserialize(payload.map(HttpPayloadChunk::getContent), type));
    }

    @Override
    public <T> StreamingHttpResponse<T> deserialize(final StreamingHttpResponse<HttpPayloadChunk> response,
                                                    final TypeHolder<T> typeHolder) {
        checkContentType(response);
        return response.transformPayloadBody(payload ->
                serializer.deserialize(payload.map(HttpPayloadChunk::getContent), typeHolder));
    }

    @Override
    public <T> BlockingStreamingHttpRequest<T> deserialize(final BlockingStreamingHttpRequest<HttpPayloadChunk> request,
                                                           final Class<T> type) {
        checkContentType(request);
        return request.transformPayloadBody(payload ->
                serializer.deserialize(toBufferIterable(request.getPayloadBody()), type));
    }

    @Override
    public <T> BlockingStreamingHttpRequest<T> deserialize(final BlockingStreamingHttpRequest<HttpPayloadChunk> request,
                                                           final TypeHolder<T> typeHolder) {
        checkContentType(request);
        return request.transformPayloadBody(payload ->
                serializer.deserialize(toBufferIterable(request.getPayloadBody()), typeHolder));
    }

    @Override
    public <T> BlockingStreamingHttpResponse<T> deserialize(final BlockingStreamingHttpResponse<HttpPayloadChunk> response,
                                                            final Class<T> type) {
        checkContentType(response);
        return response.transformPayloadBody(payload ->
                serializer.deserialize(toBufferIterable(response.getPayloadBody()), type));
    }

    @Override
    public <T> BlockingStreamingHttpResponse<T> deserialize(final BlockingStreamingHttpResponse<HttpPayloadChunk> response,
                                                            final TypeHolder<T> typeHolder) {
        checkContentType(response);
        return response.transformPayloadBody(payload ->
                serializer.deserialize(toBufferIterable(response.getPayloadBody()), typeHolder));
    }

    /**
     * Creates a new {@link HttpSerializer} that could serialize and deserialize JSON using the passed
     * {@link Serializer}. The returned {@link HttpSerializer} adds a {@link HttpHeaderNames#CONTENT_TYPE} header with
     * value {@link HttpHeaderValues#APPLICATION_JSON} while serialization and expects the same header name and value
     * while deserialization. While deserialization, if the expected header is not present, then deserialization will
     * fail with {@link SerializationException}.
     *
     * @param serializer {@link Serializer} that has the capability to serialize and deserialize JSON.
     * @return {@link HttpSerializer} that could serialize and deserialize JSON.
     */
    public static HttpSerializer forJson(Serializer serializer) {
        return new DefaultHttpSerializer(serializer,
                metaData -> metaData.getHeaders().contains(CONTENT_TYPE, APPLICATION_JSON),
                metaData -> metaData.getHeaders().set(CONTENT_TYPE, APPLICATION_JSON));
    }

    /**
     * Creates a new {@link HttpSerializer} that could serialize and deserialize JSON using the passed
     * {@link SerializationProvider}. The returned {@link HttpSerializer} adds a {@link HttpHeaderNames#CONTENT_TYPE}
     * header with value {@link HttpHeaderValues#APPLICATION_JSON} while serialization and expects the same header name
     * and value while deserialization. While deserialization, if the expected header is not present, then
     * deserialization will fail with {@link SerializationException}.
     *
     * @param serializationProvider {@link SerializationProvider} that has the capability to serialize and deserialize
     * JSON.
     * @return {@link HttpSerializer} that could serialize and deserialize JSON.
     */
    public static HttpSerializer forJson(SerializationProvider serializationProvider) {
        return forJson(new DefaultSerializer(serializationProvider));
    }

    /**
     * Creates a new {@link HttpSerializer} that could serialize and deserialize JSON using the passed
     * {@link Serializer}. The returned {@link HttpSerializer} adds a {@link HttpHeaderNames#CONTENT_TYPE} header with
     * value {@link HttpHeaderValues#APPLICATION_JSON} while serialization and expects the same header name and value
     * while deserialization. While deserialization, if the expected header is not present, then deserialization will
     * fail with {@link SerializationException}.
     *
     * @param serializer {@link Serializer} that has the capability to serialize and deserialize JSON.
     * @param checkContentType A {@link Predicate} that checks whether the passed {@link HttpMetaData} is valid for
     * deserialization. Typically, this involves checking the {@link HttpHeaderNames#CONTENT_TYPE} header.
     * @param addContentType A {@link Consumer} that adds relevant headers to the passed {@link HttpMetaData} that will
     * match the serialized payload. Typically, this involves adding a {@link HttpHeaderNames#CONTENT_TYPE} header.
     * @return {@link HttpSerializer} that could serialize and deserialize JSON.
     */
    public static HttpSerializer forContentType(Serializer serializer, Predicate<HttpMetaData> checkContentType,
                                                Consumer<HttpMetaData> addContentType) {
        return new DefaultHttpSerializer(serializer, checkContentType, addContentType);
    }

    /**
     * Creates a new {@link HttpSerializer} that could serialize and deserialize JSON using the passed
     * {@link SerializationProvider}. The returned {@link HttpSerializer} adds a {@link HttpHeaderNames#CONTENT_TYPE}
     * header with value {@link HttpHeaderValues#APPLICATION_JSON} while serialization and expects the same header name
     * and value while deserialization. While deserialization, if the expected header is not present, then
     * deserialization will fail with {@link SerializationException}.
     *
     * @param serializationProvider {@link SerializationProvider} that has the capability to serialize and deserialize
     * JSON.
     * @param checkContentType A {@link Predicate} that checks whether the passed {@link HttpMetaData} is valid for
     * deserialization. Typically, this involves checking the {@link HttpHeaderNames#CONTENT_TYPE} header.
     * @param addContentType A {@link Consumer} that adds relevant headers to the passed {@link HttpMetaData} that will
     * match the serialized payload. Typically, this involves adding a {@link HttpHeaderNames#CONTENT_TYPE} header.
     * @return {@link HttpSerializer} that could serialize and deserialize JSON.
     */
    public static HttpSerializer forContentType(SerializationProvider serializationProvider,
                                                Predicate<HttpMetaData> checkContentType,
                                                Consumer<HttpMetaData> addContentType) {
        return forContentType(new DefaultSerializer(serializationProvider), checkContentType, addContentType);
    }

    private BlockingIterable<HttpPayloadChunk> toPayloadChunkIterable(final BlockingIterable<Buffer> buffers) {
        return () -> {
            final BlockingIterator<Buffer> iterator = buffers.iterator();
            return new BlockingIterator<HttpPayloadChunk>() {
                @Override
                public boolean hasNext(final long timeout, final TimeUnit unit) throws TimeoutException {
                    return iterator.hasNext(timeout, unit);
                }

                @Override
                public HttpPayloadChunk next(final long timeout, final TimeUnit unit) throws TimeoutException {
                    final Buffer next = iterator.next(timeout, unit);
                    return toPayloadChunk(next);
                }

                @Override
                public void close() throws Exception {
                    iterator.close();
                }

                @Override
                public boolean hasNext() {
                    return iterator.hasNext();
                }

                @Override
                public HttpPayloadChunk next() {
                    return toPayloadChunk(iterator.next());
                }

                @Nullable
                private HttpPayloadChunk toPayloadChunk(@Nullable final Buffer next) {
                    return next == null ? null : newPayloadChunk(next);
                }
            };
        };
    }

    private BlockingIterable<Buffer> toBufferIterable(final BlockingIterable<HttpPayloadChunk> buffers) {
        return () -> {
            final BlockingIterator<HttpPayloadChunk> iterator = buffers.iterator();
            return new BlockingIterator<Buffer>() {
                @Override
                public boolean hasNext(final long timeout, final TimeUnit unit) throws TimeoutException {
                    return iterator.hasNext(timeout, unit);
                }

                @Override
                public Buffer next(final long timeout, final TimeUnit unit) throws TimeoutException {
                    final HttpPayloadChunk next = iterator.next(timeout, unit);
                    return toBuffer(next);
                }

                @Override
                public void close() throws Exception {
                    iterator.close();
                }

                @Override
                public boolean hasNext() {
                    return iterator.hasNext();
                }

                @Override
                public Buffer next() {
                    return toBuffer(iterator.next());
                }

                @Nullable
                private Buffer toBuffer(@Nullable final HttpPayloadChunk chunk) {
                    return chunk == null ? null : chunk.getContent();
                }
            };
        };
    }

    private void checkContentType(final HttpMetaData metaData) {
        if (!checkContentType.test(metaData)) {
            throw new SerializationException("Unexpected metadata, can not deserialize. Metadata: "
                    + metaData.toString());
        }
    }
}
