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

import io.servicetalk.concurrent.internal.PlatformDependent;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.redis.api.RedisClient;
import io.servicetalk.redis.api.RedisCommander;
import io.servicetalk.redis.api.RedisServerException;
import io.servicetalk.redis.utils.RedisAuthConnectionFactory;
import io.servicetalk.redis.utils.RedisAuthorizationException;
import io.servicetalk.redis.utils.RetryingRedisRequesterFilter;
import io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutor;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.function.Consumer;
import javax.annotation.Nullable;

import static io.servicetalk.redis.api.RedisExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.transport.netty.NettyIoExecutors.createIoExecutor;
import static io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutors.toEventLoopAwareNettyIoExecutor;
import static java.net.InetAddress.getLoopbackAddress;
import static java.time.Duration.ofSeconds;
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
    private EventLoopAwareNettyIoExecutor ioExecutor;
    @Nullable
    private RedisClient client;

    @After
    public void cleanup() throws Exception {
        if (client != null) {
            assert ioExecutor != null;
            client.closeAsync().concatWith(ioExecutor.closeAsync()).toFuture().get();
        }
    }

    @Test
    public void noPasswordSetFailure() {
        authTest(System.getenv("REDIS_PORT"), CORRECT_PASSWORD, client -> {
            RedisCommander commandClient = client.asCommander();
            try {
                commandClient.ping().toFuture().get();
                fail();
            } catch (Exception e) {
                assertThat(e.getCause(), is(instanceOf(RedisAuthorizationException.class)));
                assertThat(e.getCause().getCause(), is(instanceOf(RedisServerException.class)));
            }
        });
    }

    @Test
    public void correctPassword() {
        authTest(System.getenv("REDIS_AUTH_PORT"), CORRECT_PASSWORD, client -> {
            RedisCommander commandClient = client.asCommander();
            try {
                assertThat(commandClient.ping().toFuture().get(), is("PONG"));
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
                commandClient.ping().toFuture().get();
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
                commandClient.subscribe("test-channel").toFuture().get();
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
                commandClient.subscribe("test-channel").toFuture().get();
                fail();
            } catch (Exception e) {
                assertThat(e.getCause(), is(instanceOf(RedisAuthorizationException.class)));
            }
        });
    }

    private void authTest(String tmpRedisPort, String password, Consumer<RedisClient> clientConsumer) {
        int redisPort;
        String redisHost;
        assumeThat(tmpRedisPort, not(isEmptyOrNullString()));
        redisPort = Integer.parseInt(tmpRedisPort);

        redisHost = System.getenv().getOrDefault("REDIS_HOST", getLoopbackAddress().getHostName());

        ioExecutor = toEventLoopAwareNettyIoExecutor(createIoExecutor());
        client = RedisClients.forAddress(redisHost, redisPort)
                .appendConnectionFactoryFilter(f ->
                        new RedisAuthConnectionFactory<>(f, ctx -> ctx.executionContext().bufferAllocator()
                                .fromAscii(password)))
                .maxPipelinedRequests(10)
                .ioExecutor(ioExecutor)
                .executionStrategy(noOffloadsStrategy())
                .idleConnectionTimeout(ofSeconds(2))
                .appendClientFilter(new RetryingRedisRequesterFilter.Builder().maxRetries(10).buildWithImmediateRetries())
                .build();
        clientConsumer.accept(client);
    }
}
