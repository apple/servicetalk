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
package io.servicetalk.http.router.jersey.internal;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.transport.api.IoThreadFactory;

import org.glassfish.jersey.message.internal.EntityInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;
import static org.glassfish.jersey.message.internal.ReaderInterceptorExecutor.closeableInputStream;

/**
 * An {@link InputStream} built around a {@link Publisher Publisher&lt;Buffer&gt;}, which can either be read
 * OIO style or provide its wrapped {@link Publisher}. This allows us to provide JAX-RS with an {@link InputStream}
 * and also short-circuit its usage when our code can directly deal with
 * the {@link Publisher Publisher&lt;Buffer&gt;} it wraps.
 * <p>
 * Not threadsafe and intended to be used internally only, where no concurrency occurs
 * between {@link BufferPublisherInputStream#read()}, {@link BufferPublisherInputStream#read(byte[], int, int)}
 * and {@link BufferPublisherInputStream#bufferPublisher(boolean)}.
 */
public final class BufferPublisherInputStream extends InputStream {
    private static final byte[] EMPTY_ARRAY = new byte[0];
    private static final InputStream EMPTY_INPUT_STREAM = new InputStream() {
        @Override
        public int read() {
            return -1;
        }
    };

    private InputStream inputStream;
    private Publisher<Buffer> publisher;
    private final int queueCapacity;
    // Curried applyAggregationSizeLimit (identity() when no limit applies); only aggregating readers pass it through.
    private final UnaryOperator<Publisher<Buffer>> aggregationSizeLimiter;

    /**
     * Creates a new {@link BufferPublisherInputStream} instance with no aggregation size limit.
     *
     * @param publisher the {@link Publisher Publisher&lt;Buffer&gt;} to read from.
     * @param queueCapacity the capacity hint for the intermediary queue that stores items.
     * @deprecated Use {@link #BufferPublisherInputStream(Publisher, int, UnaryOperator)} to enforce an aggregation size
     * limit; this overload applies none.
     */
    @Deprecated
    public BufferPublisherInputStream(final Publisher<Buffer> publisher, final int queueCapacity) {
        this(publisher, queueCapacity, UnaryOperator.identity());
    }

    /**
     * Creates a new {@link BufferPublisherInputStream} instance.
     *
     * @param publisher the {@link Publisher Publisher&lt;Buffer&gt;} to read from.
     * @param queueCapacity the capacity hint for the intermediary queue that stores items.
     * @param aggregationSizeLimiter applied to the wrapped {@link Publisher} for readers that buffer the whole body in
     * memory, to enforce the aggregation size limit; typically curries
     * {@code StreamingHttpRequests.applyAggregationSizeLimit(request, ...)}, or {@link UnaryOperator#identity()} for no
     * limit.
     */
    public BufferPublisherInputStream(final Publisher<Buffer> publisher, final int queueCapacity,
                                      final UnaryOperator<Publisher<Buffer>> aggregationSizeLimiter) {
        inputStream = EMPTY_INPUT_STREAM;
        this.publisher = requireNonNull(publisher);
        this.queueCapacity = queueCapacity;
        this.aggregationSizeLimiter = requireNonNull(aggregationSizeLimiter);
    }

    @Override
    public int read() throws IOException {
        publisherToInputStream();
        return inputStream.read();
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        publisherToInputStream();
        return inputStream.read(b, off, len);
    }

    /**
     * Offload operations on the wrapped {@link Publisher Publisher&lt;Buffer&gt;} to the designated executor.
     *
     * @param executionStrategy the {@link HttpExecutionStrategy} to use.
     * @param executor the {@link Executor} to use with the {@link HttpExecutionStrategy}.
     */
    public void offloadSourcePublisher(final HttpExecutionStrategy executionStrategy, final Executor executor) {
        if (inputStream == EMPTY_INPUT_STREAM) {
            publisher = executionStrategy.isMetadataReceiveOffloaded() || executionStrategy.isDataReceiveOffloaded() ?
                    // We only need to add shareContextOnSubscribe() if we decide to offload. Otherwise, it's already
                    // shared before the `publisher` was wrapped with BufferPublisherInputStream in
                    // DefaultJerseyStreamingHttpRouter.
                    publisher.publishOn(executor, IoThreadFactory.IoThread::currentThreadIsIoThread)
                            .shareContextOnSubscribe() : publisher;
        } else {
            throw new IllegalStateException("Can't offload source publisher because it is consumed via InputStream");
        }
    }

