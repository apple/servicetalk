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
import io.servicetalk.client.api.partition.PartitionAttributes;
import io.servicetalk.client.api.partition.PartitionAttributes.Key;
import io.servicetalk.client.api.partition.PartitionAttributesBuilder;
import io.servicetalk.client.api.partition.PartitionedEvent;
import io.servicetalk.client.api.partition.UnknownPartitionException;
import io.servicetalk.client.internal.partition.DefaultPartitionAttributesBuilder;
import io.servicetalk.client.loadbalancer.RoundRobinLoadBalancer;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.PublisherRule;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.redis.api.PartitionedRedisClient;
import io.servicetalk.redis.api.RedisClient;
import io.servicetalk.redis.api.RedisCommander;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisPartitionAttributesBuilder;
import io.servicetalk.redis.api.RedisProtocolSupport;
import io.servicetalk.redis.api.RedisRequest;
import io.servicetalk.transport.api.IoExecutorGroup;
import io.servicetalk.transport.netty.NettyIoExecutors;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.INFO;
import static io.servicetalk.redis.api.RedisProtocolSupport.CommandFlag.WRITE;
import static io.servicetalk.redis.api.RedisRequests.newRequest;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.time.Duration.ofSeconds;
import static java.util.Comparator.comparingInt;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeThat;

