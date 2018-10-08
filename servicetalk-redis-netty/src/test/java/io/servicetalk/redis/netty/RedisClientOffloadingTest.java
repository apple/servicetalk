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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.redis.api.RedisClient.ReservedRedisConnection;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisData.CompleteBulkString;
import io.servicetalk.redis.api.RedisData.RequestRedisData;
import io.servicetalk.redis.api.RedisRequest;
import io.servicetalk.transport.api.ConnectionContext;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitelyNonNull;
import static io.servicetalk.redis.api.RedisConnection.SettingKey.MAX_CONCURRENCY;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.PING;
import static io.servicetalk.redis.api.RedisRequests.newRequest;
import static java.lang.Long.MAX_VALUE;
import static java.lang.Thread.currentThread;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

public class RedisClientOffloadingTest {

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    @Nullable
    private static RedisTestEnvironment env;

    private Thread testThread;
    private Queue<Throwable> errors;
    private CountDownLatch terminated;
    private ConnectionContext connectionContext;

    @BeforeClass
    public static void startClient() throws Exception {
        env = new RedisTestEnvironment(newCachedThreadExecutor());
    }

    @AfterClass
    public static void stopClient() throws Exception {
        // @After is run even if assumption in @Before is violated
        if (env == null) {
            return;
        }
        env.close();
    }

    @Before
    public void setUp() throws Exception {
        testThread = currentThread();
        errors = new ConcurrentLinkedQueue<>();
        terminated = new CountDownLatch(1);
        connectionContext = awaitIndefinitelyNonNull(getEnv().client.reserveConnection(PING)).connectionContext();
    }

    @Test
    public void requestResponseIsOffloaded() throws Exception {
        final RequestRedisData ping = new CompleteBulkString(
                connectionContext.executionContext().bufferAllocator().fromUtf8("Hello"));
        final Publisher<RequestRedisData> reqContent = just(ping).doBeforeRequest(n -> {
            if (inClientEventLoopOrTestThread().test(currentThread())) {
                errors.add(new AssertionError("Request content: request-n not offloaded"));
            }
        });
        final RedisRequest request = newRequest(PING, reqContent);
        final Publisher<RedisData> response = getEnv().client.request(request);
        subscribeTo(inClientEventLoopOrTestThread(), errors, response.doAfterFinally(terminated::countDown),
                "Response content: ");
        terminated.await();
        assertThat("Unexpected errors.", errors, is(empty()));
    }

    @Test
    public void reserveConnectionIsOffloaded() throws Exception {
        getEnv().client.reserveConnection(PING).doAfterFinally(terminated::countDown)
                .subscribe(new Single.Subscriber<ReservedRedisConnection>() {
                    @Override
                    public void onSubscribe(final Cancellable cancellable) {
                        if (inClientEventLoopOrTestThread().test(currentThread())) {
                            errors.add(new AssertionError("onSubscribe not offloaded. Thread: "
                                    + currentThread().getName()));
                        }
                    }

                    @Override
                    public void onSuccess(@Nullable final ReservedRedisConnection result) {
                        if (result == null) {
                            errors.add(new AssertionError("Reserved connection is null."));
                            return;
                        }
                        if (inClientEventLoopOrTestThread().test(currentThread())) {
                            errors.add(new AssertionError("onSuccess not offloaded. Thread: "
                                    + currentThread().getName()));
                        }
                    }

                    @Override
                    public void onError(final Throwable t) {
                        if (inClientEventLoopOrTestThread().test(currentThread())) {
                            errors.add(new AssertionError("onError was not offloaded. Thread: "
                                    + currentThread().getName()));
                        }
                        errors.add(new AssertionError("Unexpected error.", t));
                    }
                });
        terminated.await();
        assertThat("Unexpected errors.", errors, is(empty()));
    }