    /**
     * Gets the wrapped {@link Publisher Publisher&lt;Buffer&gt;} if reading this stream hasn't started.
     *
     * @param applyPayloadSizeLimit when {@code true} the returned publisher enforces the aggregation size limit as
     * bytes flow (for readers that buffer the whole body in memory); when {@code false} the raw publisher is returned
     * for streaming readers that must not be bounded.
     * @return the wrapped {@link Publisher Publisher&lt;Buffer&gt;}
     * @throws IllegalStateException in case reading the stream has started
     */
    private Publisher<Buffer> bufferPublisher(final boolean applyPayloadSizeLimit) {
        if (inputStream != EMPTY_INPUT_STREAM) {
            throw new IllegalStateException("Publisher is being consumed via InputStream");
        }
        return applyPayloadSizeLimit ? aggregationSizeLimiter.apply(publisher) : publisher;
    }

    private void publisherToInputStream() {
        if (inputStream == EMPTY_INPUT_STREAM) {
            // Reads via the InputStream (String/byte[]/InputStream resource params, etc.) are treated as streaming and
            // left unbounded: the consumer's intent (aggregate vs stream) isn't known here, so we don't cap it. Only
            // the Publisher-based aggregating readers enforce the limit (see bufferPublisher(boolean)).
            inputStream = publisher.toInputStream(BufferPublisherInputStream::getBytes, queueCapacity);
        }
    }

    /**
     * Helper method for dealing with a request entity {@link InputStream} that is potentially
     * a {@link BufferPublisherInputStream}.
     *
     * @param entityStream the request entity {@link InputStream}
     * @param allocator the {@link BufferAllocator} to use
     * @param bufferPublisherHandler a {@link BiFunction} that is called in case the entity {@link InputStream} is
     * a {@link BufferPublisherInputStream}
     * @param inputStreamHandler a {@link BiFunction} that is called in case the entity {@link InputStream} is not
     * a {@link BufferPublisherInputStream}
     * @param <T> the type of data returned by the {@link BiFunction}s.
     * @return the data returned by one of the {@link BiFunction}.
     * @deprecated Use {@link #handleEntityStream(InputStream, BufferAllocator, boolean, BiFunction, BiFunction)} to
     * enforce an aggregation size limit; this overload applies none.
     */
    @Deprecated
    public static <T> T handleEntityStream(final InputStream entityStream,
                                           final BufferAllocator allocator,
                                           final BiFunction<Publisher<Buffer>,
                                                   BufferAllocator, T> bufferPublisherHandler,
                                           final BiFunction<InputStream, BufferAllocator, T> inputStreamHandler) {
        return handleEntityStream(entityStream, allocator, false, bufferPublisherHandler, inputStreamHandler);
    }

    /**
     * Helper method for dealing with a request entity {@link InputStream} that is potentially
     * a {@link BufferPublisherInputStream}.
     *
     * @param entityStream the request entity {@link InputStream}
     * @param allocator the {@link BufferAllocator} to use
     * @param applyAggregationLimit when {@code true} the {@link Publisher} handed to {@code bufferPublisherHandler}
     * enforces the aggregation size limit as it is consumed; streaming readers pass {@code false} so their bodies are
     * not bounded.
     * @param bufferPublisherHandler a {@link BiFunction} that is called in case the entity {@link InputStream} is
     * a {@link BufferPublisherInputStream}
     * @param inputStreamHandler a {@link BiFunction} that is called in case the entity {@link InputStream} is not
     * a {@link BufferPublisherInputStream}
     * @param <T> the type of data returned by the {@link BiFunction}s.
     * @return the data returned by one of the {@link BiFunction}.
     */
    public static <T> T handleEntityStream(final InputStream entityStream,
                                           final BufferAllocator allocator,
                                           final boolean applyAggregationLimit,
                                           final BiFunction<Publisher<Buffer>,
                                                   BufferAllocator, T> bufferPublisherHandler,
                                           final BiFunction<InputStream, BufferAllocator, T> inputStreamHandler) {
        requireNonNull(allocator);
        requireNonNull(bufferPublisherHandler);
        requireNonNull(inputStreamHandler);

        // Unwrap the entity stream created by Jersey to fetch the wrapped one
        final EntityInputStream eis = (EntityInputStream) closeableInputStream(requireNonNull(entityStream));
        final InputStream wrappedStream = eis.getWrappedStream();

        if (wrappedStream instanceof BufferPublisherInputStream) {
            // If the wrapped stream is built around a Publisher, provide it to the resource as-is
            return bufferPublisherHandler.apply(
                    ((BufferPublisherInputStream) wrappedStream).bufferPublisher(applyAggregationLimit), allocator);
        }

        return inputStreamHandler.apply(wrappedStream, allocator);
    }

    private static byte[] getBytes(final Buffer content) {
        final int readableBytes = content.readableBytes();

        if (readableBytes == 0) {
            return EMPTY_ARRAY;
        }

        if (content.hasArray() && content.arrayOffset() == 0 && content.array().length == readableBytes) {
            return content.array();
        }

        final byte[] bytes = new byte[readableBytes];
        content.readBytes(bytes);
        return bytes;
    }
}
