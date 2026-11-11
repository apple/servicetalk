/*
 * Copyright © 2021-2026 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.Publisher;

import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.defer;
import static io.servicetalk.concurrent.api.Publisher.empty;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Publisher.fromIterable;
import static io.servicetalk.http.api.HttpDataSourceTransformations.eagerForFirstSubscribe;
import static java.lang.System.nanoTime;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

final class BlockingStreamingHttpMessageBodyUtils {
    private BlockingStreamingHttpMessageBodyUtils() {
    }

    static HttpMessageBodyIterable<Buffer> newMessageBody(BlockingIterable<Object> rawMsgBody) {
        return () -> new DefaultHttpMessageBodyIterator<>(rawMsgBody.iterator());
    }

    static <T> HttpMessageBodyIterable<T> newMessageBody(
            BlockingIterable<Object> rawMsgBody, HttpHeaders headers, HttpStreamingDeserializer<T> deserializer,
            BufferAllocator allocator) {
        return () -> new HttpMessageBodyIterator<T>() {
            private final HttpMessageBodyIterator<Buffer> itr =
                    new DefaultHttpMessageBodyIterator<>(rawMsgBody.iterator());
            private final BlockingIterator<T> deserialized =
                    deserializer.deserialize(headers, () -> itr, allocator).iterator();
            @Nullable
            @Override
            public HttpHeaders trailers() {
                return itr.trailers();
            }

            @Override
            public boolean hasNext(final long timeout, final TimeUnit unit) throws TimeoutException {
                return deserialized.hasNext(timeout, unit);
            }

            @Nullable
            @Override
            public T next(final long timeout, final TimeUnit unit) throws TimeoutException {
                return deserialized.next(timeout, unit);
            }

            @Nullable
            @Override
            public T next() {
                return deserialized.next();
            }

            @Override
            public void close() throws Exception {
                deserialized.close();
            }

            @Override
            public boolean hasNext() {
                return deserialized.hasNext();
            }
        };
    }

    static <T> Publisher<Object> serializeMessageBody(
            HttpMessageBodyIterable<T> messageBody, HttpHeaders headers, HttpStreamingSerializer<T> serializer,
            BufferAllocator allocator) {
        // Serialize now rather than on subscribe: the serializer may update headers, which are sent before the body.
        return eagerForFirstSubscribe(() -> {
            final TrailersTrackingIterable<T> body = new TrailersTrackingIterable<>(messageBody);
            return fromIterable(serializer.serialize(headers, body, allocator))
                    .cast(Object.class)
                    .concat(defer(() -> fromFilterNull(body.trailers()).shareContextOnSubscribe()));
        });
    }

    static <T> Publisher<T> fromFilterNull(@Nullable T value) {
        return value == null ? empty() : from(value);
    }

    private static final class TrailersTrackingIterable<T> implements BlockingIterable<T> {
        private final HttpMessageBodyIterable<T> delegate;
        @Nullable
        private volatile HttpMessageBodyIterator<T> current;

        TrailersTrackingIterable(HttpMessageBodyIterable<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public HttpMessageBodyIterator<T> iterator() {
            final HttpMessageBodyIterator<T> iterator = delegate.iterator();
            current = iterator;
            return iterator;
        }

        @Nullable
        HttpHeaders trailers() {
            final HttpMessageBodyIterator<T> iterator = current;
            return iterator == null ? null : iterator.trailers();
        }
    }

    private static final class DefaultHttpMessageBodyIterator<I> implements HttpMessageBodyIterator<Buffer> {
        private final BlockingIterator<I> rawMessageBody;
        @Nullable
        private HttpHeaders trailers;
        @Nullable
        private Buffer next;

        DefaultHttpMessageBodyIterator(BlockingIterator<I> rawMessageBody) {
            this.rawMessageBody = requireNonNull(rawMessageBody);
        }

        @Nullable
        @Override
        public HttpHeaders trailers() {
            return trailers;
        }

        @Override
        public boolean hasNext(final long timeout, final TimeUnit unit) throws TimeoutException {
            if (next != null) {
                return true;
            }
            long remainingTimeoutNanos = unit.toNanos(timeout);
            final long timeStampANanos = nanoTime();
            if (rawMessageBody.hasNext(remainingTimeoutNanos, NANOSECONDS)) {
                remainingTimeoutNanos -= nanoTime() - timeStampANanos;
                setNext(rawMessageBody.next(remainingTimeoutNanos, NANOSECONDS));
            }
            return next != null;
        }

        @Override
        public Buffer next(final long timeout, final TimeUnit unit) {
            return next();
        }

        @Override
        public boolean hasNext() {
            if (next != null) {
                return true;
            }
            if (rawMessageBody.hasNext()) {
                setNext(rawMessageBody.next());
            }
            return next != null;
        }

        @Override
        public Buffer next() {
            if (next == null) {
                throw new NoSuchElementException();
            }
            Buffer tmp = next;
            next = null;
            return tmp;
        }

        @Override
        public void remove() {
            rawMessageBody.remove();
        }

        @Override
        public void close() throws Exception {
            rawMessageBody.close();
        }

        private void setNext(@Nullable Object rawNext) {
            if (rawNext instanceof Buffer) {
                next = (Buffer) rawNext;
            } else if (rawNext instanceof HttpHeaders) {
                trailers = (HttpHeaders) rawNext;
            } else if (rawNext != null) {
                try {
                    close();
                } catch (Exception e) {
                    throw new IllegalStateException(
                            "exception while closing due to unsupported type: " + rawNext, e);
                }
                throw new IllegalArgumentException("unsupported type: " + rawNext);
            }
        }
    }
}
