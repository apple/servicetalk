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
package io.servicetalk.redis.api;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ConcurrentSubscription;
import io.servicetalk.redis.api.RedisData.BulkStringChunk;
import io.servicetalk.redis.api.RedisData.FirstBulkStringChunk;
import io.servicetalk.redis.api.RedisData.LastBulkStringChunk;
import io.servicetalk.redis.api.RedisData.Null;
import io.servicetalk.redis.api.RedisData.SimpleString;

import org.reactivestreams.Subscription;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.ConcurrentSubscription.wrap;
import static java.nio.charset.StandardCharsets.UTF_8;

final class RedisRequesterUtils {
    private RedisRequesterUtils() {
    }

    abstract static class AggregatingSubscriber<R> implements org.reactivestreams.Subscriber<RedisData> {
        final Single.Subscriber<? super R> singleSubscriber;

        AggregatingSubscriber(Single.Subscriber<? super R> singleSubscriber) {
            this.singleSubscriber = singleSubscriber;
        }

        @Override
        public final void onSubscribe(Subscription s) {
            ConcurrentSubscription cs = wrap(s);
            singleSubscriber.onSubscribe(cs::cancel);
            cs.request(Long.MAX_VALUE);
        }
    }

    static final class ToStringSingle<R> extends Single<R> {
        private final RedisExecutionStrategy strategy;
        private final RedisRequester requester;
        private final RedisRequest request;

        ToStringSingle(final RedisExecutionStrategy strategy, final RedisRequester requester,
                       final RedisRequest request) {
            this.strategy = strategy;
            this.requester = requester;
            this.request = request;
        }

        @Override
        protected void handleSubscribe(final Subscriber<? super R> subscriber) {
            requester.request(strategy, request).subscribe(new AggregatingSubscriber<R>(subscriber) {
                @Nullable
                private CharSequence simpleString;
                @Nullable
                private Buffer aggregator;
                @Nullable
                private RedisData.Error error;

                @Override
                public void onNext(final RedisData redisData) {
                    if (redisData instanceof SimpleString) {
                        assert simpleString == null; // a single SimpleString is the only chunk of data expected.
                        // Either BulkString, SimpleString, or Error is expected, but not multiple.
                        assert aggregator == null && error == null;
                        simpleString = redisData.getCharSequenceValue();
                    } else if (redisData instanceof BulkStringChunk) {
                        // Either BulkString, SimpleString, or Error is expected, but not multiple.
                        assert simpleString == null && error == null;
                        if (aggregator == null) {
                            aggregator = redisData.getBufferValue();
                            if (!(redisData instanceof LastBulkStringChunk)) {
                                assert redisData instanceof FirstBulkStringChunk;
                                // FirstBulkStringChunk includes the number of bytes we expect to read, so proactively
                                // ensure the buffer is large enough to accumulate all the data.
                                final int expectedBytes = ((FirstBulkStringChunk) redisData).bulkStringLength();
                                if (!aggregator.tryEnsureWritable(expectedBytes, true)) {
                                    aggregator = requester.executionContext().bufferAllocator().newBuffer(
                                            expectedBytes + aggregator.readableBytes()).writeBytes(aggregator);
                                }
                            }
                        } else {
                            aggregator.writeBytes(redisData.getBufferValue());
                        }
                    } else if (redisData instanceof RedisData.Error) {
                        error = (RedisData.Error) redisData;
                        simpleString = null;
                        aggregator = null;
                    } else if (!(redisData instanceof Null)) {
                        throw new IllegalArgumentException("unsupported data:" + redisData);
                    }
                }

                @Override
                public void onError(final Throwable t) {
                    singleSubscriber.onError(t);
                }

                @SuppressWarnings("unchecked")
                @Override
                public void onComplete() {
                    if (aggregator != null) {
                        singleSubscriber.onSuccess((R) aggregator.toString(UTF_8));
                    } else if (simpleString != null || error == null) {
                        singleSubscriber.onSuccess((R) simpleString);
                    } else {
                        singleSubscriber.onError(new RedisServerException(error));
                    }
                }
            });
        }
    }

    static final class ToBufferSingle<R> extends Single<R> {
        private final RedisExecutionStrategy strategy;
        private final RedisRequester requester;
        private final RedisRequest request;

        ToBufferSingle(final RedisExecutionStrategy strategy, final RedisRequester requester,
                       final RedisRequest request) {
            this.strategy = strategy;
            this.requester = requester;
            this.request = request;
        }

