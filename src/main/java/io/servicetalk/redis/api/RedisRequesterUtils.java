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

import io.servicetalk.buffer.Buffer;
import io.servicetalk.concurrent.api.Single;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import javax.annotation.Nullable;

import static io.servicetalk.buffer.netty.BufferUtil.maxUtf8Bytes;
import static io.servicetalk.concurrent.internal.SubscriberUtils.checkDuplicateSubscription;
import static java.nio.charset.StandardCharsets.UTF_8;

final class RedisRequesterUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisRequesterUtils.class);

    private RedisRequesterUtils() {
    }

    abstract static class AggregatingSubscriber<R> implements org.reactivestreams.Subscriber<RedisData> {
        final Single.Subscriber<? super R> singleSubscriber;
        @Nullable
        private Subscription subscription;

        AggregatingSubscriber(Single.Subscriber<? super R> singleSubscriber) {
            this.singleSubscriber = singleSubscriber;
        }

        @Override
        public final void onSubscribe(Subscription s) {
            if (checkDuplicateSubscription(subscription, s)) {
                subscription = s;
                singleSubscriber.onSubscribe(subscription::cancel);
                subscription.request(Long.MAX_VALUE);
            }
        }
    }

    static final class ToStringSingle<R> extends Single<R> {
        private final RedisRequester requester;
        private final RedisRequest request;

        ToStringSingle(final RedisRequester requester, final RedisRequest request) {
            this.requester = requester;
            this.request = request;
        }

        @Override
        protected void handleSubscribe(final Subscriber<? super R> subscriber) {
            requester.request(request).subscribe(new AggregatingSubscriber<R>(subscriber) {
                @Nullable
                private CharSequence lastMessage;
                @Nullable
                private StringBuilder totalMessages;
                @Nullable
                private RedisData.Error error;

                @Override
                public void onNext(final RedisData redisData) {
                    if (error != null) {
                        LOGGER.debug("discarding string {} because error received {}", redisData, error);
                        return;
                    }
                    if (redisData instanceof RedisData.Error) {
                        error = (RedisData.Error) redisData;
                        lastMessage = totalMessages = null;
                    } else if (totalMessages == null && lastMessage == null) {
                        if (!ignoreRedisData(redisData)) {
                            lastMessage = toCharSequence(redisData);
                        }
                    } else {
                        final CharSequence currentMessage = toCharSequence(redisData);
                        if (totalMessages == null) {
                            assert lastMessage != null;
                            totalMessages = new StringBuilder(lastMessage.length() + currentMessage.length());
                            totalMessages.append(lastMessage);
                            lastMessage = null;
                        }
                        totalMessages.append(currentMessage);
                    }
                }

                @Override
                public void onError(final Throwable t) {
                    singleSubscriber.onError(t);
                }

                @SuppressWarnings("unchecked")
                @Override
                public void onComplete() {
                    if (error != null) {
                        singleSubscriber.onError(new RedisException(error));
                    } else if (totalMessages != null) {
                        singleSubscriber.onSuccess((R) totalMessages.toString());
                    } else {
                        singleSubscriber.onSuccess((R) lastMessage);
                    }
                }

                private CharSequence toCharSequence(final RedisData redisData) {
                    if (redisData instanceof RedisData.SimpleString) {
                        return redisData.getCharSequenceValue();
                    }
                    if (redisData instanceof RedisData.BulkStringChunk) {
                        return redisData.getBufferValue().toString(UTF_8);
                    }
                    throw new IllegalArgumentException("unsupported data:" + redisData);
                }
            });
        }
    }

    static final class ToBufferSingle<R> extends Single<R> {
        private final RedisRequester requester;
        private final RedisRequest request;

        ToBufferSingle(final RedisRequester requester, final RedisRequest request) {
            this.requester = requester;
            this.request = request;
        }

        @Override
        protected void handleSubscribe(final Subscriber<? super R> subscriber) {
            requester.request(request).subscribe(new AggregatingSubscriber<R>(subscriber) {
                @Nullable
                private Buffer aggregator;
                @Nullable
                private RedisData.Error error;

                @Override
                public void onNext(final RedisData redisData) {
                    if (error != null) {
                        LOGGER.debug("discarding buffer data {} because error received {}", redisData, error);
                        return;
                    }
                    if (redisData instanceof RedisData.Error) {
                        error = (RedisData.Error) redisData;
                        aggregator = null;
                    } else if (aggregator == null) {
                        if (redisData instanceof RedisData.SimpleString) {
                            aggregator = requester.getBufferAllocator().fromUtf8(redisData.getCharSequenceValue());
                        } else if (redisData instanceof RedisData.BulkStringChunk) {
                            aggregator = redisData.getBufferValue();
                        } else if (!ignoreRedisData(redisData)) {
                            throw new IllegalArgumentException("unsupported data:" + redisData);
                        }
                    } else if (redisData instanceof RedisData.SimpleString) {
                        CharSequence cs = redisData.getCharSequenceValue();
                        int maxBytes = maxUtf8Bytes(cs);
                        if (!aggregator.tryEnsureWritable(maxBytes, false)) {
                            reallocateAggregator(maxBytes);
                        }
                        aggregator.writeUtf8(redisData.getCharSequenceValue());
                    } else if (redisData instanceof RedisData.BulkStringChunk) {
                        int redisDataBytes = redisData.getBufferValue().getReadableBytes();
                        if (!aggregator.tryEnsureWritable(redisDataBytes, false)) {
                            reallocateAggregator(redisDataBytes);
                        }
                        aggregator.writeBytes(redisData.getBufferValue());
                    } else {
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
                        singleSubscriber.onError(new RedisException(error));
                    }
                }

                private void reallocateAggregator(int extraBytes) {
                    assert aggregator != null;
                    aggregator = requester.getBufferAllocator().newBuffer(extraBytes + aggregator.getReadableBytes()).writeBytes(aggregator);
                }
            });
        }
    }

    static final class ToLongSingle<R> extends Single<R> {
        private final RedisRequester requester;
        private final RedisRequest request;

        ToLongSingle(final RedisRequester requester, final RedisRequest request) {
            this.requester = requester;
            this.request = request;
        }

        @Override
        protected void handleSubscribe(final Subscriber<? super R> subscriber) {
            requester.request(request).subscribe(new AggregatingSubscriber<R>(subscriber) {
                @Nullable
                private Long answer;
                @Nullable
                private RedisData.Error error;

                @Override
                public void onNext(final RedisData redisData) {
                    // We only expect a single value
                    if (answer != null) {
                        throw new IllegalStateException("answer is not null: " + answer + " redisData: " + redisData);
                    }

                    if (redisData instanceof RedisData.Error) {
                        if (error == null) {
                            error = (RedisData.Error) redisData;
                        } else {
                            throw new IllegalArgumentException("error already set:" + error);
                        }
                    } else if (redisData instanceof RedisData.Integer) {
                        answer = redisData.getLongValue();
                    } else if (!(redisData instanceof RedisData.Null)) {
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
                        singleSubscriber.onError(new RedisException(error));
                    }
                }
            });
        }
    }

    static final class ToListSingle<R> extends Single<R> {
        private final boolean coerceBuffersToCharSequences;
        private final RedisRequester requester;
        private final RedisRequest request;

        ToListSingle(final RedisRequester requester, final RedisRequest request, final boolean coerceBuffersToCharSequences) {
            this.coerceBuffersToCharSequences = coerceBuffersToCharSequences;
            this.requester = requester;
            this.request = request;
        }

        @Override
        protected void handleSubscribe(final Subscriber<? super R> subscriber) {
            requester.request(request).subscribe(new AggregatingSubscriber<R>(subscriber) {
                @Nullable
                private RedisException redisError;
                @Nullable
                private Deque<AggregateState> depths;
                @Nullable
                private List<Object> result;
                @Nullable
                private Buffer aggregator;  // to aggregate BulkStringChunks
                private int bulkStringSize;

                @Override
                public void onNext(final RedisData redisData) {
                    if (redisData instanceof RedisData.ArraySize) {
                        final long length = redisData.getLongValue();
                        if (length > Integer.MAX_VALUE) {
                            throw new IllegalArgumentException("length " + length + "(expected <=" + Integer.MAX_VALUE + ")");
                        }
                        if (result == null) {
                            result = new ArrayList<>((int) length);
                        } else {
                            if (depths == null) {
                                depths = new ArrayDeque<>(4);
                            }
                            depths.offerFirst(new AggregateState((int) length));
                        }
                    } else if (redisData instanceof RedisData.BulkStringSize) {
                        bulkStringSize = redisData.getIntValue();
                    } else if (RedisData.BulkStringChunk.class.equals(redisData.getClass())) {
                        if (aggregator == null) {
                            aggregator = redisData.getBufferValue();
                            if (!aggregator.tryEnsureWritable(bulkStringSize - aggregator.getReadableBytes(), false)) {
                                aggregator = requester.getBufferAllocator().newBuffer(bulkStringSize).writeBytes(aggregator);
                            }
                        } else {
                            aggregator.writeBytes(redisData.getBufferValue());
                        }
                    } else {
                        if (depths == null || depths.isEmpty()) {
                            if (result == null) {
                                if (redisData instanceof RedisData.Error) {
                                    redisError = new RedisException((RedisData.Error) redisData);
                                } else if (!(redisData instanceof RedisData.Null)) {
                                    throw new IllegalArgumentException("unexpected data: " + redisData);
                                }
                            } else {
                                result.add(unwrapData(redisData));
                            }
                        } else {
                            AggregateState current = depths.peek();
                            current.children.add(unwrapData(redisData));
                            while (current.children.size() == current.length) {
                                depths.pollFirst(); // Remove the current.
                                final AggregateState next = depths.peek();
                                if (next == null) {
                                    assert result != null;
                                    result.add(current.children);
                                    break;
                                } else {
                                    next.children.add(current.children);
                                    current = next;
                                }
                            }
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
                    } else if (depths != null && !depths.isEmpty()) {
                        singleSubscriber.onError(new IllegalStateException("aggregation didn't finish. result: " + result + " depths: " + depths));
                    } else {
                        singleSubscriber.onSuccess((R) result);
                    }
                }

                @Nullable
                private Object unwrapData(final RedisData redisData) {
                    if (redisData instanceof RedisData.SimpleString) {
                        return redisData.getCharSequenceValue();
                    }
                    if (redisData instanceof RedisData.Integer) {
                        return redisData.getLongValue();
                    }
                    if (redisData instanceof RedisData.LastBulkStringChunk) {
                        final Buffer buffer;
                        if (aggregator != null) {
                            aggregator.writeBytes(redisData.getBufferValue());
                            buffer = aggregator;
                            aggregator = null;
                        } else {
                            buffer = redisData.getBufferValue();
                        }
                        return coerceBuffersToCharSequences ? buffer.toString(UTF_8) : buffer;
                    }
                    if (redisData instanceof RedisData.Null) {
                        return null;
                    }
                    if (redisData instanceof RedisData.Error) {
                        return new RedisException((RedisData.Error) redisData);
                    }
                    throw new IllegalArgumentException("unsupported type: " + redisData);
                }
            });
        }
    }

    private static boolean ignoreRedisData(final RedisData redisData) {
        return redisData instanceof RedisData.Null || redisData instanceof RedisData.BulkStringSize;
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
