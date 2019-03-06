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
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
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

import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.BlockingTestUtils.awaitIndefinitelyNonNull;
import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.redis.api.RedisConnection.SettingKey.MAX_CONCURRENCY;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.PING;
import static io.servicetalk.redis.api.RedisRequests.newRequest;
import static io.servicetalk.redis.netty.RedisTestEnvironment.isInClientEventLoop;
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
        errors = new ConcurrentLinkedQueue<>();
        terminated = new CountDownLatch(1);
        connectionContext = awaitIndefinitelyNonNull(env().client.reserveConnection(PING)).connectionContext();
    }

    @Test
    public void requestResponseIsOffloaded() throws Exception {
        final RequestRedisData ping = new CompleteBulkString(
                connectionContext.executionContext().bufferAllocator().fromUtf8("Hello"));
        final Publisher<RequestRedisData> reqContent = just(ping).doBeforeRequest(n -> {
            if (isInClientEventLoop(currentThread())) {
                errors.add(new AssertionError("Request content: request-n not offloaded"));
            }
        });
        final RedisRequest request = newRequest(PING, reqContent);
        final Publisher<RedisData> response = env().client.request(request);
        subscribeTo(RedisTestEnvironment::isInClientEventLoop, errors, response.doAfterFinally(terminated::countDown),
                "Response content: ");
        terminated.await();
        assertThat("Unexpected errors.", errors, is(empty()));
    }

    @Test
    public void reserveConnectionIsOffloaded() throws Exception {
        toSource(env().client.reserveConnection(PING).doAfterFinally(terminated::countDown))
                .subscribe(new SingleSource.Subscriber<ReservedRedisConnection>() {
                    @Override
                    public void onSubscribe(final Cancellable cancellable) {
                        if (isInClientEventLoop(currentThread())) {
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
                        if (isInClientEventLoop(currentThread())) {
                            errors.add(new AssertionError("onSuccess not offloaded. Thread: "
                                    + currentThread().getName()));
                        }
                    }

                    @Override
                    public void onError(final Throwable t) {
                        if (isInClientEventLoop(currentThread())) {
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
                awaitIndefinitelyNonNull(env().client.reserveConnection(PING));
        subscribeTo(RedisTestEnvironment::isInClientEventLoop, errors,
                connection.settingStream(MAX_CONCURRENCY).doAfterFinally(terminated::countDown),
                "Client settings stream: ");
        connection.closeAsyncGracefully().toVoidFuture().get();
        terminated.await();
        assertThat("Unexpected errors.", errors, is(empty()));
    }

    @Test
    public void closeAsyncIsOffloaded() throws Exception {
        subscribeTo(RedisTestEnvironment::isInClientEventLoop, errors,
                connectionContext.closeAsync().doAfterFinally(terminated::countDown));
        terminated.await();
        assertThat("Unexpected errors.", errors, is(empty()));
    }

    @Test
    public void closeAsyncGracefullyIsOffloaded() throws Exception {
        subscribeTo(RedisTestEnvironment::isInClientEventLoop, errors,
                connectionContext.closeAsyncGracefully().doAfterFinally(terminated::countDown));
        terminated.await();
        assertThat("Unexpected errors.", errors, is(empty()));
    }

    @Test
    public void onCloseIsOffloaded() throws Exception {
        connectionContext.closeAsync().toVoidFuture().get();
        subscribeTo(RedisTestEnvironment::isInClientEventLoop, errors,
                connectionContext.onClose().doAfterFinally(terminated::countDown));
        terminated.await();
        assertThat("Unexpected errors.", errors, is(empty()));
    }

    private void subscribeTo(Predicate<Thread> notExpectedThread, Collection<Throwable> errors,
                             Completable source) {
        toSource(source).subscribe(new CompletableSource.Subscriber() {
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
        toSource(source).subscribe(new Subscriber<T>() {
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
                    errors.add(new AssertionError(msgPrefix + " onNext was not offloaded for value: " +
                            integer + ". Thread: " + currentThread().getName()));
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

    private static RedisTestEnvironment env() {
        if (env == null) {
            throw new IllegalStateException("Environment is not setup.");
        }
        return env;
    }
}
