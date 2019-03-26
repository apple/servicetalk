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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.PlatformDependent;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.redis.api.RedisCommander;
import io.servicetalk.redis.api.RedisConnection;
import io.servicetalk.redis.api.RedisServerException;
import io.servicetalk.redis.utils.RedisAuthConnectionFactory;
import io.servicetalk.redis.utils.RedisAuthorizationException;
import io.servicetalk.transport.netty.internal.NettyIoExecutor;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.net.InetSocketAddress;
import java.util.function.Consumer;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.transport.netty.NettyIoExecutors.createIoExecutor;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.toNettyIoExecutor;
import static java.net.InetAddress.getLoopbackAddress;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeThat;

public class RedisAuthConnectionFactoryConnectionTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private static final String CORRECT_PASSWORD = "password";

    @Nullable
    private NettyIoExecutor ioExecutor;
    @Nullable
    private Single<RedisConnection> connectionSingle;

    @After
    public void cleanup() throws Exception {
        if (connectionSingle != null) {
            assert ioExecutor != null;
            connectionSingle.flatMap(connection ->
                    connection.closeAsync().onErrorResume(cause -> completed()).concat(success(connection)))
                    .ignoreResult()
                    .onErrorResume(cause -> completed())
                    .concat(ioExecutor.closeAsync()).toFuture().get();
        }
    }

    @Test
    public void noPasswordSetFailureConnectionBuilder() throws Exception {
        authTestConnection(System.getenv("REDIS_PORT"), CORRECT_PASSWORD, connectionSingle -> {
            try {
                RedisCommander commandClient = connectionSingle.toFuture().get().asCommander();
                commandClient.ping().toFuture().get();
                fail();
            } catch (Exception e) {
                assertThat(e.getCause(), is(instanceOf(RedisAuthorizationException.class)));
                assertThat(e.getCause().getCause(), is(instanceOf(RedisServerException.class)));
            }
        });
    }

    @Test
    public void correctPasswordConnectionBuilder() {
        authTestConnection(System.getenv("REDIS_AUTH_PORT"), CORRECT_PASSWORD, connectionSingle -> {
            try {
                RedisCommander commandClient = connectionSingle.toFuture().get().asCommander();
                assertThat(commandClient.ping().toFuture().get(), is("PONG"));
            } catch (Exception e) {
                PlatformDependent.throwException(e);
            }
        });
    }

    @Test
    public void invalidPasswordConnectionBuilder() {
        authTestConnection(System.getenv("REDIS_AUTH_PORT"), CORRECT_PASSWORD + "foo", connectionSingle -> {
            try {
                RedisCommander commandClient = connectionSingle.toFuture().get().asCommander();
                commandClient.ping().toFuture().get();
                fail();
            } catch (Exception e) {
                assertThat(e.getCause(), is(instanceOf(RedisAuthorizationException.class)));
            }
        });
    }

    private void authTestConnection(String tmpRedisPort, String password,
                                    Consumer<Single<RedisConnection>> connectionConsumer) {
        int redisPort;
        String redisHost;
        assumeThat(tmpRedisPort, not(isEmptyOrNullString()));
        redisPort = Integer.parseInt(tmpRedisPort);

        redisHost = System.getenv().getOrDefault("REDIS_HOST", getLoopbackAddress().getHostName());

        ioExecutor = toNettyIoExecutor(createIoExecutor());

        connectionSingle = new RedisAuthConnectionFactory<>(
                DefaultRedisConnectionBuilder.<InetSocketAddress>forPipeline()
                        .maxPipelinedRequests(10)
                        .ioExecutor(ioExecutor)
                        .executor(immediate())
                        .asConnectionFactory(),
                ctx -> ctx.executionContext().bufferAllocator().fromAscii(password))
                .newConnection(new InetSocketAddress(redisHost, redisPort));
        connectionConsumer.accept(connectionSingle);
    }
}