        @Override
        protected void handleSubscribe(final Subscriber<? super R> subscriber) {
            requester.request(strategy, request).subscribe(new AggregatingSubscriber<R>(subscriber) {
                @Nullable
                private Buffer aggregator;
                @Nullable
                private RedisData.Error error;

                @Override
                public void onNext(final RedisData redisData) {
                    if (redisData instanceof BulkStringChunk) {
                        assert error == null;
                        if (aggregator == null) {
                            aggregator = redisData.getBufferValue();
                            if (!(redisData instanceof LastBulkStringChunk)) {
                                assert redisData instanceof FirstBulkStringChunk;
                                // FirstBulkStringChunk includes the number of bytes we expect to read, so proactively
                                // ensure the buffer is large enough to accumulate all the data.
                                final int expectedBytes = ((FirstBulkStringChunk) redisData).bulkStringLength();
                                if (!aggregator.tryEnsureWritable(expectedBytes, true)) {
                                    aggregator = requester.executionContext().bufferAllocator().newBuffer(
                                            expectedBytes + aggregator.readableBytes()).writeBytes(aggregator);
                                }
                            }
                        } else {
                            aggregator.writeBytes(redisData.getBufferValue());
                        }
                    } else if (redisData instanceof SimpleString) {
                        // Either BulkString, SimpleString, or Error is expected, but not multiple.
                        assert aggregator == null && error == null;
                        aggregator = requester.executionContext().bufferAllocator()
                                .fromUtf8(redisData.getCharSequenceValue());
                    } else if (redisData instanceof RedisData.Error) {
                        error = (RedisData.Error) redisData;
                        aggregator = null;
                    } else if (!(redisData instanceof Null)) {
                        throw new IllegalArgumentException("unsupported data:" + redisData);
                    }
                }

                @Override
                public void onError(final Throwable t) {
                    singleSubscriber.onError(t);
                }

                @SuppressWarnings("unchecked")
                @Override
                public void onComplete() {
                    if (error == null) {
                        singleSubscriber.onSuccess((R) aggregator);
                    } else {
                        singleSubscriber.onError(new RedisServerException(error));
                    }
                }
            });
        }
    }

    static final class ToLongSingle<R> extends Single<R> {
        private final RedisExecutionStrategy strategy;
        private final RedisRequester requester;
        private final RedisRequest request;

        ToLongSingle(final RedisExecutionStrategy strategy, final RedisRequester requester,
                     final RedisRequest request) {
            this.strategy = strategy;
            this.requester = requester;
            this.request = request;
        }

        @Override
        protected void handleSubscribe(final Subscriber<? super R> subscriber) {
            requester.request(strategy, request).subscribe(new AggregatingSubscriber<R>(subscriber) {
                @Nullable
                private Long answer;
                @Nullable
                private RedisData.Error error;

                @Override
                public void onNext(final RedisData redisData) {
                    assert answer == null; // We only expect a single value
                    if (redisData instanceof RedisData.Error) {
                        if (error == null) {
                            error = (RedisData.Error) redisData;
                        } else {
                            throw new IllegalArgumentException("error already set:" + error);
                        }
                    } else if (redisData instanceof RedisData.Integer) {
                        answer = redisData.getLongValue();
                    } else if (!(redisData instanceof Null)) {
                        throw new IllegalArgumentException("unsupported data:" + redisData);
                    }
                }

                @Override
                public void onError(final Throwable t) {
                    singleSubscriber.onError(t);
                }

                @SuppressWarnings("unchecked")
                @Override
                public void onComplete() {
                    if (error == null) {
                        singleSubscriber.onSuccess((R) answer);
                    } else {
                        singleSubscriber.onError(new RedisServerException(error));
                    }
                }
            });
        }
    }

    static final class ToListSingle<R> extends Single<R> {
        private final RedisExecutionStrategy strategy;
        private final boolean coerceBuffersToCharSequences;
        private final RedisRequester requester;
        private final RedisRequest request;

        ToListSingle(final RedisExecutionStrategy strategy, final RedisRequester requester, final RedisRequest request,
                     final boolean coerceBuffersToCharSequences) {
            this.strategy = strategy;
            this.coerceBuffersToCharSequences = coerceBuffersToCharSequences;
            this.requester = requester;
            this.request = request;
        }