    @Test
    public void settingsStreamIsOffloaded() throws Exception {
        final ReservedRedisConnection connection =
                awaitIndefinitelyNonNull(getEnv().client.reserveConnection(PING));
        subscribeTo(getEnv()::isInClientEventLoop, errors,
                connection.settingStream(MAX_CONCURRENCY).doAfterFinally(terminated::countDown),
                "Client settings stream: ");
        awaitIndefinitely(connection.closeAsyncGracefully());
        terminated.await();
        assertThat("Unexpected errors.", errors, is(empty()));
    }

    @Test
    public void closeAsyncIsOffloaded() throws Exception {
        subscribeTo(getEnv()::isInClientEventLoop, errors,
                connectionContext.closeAsync().doAfterFinally(terminated::countDown));
        terminated.await();
        assertThat("Unexpected errors.", errors, is(empty()));
    }

    @Test
    public void closeAsyncGracefullyIsOffloaded() throws Exception {
        subscribeTo(getEnv()::isInClientEventLoop, errors,
                connectionContext.closeAsyncGracefully().doAfterFinally(terminated::countDown));
        terminated.await();
        assertThat("Unexpected errors.", errors, is(empty()));
    }

    @Test
    public void onCloseIsOffloaded() throws Exception {
        awaitIndefinitely(connectionContext.closeAsync());
        subscribeTo(getEnv()::isInClientEventLoop, errors,
                connectionContext.onClose().doAfterFinally(terminated::countDown));
        terminated.await();
        assertThat("Unexpected errors.", errors, is(empty()));
    }

    private Predicate<Thread> inClientEventLoopOrTestThread() {
        return thread -> getEnv().isInClientEventLoop(thread) || thread == testThread;
    }

    private void subscribeTo(Predicate<Thread> notExpectedThread, Collection<Throwable> errors, Completable source) {
        source.subscribe(new Completable.Subscriber() {
            @Override
            public void onSubscribe(final Cancellable cancellable) {
                if (notExpectedThread.test(currentThread())) {
                    errors.add(new AssertionError("onSubscribe was not offloaded. Thread: "
                            + currentThread().getName()));
                }
            }

            @Override
            public void onComplete() {
                if (notExpectedThread.test(currentThread())) {
                    errors.add(new AssertionError("onComplete was not offloaded. Thread: "
                            + currentThread().getName()));
                }
            }

            @Override
            public void onError(final Throwable t) {
                if (notExpectedThread.test(currentThread())) {
                    errors.add(new AssertionError("onError was not offloaded. Thread: "
                            + currentThread().getName()));
                }
                errors.add(new AssertionError("Unexpected error.", t));
            }
        });
    }

    private static <T> void subscribeTo(Predicate<Thread> notExpectedThread, Collection<Throwable> errors,
                                        Publisher<T> source, String msgPrefix) {
        source.subscribe(new Subscriber<T>() {
            @Override
            public void onSubscribe(final Subscription s) {
                if (notExpectedThread.test(currentThread())) {
                    errors.add(new AssertionError(msgPrefix + " onSubscribe was not offloaded. Thread: "
                            + currentThread().getName()));
                }
                s.request(MAX_VALUE);
            }

            @Override
            public void onNext(final T integer) {
                if (notExpectedThread.test(currentThread())) {
                    errors.add(new AssertionError(msgPrefix + " onNext was not offloaded for value: " + integer
                            + ". Thread: " + currentThread().getName()));
                }
            }

            @Override
            public void onError(final Throwable t) {
                if (notExpectedThread.test(currentThread())) {
                    errors.add(new AssertionError(msgPrefix + " onError was not offloaded. Thread: "
                            + currentThread().getName()));
                }
                errors.add(new AssertionError(msgPrefix + " Unexpected error.", t));
            }

            @Override
            public void onComplete() {
                if (notExpectedThread.test(currentThread())) {
                    errors.add(new AssertionError(msgPrefix + " onComplete was not offloaded. Thread: "
                            + currentThread().getName()));
                }
            }
        });
    }

    private static RedisTestEnvironment getEnv() {
        if (env == null) {
            throw new IllegalStateException("Environment is not setup.");
        }
        return env;
    }
}
