/**
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

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.concurrent.internal.TerminalNotification;
import io.servicetalk.redis.api.PubSubRedisConnection;
import io.servicetalk.redis.api.PubSubRedisMessage;
import io.servicetalk.redis.api.RedisCommander;
import io.servicetalk.redis.api.RedisConnection;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisRequest;
import io.servicetalk.transport.api.IoExecutorGroup;
import io.servicetalk.transport.netty.NettyIoExecutors;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.SUBSCRIBE;
import static io.servicetalk.redis.netty.DefaultRedisConnectionBuilder.forPipeline;
import static io.servicetalk.redis.netty.RedisTestUtils.randomStringOfLength;
import static io.servicetalk.transport.api.FlushStrategy.defaultFlushStrategy;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assume.assumeThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class InternalSubscribedRedisConnectionTest {

    @Rule
    public final ServiceTalkTestTimeout timeout = new ServiceTalkTestTimeout(30, SECONDS);

    @Nullable private static IoExecutorGroup group;
    @Nullable private static DefaultRedisConnectionBuilder<InetSocketAddress> builder;
    @Nullable private static InetSocketAddress redisAddress;

    @BeforeClass
    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    public static void setUp() {
        final String tmpRedisPort = System.getenv("REDIS_PORT");
        assumeThat(tmpRedisPort, not(isEmptyOrNullString()));
        int redisPort = Integer.parseInt(tmpRedisPort);
        String redisHost = System.getenv().getOrDefault("REDIS_HOST", "127.0.0.1");
        redisAddress = InetSocketAddress.createUnresolved(redisHost, redisPort);
        group = NettyIoExecutors.createGroup();
        builder = DefaultRedisConnectionBuilder.<InetSocketAddress>forSubscribe()
                .setPingPeriod(Duration.ofSeconds(1)).setIdleConnectionTimeout(Duration.ofSeconds(2));
    }

    @AfterClass
    public static void tearDown() {
        if (group != null) {
            group.closeAsync(0, 0, SECONDS).subscribe();
        }
    }

    @Test
    public void testWriteCancelAndClose() throws ExecutionException, InterruptedException {
        assert builder != null && redisAddress != null && group != null;

        TestPublisher<RedisData.RequestRedisData> requestContent = new TestPublisher<>();
        requestContent.sendOnSubscribe();
        CountDownLatch requestStreamCancelled = new CountDownLatch(1);
        RedisRequest mockRequest = newMockRequest(requestContent.doBeforeCancel(requestStreamCancelled::countDown));

        RedisConnection connection = awaitIndefinitely(builder.build(group, redisAddress));
        assert connection != null;

        Subscription subscription = subscribeToResponse(connection.request(mockRequest), new ConcurrentLinkedQueue<>());
        subscription.cancel();
        requestStreamCancelled.await();

        awaitIndefinitely(connection.closeAsync());
    }

    @Test
    public void testReadCancelAndClose() throws ExecutionException, InterruptedException {
        assert builder != null && redisAddress != null && group != null;

        RedisConnection connection = awaitIndefinitely(builder.build(group, redisAddress));
        assert connection != null;
        RedisCommander commander = connection.asCommander();

        CharSequence channelToSubscribe = randomStringOfLength(32);
        Publisher<PubSubRedisMessage> subscribeResponse = awaitIndefinitely(commander.subscribe(channelToSubscribe)
                .map(PubSubRedisConnection::getMessages));
        assert subscribeResponse != null : "Subscribe response stream can not be null.";

        LinkedBlockingQueue<Object> notifications = new LinkedBlockingQueue<>();
        Subscription subscription = subscribeToResponse(subscribeResponse, notifications);
        subscription.request(1);
        RedisConnection publishConnection = awaitIndefinitely(forPipeline().build(group, redisAddress));
        assert publishConnection != null;

        awaitIndefinitely(publishConnection.asCommander().publish(channelToSubscribe, randomStringOfLength(32)));

        // Await one message from subscribe response to make sure we have started reading.
        Object notification = notifications.take();
        assertThat("Unexpected notification from subscribe response stream.", notification, not(instanceOf(TerminalNotification.class)));

        subscription.cancel();

        awaitIndefinitely(connection.closeAsync());
    }

    private static <T> Subscription subscribeToResponse(Publisher<T> response, Queue<Object> notifications) throws InterruptedException {
        final BlockingQueue<Subscription> subscriptionExchanger = new LinkedBlockingQueue<>(1);
        @SuppressWarnings("unchecked") Subscriber<T> responseSubscriber = mock(Subscriber.class);
        doAnswer(invocation -> {
            subscriptionExchanger.put(invocation.getArgument(0));
            return null;
        }).when(responseSubscriber).onSubscribe(any());
        doAnswer(invocation -> {
            notifications.add(invocation.getArgument(0));
            return null;
        }).when(responseSubscriber).onNext(any());
        doAnswer(invocation -> {
            notifications.add(TerminalNotification.complete());
            return null;
        }).when(responseSubscriber).onComplete();
        doAnswer(invocation -> {
            notifications.add(TerminalNotification.error(invocation.getArgument(0)));
            return null;
        }).when(responseSubscriber).onError(any());

        response.subscribe(responseSubscriber);
        Subscription subscription = subscriptionExchanger.take();

        assert subscription != null : "subscription null post subscribe.";
        return subscription;
    }

    @Nonnull
    private static RedisRequest newMockRequest(Publisher<RedisData.RequestRedisData> requestContent) {
        RedisRequest mockRequest = mock(RedisRequest.class);
        when(mockRequest.getFlushStrategy()).thenReturn(defaultFlushStrategy());
        when(mockRequest.getCommand()).thenReturn(SUBSCRIBE);
        when(mockRequest.getContent()).thenReturn(requestContent);
        return mockRequest;
    }
}
