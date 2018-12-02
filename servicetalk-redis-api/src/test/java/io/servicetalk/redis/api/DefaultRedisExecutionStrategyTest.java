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
package io.servicetalk.redis.api;

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReferenceArray;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.api.Single.never;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.redis.api.NoOffloadsRedisExecutionStrategy.NO_OFFLOADS;
import static io.servicetalk.redis.api.RedisExecutionStrategies.customStrategyBuilder;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.PING;
import static io.servicetalk.redis.api.RedisRequests.newRequest;
import static java.lang.Thread.currentThread;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

@RunWith(Parameterized.class)
public class DefaultRedisExecutionStrategyTest {

    private final boolean offloadReceive;
    private final boolean offloadSend;
    private final RedisExecutionStrategy strategy;
    private final Executor executor;

    public DefaultRedisExecutionStrategyTest(@SuppressWarnings("unused") final String description,
                                             final boolean offloadReceive, final boolean offloadSend,
                                             final boolean strategySpecifiesExecutor) {
        this.offloadReceive = offloadReceive;
        this.offloadSend = offloadSend;
        executor = newCachedThreadExecutor();
        RedisExecutionStrategies.Builder builder = customStrategyBuilder();
        if (strategySpecifiesExecutor) {
            builder.executor(executor);
        }
        if (offloadReceive) {
            builder.offloadReceive();
        }
        if (offloadSend) {
            builder.offloadSend();
        }
        if (offloadSend) {
            builder.offloadSend();
        }
        strategy = builder.build();
    }

    @Parameterized.Parameters(name = "{index} - {0}")
    public static Collection<Object[]> params() {
        List<Object[]> params = new ArrayList<>();
        params.add(newParam("exec & offload none", false, false, true));
        params.add(newParam("exec & offload all", true, true, true));
        params.add(newParam("exec & offload recv", true, false, true));
        params.add(newParam("exec & offload send", false, true, true));
        params.add(newParam("no exec & offload none", false, false, false));
        params.add(newParam("no exec & offload all", true, true, false));
        params.add(newParam("no exec & offload recv", true, false, false));
        params.add(newParam("no exec & offload send", false, true, false));
        return params;
    }

    private static Object[] newParam(String description, final boolean offloadReceive, final boolean offloadSend,
                                     final boolean strategySpecifiesExecutor) {
        Object[] param = new Object[4];
        param[0] = description;
        param[1] = offloadReceive;
        param[2] = offloadSend;
        param[3] = strategySpecifiesExecutor;
        return param;
    }

    @After
    public void tearDown() throws Exception {
        executor.closeAsync().toFuture().get();
    }

    @Test
    public void invokeClient() throws Exception {
        ThreadAnalyzer analyzer = new ThreadAnalyzer();
        RedisRequest req = analyzer.createNewRequest();
        Publisher<RedisData> resp = analyzer.createNewResponse();
        analyzer.instrumentedResponse(strategy.invokeClient(executor, req, request ->
            analyzer.instrumentedRequest(request).content().ignoreElements().concatWith(resp)
        )).toFuture().get();
        analyzer.verify();
    }

    @Test
    public void offloadSendSingle() throws Exception {
        ThreadAnalyzer analyzer = new ThreadAnalyzer();
        analyzer.instrumentSend(strategy.offloadSend(executor, never())).subscribe(__ -> { }).cancel();
        analyzer.awaitCancel.await();
        analyzer.verifySend();
    }

    @Test
    public void offloadSendPublisher() throws Exception {
        ThreadAnalyzer analyzer = new ThreadAnalyzer();
        analyzer.instrumentSend(strategy.offloadSend(executor, just(1))).toFuture().get();
        analyzer.verifySend();
    }

    @Test
    public void offloadReceiveSingle() throws Exception {
        ThreadAnalyzer analyzer = new ThreadAnalyzer();
        analyzer.instrumentReceive(strategy.offloadReceive(executor, success(1))).toFuture().get();
        analyzer.verifyReceive();
    }

