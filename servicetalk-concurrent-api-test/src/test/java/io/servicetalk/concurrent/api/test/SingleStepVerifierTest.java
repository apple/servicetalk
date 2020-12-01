/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api.test;

import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.AsyncContextMap;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Executors;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.DeliberateException;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Processors.newSingleProcessor;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.never;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.api.test.StepVerifiers.create;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.time.Duration.ofDays;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofNanos;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class SingleStepVerifierTest {
    private static final AsyncContextMap.Key<Integer> ASYNC_KEY = AsyncContextMap.Key.newKey();
    @Nullable
    private static Executor executor;

    @BeforeClass
    public static void beforeClass() {
        executor = Executors.newCachedThreadExecutor();
    }

    @AfterClass
    public static void afterClass() throws ExecutionException, InterruptedException {
        if (executor != null) {
            executor.closeAsync().toFuture().get();
        }
    }

    @Test
    public void expectCancellable() {
        create(succeeded("foo"))
                .expectCancellable(Assert::assertNotNull)
                .expectSuccess("foo")
                .verify();
    }

    @Test(expected = AssertionError.class)
    public void expectCancellableTimeout() {
        assert executor != null;
        CountDownLatch latch = new CountDownLatch(1);
        try {
            verifyException(() -> create(completed().publishAndSubscribeOn(executor))
                    .expectCancellable(cancellable -> {
                        try {
                            latch.await();
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .expectComplete()
                    .verify(ofNanos(10)));
        } finally {
            latch.countDown();
        }
    }

    @Test
    public void onSuccessDuplicateVerify() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        StepVerifier verifier = create(succeeded("foo"))
                .expectCancellable(cancellable -> {
                    assertNotNull(cancellable);
                    latch.countDown();
                })
                .expectSuccess("foo");
        verifier.verify();
        verifier.verify();
        assertTrue(latch.await(10, SECONDS));
    }

    @Test
    public void onSuccess() {
        assertNotNull(create(succeeded("foo"))
                .expectSuccess("foo")
                .verify());
    }

    @Test
    public void onSuccessNull() {
        assertNotNull(create(succeeded(null))
                .expectSuccess((String) null)
                .verify());
    }

    @Test
    public void onSuccessLargeTimeout() {
        assertNotNull(create(succeeded("foo"))
                .expectSuccess("foo")
                .verify(ofDays(1)));
    }

    @Test(expected = AssertionError.class)
    public void onSuccessTimeout() {
        verifyException(() -> create(never())
                .expectSuccess("foo")
                .verify(ofNanos(10)));
    }

    @Test
    public void onErrorClass() {
        create(failed(DELIBERATE_EXCEPTION))
                .expectError(DeliberateException.class)
                .verify();
    }

    @Test
    public void onErrorPredicate() {
        create(failed(DELIBERATE_EXCEPTION))
                .expectError(error -> error instanceof DeliberateException)
                .verify();
    }

    @Test
    public void onErrorConsumer() {
        create(failed(DELIBERATE_EXCEPTION))
                .expectError(error -> {
                    assertThat(error, is(DELIBERATE_EXCEPTION));
                })
                .verify();
    }

    @Test(expected = AssertionError.class)
    public void expectOnErrorWhenOnSuccess() {
        verifyException(() -> create(succeeded("foo"))
                .expectError(DeliberateException.class)
                .verify());
    }

    @Test(expected = AssertionError.class)
    public void expectOnSuccessWhenOnError() {
        verifyException(() -> create(failed(DELIBERATE_EXCEPTION))
                    .expectSuccess("foo")
                    .verify());
    }

    @Test
    public void noSignalsSubscriptionCancelSucceeds() {
        // expectNoSignals and subscription event are dequeued/processed sequentially on the Subscriber thread
        // and the scenario isn't instructed to expect the subscription so we pass the test.
        create(never())
                .expectNoSignals(ofDays(1))
                .thenCancel()
                .verify();
    }

    @Test(expected = AssertionError.class)
    public void noSignalsSuccessFail() {
        verifyException(() -> create(succeeded("foo"))
                .expectCancellable(c -> { })
                .expectNoSignals(ofDays(1))
                .expectSuccess("foo")
                .verify());
    }

    @Test(expected = AssertionError.class)
    public void noSignalsErrorFails() {
        verifyException(() -> create(failed(DELIBERATE_EXCEPTION))
                .expectCancellable(c -> { })
                .expectNoSignals(ofDays(1))
                .expectError(DeliberateException.class)
                .verify());
    }

    @Test
    public void noSignalsAfterSubscriptionSucceeds() {
        create(Single.never())
                .expectCancellable(c -> { })
                .expectNoSignals(ofMillis(100))
                .thenCancel()
                .verify();
    }

    @Test
    public void thenCancel() {
        create(succeeded("foo"))
                .thenCancel()
                .verify();
    }

    @Test
    public void thenRun() {
        SingleSource.Processor<String, String> processor = newSingleProcessor();
        create(fromSource(processor))
                .then(() -> processor.onSuccess("foo"))
                .expectSuccess("foo")
                .verify();
    }

    @Test(expected = IllegalStateException.class)
    public void thenRunThrows() {
        create(succeeded("foo"))
                .then(() -> {
                    throw new IllegalStateException();
                })
                .expectSuccess("foo")
                .verify();
    }

    @Test
    public void asyncContextOnSuccess() {
        assert executor != null;
        create(succeeded("foo").publishAndSubscribeOn(executor))
                .expectCancellable(s -> {
                    assertNotNull(s);
                    AsyncContext.put(ASYNC_KEY, 10);
                })
                .expectSuccess(next -> {
                    assertEquals("foo", next);
                    assertThat(AsyncContext.get(ASYNC_KEY), is(10));
                })
                .verify();
    }

    @Test
    public void asyncContextOnError() {
        assert executor != null;
        create(failed(DELIBERATE_EXCEPTION).publishAndSubscribeOn(executor))
                .expectCancellable(s -> {
                    assertNotNull(s);
                    AsyncContext.put(ASYNC_KEY, 10);
                })
                .expectError(error -> {
                    assertSame(DELIBERATE_EXCEPTION, error);
                    assertThat(AsyncContext.get(ASYNC_KEY), is(10));
                })
                .verify();
    }

    @Test
    public void thenAwaitRespectsDelaysComplete() {
        SingleSource.Processor<String, String> processor = newSingleProcessor();
        new InlineSingleFirstStep<>(processor, new DefaultModifiableTimeSource())
                .expectCancellable(c -> { })
                .expectNoSignals(ofDays(500))
                .thenAwait(ofDays(1000))
                .then(() -> processor.onSuccess("foo"))
                .expectSuccess("foo")
                .verify();
    }

    @Test(expected = AssertionError.class)
    public void thenAwaitRespectsDelaysEqualsFail() {
        thenAwaitRespectsDelaysFail(true);
    }

    @Test(expected = AssertionError.class)
    public void thenAwaitRespectsDelaysGTFail() {
        thenAwaitRespectsDelaysFail(false);
    }

    private static void thenAwaitRespectsDelaysFail(boolean equals) {
        SingleSource.Processor<String, String> processor = newSingleProcessor();
        verifyException(() -> new InlineSingleFirstStep<>(processor, new DefaultModifiableTimeSource())
                .expectCancellable(c -> { })
                .expectNoSignals(ofDays(equals ? 1000 : 1001))
                .thenAwait(ofDays(1000))
                .then(() -> processor.onSuccess("foo"))
                .expectSuccess("foo")
                .verify());
    }

    private static void verifyException(Supplier<Duration> verifier) {
        PublisherStepVerifierTest.verifyException(verifier, SingleStepVerifierTest.class.getName());
    }
}
