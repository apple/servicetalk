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
import io.servicetalk.client.api.RetryableException;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.internal.DefaultHostAndPort;
import io.servicetalk.client.internal.HostAndPort;
import io.servicetalk.client.loadbalancer.RoundRobinLoadBalancer;
import io.servicetalk.client.servicediscoverer.dns.DefaultDnsServiceDiscoverer;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.redis.api.RedisClient;
import io.servicetalk.redis.api.RedisData.BulkStringChunk;
import io.servicetalk.redis.api.RedisData.CompleteBulkString;
import io.servicetalk.redis.utils.RetryingRedisClient;
import io.servicetalk.tcp.netty.internal.TcpClientConfig;
import io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutor;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;

import java.net.InetSocketAddress;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithExponentialBackoff;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.INFO;
import static io.servicetalk.redis.api.RedisRequests.newRequest;
import static io.servicetalk.transport.netty.NettyIoExecutors.createExecutor;
import static io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutors.toEventLoopAwareNettyIoExecutor;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static java.util.Comparator.comparingInt;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.IntStream.rangeClosed;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeThat;

public abstract class BaseRedisClientTest {
    protected static final int PING_PERIOD_SECONDS = 1;

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    static EventLoopAwareNettyIoExecutor executor;
    static int redisPort;
    static String redisHost;

    @Nullable
    protected static RedisClient client;
    @Nullable
    private static ServiceDiscoverer<HostAndPort, InetSocketAddress> serviceDiscoverer;

    protected static int[] serverVersion;

    @BeforeClass
    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    public static void startClient() throws Exception {
        final String tmpRedisPort = System.getenv("REDIS_PORT");
        assumeThat(tmpRedisPort, not(isEmptyOrNullString()));
        redisPort = Integer.parseInt(tmpRedisPort);

        redisHost = System.getenv().getOrDefault("REDIS_HOST", "127.0.0.1");

        executor = toEventLoopAwareNettyIoExecutor(createExecutor());
        serviceDiscoverer = new DefaultDnsServiceDiscoverer.Builder(executor.next(), immediate()).build()
                .toHostAndPortDiscoverer();
        RedisClientConfig config = new RedisClientConfig(new TcpClientConfig(false))
                .setDeferSubscribeTillConnect(true);
        client = new RetryingRedisClient(new DefaultRedisClientBuilder<InetSocketAddress>(
                (eventPublisher, connectionFactory) -> new RoundRobinLoadBalancer<>(eventPublisher, connectionFactory,
                        comparingInt(Object::hashCode)), config)
                        .setMaxPipelinedRequests(10)
                        .setIdleConnectionTimeout(ofSeconds(2))
                        .setPingPeriod(ofSeconds(PING_PERIOD_SECONDS))
                        .build(executor, immediate(), serviceDiscoverer.discover(new DefaultHostAndPort(redisHost, redisPort))),
                retryWithExponentialBackoff(10, cause -> cause instanceof RetryableException, ofMillis(10),
                        backoffNanos -> executor.next().scheduleOnEventloop(backoffNanos, NANOSECONDS)));

        final String serverInfo = awaitIndefinitely(
                client.request(newRequest(INFO, new CompleteBulkString(buf("SERVER"))))
                        .filter(d -> d instanceof BulkStringChunk)
                        .reduce(StringBuilder::new, (sb, d) -> sb.append(d.getBufferValue().toString(US_ASCII))))
                .toString();

        final java.util.regex.Matcher versionMatcher = Pattern.compile("(?s).*redis_version:([\\d]+)\\.([\\d]+)\\.([\\d]+).*").matcher(serverInfo);
        assertThat(versionMatcher.matches(), is(true));
        serverVersion = rangeClosed(1, 3).map(i -> Integer.parseInt(versionMatcher.group(i))).toArray();
    }

    @AfterClass
    public static void stopClient() throws Exception {
        // @After is run even if assumption in @Before is violated
        if (client == null) {
            if (serviceDiscoverer != null) {
                awaitIndefinitely(serviceDiscoverer.closeAsync());
            }
            return;
        }

        awaitIndefinitely(client.closeAsync().andThen(serviceDiscoverer.closeAsync()).andThen(executor.closeAsync(0, 0, SECONDS)));
    }

    protected static Buffer buf(final CharSequence cs) {
        return client.getBufferAllocator().fromUtf8(cs);
    }

    protected static Matcher<Buffer> bufStartingWith(final Buffer buf) {
        return new BaseMatcher<Buffer>() {
            @Override
            public boolean matches(final Object argument) {
                return argument instanceof Buffer &&
                        ((Buffer) argument).slice(0, buf.getReadableBytes()).equals(buf);
            }

            @Override
            public void describeTo(final Description description) {
                description.appendText(Buffer.class.getSimpleName())
                        .appendText("{")
                        .appendValue(buf)
                        .appendText("}");
            }
        };
    }
}
