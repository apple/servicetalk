/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.BlockingIterator;

import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;

import static java.lang.System.nanoTime;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

final class BlockingStreamingHttpMessageBodyUtils {
    private BlockingStreamingHttpMessageBodyUtils() {
    }

    static BlockingStreamingHttpMessageBody<Buffer> newMessageBody(BlockingIterator<Object> rawMsgBody) {
        return new BlockingStreamingHttpMessageBody<Buffer>() {
            private final BlockingStreamingHttpMessageBodyAdapter<Object> msgBody =
                    new BlockingStreamingHttpMessageBodyAdapter<>(rawMsgBody);

            @Override
            public BlockingIterator<Buffer> payloadBody() {
                return msgBody;
            }

            @Nullable
            @Override
            public HttpHeaders trailers() {
                return msgBody.trailers();
            }
        };
    }

    static <T> BlockingStreamingHttpMessageBody<T> newMessageBody(
            BlockingIterator<Object> rawMsgBody, HttpHeaders headers, HttpStreamingDeserializer<T> deserializer,
            BufferAllocator allocator) {
        final BlockingStreamingHttpMessageBodyAdapter<Object> msgBody =
                new BlockingStreamingHttpMessageBodyAdapter<>(rawMsgBody);
        BlockingIterator<T> deserialized = deserializer.deserialize(headers, () -> msgBody, allocator).iterator();
        return new BlockingStreamingHttpMessageBody<T>() {
            @Override
            public BlockingIterator<T> payloadBody() {
                return deserialized;
            }

            @Nullable
            @Override
            public HttpHeaders trailers() {
                return msgBody.trailers();
            }
        };
    }

    private static final class BlockingStreamingHttpMessageBodyAdapter<I> implements BlockingIterator<Buffer> {
        private final BlockingIterator<I> rawMessageBody;
        @Nullable
        private HttpHeaders trailers;
        @Nullable
        private Buffer next;

        BlockingStreamingHttpMessageBodyAdapter(BlockingIterator<I> rawMessageBody) {
            this.rawMessageBody = requireNonNull(rawMessageBody);
        }

        @Nullable
        HttpHeaders trailers() {
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