    @Test
    public void offloadReceivePublisher() throws Exception {
        ThreadAnalyzer analyzer = new ThreadAnalyzer();
        analyzer.instrumentReceive(strategy.offloadReceive(executor, just(1))).toFuture().get();
        analyzer.verifyReceive();
    }

    private final class ThreadAnalyzer {

        private static final int SEND_ANALYZED_INDEX = 0;
        private static final int RECEIVE_ANALYZED_INDEX = 1;
        private final ConcurrentLinkedQueue<AssertionError> errors = new ConcurrentLinkedQueue<>();
        private final Thread testThread = currentThread();
        private final AtomicReferenceArray<Boolean> analyzed = new AtomicReferenceArray<>(2);
        private final CountDownLatch awaitCancel = new CountDownLatch(1);

        RedisRequest createNewRequest() {
            return newRequest(PING, DEFAULT_ALLOCATOR.fromAscii("Hello"));
        }

        Publisher<RedisData> createNewResponse() {
            return just(DEFAULT_ALLOCATOR.fromAscii("Hello-Response")).map(RedisData.CompleteBulkString::new);
        }

        RedisRequest instrumentedRequest(RedisRequest request) {
            return request.transformContent(p -> p.doBeforeRequest(__ -> {
                analyzed.set(SEND_ANALYZED_INDEX, true);
                verifyThread(offloadSend, "Unexpected thread requested from request.");
            }));
        }

        Publisher<RedisData> instrumentedResponse(Publisher<RedisData> resp) {
            return resp.doBeforeNext(__ -> {
                analyzed.set(RECEIVE_ANALYZED_INDEX, true);
                verifyThread(offloadReceive, "Unexpected thread for response payload onNext.");
            });
        }

        <T> Single<T> instrumentSend(Single<T> original) {
            return original.doBeforeCancel(() -> {
                analyzed.set(SEND_ANALYZED_INDEX, true);
                verifyThread(offloadSend, "Unexpected thread requested from cancel.");
                awaitCancel.countDown();
            });
        }

        <T> Publisher<T> instrumentSend(Publisher<T> original) {
            return original.doBeforeRequest(__ -> {
                analyzed.set(SEND_ANALYZED_INDEX, true);
                verifyThread(offloadSend, "Unexpected thread requested from request.");
            });
        }

        <T> Single<T> instrumentReceive(Single<T> original) {
            return original.doBeforeSuccess(__ -> {
                analyzed.set(RECEIVE_ANALYZED_INDEX, true);
                verifyThread(offloadReceive, "Unexpected thread requested from success.");
            });
        }

        <T> Publisher<T> instrumentReceive(Publisher<T> original) {
            return original.doBeforeNext(__ -> {
                analyzed.set(RECEIVE_ANALYZED_INDEX, true);
                verifyThread(offloadReceive, "Unexpected thread requested from next.");
            });
        }

        private void verifyThread(final boolean offloadedPath, final String errMsg) {
            if (strategy == NO_OFFLOADS && testThread != currentThread()) {
                addError(errMsg);
            } else if (offloadedPath && testThread == currentThread()) {
                addError(errMsg);
            }
        }

        void verifySend() {
            assertThat("Send path not analyzed.", analyzed.get(SEND_ANALYZED_INDEX), is(true));
            verifyNoErrors();
        }

        void verifyReceive() {
            assertThat("Receive path not analyzed.", analyzed.get(RECEIVE_ANALYZED_INDEX), is(true));
            verifyNoErrors();
        }

        void verify() {
            assertThat("Send path not analyzed.", analyzed.get(SEND_ANALYZED_INDEX), is(true));
            assertThat("Receive path not analyzed.", analyzed.get(RECEIVE_ANALYZED_INDEX), is(true));
            verifyNoErrors();
        }

        private void verifyNoErrors() {
            assertThat("Unexpected errors found: " + errors, errors, hasSize(0));
        }

        private void addError(final String msg) {
            errors.add(new AssertionError(msg + " Thread: " + currentThread()));
        }
    }
}
