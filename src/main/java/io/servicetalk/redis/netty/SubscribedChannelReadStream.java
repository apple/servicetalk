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
package io.servicetalk.redis.netty;

import io.servicetalk.buffer.Buffer;
import io.servicetalk.buffer.BufferAllocator;
import io.servicetalk.buffer.netty.BufferAllocators;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.ConcurrentSubscription;
import io.servicetalk.redis.api.PubSubRedisMessage.ChannelPubSubRedisMessage;
import io.servicetalk.redis.api.PubSubRedisMessage.PatternPubSubRedisMessage;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisData.CompleteBulkString;
import io.servicetalk.redis.api.RedisData.CompleteRedisData;
import io.servicetalk.redis.api.RedisData.SimpleString;
import io.servicetalk.redis.internal.CoercionException;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.SubscriberUtils.checkDuplicateSubscription;
import static io.servicetalk.redis.netty.SubscribedChannelReadStream.PubSubChannelMessage.KeyType.Channel;
import static io.servicetalk.redis.netty.SubscribedChannelReadStream.PubSubChannelMessage.KeyType.Pattern;
import static io.servicetalk.redis.netty.SubscribedChannelReadStream.PubSubChannelMessage.KeyType.Ping;
import static io.servicetalk.redis.netty.SubscribedChannelReadStream.PubSubChannelMessage.KeyType.Quit;
import static io.servicetalk.redis.netty.SubscribedChannelReadStream.PubSubChannelMessage.KeyType.SimpleString;
import static io.servicetalk.redis.netty.SubscribedChannelReadStream.PubSubChannelMessage.MessageType.DATA;
import static io.servicetalk.redis.netty.SubscribedChannelReadStream.PubSubChannelMessage.MessageType.SUBSCRIBE_ACK;
import static io.servicetalk.redis.netty.SubscribedChannelReadStream.PubSubChannelMessage.MessageType.UNSUBSCRIBE;
import static java.nio.charset.Charset.defaultCharset;
import static java.util.Objects.requireNonNull;

// TODO repeal and replace with buffer operator (<rdar://problem/34135772>)
final class SubscribedChannelReadStream extends Publisher<SubscribedChannelReadStream.PubSubChannelMessage> {

    private static final CompleteBulkString MESSAGE_PUBSUB_MESSAGE_TYPE =
            new CompleteBulkString(BufferAllocators.DEFAULT.getAllocator().fromAscii("message").asReadOnly());

    private static final CompleteBulkString PMESSAGE_PUBSUB_MESSAGE_TYPE =
            new CompleteBulkString(BufferAllocators.DEFAULT.getAllocator().fromAscii("pmessage").asReadOnly());

    private static final CompleteBulkString PONG_PUBSUB_MESSAGE_TYPE =
            new CompleteBulkString(BufferAllocators.DEFAULT.getAllocator().fromAscii("pong").asReadOnly());

    private static final CompleteBulkString OK_PUBSUB_MESSAGE_TYPE =
            new CompleteBulkString(BufferAllocators.DEFAULT.getAllocator().fromAscii("OK").asReadOnly());

    private static final CompleteBulkString SUBSCRIBE_PUBSUB_MESSAGE_TYPE =
            new CompleteBulkString(BufferAllocators.DEFAULT.getAllocator().fromAscii("subscribe").asReadOnly());

    private static final CompleteBulkString PSUBSCRIBE_PUBSUB_MESSAGE_TYPE =
            new CompleteBulkString(BufferAllocators.DEFAULT.getAllocator().fromAscii("psubscribe").asReadOnly());

    private static final CompleteBulkString UNSUBSCRIBE_PUBSUB_MESSAGE_TYPE =
            new CompleteBulkString(BufferAllocators.DEFAULT.getAllocator().fromAscii("unsubscribe").asReadOnly());

    private static final CompleteBulkString PUNSUBSCRIBE_PUBSUB_MESSAGE_TYPE =
            new CompleteBulkString(BufferAllocators.DEFAULT.getAllocator().fromAscii("punsubscribe").asReadOnly());

    private final BufferAllocator allocator;
    private final Publisher<RedisData> original;

