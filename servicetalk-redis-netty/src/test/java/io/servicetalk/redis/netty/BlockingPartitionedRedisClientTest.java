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
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Executors;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.redis.api.BlockingRedisCommander;
import io.servicetalk.redis.api.PartitionedRedisClient;
import io.servicetalk.redis.api.RedisClient.ReservedRedisConnection;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisExecutionStrategy;
import io.servicetalk.redis.api.RedisPartitionAttributesBuilder;
import io.servicetalk.redis.api.RedisProtocolSupport.Command;
import io.servicetalk.redis.api.RedisRequest;
import io.servicetalk.transport.api.ExecutionContext;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static io.servicetalk.concurrent.api.BlockingTestUtils.awaitIndefinitely;
import static java.util.Objects.requireNonNull;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class BlockingPartitionedRedisClientTest extends AbstractPartitionedRedisClientTest {

    @Test
    public void zeroPartitionRead() throws Exception {
        BlockingRedisCommander commander = client.asBlockingCommander();
        sendHost1ServiceDiscoveryEvent(false);
        try {
            commander.mget("key1", "key3", "key5");
            fail();
        } catch (UnknownPartitionException expected) {
            // expected
        } finally {
            sendHost1ServiceDiscoveryEvent(true);
        }
    }

    @Test
    public void singlePartitionReadWrite() throws Exception {
        BlockingRedisCommander commander = client.asBlockingCommander();
        assertThat(commander.mset("key1", "val1", "key3", "val3", "key5", "val5"), is("OK"));
        assertThat(commander.mget("key1", "key3", "key5"), contains("val1", "val3", "val5"));
        assertThat(commander.objectRefcount("key1"), is(greaterThanOrEqualTo(0L)));
    }

    @Test
    public void twoPartitionReadWrite() throws Exception {
        BlockingRedisCommander commander = client.asBlockingCommander();
        assertThat(commander.mset("key1", "val1", "key3", "val3", "key5", "val5"), is("OK"));
        assertThat(commander.mget("key1", "key3", "key5"), contains("val1", "val3", "val5"));
        assertThat(commander.objectRefcount("key1"), is(greaterThanOrEqualTo(0L)));

        try {
            commander.mset("key2", "val2", "key4", "val4", "key6", "val6");
            fail();
        } catch (UnknownPartitionException expected) {
            // expected
        }

        sendHost2ServiceDiscoveryEvent(true);

        assertThat(commander.mset("key2", "val2", "key4", "val4", "key6", "val6"), is("OK"));
        assertThat(commander.mget("key2", "key4", "key6"), contains("val2", "val4", "val6"));
        assertThat(commander.objectRefcount("key2"), is(greaterThanOrEqualTo(0L)));

        sendHost2ServiceDiscoveryEvent(false);

        try {
            assertThat(commander.mget("key2", "key4", "key6"), contains("val2", "val4", "val6"));
            fail();
        } catch (UnknownPartitionException expected) {
            // expected
        }
    }

    @Test
    public void blockingRedisCommanderUsesFilters() throws Exception {
        final PartitionedRedisClient delegate = requireNonNull(client);
        final AtomicBoolean requestCalled = new AtomicBoolean();
        CountDownLatch closedLatch = new CountDownLatch(1);
        PartitionedRedisClient filteredClient = new PartitionedRedisClient() {
            @Override
            public Publisher<RedisData> request(final RedisExecutionStrategy strategy,
                                                final PartitionAttributes partitionSelector,
                                                final RedisRequest request) {
                requestCalled.set(true);
                return delegate.request(partitionSelector, request);
            }

            @Override
            public <R> Single<R> request(final RedisExecutionStrategy strategy,
                                         final PartitionAttributes partitionSelector,
                                         final RedisRequest request, final Class<R> responseType) {
                requestCalled.set(true);
                return delegate.request(partitionSelector, request, responseType);
            }

            @Override
            public Single<? extends ReservedRedisConnection> reserveConnection(
                    final RedisExecutionStrategy strategy, final PartitionAttributes partitionSelector,
                    final Command command) {
                return delegate.reserveConnection(strategy, partitionSelector, command);
            }

            @Override
            public ExecutionContext executionContext() {
                return delegate.executionContext();
            }

            @Override
            public Function<Command, RedisPartitionAttributesBuilder> redisPartitionAttributesBuilderFunction() {
                return getPartitionAttributesBuilderFactory();
            }

            @Override
            public Completable onClose() {
                return delegate.onClose();
            }

            @Override
            public Completable closeAsync() {
                closedLatch.countDown();
                // Don't actually close the client connection for this unit test ... because it is used in a static fashion.
                return delegate.onClose();
            }
        };
        BlockingRedisCommander commander = filteredClient.asBlockingCommander();

        assertThat(commander.mset("key1", "val1", "key3", "val3", "key5", "val5"), is("OK"));
        assertTrue(requestCalled.get());

        // Don't wait because we don't actually do the close, but instead just verify the method was called.
        Executor executor = Executors.newFixedSizeExecutor(1);
        try {
            executor.execute(() -> {
                try {
                    commander.close();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            closedLatch.await();
        } finally {
            awaitIndefinitely(executor.closeAsync());
        }
    }
}
