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

import io.servicetalk.concurrent.api.DefaultThreadFactory;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.redis.api.RedisClient;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.utils.RetryingRedisRequesterFilter;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.IoExecutor;

import java.net.InetSocketAddress;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.BlockingTestUtils.awaitIndefinitely;
import static io.servicetalk.concurrent.api.BlockingTestUtils.awaitIndefinitelyNonNull;
import static io.servicetalk.redis.api.RedisExecutionStrategies.defaultStrategy;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.INFO;
import static io.servicetalk.redis.api.RedisRequests.newRequest;
import static io.servicetalk.redis.netty.RedisClients.forAddress;
import static io.servicetalk.transport.netty.NettyIoExecutors.createIoExecutor;
import static java.lang.Thread.NORM_PRIORITY;
import static java.net.InetAddress.getLoopbackAddress;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.time.Duration.ofSeconds;
import static java.util.stream.IntStream.rangeClosed;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeThat;

final class RedisTestEnvironment implements AutoCloseable {

    public static final int PING_PERIOD_SECONDS = 1;
    static final String IO_EXECUTOR_THREAD_NAME_PREFIX = "redis-client-io-executor-";

    final int redisPort;
    final String redisHost;
    final RedisClient client;

    final int[] serverVersion;
    final Executor executor;
    final IoExecutor ioExecutor;

    RedisTestEnvironment(Executor executor) throws Exception {
        this.executor = executor;
        redisPort = redisServerPort();
        redisHost = redisServerHost();

        DefaultRedisClientBuilder<HostAndPort, InetSocketAddress> builder =
                (DefaultRedisClientBuilder<HostAndPort, InetSocketAddress>) forAddress(redisHost, redisPort);
        ioExecutor = newIoExecutor();
        client = builder
                .deferSubscribeTillConnect(true)
                .ioExecutor(ioExecutor)
                .executionStrategy(defaultStrategy(executor))
                .maxPipelinedRequests(10)
                .pingPeriod(ofSeconds(PING_PERIOD_SECONDS))
                .appendClientFilter(new RetryingRedisRequesterFilter.Builder().maxRetries(10).buildWithImmediateRetries())
                .build();

        final String serverInfo = awaitIndefinitelyNonNull(
                client.request(newRequest(INFO, new RedisData.CompleteBulkString(DEFAULT_ALLOCATOR.fromUtf8("SERVER"))))
                        .filter(d -> d instanceof RedisData.BulkStringChunk)
                        .reduce(StringBuilder::new, (sb, d) -> sb.append(d.bufferValue().toString(US_ASCII))))
                .toString();

        final Matcher versionMatcher =
                Pattern.compile("(?s).*redis_version:([\\d]+)\\.([\\d]+)\\.([\\d]+).*").matcher(serverInfo);
        assertThat("Unexpected server version. Server info: " + serverInfo, versionMatcher.matches(), is(true));
        serverVersion = rangeClosed(1, 3).map(i -> Integer.parseInt(versionMatcher.group(i))).toArray();
    }

    @Override
    public void close() throws Exception {
        awaitIndefinitely(newCompositeCloseable().appendAll(client, ioExecutor, executor).closeAsync());
    }

    static IoExecutor newIoExecutor() {
        DefaultThreadFactory threadFactory = new DefaultThreadFactory(IO_EXECUTOR_THREAD_NAME_PREFIX, true,
                NORM_PRIORITY);
        return createIoExecutor(threadFactory);
    }

    static String redisServerHost() {
        return System.getenv().getOrDefault("REDIS_HOST", getLoopbackAddress().getHostName());
    }

    static int redisServerPort() {
        final String tmpRedisPort = System.getenv("REDIS_PORT");
        assumeThat(tmpRedisPort, not(isEmptyOrNullString()));
        return Integer.parseInt(tmpRedisPort);
    }

    static boolean isInClientEventLoop(Thread thread) {
        return thread.getName().startsWith(IO_EXECUTOR_THREAD_NAME_PREFIX);
    }
}
