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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.GroupedPublisher;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.ConcurrentSubscription;
import io.servicetalk.concurrent.internal.QueueFullAndRejectedSubscribeException;
import io.servicetalk.concurrent.internal.RejectedSubscribeException;
import io.servicetalk.concurrent.internal.ScalarValueSubscription;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisProtocolSupport.Command;
import io.servicetalk.redis.api.RedisRequest;
import io.servicetalk.redis.netty.SubscribedChannelReadStream.PubSubChannelMessage;
import io.servicetalk.redis.netty.SubscribedChannelReadStream.PubSubChannelMessage.KeyType;
import io.servicetalk.redis.netty.SubscribedChannelReadStream.PubSubChannelMessage.MessageType;
import io.servicetalk.redis.netty.TerminalMessagePredicates.TerminalMessagePredicate;
import io.servicetalk.transport.netty.internal.NettyConnection;

import io.netty.buffer.ByteBuf;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.EmptySubscription.EMPTY_SUBSCRIPTION;
import static io.servicetalk.concurrent.internal.SubscriberUtils.checkDuplicateSubscription;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.PING;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.PUNSUBSCRIBE;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.UNSUBSCRIBE;
import static io.servicetalk.redis.api.RedisRequests.calculateInitialCommandBufferSize;
import static io.servicetalk.redis.api.RedisRequests.calculateRequestArgumentSize;
import static io.servicetalk.redis.api.RedisRequests.newRequest;
import static io.servicetalk.redis.api.RedisRequests.writeRequestArgument;
import static io.servicetalk.redis.api.RedisRequests.writeRequestArraySize;
import static io.servicetalk.redis.netty.SubscribedChannelReadStream.PubSubChannelMessage.KeyType.Channel;
import static io.servicetalk.redis.netty.SubscribedChannelReadStream.PubSubChannelMessage.KeyType.Pattern;
import static io.servicetalk.redis.netty.SubscribedChannelReadStream.PubSubChannelMessage.KeyType.SimpleString;
import static io.servicetalk.redis.netty.SubscribedChannelReadStream.PubSubChannelMessage.MessageType.SUBSCRIBE_ACK;
import static io.servicetalk.redis.netty.TerminalMessagePredicates.ZERO;
import static io.servicetalk.redis.netty.TerminalMessagePredicates.forCommand;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

/**
 * A splitter of {@link NettyConnection#read()} {@link Publisher} so that it can be used concurrently with multiple
 * {@link Command#SUBSCRIBE}, {@link Command#PSUBSCRIBE} or {@link Command#PING} commands.
 *
 * <h2>Assumptions</h2>
 * This class assumes the following usage:
 * <ul>
 *     <li>No concurrent calls to {@link #registerNewCommand(Command)}.</li>
 *     <li>No concurrent calls to all {@link Publisher} returned by {@link #registerNewCommand(Command)}.</li>
 *     <li>Calls to {@link Publisher#subscribe(Subscriber)} to {@link Publisher} returned by {@link #registerNewCommand(Command)} is exactly
 *     in the same order as {@link #registerNewCommand(Command)} is called.</li>
 * </ul>
 *
 * The above rules mean that the caller of {@link #registerNewCommand(Command)} MUST immediately subscribe to the returned {@link Publisher}
 * and then proceed to any further writes. These rules are followed by {@link InternalSubscribedRedisConnection}.
 */
final class ReadStreamSplitter {

    private static final int STATE_INIT = 0;
    private static final int STATE_READ_SUBSCRIBED = 1;
    private static final int STATE_TERMINATED = 2;

    private static final Logger LOGGER = LoggerFactory.getLogger(ReadStreamSplitter.class);

    private static final AtomicIntegerFieldUpdater<ReadStreamSplitter> pendingGroupRequestedUpdater =
            newUpdater(ReadStreamSplitter.class, "pendingGroupRequested");
    private static final AtomicIntegerFieldUpdater<ReadStreamSplitter> stateUpdater =
            newUpdater(ReadStreamSplitter.class, "state");

    private final Publisher<GroupedPublisher<Key, PubSubChannelMessage>> original;
    private final Queue<Subscriber<? super PubSubChannelMessage>> subscribers;
    private final LinkedPredicate predicate;
    private final NettyConnection<RedisData, ByteBuf> connection;
    private final Function<RedisRequest, Completable> unsubscribeWriter;

