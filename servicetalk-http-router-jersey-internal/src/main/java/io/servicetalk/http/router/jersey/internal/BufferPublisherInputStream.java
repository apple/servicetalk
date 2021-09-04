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

import org.glassfish.jersey.message.internal.EntityInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.BiFunction;
import javax.annotation.Nullable;

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
 * and {@link BufferPublisherInputStream#bufferPublisher()}.
 */
public final class BufferPublisherInputStream extends InputStream {
    private static final InputStream EMPTY_INPUT_STREAM = new InputStream() {
        @Override
        public int read() {
            return -1;
        }
    };

    private InputStream inputStream;
    private Publisher<Buffer> publisher;
    private final int queueCapacity;

    /**
     * Creates a new {@link BufferPublisherInputStream} instance.
     *
     * @param publisher the {@link Publisher Publisher&lt;Buffer&gt;} to read from.
     * @param queueCapacity the capacity hint for the intermediary queue that stores items.
     */
    public BufferPublisherInputStream(final Publisher<Buffer> publisher, final int queueCapacity) {
        inputStream = EMPTY_INPUT_STREAM;
        this.publisher = requireNonNull(publisher);
        this.queueCapacity = queueCapacity;
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
                    publisher.publishOn(executor) : publisher;
        } else {
            throw new IllegalStateException("Can't offload source publisher because it is consumed via InputStream");
        }
    }

    /**
     * Gets the wrapped {@link Publisher Publisher&lt;Buffer&gt;} if reading this stream hasn't started.
     *
     * @return the wrapped {@link Publisher Publisher&lt;Buffer&gt;}
     * @throws IllegalStateException in case reading the stream has started
     */
    private Publisher<Buffer> bufferPublisher() {
        if (inputStream != EMPTY_INPUT_STREAM) {
            throw new IllegalStateException("Publisher is being consumed via InputStream");
        }
        return publisher;
    }

    private void publisherToInputStream() {
        if (inputStream == EMPTY_INPUT_STREAM) {
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
     */
    public static <T> T handleEntityStream(final InputStream entityStream,
                                           final BufferAllocator allocator,
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
            return bufferPublisherHandler.apply(((BufferPublisherInputStream) wrappedStream).bufferPublisher(),
                    allocator);
        }

        return inputStreamHandler.apply(wrappedStream, allocator);
    }

    @Nullable
    private static byte[] getBytes(final Buffer content) {
        final int readableBytes = content.readableBytes();

        if (readableBytes == 0) {
            return null;
        }

        if (content.hasArray() && content.arrayOffset() == 0 && content.array().length == readableBytes) {
            return content.array();
        }

        final byte[] bytes = new byte[readableBytes];
        content.readBytes(bytes);
        return bytes;
    }
}
