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

import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.internal.DefaultThreadFactory;
import io.servicetalk.dns.discovery.netty.DefaultDnsServiceDiscovererBuilder;
import io.servicetalk.redis.api.RedisClient;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.transport.api.DefaultExecutionContext;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.HostAndPort;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitelyNonNull;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.INFO;
import static io.servicetalk.redis.api.RedisRequests.newRequest;
import static io.servicetalk.redis.netty.RedisClients.forAddress;
import static io.servicetalk.redis.utils.RetryingRedisClient.newBuilder;
import static io.servicetalk.transport.netty.NettyIoExecutors.createIoExecutor;
import static java.lang.Thread.NORM_PRIORITY;
import static java.net.InetAddress.getLoopbackAddress;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static java.util.stream.IntStream.rangeClosed;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeThat;

final class RedisTestEnvironment implements AutoCloseable {

    public static final int PING_PERIOD_SECONDS = 1;
    private static final InetAddress LOOPBACK_ADDRESS = getLoopbackAddress();
    private static final String IO_EXECUTOR_THREAD_NAME_PREFIX = "redis-client-io-executor-";

    final ExecutionContext executionContext;
    final int redisPort;
    final String redisHost;
    final RedisClient client;
    final ServiceDiscoverer<HostAndPort, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>> serviceDiscoverer;

    final int[] serverVersion;

    RedisTestEnvironment(Executor executor) throws Exception {
        final String tmpRedisPort = System.getenv("REDIS_PORT");
        assumeThat(tmpRedisPort, not(isEmptyOrNullString()));
        redisPort = Integer.parseInt(tmpRedisPort);

        redisHost = System.getenv().getOrDefault("REDIS_HOST", LOOPBACK_ADDRESS.getHostName());

        final DefaultThreadFactory threadFactory = new DefaultThreadFactory(IO_EXECUTOR_THREAD_NAME_PREFIX, true,
                NORM_PRIORITY);
        executionContext = new DefaultExecutionContext(DEFAULT_ALLOCATOR, createIoExecutor(threadFactory), executor);
        serviceDiscoverer = new DefaultDnsServiceDiscovererBuilder(executionContext).build();
        DefaultRedisClientBuilder<HostAndPort, InetSocketAddress> builder =
                (DefaultRedisClientBuilder<HostAndPort, InetSocketAddress>) forAddress(redisHost, redisPort);
        RedisClient rawClient = builder
                .deferSubscribeTillConnect(true)
                .executionContext(executionContext)
                .maxPipelinedRequests(10)
                .idleConnectionTimeout(ofSeconds(2))
                .pingPeriod(ofSeconds(PING_PERIOD_SECONDS))
                .build();
        client = newBuilder(rawClient).exponentialBackoff(ofMillis(10)).build(10);

        final String serverInfo = awaitIndefinitelyNonNull(
                client.request(newRequest(INFO, new RedisData.CompleteBulkString(DEFAULT_ALLOCATOR.fromUtf8("SERVER"))))
                        .filter(d -> d instanceof RedisData.BulkStringChunk)
                        .reduce(StringBuilder::new, (sb, d) -> sb.append(d.getBufferValue().toString(US_ASCII))))
                .toString();

        final Matcher versionMatcher =
                Pattern.compile("(?s).*redis_version:([\\d]+)\\.([\\d]+)\\.([\\d]+).*").matcher(serverInfo);
        assertThat(versionMatcher.matches(), is(true));
        serverVersion = rangeClosed(1, 3).map(i -> Integer.parseInt(versionMatcher.group(i))).toArray();
    }

    @Override
    public void close() throws Exception {
        awaitIndefinitely(newCompositeCloseable().appendAll(client, serviceDiscoverer,
                executionContext.ioExecutor(), executionContext.executor()).closeAsync());
    }

    boolean isInClientEventLoop(Thread thread) {
        return thread.getName().startsWith(IO_EXECUTOR_THREAD_NAME_PREFIX);
    }
}