    SubscribedChannelReadStream(final Publisher<RedisData> original, final BufferAllocator allocator) {
        this.original = requireNonNull(original);
        this.allocator = requireNonNull(allocator);
    }

    @Override
    protected void handleSubscribe(Subscriber<? super PubSubChannelMessage> subscriber) {
        original.subscribe(new AggregatingSubscriber(subscriber, allocator));
    }

    /**
     * Any message received on a connection which is in "subscribe" mode.
     */
    static final class PubSubChannelMessage implements ChannelPubSubRedisMessage, PatternPubSubRedisMessage {

        enum KeyType {
            Channel(true),
            Pattern(true),
            Ping(false),
            Quit(false),
            SimpleString(false);

            private final boolean forChannel;

            KeyType(boolean forChannel) {
                this.forChannel = forChannel;
            }

            MessageType asActualMessageType(MessageType messageType) {
                return forChannel ? messageType : DATA;
            }

            boolean isForChannel() {
                return forChannel;
            }
        }

        enum MessageType {
            SUBSCRIBE_ACK,
            UNSUBSCRIBE,
            DATA
        }

        private final KeyType keyType;
        private final MessageType messageType;
        @Nullable
        private final String channel;
        @Nullable
        private final String pattern;

        private final CompleteRedisData data;

        PubSubChannelMessage(KeyType keyType, CompleteRedisData data) {
            if (keyType.isForChannel()) {
                throw new IllegalArgumentException("No channel present for a subscribe response.");
            }
            this.keyType = keyType;
            this.messageType = DATA;
            this.channel = null;
            this.pattern = null;
            this.data = requireNonNull(data);
        }

        PubSubChannelMessage(MessageType messageType, String channel, CompleteRedisData data) {
            this.keyType = Channel;
            this.messageType = keyType.asActualMessageType(messageType);
            this.channel = requireNonNull(channel);
            this.pattern = null;
            this.data = requireNonNull(data);
        }

        PubSubChannelMessage(MessageType messageType, String pattern, String channel, CompleteRedisData data) {
            this.keyType = Pattern;
            this.messageType = keyType.asActualMessageType(messageType);
            this.channel = requireNonNull(channel);
            this.pattern = requireNonNull(pattern);
            this.data = requireNonNull(data);
        }

        KeyType getKeyType() {
            return keyType;
        }

        MessageType getMessageType() {
            return messageType;
        }

        CompleteRedisData getData() {
            return data;
        }

        @Override
        @Nullable
        public String getChannel() {
            return channel;
        }

        @Nullable
        @Override
        public String getPattern() {
            return pattern;
        }

        @Override
        public Buffer getBufferValue() {
            if ((data instanceof Null) || (data instanceof CompleteBulkString)) {
                return data.getBufferValue();
            }
            throw new CoercionException(data, Buffer.class);
        }

        @Override
        public CharSequence getCharSequenceValue() {
            String string = RedisUtils.convertToString(data);
            return string == null ? data.getCharSequenceValue() : string;
        }

        @Override
        public String toString() {
            return "PubSubChannelMessage{" +
                    "keyType=" + keyType +
                    ", messageType=" + messageType +
                    ", channel='" + channel + '\'' +
                    ", pattern='" + pattern + '\'' +
                    ", data=<hidden(not-null)>" +
                    '}';
        }
    }

    /**
     * <strong>Important:</strong>
     * We realize the restrictions of <a href="https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.1/README.md#2.13">Reactive Streams 2.13</a>,
     * but in this case the "source publisher" is internal and known to handle exceptions in such a way that the exception
     * will be pushed to {@link org.reactivestreams.Subscriber#onError(Throwable)} and untimely to the user.
     * This is decided to be a better alternative than just cancelling the {@link org.reactivestreams.Subscription} and logging an
     * error because it provides more visibility and direct feedback for users.
     */
    private static final class AggregatingSubscriber implements Subscriber<RedisData> {

        private static final int STATE_IDLE = 0;
        private static final int STATE_AGGR_TYPE = 1;
        private static final int STATE_AGGR_PATTERN = 2;
        private static final int STATE_AGGR_CHANNEL = 3;
        private static final int STATE_AGGR_DATA = 4;