    @SuppressWarnings("unused")
    private volatile int pendingGroupRequested;
    @SuppressWarnings("unused")
    private volatile int state;
    @Nullable
    private volatile Subscription groupSubscription;

    ReadStreamSplitter(NettyConnection<RedisData, ByteBuf> connection, int maxConcurrentRequests, int maxBufferPerGroup, Function<RedisRequest, Completable> unsubscribeWriter) {
        this.connection = requireNonNull(connection);
        this.unsubscribeWriter = requireNonNull(unsubscribeWriter);
        this.original = new SubscribedChannelReadStream(connection.read(),
                connection.executionContext().bufferAllocator())
                .groupBy(new GroupSelector(), maxBufferPerGroup, maxConcurrentRequests);
        NettyConnection.TerminalPredicate<RedisData> terminalMsgPredicate = connection.terminalMsgPredicate();
        // Max pending is enforced by the upstream connection for writes, so this can be unbounded.
        // poll() could be invoked from a group onNext in case of duplicate redis (p)subscribe commands
        // for the same channel name/pattern
        subscribers = new ConcurrentLinkedQueue<>();
        predicate = new LinkedPredicate();
        terminalMsgPredicate.replaceCurrent(predicate);
    }

    /**
     * Registers new {@link Command} with this splitter and returns a response {@link Publisher} for that command.
     *
     * <h2>Assumptions</h2>
     * As defined by the class javadoc: {@link ReadStreamSplitter}.
     *
     * @param command {@link Command} to register.
     * @return {@link Publisher} containing response for this command.
     */
    Publisher<PubSubChannelMessage> registerNewCommand(Command command) {
        return new Publisher<PubSubChannelMessage>() {
            @Override
            protected void handleSubscribe(Subscriber<? super PubSubChannelMessage> subscriber) {
                TerminalMessagePredicate cmdPredicate = null;
                if (command == PING) {
                    cmdPredicate = forCommand(command);
                    predicate.addPredicate(cmdPredicate);
                }

                if (!subscribers.offer(subscriber)) {
                    if (cmdPredicate != null) {
                        predicate.remove(cmdPredicate);
                    }
                    subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
                    subscriber.onError(new QueueFullAndRejectedSubscribeException("subscribers-queue"));
                    return;
                }
                if (state == STATE_TERMINATED) {
                    if (subscribers.remove(subscriber)) {
                        subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
                        subscriber.onError(new RejectedSubscribeException(
                                "Connection read stream has already terminated."));
                    }
                    return;
                }
                Subscription groupSub = groupSubscription;
                if (groupSub == null) {
                    pendingGroupRequestedUpdater.incrementAndGet(ReadStreamSplitter.this);
                    requestPendingGroups(groupSubscription);
                } else {
                    groupSub.request(1); // Subscription is concurrent.
                }
                // Since writes are sequential, there is no concurrency here. So, we don't need to be atomic.
                if (stateUpdater.compareAndSet(ReadStreamSplitter.this, STATE_INIT, STATE_READ_SUBSCRIBED)) {
                    subscribeToOriginal();
                }
            }
        };
    }

    private void requestPendingGroups(@Nullable Subscription groupSubscription) {
        if (groupSubscription == null) {
            return;
        }
        for (;;) {
            int pending = pendingGroupRequestedUpdater.getAndSet(ReadStreamSplitter.this, 0);
            if (pending == 0) {
                return;
            }
            groupSubscription.request(pending);
        }
    }

