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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.RejectedSubscribeException;
import io.servicetalk.concurrent.internal.TerminalNotification;
import io.servicetalk.redis.api.PubSubRedisMessage.ChannelPubSubRedisMessage;
import io.servicetalk.redis.api.PubSubRedisMessage.PatternPubSubRedisMessage;
import io.servicetalk.redis.api.RedisClient.ReservedRedisConnection;
import io.servicetalk.redis.api.RedisConnection;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisData.CompleteBulkString;
import io.servicetalk.redis.api.RedisRequest;
import io.servicetalk.redis.netty.SubscribedChannelReadStream.PubSubChannelMessage;
import io.servicetalk.redis.netty.SubscribedChannelReadStream.PubSubChannelMessage.KeyType;

import org.junit.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.nio.channels.ClosedChannelException;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.concurrent.api.BlockingTestUtils.awaitIndefinitely;
import static io.servicetalk.concurrent.internal.ServiceTalkTestTimeout.DEFAULT_TIMEOUT_SECONDS;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.MONITOR;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.PING;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.PSUBSCRIBE;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.SUBSCRIBE;
import static io.servicetalk.redis.api.RedisRequests.newRequest;
import static io.servicetalk.redis.netty.RedisDataMatcher.redisCompleteBulkString;
import static io.servicetalk.redis.netty.RedisDataMatcher.redisSimpleString;
import static io.servicetalk.redis.netty.SubscribedChannelReadStream.PubSubChannelMessage.KeyType.Channel;
import static io.servicetalk.redis.netty.SubscribedChannelReadStream.PubSubChannelMessage.KeyType.Pattern;
import static io.servicetalk.redis.netty.SubscribedChannelReadStream.PubSubChannelMessage.KeyType.Ping;
import static io.servicetalk.redis.netty.SubscribedChannelReadStream.PubSubChannelMessage.MessageType.DATA;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.parseBoolean;
import static java.lang.Thread.currentThread;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.Matchers.emptyCollectionOf;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeThat;

public class SubscribedRedisClientTest extends BaseRedisClientTest {
    static final class AccumulatingSubscriber<T> implements Subscriber<T> {
        private final Semaphore onNextSemaphore = new Semaphore(0);
        private final Deque<T> received = new ConcurrentLinkedDeque<>();
        private final CountDownLatch onSubscribe = new CountDownLatch(1);
        private final CountDownLatch onTerminal = new CountDownLatch(1);

        @Nullable
        private volatile Subscription s;
        @Nullable
        private volatile TerminalNotification terminal;

        AccumulatingSubscriber<T> subscribe(final Publisher<T> publisher) {
            publisher.subscribe(this);
            return this;
        }

        AccumulatingSubscriber<T> request(final long request) {
            final Subscription subscription = s;
            assert subscription != null : "Subscription can not be null.";
            subscription.request(request);
            return this;
        }

        AccumulatingSubscriber<T> cancel() {
            final Subscription subscription = s;
            assert subscription != null : "Subscription can not be null.";
            subscription.cancel();
            return this;
        }

        boolean awaitUntilAtLeastNReceived(final int n, final long timeout, final TimeUnit unit)
                throws InterruptedException {
            return onNextSemaphore.tryAcquire(n, timeout, unit);
        }

        boolean awaitSubscription(final long timeout, final TimeUnit unit) throws InterruptedException {
            return onSubscribe.await(timeout, unit);
        }

        boolean awaitTerminal(final long timeout, final TimeUnit unit) throws InterruptedException {
            return onTerminal.await(timeout, unit);
        }

        Deque<T> getReceived() {
            return received;
        }

        @Nullable
        TerminalNotification getTerminal() {
            return terminal;
        }

        @Override
        public void onSubscribe(final Subscription s) {
            this.s = s;
            onSubscribe.countDown();
        }

        @Override
        public void onNext(final T data) {
            received.offer(data);
            onNextSemaphore.release(1);
        }

        @Override
        public void onError(final Throwable t) {
            terminal = TerminalNotification.error(t);
            onTerminal.countDown();
        }