        private int aggregationState;

        private final Subscriber<? super PubSubChannelMessage> target;
        private final BufferAllocator allocator;

        @Nullable
        private ConcurrentSubscription subscription;

        // Current message aggregation state
        private int msgArraySize;
        @Nullable
        private PubSubChannelMessage.KeyType currentKeyType;
        @Nullable
        private PubSubChannelMessage.MessageType currentMessageType;
        @Nullable
        private String currentChannelName;
        @Nullable
        private String currentPatternName;
        @Nullable
        private RedisData.CompleteRedisData currentMessageData;
        @Nullable
        private Buffer currentDataBuffer;

        AggregatingSubscriber(Subscriber<? super PubSubChannelMessage> target, BufferAllocator allocator) {
            this.target = target;
            this.allocator = allocator;
        }

        @Override
        public void onSubscribe(final Subscription s) {
            if (!checkDuplicateSubscription(subscription, s)) {
                return;
            }

            subscription = ConcurrentSubscription.wrap(s);
            target.onSubscribe(subscription);
        }

        @Override
        public void onNext(final RedisData data) {
            assert subscription != null : "Subscription can not be null in onNext.";
            assert data != null : "Data can not be null.";
            if (data instanceof SimpleString && aggregationState == STATE_IDLE) {
                // Simple strings received when not aggregating are passed through.
                // This can happen when using one of the commands allowed in InternalSubscribedRedisConnection#request before sending the SUBSCRIBE request.
                // For example, a +OK response is received when sending AUTH right after creating the InternalSubscribedRedisConnection.
                target.onNext(new PubSubChannelMessage(SimpleString, (SimpleString) data));
                return;
            }
            if (data instanceof RedisData.ArraySize) {
                if (aggregationState != STATE_IDLE) {
                    // Do not expect array data type but for start of a message.
                    throw new IllegalStateException("Unexpected data type: " + RedisData.ArraySize.class.getName()
                            + ". Current State: " + aggregationState);
                }
                aggregationState = STATE_AGGR_TYPE;
                msgArraySize = (int) data.getLongValue();
                // We request `size` because at least `size` CompleteRedisData are expected
                subscription.request(msgArraySize);
                return;
            }

            if (aggregationState <= STATE_IDLE) {
                // If this is not an array size data then we should be parsing one of the fields.
                throw new IllegalStateException("Unexpected data type: " + data.getClass().getName() + ". Current State: " + aggregationState);
            }

            if (data instanceof RedisData.BulkStringSize) {
                final int bufferSize = data.getIntValue();
                currentDataBuffer = allocator.newBuffer(bufferSize);
                // Request 1 because there's at least one extra BulkStringChunk needed to complete this BulkString
                subscription.request(1);
                return;
            }

            if (data instanceof CompleteRedisData) {
                if (currentDataBuffer != null && currentDataBuffer.getReadableBytes() > 0) {
                    throw new IllegalStateException("Incomplete buffer exists " + currentDataBuffer.toString(defaultCharset())
                            + " but got " + data.getClass().getSimpleName() + ", current state: " + aggregationState);
                }
                storeCompletedMessage((CompleteRedisData) data);
            } else if (data instanceof RedisData.BulkStringChunk) {
                if (currentDataBuffer == null) {
                    throw new IllegalStateException("Received a bulk string chunk without size.");
                }

                currentDataBuffer.writeBytes(data.getBufferValue());
                if (data instanceof RedisData.LastBulkStringChunk) {
                    CompleteBulkString val = new CompleteBulkString(currentDataBuffer);
                    currentDataBuffer = null;
                    storeCompletedMessage(val);
                } else {
                    // Request 1 because there's at least one extra BulkStringChunk needed to complete this BulkString
                    subscription.request(1);
                }
            } else {
                throw new IllegalStateException("Unknown data type while aggregation: " + data.getClass().getSimpleName()
                        + ", current state: " + aggregationState);
            }
        }

