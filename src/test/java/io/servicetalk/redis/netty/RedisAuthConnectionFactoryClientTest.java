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

import io.servicetalk.client.api.RetryableException;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.internal.DefaultHostAndPort;
import io.servicetalk.client.internal.HostAndPort;
import io.servicetalk.client.loadbalancer.RoundRobinLoadBalancer;
import io.servicetalk.client.servicediscoverer.dns.DefaultDnsServiceDiscoverer;
import io.servicetalk.concurrent.internal.PlatformDependent;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.redis.api.RedisClient;
import io.servicetalk.redis.api.RedisCommander;
import io.servicetalk.redis.api.RedisException;
import io.servicetalk.redis.utils.RedisAuthConnectionFactory;
import io.servicetalk.redis.utils.RedisAuthorizationException;
import io.servicetalk.redis.utils.RetryingRedisClient;
import io.servicetalk.transport.api.IoExecutorGroup;
import io.servicetalk.transport.netty.NettyIoExecutors;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.net.InetSocketAddress;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.RetryStrategies.retryWithExponentialBackoff;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static java.util.Comparator.comparingInt;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeThat;

public class RedisAuthConnectionFactoryClientTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private static final String CORRECT_PASSWORD = "password";

    @Nullable
    private ServiceDiscoverer<HostAndPort, InetSocketAddress> serviceDiscoverer;
    @Nullable
    private IoExecutorGroup group;
    @Nullable
    private RedisClient client;

    @After
    public void cleanup() throws Exception {
        if (client == null) {
            if (serviceDiscoverer != null) {
                awaitIndefinitely(serviceDiscoverer.closeAsync());
            }
        } else {
            assert group != null;
            assert serviceDiscoverer != null;
            awaitIndefinitely(client.closeAsync().andThen(serviceDiscoverer.closeAsync()).andThen(group.closeAsync(0, 0, SECONDS)));
        }
    }

    @Test
    public void noPasswordSetFailure() {
        authTest(System.getenv("REDIS_PORT"), CORRECT_PASSWORD, client -> {
            RedisCommander commandClient = client.asCommander();
            try {
                awaitIndefinitely(commandClient.ping());
                fail();
            } catch (Exception e) {
                assertThat(e.getCause(), is(instanceOf(RedisAuthorizationException.class)));
                assertThat(e.getCause().getCause(), is(instanceOf(RedisException.class)));
            }
        });
    }

    @Test
    public void correctPassword() {
        authTest(System.getenv("REDIS_AUTH_PORT"), CORRECT_PASSWORD, client -> {
            RedisCommander commandClient = client.asCommander();
            try {
                assertThat(awaitIndefinitely(commandClient.ping()), is("PONG"));
            } catch (Exception e) {
                PlatformDependent.throwException(e);
            }
        });
    }

    @Test
    public void invalidPassword() {
        authTest(System.getenv("REDIS_AUTH_PORT"), CORRECT_PASSWORD + "foo", client -> {
            RedisCommander commandClient = client.asCommander();
            try {
                awaitIndefinitely(commandClient.ping());
                fail();
            } catch (Exception e) {
                assertThat(e.getCause(), is(instanceOf(RedisAuthorizationException.class)));
            }
        });
    }

    @Test
    public void pubsubCorrectPassword() {
        authTest(System.getenv("REDIS_AUTH_PORT"), CORRECT_PASSWORD, client -> {
            RedisCommander commandClient = client.asCommander();
            try {
                awaitIndefinitely(commandClient.subscribe("test-channel"));
            } catch (Exception e) {
                PlatformDependent.throwException(e);
            }
        });
    }

    @Test
    public void pubsubInvalidPassword() {
        authTest(System.getenv("REDIS_AUTH_PORT"), CORRECT_PASSWORD + "foo", client -> {
            RedisCommander commandClient = client.asCommander();
            try {
                awaitIndefinitely(commandClient.subscribe("test-channel"));
                fail();
            } catch (Exception e) {
                assertThat(e.getCause(), is(instanceOf(RedisAuthorizationException.class)));
            }
        });
    }

    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    private void authTest(String tmpRedisPort, String password, Consumer<RedisClient> clientConsumer) {
        int redisPort;
        String redisHost;
        assumeThat(tmpRedisPort, not(isEmptyOrNullString()));
        redisPort = Integer.parseInt(tmpRedisPort);

        redisHost = System.getenv().getOrDefault("REDIS_HOST", "127.0.0.1");

        group = NettyIoExecutors.createGroup();
        serviceDiscoverer = new DefaultDnsServiceDiscoverer.Builder(group.next()).build().toHostAndPortDiscoverer();
        client = new RetryingRedisClient(
                new DefaultRedisClientBuilder<InetSocketAddress>((eventPublisher, connectionFactory) -> new RoundRobinLoadBalancer<>(eventPublisher, new RedisAuthConnectionFactory<>(connectionFactory, ctx -> ctx.getAllocator().fromAscii(password)), comparingInt(Object::hashCode)))
                        .setMaxPipelinedRequests(10)
                        .setIdleConnectionTimeout(ofSeconds(2))
                        .build(group, serviceDiscoverer.discover(new DefaultHostAndPort(redisHost, redisPort))),
                retryWithExponentialBackoff(10, cause -> cause instanceof RetryableException, ofMillis(10), backoffNanos -> group.next().timer(backoffNanos, NANOSECONDS)));
        clientConsumer.accept(client);
    }
}
