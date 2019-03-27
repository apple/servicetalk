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

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.redis.api.RedisClient;
import io.servicetalk.redis.api.RedisExecutionStrategy;
import io.servicetalk.redis.api.RedisRequest;
import io.servicetalk.transport.api.IoExecutor;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.redis.api.RedisExecutionStrategies.defaultStrategy;
import static io.servicetalk.redis.api.RedisExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.PING;
import static io.servicetalk.redis.api.RedisRequests.newRequest;
import static io.servicetalk.redis.netty.RedisClients.forAddress;
import static io.servicetalk.redis.netty.RedisTestEnvironment.isInClientEventLoop;
import static io.servicetalk.redis.netty.RedisTestEnvironment.newIoExecutor;
import static io.servicetalk.redis.netty.RedisTestEnvironment.redisServerHost;
import static io.servicetalk.redis.netty.RedisTestEnvironment.redisServerPort;
import static java.lang.Thread.currentThread;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

@RunWith(Parameterized.class)
public class RedisClientOverrideOffloadingTest {

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private final IoExecutor ioExecutor;
    private final Executor executor;
    private final Predicate<Thread> isInvalidThread;
    private final RedisExecutionStrategy overridingStrategy;
    private final RedisClient client;

    public RedisClientOverrideOffloadingTest(@SuppressWarnings("unused") final String description,
                                             final Predicate<Thread> isInvalidThread,
                                             @Nullable final RedisExecutionStrategy overridingStrategy,
                                             @Nullable final RedisExecutionStrategy defaultStrategy) {
        this.ioExecutor = newIoExecutor();
        this.executor = newCachedThreadExecutor();
        this.isInvalidThread = isInvalidThread;
        this.overridingStrategy = overridingStrategy == null ? defaultStrategy(executor) : overridingStrategy;
        client = forAddress(redisServerHost(), redisServerPort())
                .ioExecutor(ioExecutor)
                .executionStrategy(defaultStrategy == null ? defaultStrategy(executor) : defaultStrategy)
                .build();
    }

    @Parameterized.Parameters(name = "{index} - {0}")
    public static Collection<Object[]> params() {
        List<Object[]> params = new ArrayList<>();
        params.add(newParam("Override no offload", th -> !isInClientEventLoop(th), noOffloadsStrategy(), null));
        params.add(newParam("Override offload, default no offload", RedisTestEnvironment::isInClientEventLoop,
                null, noOffloadsStrategy()));
        params.add(newParam("Both offloads", RedisTestEnvironment::isInClientEventLoop, null, null));
        return params;
    }

    private static Object[] newParam(String description, Predicate<Thread> isInvalidThread,
                                     @Nullable RedisExecutionStrategy overridingStrategy,
                                     @Nullable RedisExecutionStrategy defaultStrategy) {
        return new Object[]{description, isInvalidThread, overridingStrategy, defaultStrategy};
    }

    @After
    public void tearDown() throws Exception {
        newCompositeCloseable().appendAll(client, ioExecutor, executor).closeAsync().toFuture().get();
    }

    @Test
    public void reserveRespectsOverride() throws Exception {
        client.reserveConnection(overridingStrategy, PING).doBeforeOnSuccess(__ -> {
            if (isInvalidThread()) {
                throw new AssertionError("Invalid thread found providing the connection. Thread: "
                        + currentThread());
            }
        }).toFuture().get().closeAsync().toFuture().get();
    }

    @Test
    public void requestRespectsOverride() throws Exception {
        ConcurrentLinkedQueue<AssertionError> errors = new ConcurrentLinkedQueue<>();
        RedisRequest req = newRequest(PING).transformContent(p -> p.doBeforeRequest(__ -> {
            if (isInvalidThread()) {
                errors.add(new AssertionError("Invalid thread called request-n. Thread: "
                        + currentThread()));
            }
        }));
        client.request(overridingStrategy, req)
                .doBeforeOnNext(__ -> {
                    if (isInvalidThread()) {
                        errors.add(new AssertionError("Invalid thread called response onNext. Thread: " +
                                currentThread()));
                    }
                }).doBeforeOnComplete(() -> {
                    if (isInvalidThread()) {
                        errors.add(new AssertionError("Invalid thread called response onComplete. Thread: "
                                + currentThread()));
                    }
                }).toFuture().get();

        assertThat("Unexpected errors: " + errors, errors, hasSize(0));
    }

    @Test
    public void requestAggregatedRespectsOverride() throws Exception {
        ConcurrentLinkedQueue<AssertionError> errors = new ConcurrentLinkedQueue<>();
        RedisRequest req = newRequest(PING).transformContent(p -> p.doBeforeRequest(n -> {
            if (isInvalidThread()) {
                errors.add(new AssertionError("Invalid thread called request-n. Thread: "
                        + currentThread()));
            }
        }));
        client.request(overridingStrategy, req, CharSequence.class)
                .doBeforeOnSuccess(__ -> {
                    if (isInvalidThread()) {
                        errors.add(new AssertionError("Invalid thread called response onNext. Thread: " +
                                currentThread()));
                    }
                }).toFuture().get();

        assertThat("Unexpected errors: " + errors, errors, hasSize(0));
    }

    private boolean isInvalidThread() {
        return isInvalidThread.test(currentThread());
    }
}
