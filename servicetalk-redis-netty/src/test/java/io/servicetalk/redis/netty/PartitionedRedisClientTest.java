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

import io.servicetalk.client.api.partition.PartitionAttributes;
import io.servicetalk.client.api.partition.UnknownPartitionException;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.redis.api.PartitionedRedisClient;
import io.servicetalk.redis.api.PubSubRedisConnection;
import io.servicetalk.redis.api.PubSubRedisMessage;
import io.servicetalk.redis.api.RedisClient.ReservedRedisConnection;
import io.servicetalk.redis.api.RedisCommander;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisPartitionAttributesBuilder;
import io.servicetalk.redis.api.RedisProtocolSupport.Command;
import io.servicetalk.redis.api.RedisRequest;
import io.servicetalk.transport.api.ExecutionContext;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PartitionedRedisClientTest extends AbstractPartitionedRedisClientTest {

    @Test
    public void zeroPartitionRead() throws Exception {
        RedisCommander commander = client.asCommander();
        sendHost1ServiceDiscoveryEvent(false);
        try {
            awaitIndefinitely(commander.mget("key1", "key3", "key5"));
            fail();
        } catch (ExecutionException expected) {
            assertThat(expected.getCause(), instanceOf(UnknownPartitionException.class));
        } finally {
            sendHost1ServiceDiscoveryEvent(true);
        }
    }

    @Test
    public void singlePartitionReadWrite() throws Exception {
        RedisCommander commander = client.asCommander();
        assertThat(awaitIndefinitely(commander.mset("key1", "val1", "key3", "val3", "key5", "val5")), is("OK"));
        assertThat(awaitIndefinitely(commander.mget("key1", "key3", "key5")), contains("val1", "val3", "val5"));
        assertThat(awaitIndefinitely(commander.objectRefcount("key1")), is(greaterThanOrEqualTo(0L)));
    }

    @Test
    public void twoPartitionReadWrite() throws Exception {
        RedisCommander commander = client.asCommander();
        assertThat(awaitIndefinitely(commander.mset("key1", "val1", "key3", "val3", "key5", "val5")), is("OK"));
        assertThat(awaitIndefinitely(commander.mget("key1", "key3", "key5")), contains("val1", "val3", "val5"));
        assertThat(awaitIndefinitely(commander.objectRefcount("key1")), is(greaterThanOrEqualTo(0L)));

        try {
            awaitIndefinitely(commander.mset("key2", "val2", "key4", "val4", "key6", "val6"));
            fail();
        } catch (ExecutionException expected) {
            assertThat(expected.getCause(), instanceOf(UnknownPartitionException.class));
        }

        sendHost2ServiceDiscoveryEvent(true);

        assertThat(awaitIndefinitely(commander.mset("key2", "val2", "key4", "val4", "key6", "val6")), is("OK"));
        assertThat(awaitIndefinitely(commander.mget("key2", "key4", "key6")), contains("val2", "val4", "val6"));
        assertThat(awaitIndefinitely(commander.objectRefcount("key2")), is(greaterThanOrEqualTo(0L)));

        sendHost2ServiceDiscoveryEvent(false);

        try {
            assertThat(awaitIndefinitely(commander.mget("key2", "key4", "key6")), contains("val2", "val4", "val6"));
            fail();
        } catch (ExecutionException expected) {
            assertThat(expected.getCause(), instanceOf(UnknownPartitionException.class));
        }
    }

    @Test
    public void redisCommanderUsesFilters() throws Exception {
        final PartitionedRedisClient delegate = client;
        final AtomicBoolean requestCalled = new AtomicBoolean();
        final AtomicBoolean closeCalled = new AtomicBoolean();
        PartitionedRedisClient filteredClient = new PartitionedRedisClient() {
            @Override
            public Publisher<RedisData> request(PartitionAttributes partitionSelector, RedisRequest request) {
                requestCalled.set(true);
                return delegate.request(partitionSelector, request);
            }

            @Override
            public <R> Single<R> request(PartitionAttributes partitionSelector, RedisRequest request,
                                         Class<R> responseType) {
                requestCalled.set(true);
                return delegate.request(partitionSelector, request, responseType);
            }

            @Override
            public Single<? extends ReservedRedisConnection> reserveConnection(PartitionAttributes partitionSelector,
                                                                               Command command) {
                return delegate.reserveConnection(partitionSelector, command);
            }

            @Override
            public ExecutionContext executionContext() {
                return delegate.executionContext();
            }

            @Override
            protected Function<Command, RedisPartitionAttributesBuilder> redisPartitionAttributesBuilderFunction() {
                return getPartitionAttributesBuilderFactory();
            }

            @Override
            public Completable onClose() {
                return delegate.onClose();
            }

            @Override
            public Completable closeAsync() {
                closeCalled.set(true);
                // Don't actually close the client connection for this unit test, because it is used in a static fashion.
                return delegate.onClose();
            }
        };
        RedisCommander commander = filteredClient.asCommander();

        assertThat(awaitIndefinitely(commander.mset("key1", "val1", "key3", "val3", "key5", "val5")), is("OK"));
        assertTrue(requestCalled.get());

        // Don't subscribe because we don't actually do the close, but instead just verify the method was called.
        commander.closeAsync();
        assertTrue(closeCalled.get());
    }

    @Test
    public void requestsAreRetryable() throws Exception {
        RedisCommander commander = client.asCommander();

        Single<String> msetCommand = commander.mset("key1", "val1", "key3", "val3", "key5", "val5");
        assertThat(awaitIndefinitely(msetCommand), is("OK"));

        sendHost1ServiceDiscoveryEvent(false);

        Single<List<Object>> mgetCommand = commander.mget("key1", "key3", "key5")
                .retry((i, t) -> {
                    if (i > 1 || !(t instanceof UnknownPartitionException)) {
                        return false;
                    }
                    sendHost1ServiceDiscoveryEvent(true);
                    return true;
                });
        assertThat(awaitIndefinitely(mgetCommand), contains("val1", "val3", "val5"));
    }

    @Test
    public void reservedConnectionRequestsAreRetryable() throws Exception {

        RedisCommander commander = client.asCommander();

        sendHost1ServiceDiscoveryEvent(false);

        // SUBSCRIBE uses reservedConnection()
        Single<PubSubRedisMessage.Pong<String>> subscribeAndPingCommand = commander.subscribe("somechan")
                .flatMap(PubSubRedisConnection::ping)
                .retry((i, t) -> {
                    if (i > 1 || !(t instanceof UnknownPartitionException)) {
                        return false;
                    }
                    sendHost1ServiceDiscoveryEvent(true);
                    return true;
                });

        assertThat(awaitIndefinitely(subscribeAndPingCommand).getValue(), equalTo("PONG"));
    }
}
