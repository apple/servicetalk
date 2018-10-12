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
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.partition.PartitionAttributes;
import io.servicetalk.client.api.partition.PartitionAttributes.Key;
import io.servicetalk.client.api.partition.PartitionAttributesBuilder;
import io.servicetalk.client.api.partition.PartitionedServiceDiscovererEvent;
import io.servicetalk.client.internal.partition.DefaultPartitionAttributesBuilder;
import io.servicetalk.concurrent.api.PublisherRule;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.redis.api.PartitionedRedisClient;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisPartitionAttributesBuilder;
import io.servicetalk.redis.api.RedisProtocolSupport.Command;
import io.servicetalk.transport.api.DefaultExecutionContext;
import io.servicetalk.transport.netty.internal.NettyIoExecutor;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.Timeout;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.INFO;
import static io.servicetalk.redis.api.RedisProtocolSupport.CommandFlag.PUBSUB;
import static io.servicetalk.redis.api.RedisProtocolSupport.CommandFlag.WRITE;
import static io.servicetalk.redis.api.RedisRequests.newRequest;
import static io.servicetalk.transport.netty.NettyIoExecutors.createIoExecutor;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.toNettyIoExecutor;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.time.Duration.ofSeconds;
import static java.util.regex.Pattern.compile;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public abstract class AbstractPartitionedRedisClientTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final PublisherRule<PartitionedServiceDiscovererEvent<InetSocketAddress>> serviceDiscoveryPublisher =
            new PublisherRule<>();

    private static final Key<Boolean> MASTER_KEY = Key.newKey("master");
    private static final Key<Integer> SHARD_KEY = Key.newKey("shard");
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

    private NettyIoExecutor ioExecutor;
    private static final String redisPortString = System.getenv("REDIS_PORT");
    private static final int redisPort = redisPortString == null || redisPortString.isEmpty() ? -1 :
            Integer.parseInt(redisPortString);
    private static final String redisHost = System.getenv().getOrDefault("REDIS_HOST", "localhost");
    @Nullable
    protected PartitionedRedisClient client;
    private Function<Command, RedisPartitionAttributesBuilder> partitionAttributesBuilderFactory;

    @Before
    public void startClient() throws Exception {
        assumeThat(redisPortString, not(isEmptyOrNullString()));

        ioExecutor = toNettyIoExecutor(createIoExecutor());

        partitionAttributesBuilderFactory = command -> {
            final PartitionAttributesBuilder partitionAttributesBuilder;
            if (command.hasFlag(WRITE) || command.hasFlag(PUBSUB)) {
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

        @SuppressWarnings("unchecked")
        ServiceDiscoverer<String, InetSocketAddress, PartitionedServiceDiscovererEvent<InetSocketAddress>> sd =
                (ServiceDiscoverer<String, InetSocketAddress, PartitionedServiceDiscovererEvent<InetSocketAddress>>)
                        mock(ServiceDiscoverer.class);
        when(sd.discover(any())).thenReturn(serviceDiscoveryPublisher.getPublisher());
        client = RedisClients.forPartitionedAddress(sd, "ignored", partitionAttributesBuilderFactory)
                .maxPipelinedRequests(10)
                .pingPeriod(ofSeconds(1))
                .executionContext(new DefaultExecutionContext(DEFAULT_ALLOCATOR, ioExecutor, immediate()))
                .build();

        sendHost1ServiceDiscoveryEvent(true);

        PartitionAttributesBuilder partitionAttributesBuilder = new DefaultPartitionAttributesBuilder(1);
        partitionAttributesBuilder.add(MASTER_KEY, true);
        final String serverInfo = awaitIndefinitely(
                client.request(partitionAttributesBuilder.build(), newRequest(INFO,
                        new RedisData.CompleteBulkString(buf("SERVER"))))
                        .filter(d -> d instanceof RedisData.BulkStringChunk)
                        .reduce(StringBuilder::new, (sb, d) -> sb.append(d.getBufferValue().toString(US_ASCII))))
                .toString();

        final java.util.regex.Matcher versionMatcher = compile("(?s).*redis_version:([\\d]+)\\.([\\d]+)\\.([\\d]+).*")
                .matcher(serverInfo);
        assertThat(versionMatcher.matches(), is(true));
    }

    @After
    public void stopClient() throws Exception {
        // @After is run even if assumption in @Before is violated
        if (client == null) {
            return;
        }

        awaitIndefinitely(client.closeAsync().andThen(ioExecutor.closeAsync()));
    }

    public Function<Command, RedisPartitionAttributesBuilder> getPartitionAttributesBuilderFactory() {
        return partitionAttributesBuilderFactory;
    }

    void sendHost1ServiceDiscoveryEvent(boolean available) {
        PartitionAttributesBuilder partitionAttributesBuilder = new DefaultPartitionAttributesBuilder(2);
        partitionAttributesBuilder.add(MASTER_KEY, true);
        partitionAttributesBuilder.add(SHARD_KEY, 0);
        sendServiceDiscoveryEvent(available, partitionAttributesBuilder.build());
    }

    void sendHost2ServiceDiscoveryEvent(boolean available) {
        PartitionAttributesBuilder partitionAttributesBuilder = new DefaultPartitionAttributesBuilder(2);
        partitionAttributesBuilder.add(MASTER_KEY, true);
        partitionAttributesBuilder.add(SHARD_KEY, 1);
        sendServiceDiscoveryEvent(available, partitionAttributesBuilder.build());
    }

    void sendServiceDiscoveryEvent(boolean available, PartitionAttributes partitionAddress) {
        serviceDiscoveryPublisher.sendItems(new PartitionedServiceDiscovererEvent<InetSocketAddress>() {
            private final InetSocketAddress address = new InetSocketAddress(redisHost, redisPort);

            @Override
            public PartitionAttributes partitionAddress() {
                return partitionAddress;
            }

            @Override
            public InetSocketAddress address() {
                return address;
            }

            @Override
            public boolean available() {
                return available;
            }
        });
    }

    protected Buffer buf(final CharSequence cs) {
        return client.executionContext().bufferAllocator().fromUtf8(cs);
    }
}
