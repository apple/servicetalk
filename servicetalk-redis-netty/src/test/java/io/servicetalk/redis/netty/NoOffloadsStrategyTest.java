/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.redis.api.RedisClient;
import io.servicetalk.redis.api.RedisClientBuilder;
import io.servicetalk.redis.api.RedisExecutionStrategyAdapter;
import io.servicetalk.transport.api.HostAndPort;

import org.junit.After;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.redis.api.RedisExecutionStrategies.noOffloadsStrategy;
import static java.lang.Thread.currentThread;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

public class NoOffloadsStrategyTest {
    private final RedisClientBuilder<HostAndPort, InetSocketAddress> builder;
    @Nullable
    private RedisClient client;

    public NoOffloadsStrategyTest() {
        builder = RedisClients.forAddress("foo", 1801);
    }

    @After
    public void tearDown() throws Exception {
        if (client != null) {
            client.closeAsync().toVoidFuture().get();
        }
    }

    @Test
    public void noOffloadsStillUsesAClientExecutor() throws Exception {
        String testThread = currentThread().getName();
        client = builder.executionStrategy(noOffloadsStrategy()).build();
        final AtomicReference<Thread> executorThread = new AtomicReference<>();
        client.executionContext().executor().submit(() -> executorThread.set(currentThread())).toVoidFuture().get();
        assertThat("Unexpected thread for the server executor.", executorThread.get().getName(),
                not(startsWith(testThread)));
    }

    @Test
    public void turnOffAllExecutors() throws Exception {
        String testThread = currentThread().getName();
        client = builder.executionStrategy(new RedisExecutionStrategyAdapter(noOffloadsStrategy()) {
            @Override
            public Executor executor() {
                return immediate();
            }
        }).build();
        final AtomicReference<Thread> executorThread = new AtomicReference<>();
        client.executionContext().executor().submit(() -> executorThread.set(currentThread())).toVoidFuture().get();
        assertThat("Unexpected thread for the server executor.", executorThread.get().getName(),
                startsWith(testThread));
    }
}