        @Override
        public void onComplete() {
            terminal = complete();
            onTerminal.countDown();
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void monitor() throws Exception {
        final RedisRequest monitorRequest = newRequest(MONITOR);

        final ReservedRedisConnection cnx = awaitIndefinitely(getEnv().client.reserveConnection(MONITOR));
        assert cnx != null : "Connection can not be null";

        final AccumulatingSubscriber<RedisData> subscriber = new AccumulatingSubscriber<>();
        cnx.request(monitorRequest).subscribe(subscriber);
        assertThat(subscriber.request(1).awaitUntilAtLeastNReceived(1, DEFAULT_TIMEOUT_SECONDS, SECONDS), is(true));
        assertThat(subscriber.getReceived().poll(), is(redisSimpleString("OK")));

        final RedisData pong = awaitIndefinitely(getEnv().client.request(newRequest(PING)).first());
        assertThat(pong, is(redisSimpleString("PONG")));

        assertThat(subscriber.request(1).awaitUntilAtLeastNReceived(1, DEFAULT_TIMEOUT_SECONDS, SECONDS), is(true));
        assertThat(subscriber.getReceived().poll(), is(redisSimpleString(endsWith("\"PING\""))));

        awaitIndefinitely(cnx.closeAsync());

        // Check that the violent termination of the subscription has been detected
        assertThat(subscriber.awaitTerminal(DEFAULT_TIMEOUT_SECONDS, SECONDS), is(true));
        assertThat(subscriber.getTerminal().getCause(), is(instanceOf(ClosedChannelException.class)));
    }

    @Test
    public void pubSubUntilCancel() throws Exception {
        final RedisRequest subscribeRequest = newRequest(SUBSCRIBE, new CompleteBulkString(buf("test-channel-1")));

        ReservedRedisConnection cnx = awaitIndefinitely(getEnv().client.reserveConnection(SUBSCRIBE));
        assert cnx != null : "Connection can not be null";
        testSubscribeUnsubscribe(cnx, cnx.request(subscribeRequest));

        cnx = awaitIndefinitely(getEnv().client.reserveConnection(SUBSCRIBE));
        assert cnx != null : "Connection can not be null";
        // Ensure we can actually re-do all this a second time (i.e. that no old state lingered)
        testSubscribeUnsubscribe(cnx, cnx.request(subscribeRequest));
        awaitIndefinitely(cnx.releaseAsync());
    }

    private void testSubscribeUnsubscribe(final RedisConnection cnx, final Publisher<RedisData> messages)
            throws Exception {
        final AccumulatingSubscriber<RedisData> messagesSubscriber = new AccumulatingSubscriber<RedisData>()
                .subscribe(messages);

        // Check ping requests are honored with proper responses
        RedisData pong = awaitIndefinitely(cnx.request(newRequest(PING)).first());
        checkValidPing(pong, EMPTY_BUFFER);

        pong = awaitIndefinitely(cnx.request(newRequest(PING, new CompleteBulkString(buf("my-pong")))).first());
        checkValidPing(pong, buf("my-pong"));

        // Check ping requests also work without "first"
        pong = awaitIndefinitely(cnx.request(newRequest(PING))).get(0);
        checkValidPing(pong, EMPTY_BUFFER);

        pong = awaitIndefinitely(cnx.request(newRequest(PING, new CompleteBulkString(buf("other-pong"))))).get(0);
        checkValidPing(pong, buf("other-pong"));

        // Cancel the subscriber, which issues an UNSUBSCRIBE behind the scenes
        messagesSubscriber.cancel();
    }

    @Test
    public void pubSubUntilClose() throws Exception {
        final RedisRequest subscribeRequest = newRequest(SUBSCRIBE, new CompleteBulkString(buf("test-channel-2")));
        final ReservedRedisConnection cnx = awaitIndefinitely(getEnv().client.reserveConnection(SUBSCRIBE));
        final Publisher<RedisData> messages = cnx.request(subscribeRequest);

        final AccumulatingSubscriber<RedisData> messagesSubscriber = new AccumulatingSubscriber<RedisData>()
                .subscribe(messages);
        awaitIndefinitely(cnx.closeAsync());

        // Check that the violent termination of the connection has been detected
        assertThat(messagesSubscriber.awaitTerminal(DEFAULT_TIMEOUT_SECONDS, SECONDS), is(true));
        assertThat(messagesSubscriber.getTerminal().getCause(), is(instanceOf(ClosedChannelException.class)));
    }

    @Test
    public void pubSubMultipleSubscribes() throws Exception {
        assumeThat("Ignored flaky test", parseBoolean(System.getenv("CI")), is(FALSE));
        final RedisRequest subscribeRequest = newRequest(SUBSCRIBE, new CompleteBulkString(buf("test-channel-3")));

        final ReservedRedisConnection cnx = awaitIndefinitely(getEnv().client.reserveConnection(SUBSCRIBE));
        assert cnx != null : "Connection can not be null";

        CountDownLatch latch = new CountDownLatch(1);

        final Publisher<RedisData> messages1 = cnx.request(subscribeRequest).doAfterSubscribe(__ -> latch.countDown());

        final AccumulatingSubscriber<RedisData> messages1Subscriber = new AccumulatingSubscriber<RedisData>()
                .subscribe(messages1);

        latch.await();

        // Publish a test message
        publishTestMessage("test-channel-3");

        // Check ping request get proper response
        final RedisData pong = awaitIndefinitely(cnx.request(newRequest(PING)).first());
        checkValidPing(pong, EMPTY_BUFFER);

        // Subscribe to a pattern on the same connection
        final Publisher<RedisData> messages2 = cnx.request(newRequest(PSUBSCRIBE,
                new CompleteBulkString(buf("test-channel-4*"))));
        final AccumulatingSubscriber<RedisData> messages2Subscriber =
                new AccumulatingSubscriber<RedisData>().subscribe(messages2);

        // Let's throw a wrench and psubscribe a second time to the same pattern
        final Publisher<RedisData> messages3 = cnx.request(newRequest(PSUBSCRIBE,
                new CompleteBulkString(buf("test-channel-4*"))));
        final AccumulatingSubscriber<RedisData> messages3Subscriber = new AccumulatingSubscriber<RedisData>()
                .subscribe(messages3);

        assertThat(messages3Subscriber.awaitTerminal(DEFAULT_TIMEOUT_SECONDS, SECONDS), is(true));
        assertThat(messages3Subscriber.getTerminal().getCause(), is(instanceOf(RejectedSubscribeException.class)));

        // Publish another test message
        publishTestMessage("test-channel-404");

        // Check ping request still get proper response
        final RedisData myPong = awaitIndefinitely(cnx.request(newRequest(PING,
                new CompleteBulkString(buf("my-pong")))).first());
        checkValidPing(myPong, buf("my-pong"));

        assertThat(messages1Subscriber.request(1).awaitUntilAtLeastNReceived(1, DEFAULT_TIMEOUT_SECONDS, SECONDS),
                is(true));
        checkValidPubSubData(messages1Subscriber.getReceived().poll(), Channel, "test-channel-3", buf("test-message"));

        // Cancel the subscriber, which issues an UNSUBSCRIBE behind the scenes
        messages1Subscriber.cancel();

        assertThat(messages2Subscriber.request(1).awaitUntilAtLeastNReceived(1, DEFAULT_TIMEOUT_SECONDS, SECONDS),
                is(true));
        checkValidPubSubData(messages2Subscriber.getReceived().poll(), Pattern, "test-channel-404", "test-channel-4*",
                buf("test-message"));
        // Cancel the subscriber, which issues an PUNSUBSCRIBE behind the scenes
        messages2Subscriber.cancel();
    }

    @Test
    public void pingVerifyRequested() throws Exception {
        final RedisRequest subscribeRequest = newRequest(SUBSCRIBE, new CompleteBulkString(buf("test-channel-7")));
        final ReservedRedisConnection cnx = awaitIndefinitely(getEnv().client.reserveConnection(SUBSCRIBE));
        assert cnx != null : "Connection can not be null";

        final Publisher<RedisData> messages = cnx.request(subscribeRequest);
        new AccumulatingSubscriber<RedisData>().subscribe(messages);

        Publisher<RedisData> ping1 = cnx.request(newRequest(PING, new CompleteBulkString(buf("ping-1"))));
        PingSubscriber pingSubscriber = new PingSubscriber();
        ping1.subscribe(pingSubscriber);
        pingSubscriber.awaitOnSubscribe().requestAndAwaitResponse();
    }

    @Test
    public void pubSubSequentialInterleavedRequestResponse() throws Exception {
        testPubSubInterleavedRequestResponse(false);
    }

    @Test
    public void pubSubConcurrentInterleavedRequestResponse() throws Exception {
        testPubSubInterleavedRequestResponse(true);
    }

    @Test
    public void pubSubBatchedPings() throws Exception {
        final RedisRequest subscribeRequest = newRequest(SUBSCRIBE, new CompleteBulkString(buf("test-channel-6")));
        final ReservedRedisConnection cnx = awaitIndefinitely(getEnv().client.reserveConnection(SUBSCRIBE));
        assert cnx != null : "Connection can not be null";
        final Publisher<RedisData> messages = cnx.request(subscribeRequest);
        final AccumulatingSubscriber<RedisData> messagesSubscriber = new AccumulatingSubscriber<RedisData>()
                .subscribe(messages);

        Map<Buffer, AccumulatingSubscriber<RedisData>> pingSubscribers = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            Buffer pingData = buf("ping-" + i);
            final AccumulatingSubscriber<RedisData> pingSubscriber = new AccumulatingSubscriber<RedisData>()
                    .subscribe(cnx.request(newRequest(PING, new CompleteBulkString(pingData))));
            assertThat(pingSubscriber.awaitSubscription(DEFAULT_TIMEOUT_SECONDS, SECONDS), is(true));
            pingSubscribers.put(pingData, pingSubscriber);
        }

        pingSubscribers.forEach((pingData, pingSubscriber) -> {
            try {
                pingSubscriber.request(1);
                assertThat(pingSubscriber.awaitUntilAtLeastNReceived(1, DEFAULT_TIMEOUT_SECONDS, SECONDS), is(true));
                assertThat(pingSubscriber.awaitTerminal(DEFAULT_TIMEOUT_SECONDS, SECONDS), is(true));
                checkValidPing(pingSubscriber.getReceived().poll(), pingData);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        messagesSubscriber.cancel();
    }

    @Test
    public void actualChannelInPattern() throws Exception {
        final RedisRequest subscribeRequest = newRequest(PSUBSCRIBE, new CompleteBulkString(buf("test-channel-7*")));
        final ReservedRedisConnection cnx = awaitIndefinitely(getEnv().client.reserveConnection(PSUBSCRIBE));
        assert cnx != null : "Connection can not be null";

        BlockingQueue<AccumulatingSubscriber<PatternPubSubRedisMessage>> groupSubs = new LinkedBlockingQueue<>();

        CountDownLatch latch = new CountDownLatch(1);
        cnx.request(subscribeRequest)
                .doAfterSubscribe(__ -> latch.countDown())
                .map(msg -> (PatternPubSubRedisMessage) msg)
                .groupBy(ChannelPubSubRedisMessage::getChannel, 32)
                .forEach(grp -> {
                    AccumulatingSubscriber<PatternPubSubRedisMessage> sub =
                            new AccumulatingSubscriber<PatternPubSubRedisMessage>().subscribe(grp);
                    sub.request(Long.MAX_VALUE);
                    groupSubs.offer(sub);
                });

        latch.await();
        // Publish a test message
        publishTestMessage("test-channel-77");

        AccumulatingSubscriber<PatternPubSubRedisMessage> channel77 = groupSubs.take();
        assertThat(channel77.awaitUntilAtLeastNReceived(1, DEFAULT_TIMEOUT_SECONDS, SECONDS), is(true));
        PatternPubSubRedisMessage msg77 = channel77.getReceived().poll();
        checkValidPubSubData(msg77, Pattern, "test-channel-77", "test-channel-7*", buf("test-message"));

        // Publish a test message
        publishTestMessage("test-channel-78");

        AccumulatingSubscriber<PatternPubSubRedisMessage> channel78 = groupSubs.take();
        assertThat(channel78.awaitUntilAtLeastNReceived(1, DEFAULT_TIMEOUT_SECONDS, SECONDS), is(true));
        PatternPubSubRedisMessage msg78 = channel78.getReceived().poll();
        checkValidPubSubData(msg78, Pattern, "test-channel-78", "test-channel-7*", buf("test-message"));

        // Publish a test message
        publishTestMessage("test-channel-77");

        assertThat(channel77.awaitUntilAtLeastNReceived(1, DEFAULT_TIMEOUT_SECONDS, SECONDS), is(true));
        msg77 = channel77.getReceived().poll();
        checkValidPubSubData(msg77, Pattern, "test-channel-77", "test-channel-7*", buf("test-message"));

        assertThat("Unexpected group emitted.", groupSubs, hasSize(0));
    }

    private void testPubSubInterleavedRequestResponse(final boolean concurrentPings) throws Exception {
        // Start a message publisher that concurrently sends message to the test channel
        final AtomicBoolean publishMessages = new AtomicBoolean(true);
        final Thread messagePublisher = new Thread(() -> {
            try {
                while (publishMessages.get()) {
                    publishTestMessage("test-channel-5");
                    Thread.sleep(10);
                }
            } catch (final Exception e) {
                e.printStackTrace();
            }
        });
        messagePublisher.start();

        // Subscribe to the channel
        final RedisRequest subscribeRequest = newRequest(SUBSCRIBE, new CompleteBulkString(buf("test-channel-5")));
        final ReservedRedisConnection cnx = awaitIndefinitely(getEnv().client.reserveConnection(SUBSCRIBE));
        assert cnx != null : "Connection can not be null";

        final Publisher<RedisData> messages = cnx.request(subscribeRequest);
        final AccumulatingSubscriber<RedisData> messagesSubscriber = new AccumulatingSubscriber<RedisData>()
                .subscribe(messages);

        // Send a bunch of ping requests
        if (concurrentPings) {
            sendConcurrentPings(cnx);
        } else {
            sendSequentialPings(cnx);
        }

        // Capture a bunch of subscription messages
        assertThat(messagesSubscriber.request(60).awaitUntilAtLeastNReceived(50, 10, SECONDS), is(true));

        // Terminate the message publisher
        publishMessages.set(false);
        messagePublisher.join(5000);

        // Cancel the subscriber, which issues an UNSUBSCRIBE behind the scenes
        messagesSubscriber.cancel();

        // Check that no interleaved response has been mixed in all the received messages
        final Deque<RedisData> receivedMessages = messagesSubscriber.getReceived();
        RedisData msg;
        while ((msg = receivedMessages.poll()) != null) {
            checkValidPubSubData(msg, Channel, "test-channel-5", buf("test-message"));
        }
    }

    private void sendConcurrentPings(final ReservedRedisConnection cnx) throws Exception {
        final CyclicBarrier barrier = new CyclicBarrier(11);
        final ExecutorService executor = Executors.newFixedThreadPool(10);
        final List<Throwable> throwables = new CopyOnWriteArrayList<>();

        for (int i = 0; i < 10; i++) {
            executor.submit(() -> {
                try {
                    barrier.await(DEFAULT_TIMEOUT_SECONDS, SECONDS);
                    sendSequentialPings(cnx);
                } catch (final Throwable t) {
                    throwables.add(t);
                }
            });
        }

        barrier.await(DEFAULT_TIMEOUT_SECONDS, SECONDS);

        executor.shutdown();
        executor.awaitTermination(DEFAULT_TIMEOUT_SECONDS, SECONDS);

        assertThat(throwables, is(emptyCollectionOf(Throwable.class)));
    }

    private void sendSequentialPings(final ReservedRedisConnection cnx) throws Exception {
        for (int i = 0; i < 100; i++) {
            final String pingMessage = "ping-" + currentThread().getName() + "-" + i;
            final Buffer ping = buf(pingMessage);

            // Check that interleaved requests work both with and without "first"
            final RedisData pong;
            if (i % 2 == 0) {
                pong = awaitIndefinitely(cnx.request(newRequest(PING, new CompleteBulkString(ping))).first());
            } else {
                pong = awaitIndefinitely(cnx.request(newRequest(PING, new CompleteBulkString(ping)))).get(0);
            }
            checkValidPing(pong, ping);
        }
    }

    static void checkValidPing(@Nullable final RedisData pongRaw, final Buffer expectedData) {
        assertThat(pongRaw, is(notNullValue()));
        assertThat(pongRaw, is(instanceOf(PubSubChannelMessage.class)));
        final PubSubChannelMessage pong = (PubSubChannelMessage) pongRaw;
        assertThat(pong.getKeyType(), is(Ping));
        assertThat(pong.getData(), is(redisCompleteBulkString(expectedData)));
    }

    static void checkValidPubSubData(@Nullable final RedisData dataRaw, final KeyType expectedKeyType,
                                     final String channelName, final Buffer expectedData) {
        assertThat(dataRaw, is(notNullValue()));
        assertThat(dataRaw, is(instanceOf(PubSubChannelMessage.class)));

        final PubSubChannelMessage data = (PubSubChannelMessage) dataRaw;
        assertThat(data.getKeyType(), is(expectedKeyType));
        assertThat(data.getMessageType(), is(DATA));
        assertThat(data.getChannel(), is(channelName));
        assertThat(data.getData(), is(redisCompleteBulkString(expectedData)));
    }

    static void checkValidPubSubData(@Nullable final RedisData dataRaw, final KeyType expectedKeyType,
                                     final String channelName, final String pattern, final Buffer expectedData) {
        checkValidPubSubData(dataRaw, expectedKeyType, channelName, expectedData);
        assert dataRaw != null;
        assertThat(((PubSubChannelMessage) dataRaw).getPattern(), is(pattern));
    }

    private static final class PingSubscriber implements Subscriber<RedisData> {

        private final CountDownLatch subscribed;
        private final CountDownLatch dataReceived;
        private final CountDownLatch termReceived;
        @Nullable
        private volatile Subscription subscription;
        @Nullable
        private volatile TerminalNotification notification;

        PingSubscriber() {
            subscribed = new CountDownLatch(1);
            dataReceived = new CountDownLatch(1);
            termReceived = new CountDownLatch(1);
        }

        @Override
        public void onSubscribe(Subscription s) {
            assertThat("Received null Subscription.", s, is(notNullValue()));
            subscription = s;
            subscribed.countDown();
        }

        @Override
        public void onNext(RedisData redisData) {
            assertThat("Received null RedisData.", redisData, is(notNullValue()));
            dataReceived.countDown();
        }

        @Override
        public void onError(Throwable t) {
            assertThat("Received null Throwable.", t, is(notNullValue()));
            if (notification != null) {
                throw new IllegalStateException("Duplicate terminal notification. Existing: " + notification +
                        ", new: " + t);
            }
            notification = TerminalNotification.error(t);
            termReceived.countDown();
        }

        @Override
        public void onComplete() {
            if (notification != null) {
                throw new IllegalStateException("Duplicate terminal notification. Existing: " + notification +
                        ", new: " + complete());
            }
            notification = TerminalNotification.complete();
            termReceived.countDown();
        }

        PingSubscriber awaitOnSubscribe() throws InterruptedException {
            subscribed.await();
            return this;
        }

        void requestAndAwaitResponse() throws InterruptedException {
            Subscription s = subscription;
            assertThat("Subscription should not be null at time of request.", s, is(notNullValue()));
            s.request(1);
            dataReceived.await();
            termReceived.await();
            TerminalNotification n = notification;
            assertThat("Completion not received.", n, is(notNullValue()));
            assertThat("Completion not received.", n.getCause(), is(nullValue()));
        }
    }
}