    private void subscribeToOriginal() {
        original.subscribe(new Subscriber<GroupedPublisher<Key, PubSubChannelMessage>>() {

            @Override
            public void onSubscribe(Subscription s) {
                if (checkDuplicateSubscription(groupSubscription, s)) {
                    groupSubscription = ConcurrentSubscription.wrap(s);
                    requestPendingGroups(groupSubscription);
                }
            }

            @Override
            public void onNext(GroupedPublisher<Key, PubSubChannelMessage> group) {
                final Key key = group.getKey();
                if (key == Key.IGNORE_GROUP) {
                    group.ignoreElements().subscribe();
                    return;
                }
                Subscriber<? super PubSubChannelMessage> subscriber = subscribers.poll();
                if (subscriber == null) {
                    Subscription subscription = groupSubscription;
                    assert subscription != null : "Subscription can not be null in onNext.";
                    subscription.cancel();
                    LOGGER.error("Group {} received but no group subscriber registered.", group);
                    return;
                }

                group.filter(msg -> msg.getMessageType() == MessageType.DATA)
                     .subscribe(new GroupSubscriber(subscriber, key.getPChannel(), key.getKeyType() == Pattern));
            }

            @Override
            public void onError(Throwable t) {
                int oldState = stateUpdater.getAndSet(ReadStreamSplitter.this, STATE_TERMINATED);
                if (oldState != STATE_TERMINATED) {
                    for (;;) {
                        Subscriber<? super PubSubChannelMessage> next = subscribers.poll();
                        if (next == null) {
                            return;
                        }
                        next.onSubscribe(EMPTY_SUBSCRIPTION);
                        next.onError(t);
                    }
                }
            }

            @Override
            public void onComplete() {
                int oldState = stateUpdater.getAndSet(ReadStreamSplitter.this, STATE_TERMINATED);
                if (oldState != STATE_TERMINATED) {
                    RejectedSubscribeException cause = null;
                    for (;;) {
                        if (cause == null) {
                            cause = new RejectedSubscribeException(
                                    "Read stream completed with write commands pending.");
                        }
                        Subscriber<? super PubSubChannelMessage> next = subscribers.poll();
                        if (next == null) {
                            return;
                        }
                        next.onSubscribe(EMPTY_SUBSCRIPTION);
                        next.onError(cause);
                    }
                }
            }
        });
    }

    private final class GroupSubscriber implements Subscriber<PubSubChannelMessage> {

        private final Subscriber<? super PubSubChannelMessage> target;
        private final String channel;
        private final boolean isPatternSubscribe;

        private GroupSubscriber(Subscriber<? super PubSubChannelMessage> target, String channel, boolean isPatternSubscribe) {
            this.target = target;
            this.channel = channel;
            this.isPatternSubscribe = isPatternSubscribe;
        }

        @Override
        public void onSubscribe(Subscription s) {
            // Request SUBSCRIBE_ACK, since we filter this ack from the stream, this does not create a mismatch between what is requested and what is received.
            // This request will at max emit a terminal event since ACK is always the first item and it will be filtered before this Subscriber.
            s.request(1);
            target.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    s.request(n);
                }