        private void storeCompletedMessage(CompleteRedisData data) {
            switch (aggregationState) {
                case STATE_AGGR_TYPE:
                    if (SUBSCRIBE_PUBSUB_MESSAGE_TYPE.equals(data)) {
                        currentMessageType = SUBSCRIBE_ACK;
                        currentKeyType = Channel;
                    } else if (MESSAGE_PUBSUB_MESSAGE_TYPE.equals(data)) {
                        currentMessageType = DATA;
                        currentKeyType = Channel;
                    } else if (UNSUBSCRIBE_PUBSUB_MESSAGE_TYPE.equals(data)) {
                        currentMessageType = UNSUBSCRIBE;
                        currentKeyType = Channel;
                    } else if (PSUBSCRIBE_PUBSUB_MESSAGE_TYPE.equals(data)) {
                        currentMessageType = SUBSCRIBE_ACK;
                        currentKeyType = Pattern;
                    } else if (PMESSAGE_PUBSUB_MESSAGE_TYPE.equals(data)) {
                        currentMessageType = DATA;
                        currentKeyType = Pattern;
                    } else if (PUNSUBSCRIBE_PUBSUB_MESSAGE_TYPE.equals(data)) {
                        currentMessageType = UNSUBSCRIBE;
                        currentKeyType = Pattern;
                    } else if (PONG_PUBSUB_MESSAGE_TYPE.equals(data)) {
                        currentMessageType = DATA;
                        currentKeyType = Ping;
                    } else if (OK_PUBSUB_MESSAGE_TYPE.equals(data)) {
                        currentMessageType = DATA;
                        currentKeyType = Quit;
                    } else {
                        throw new IllegalStateException("Unexpected message type: " + data);
                    }
                    if (currentKeyType == Pattern) {
                        aggregationState = STATE_AGGR_PATTERN;
                    } else {
                        // We could be parsing a ping/quit response, which will not have channel name.
                        aggregationState = msgArraySize > 2 ? STATE_AGGR_CHANNEL : STATE_AGGR_DATA;
                    }
                    break;
                case STATE_AGGR_PATTERN:
                    currentPatternName = RedisUtils.convertToString(data);
                    if (currentMessageType != DATA) {
                        // If this is not data then it is (un)subscribe ack which will contain the same name
                        // as channel and pattern.
                        currentChannelName = currentPatternName;
                        aggregationState = STATE_AGGR_DATA;
                    } else {
                        aggregationState = STATE_AGGR_CHANNEL;
                    }
                    break;
                case STATE_AGGR_CHANNEL:
                    currentChannelName = RedisUtils.convertToString(data);
                    aggregationState = STATE_AGGR_DATA;
                    break;
                case STATE_AGGR_DATA:
                    currentMessageData = data;
                    aggregationState = STATE_IDLE;
                    if (currentMessageType == null || currentKeyType == null) {
                        throw new IllegalStateException("Received message data but no type.");
                    }
                    final PubSubChannelMessage msg;
                    if (!currentKeyType.isForChannel()) {
                        assert currentMessageType == DATA;
                        msg = new PubSubChannelMessage(currentKeyType, currentMessageData);
                    } else if (currentKeyType == Channel) {
                        assert currentChannelName != null;
                        msg = new PubSubChannelMessage(currentMessageType, currentChannelName, currentMessageData);
                    } else {
                        assert currentKeyType == Pattern;
                        assert currentChannelName != null && currentPatternName != null;
                        msg = new PubSubChannelMessage(currentMessageType, currentPatternName, currentChannelName, currentMessageData);
                    }
                    currentPatternName = null;
                    currentChannelName = null;
                    currentKeyType = null;
                    currentMessageType = null;
                    currentMessageData = null;
                    currentDataBuffer = null;

                    target.onNext(msg);
                    break;
                default:
                    throw new IllegalStateException("Unknown aggregation state, code: " + aggregationState);
            }
        }

        @Override
        public void onError(final Throwable t) {
            target.onError(t);
        }

        @Override
        public void onComplete() {
            // Sanity check
            if (aggregationState != STATE_IDLE) {
                target.onError(new IllegalStateException("Aggregation not completed but stream completed. Current state: " + aggregationState));
            } else {
                target.onComplete();
            }
        }
    }
}