public class PartitionedRedisClientTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final PublisherRule<PartitionedEvent<InetSocketAddress>> serviceDiscoveryPublisher = new PublisherRule<>();

    private static final Key<Boolean> MASTER_KEY = Key.newKeyWithDebugToString("master");
    private static final Key<Integer> SHARD_KEY = Key.newKeyWithDebugToString("shard");
    private static final Map<String, Integer> keyToShardMap;
    static {
        Map<String, Integer> localMap = new HashMap<>();
        localMap.put("key1", 0);
        localMap.put("key2", 1);
        localMap.put("key3", 0);
        localMap.put("key4", 1);
        localMap.put("key5", 0);
        localMap.put("key6", 1);
        keyToShardMap = Collections.unmodifiableMap(localMap);
    }

    private IoExecutorGroup group;
    private static final String redisPortString = System.getenv("REDIS_PORT");
    private static final int redisPort = redisPortString == null || redisPortString.isEmpty() ? -1 : Integer.parseInt(redisPortString);
    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    private static final String redisHost = System.getenv().getOrDefault("REDIS_HOST", "127.0.0.1");
    @Nullable
    protected PartitionedRedisClient client;
    private Function<RedisProtocolSupport.Command, RedisPartitionAttributesBuilder> partitionAttributesBuilderFactory;

    @Before
    public void startClient() throws Exception {
        assumeThat(redisPortString, not(isEmptyOrNullString()));

        group = NettyIoExecutors.createGroup();

        partitionAttributesBuilderFactory = command -> {
            final PartitionAttributesBuilder partitionAttributesBuilder;
            if (command.hasFlag(WRITE)) {
                partitionAttributesBuilder = new DefaultPartitionAttributesBuilder(2);
                partitionAttributesBuilder.add(MASTER_KEY, true);
            } else {
                partitionAttributesBuilder = new DefaultPartitionAttributesBuilder(1);
            }

            return new RedisPartitionAttributesBuilder() {
                private Integer previousShard;

                @Override
                public RedisPartitionAttributesBuilder addKey(CharSequence key) {
                    final int newShard = keyToShardMap.get(key);
                    if (previousShard == null || newShard != previousShard.intValue()) {
                        previousShard = newShard;
                        partitionAttributesBuilder.add(SHARD_KEY, newShard);
                    }
                    return this;
                }

                @Override
                public RedisPartitionAttributesBuilder addKey(Buffer key) {
                    return this;
                }

                @Override
                public PartitionAttributes build() {
                    return partitionAttributesBuilder.build();
                }
            };
        };
        client = new DefaultPartitionedRedisClientBuilder<InetSocketAddress>((eventPublisher, connectionFactory) -> new RoundRobinLoadBalancer<>(eventPublisher, connectionFactory, comparingInt(Object::hashCode)),
                partitionAttributesBuilderFactory)
                .setMaxPipelinedRequests(10)
                .setPingPeriod(ofSeconds(1))
                .build(group, serviceDiscoveryPublisher.getPublisher());

        sendHost1ServiceDiscoveryEvent(true);

        PartitionAttributesBuilder partitionAttributesBuilder = new DefaultPartitionAttributesBuilder(1);
        partitionAttributesBuilder.add(MASTER_KEY, true);
        final String serverInfo = awaitIndefinitely(
                client.request(partitionAttributesBuilder.build(), newRequest(INFO, new RedisData.CompleteBulkString(buf("SERVER"))))
                        .filter(d -> d instanceof RedisData.BulkStringChunk)
                        .reduce(StringBuilder::new, (sb, d) -> sb.append(d.getBufferValue().toString(US_ASCII))))
                .toString();

        final java.util.regex.Matcher versionMatcher = Pattern.compile("(?s).*redis_version:([\\d]+)\\.([\\d]+)\\.([\\d]+).*").matcher(serverInfo);
        assertThat(versionMatcher.matches(), is(true));
    }

    @After
    public void stopClient() throws Exception {
        // @After is run even if assumption in @Before is violated
        if (client == null) {
            return;
        }

        awaitIndefinitely(client.closeAsync().andThen(group.closeAsync(0, 0, SECONDS)));
    }

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
            public BufferAllocator getBufferAllocator() {
                return delegate.getBufferAllocator();
            }

            @Override
            public Publisher<RedisData> request(PartitionAttributes partitionSelector, RedisRequest request) {
                requestCalled.set(true);
                return delegate.request(partitionSelector, request);
            }

            @Override
            public <R> Single<R> request(PartitionAttributes partitionSelector, RedisRequest request, Class<R> responseType) {
                requestCalled.set(true);
                return delegate.request(partitionSelector, request, responseType);
            }

            @Override
            public Single<RedisClient.ReservedRedisConnection> reserveConnection(PartitionAttributes partitionSelector, RedisRequest request) {
                return delegate.reserveConnection(partitionSelector, request);
            }

            @Override
            protected Function<RedisProtocolSupport.Command, RedisPartitionAttributesBuilder> getRedisPartitionAttributesBuilderFactory() {
                return partitionAttributesBuilderFactory;
            }

            @Override
            public Completable onClose() {
                return delegate.onClose();
            }

            @Override
            public Completable closeAsync() {
                closeCalled.set(true);
                // Don't actually close the client connection for this unit test ... because it is used in a static fashion.
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
        assertThat(awaitIndefinitely(commander.mset("key1", "val1", "key3", "val3", "key5", "val5")), is("OK"));
        sendHost1ServiceDiscoveryEvent(false);

        assertThat(awaitIndefinitely(commander.mget("key1", "key3", "key5").retry((i, t) -> {
            if (i > 1 || !(t instanceof UnknownPartitionException)) {
                return false;
            }
            sendHost1ServiceDiscoveryEvent(true);
            return true;
        })), contains("val1", "val3", "val5"));
    }

    private void sendHost1ServiceDiscoveryEvent(boolean available) {
        PartitionAttributesBuilder partitionAttributesBuilder = new DefaultPartitionAttributesBuilder(2);
        partitionAttributesBuilder.add(MASTER_KEY, true);
        partitionAttributesBuilder.add(SHARD_KEY, 0);
        sendServiceDiscoveryEvent(available, partitionAttributesBuilder.build());
    }

    private void sendHost2ServiceDiscoveryEvent(boolean available) {
        PartitionAttributesBuilder partitionAttributesBuilder = new DefaultPartitionAttributesBuilder(2);
        partitionAttributesBuilder.add(MASTER_KEY, true);
        partitionAttributesBuilder.add(SHARD_KEY, 1);
        sendServiceDiscoveryEvent(available, partitionAttributesBuilder.build());
    }

    private void sendServiceDiscoveryEvent(boolean available, PartitionAttributes partitionAddress) {
        serviceDiscoveryPublisher.sendItems(new PartitionedEvent<InetSocketAddress>() {
            private final InetSocketAddress address = new InetSocketAddress(redisHost, redisPort);
            @Override
            public PartitionAttributes getPartitionAddress() {
                return partitionAddress;
            }

            @Override
            public InetSocketAddress getAddress() {
                return address;
            }

            @Override
            public boolean isAvailable() {
                return available;
            }
        });
    }

    protected Buffer buf(final CharSequence cs) {
        return client.getBufferAllocator().fromUtf8(cs);
    }
}