        @Override
        protected void handleSubscribe(final Subscriber<? super R> subscriber) {
            requester.request(strategy, request).subscribe(new AggregatingSubscriber<R>(subscriber) {
                @Nullable
                private RedisServerException redisError;
                @Nullable
                private Deque<AggregateState> depths;
                @Nullable
                private List<Object> result;
                @Nullable
                private Buffer aggregator;  // to aggregate BulkStringChunks

                @Override
                public void onNext(final RedisData redisData) {
                    if (redisData instanceof BulkStringChunk) {
                        if (aggregator == null) {
                            if (redisData instanceof LastBulkStringChunk) {
                                addResult(coerceBuffersToCharSequences ? redisData.getBufferValue().toString(UTF_8) :
                                        redisData.getBufferValue());
                            } else {
                                aggregator = redisData.getBufferValue();
                                assert redisData instanceof FirstBulkStringChunk;
                                // FirstBulkStringChunk includes the number of bytes we expect to read, so proactively
                                // ensure the buffer is large enough to accumulate all the data.
                                final int expectedBytes = ((FirstBulkStringChunk) redisData).bulkStringLength();
                                if (!aggregator.tryEnsureWritable(expectedBytes, true)) {
                                    aggregator = requester.executionContext().bufferAllocator().newBuffer(
                                            expectedBytes + aggregator.readableBytes()).writeBytes(aggregator);
                                }
                            }
                        } else {
                            aggregator.writeBytes(redisData.getBufferValue());
                            if (redisData instanceof LastBulkStringChunk) {
                                addResult(coerceBuffersToCharSequences ? aggregator.toString(UTF_8) : aggregator);
                                aggregator = null;
                            }
                        }
                    } else if (redisData instanceof RedisData.ArraySize) {
                        final long length = redisData.getLongValue();
                        if (length > Integer.MAX_VALUE) {
                            throw new IllegalArgumentException("length " + length + "(expected <=" +
                                    Integer.MAX_VALUE + ")");
                        }
                        if (result == null) {
                            result = new ArrayList<>((int) length);
                        } else {
                            if (depths == null) {
                                depths = new ArrayDeque<>(4);
                            }
                            depths.offerFirst(new AggregateState((int) length));
                        }
                    } else {
                        if (depths == null || depths.isEmpty()) {
                            if (result == null) {
                                if (redisData instanceof RedisData.Error) {
                                    redisError = new RedisServerException((RedisData.Error) redisData);
                                } else if (!(redisData instanceof Null)) {
                                    throw new IllegalArgumentException("unexpected data: " + redisData);
                                }
                            } else {
                                result.add(unwrapData(redisData));
                            }
                        } else {
                            addDepths(unwrapData(redisData));
                        }
                    }
                }

                private void addResult(@Nullable final Object toAdd) {
                    assert result != null;
                    if (depths == null || depths.isEmpty()) {
                        result.add(toAdd);
                    } else {
                        addDepths(toAdd);
                    }
                }

                private void addDepths(@Nullable final Object toAdd) {
                    assert depths != null && result != null;
                    AggregateState current = depths.peek();
                    assert current != null;
                    current.children.add(toAdd);
                    while (current.children.size() == current.length) {
                        depths.pollFirst(); // Remove the current.
                        final AggregateState next = depths.peek();
                        if (next == null) {
                            result.add(current.children);
                            return;
                        } else {
                            next.children.add(current.children);
                            current = next;
                        }
                    }
                }

                @Override
                public void onError(final Throwable t) {
                    singleSubscriber.onError(t);
                }

                @SuppressWarnings("unchecked")
                @Override
                public void onComplete() {
                    if (redisError != null) {
                        singleSubscriber.onError(redisError);
                    } else if (depths == null || depths.isEmpty()) {
                        singleSubscriber.onSuccess((R) result);
                    } else {
                        singleSubscriber.onError(new IllegalStateException("aggregation didn't finish. result: " +
                                result + " depths: " + depths));
                    }
                }
            });
        }
    }

    @Nullable
    private static Object unwrapData(final RedisData redisData) {
        if (redisData instanceof SimpleString) {
            return redisData.getCharSequenceValue();
        }
        if (redisData instanceof RedisData.Integer) {
            return redisData.getLongValue();
        }
        if (redisData instanceof Null) {
            return null;
        }
        if (redisData instanceof RedisData.Error) {
            return new RedisServerException((RedisData.Error) redisData);
        }
        throw new IllegalArgumentException("unsupported type: " + redisData);
    }

    private static final class AggregateState {
        final int length;
        final List<Object> children;

        AggregateState(final int length) {
            this.length = length;
            this.children = new ArrayList<>(length);
        }
    }
}