                @Override
                public void cancel() {
                    final Command command = isPatternSubscribe ? PUNSUBSCRIBE : UNSUBSCRIBE;
                    final int capacity = calculateInitialCommandBufferSize(2, command) + calculateRequestArgumentSize(channel);
                    final Buffer buf = connection.executionContext().bufferAllocator().newBuffer(capacity);
                    writeRequestArraySize(buf, 2);
                    command.encodeTo(buf);
                    writeRequestArgument(buf, channel);
                    final RedisRequest request = newRequest(command, buf);
                    unsubscribeWriter.apply(request).subscribe(new Completable.Subscriber() {
                        @Override
                        public void onSubscribe(final Cancellable cancellable) {
                            // The cancel cannot be propagated because we don't want to cancel outside the scope of this group.
                        }

                        @Override
                        public void onComplete() {
                            // Cancel the group subscription. Unsubscribe ACKs come and gets processed on the main channel
                            // stream and this cancellation does not stop those messages from flowing in.
                            s.cancel();
                        }

                        @Override
                        public void onError(final Throwable t) {
                            LOGGER.debug("Failed sending unsubscribe to the server.", t);
                            // Cancel the group subscription. Unsubscribe ACKs come and gets processed on the main channel
                            // stream and this cancellation does not stop those messages from flowing in.
                            s.cancel();
                        }
                    });
                }
            });
        }

        @Override
        public void onNext(PubSubChannelMessage msg) {
            target.onNext(msg);
        }

        @Override
        public void onError(Throwable t) {
            target.onError(t);
        }

        @Override
        public void onComplete() {
            target.onComplete();
        }
    }

    private static final class LinkedPredicate implements Predicate<RedisData> {
        private final Queue<TerminalMessagePredicate> nonSubscribePredicates;
        volatile boolean unsubscribedFromAll;

        LinkedPredicate() {
            // Remove and poll can be concurrent.
            nonSubscribePredicates = new ConcurrentLinkedQueue<>();
        }

        void addPredicate(TerminalMessagePredicate predicate) {
            nonSubscribePredicates.add(predicate);
        }

        void remove(TerminalMessagePredicate predicate) {
            nonSubscribePredicates.remove(predicate);
        }

        @Override
        public boolean test(RedisData redisData) {
            TerminalMessagePredicate predicate = nonSubscribePredicates.peek();
            if (predicate != null) {
                predicate.trackMessage(redisData);
                if (predicate.test(redisData)) {
                    TerminalMessagePredicate polled = nonSubscribePredicates.poll(); // remove the peeked predicate.
                    assert polled == predicate : "Additional predicates queue modified (removed item) while testing.";
                }
            } else if (ZERO.equals(redisData)) {
                unsubscribedFromAll = true;
            }
            return false; // never complete. We close connection when we unsubscribe from all active subscribes.
        }
    }

    private final class GroupSelector implements Function<PubSubChannelMessage, Key> {

        private final Map<String, Key> pChannelToKey = new HashMap<>();

        @Override
        public Key apply(PubSubChannelMessage pubSubChannelMessage) {
            KeyType keyType = pubSubChannelMessage.getKeyType();
            if (isNotSubscribedCommand(keyType)) {
                handleNonSubscribeCommand(pubSubChannelMessage, keyType);
                return Key.IGNORE_GROUP; // Special value to signal ignore
            }
            final String pChannel = keyType == Pattern ? pubSubChannelMessage.getPattern() : pubSubChannelMessage.getChannel();
            assert pChannel != null;
            Key key = pChannelToKey.get(pChannel);
            if (key == null) {
                key = new Key(keyType, pChannel);
                if (pubSubChannelMessage.getMessageType() == SUBSCRIBE_ACK) {
                    pChannelToKey.put(pChannel, key);
                }
            } else if (pubSubChannelMessage.getMessageType() == SUBSCRIBE_ACK) {
                // Duplicate SUBSCRIBE, not supported.
                failDuplicateSubscriber(pChannel);
                return Key.IGNORE_GROUP; // Special value to signal ignore
            } else if (pubSubChannelMessage.getMessageType() == MessageType.UNSUBSCRIBE) {
                pChannelToKey.remove(pChannel); // Remove pChannel mapping post unsubscribe.
                if (predicate.unsubscribedFromAll) {
                    connection.closeAsync().subscribe();
                }
            }
            return key;
        }

        private void failDuplicateSubscriber(String channelStr) {
            Subscriber<? super PubSubChannelMessage> duplicate = subscribers.poll();
            if (duplicate == null) {
                throw new IllegalStateException("Duplicate subscribe ack received for channel: " + channelStr + " but no subscriber found.");
            }
            duplicate.onSubscribe(EMPTY_SUBSCRIPTION);
            duplicate.onError(new RejectedSubscribeException("A subscription to channel " + channelStr + " already exists."));
        }

        private void handleNonSubscribeCommand(PubSubChannelMessage pubSubChannelMessage, KeyType keyType) {
            // Since Redis does not multiplex any other command other than (P) Subscribe and we aggregate chunks,
            // all other responses must only contain a single message.
            Subscriber<? super PubSubChannelMessage> nextSub = subscribers.poll();
            if (nextSub == null) {
                throw new IllegalStateException("New message received for key: " + keyType + " but no subscriber found.");
            }
            nextSub.onSubscribe(new ScalarValueSubscription<>(pubSubChannelMessage, nextSub));
        }
    }

    private static boolean isNotSubscribedCommand(KeyType keyType) {
        return keyType != Channel && keyType != Pattern;
    }

    private static final class Key {

        private static final Key IGNORE_GROUP = new Key();

        private final KeyType keyType;
        private final String pChannel;

        private Key() {
            this(SimpleString, "");
        }

        Key(KeyType keyType, String pChannel) {
            this.keyType = keyType;
            this.pChannel = pChannel;
        }

        KeyType getKeyType() {
            return keyType;
        }

        String getPChannel() {
            return pChannel;
        }

        @Override
        public String toString() {
            return "Key{" +
                    "keyType=" + keyType +
                    ", pChannel='" + pChannel + '\'' +
                    '}';
        }
    }
}
