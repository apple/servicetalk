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
package io.servicetalk.concurrent.test;

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Executors;
import io.servicetalk.concurrent.internal.DeliberateException;

import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import static io.servicetalk.concurrent.api.Publisher.failed;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Publisher.never;
import static io.servicetalk.concurrent.api.test.StepVerifiers.create;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.time.Duration.ofDays;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofNanos;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PublisherStepVerifierTest {
    @Test
    public void expectSubscription() {
        create(from("foo"))
                .expectSubscription(Assert::assertNotNull)
                .expectNext("foo")
                .expectComplete()
                .verify();
    }

    @Test(expected = AssertionError.class)
    public void expectSubscriptionTimeout() {
        verifyException(() -> create(from("foo"))
                .expectSubscription(subscription -> {
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                })
                .expectComplete()
                .verify(ofNanos(10)));
    }

    @Test
    public void singleItem() {
        assertNotNull(create(from("foo"))
                .expectNext("foo")
                .expectComplete()
                .verify());
    }

    @Test
    public void singleItemNull() {
        assertNotNull(create(from((String) null))
                .expectNext((String) null)
                .expectComplete()
                .verify());
    }

    @Test
    public void singleItemDuplicateVerify() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        StepVerifier verifier = create(from("foo"))
                .expectNext(next -> {
                    assertEquals("foo", next);
                    latch.countDown();
                })
                .expectComplete();
        verifier.verify();
        verifier.verify();
        assertTrue(latch.await(10, SECONDS));
    }

    @Test
    public void singleItemLargeTimeout() {
        assertNotNull(create(from("foo"))
                .expectNext("foo")
                .expectComplete()
                .verify(ofDays(1)));
    }

    @Test
    public void twoItems() {
        create(from("foo", "bar"))
                .expectNext("foo", "bar")
                .expectComplete()
                .verify();
    }

    @Test
    public void twoItemsIterable() {
        create(from("foo", "bar"))
                .expectNext(() -> Arrays.asList("foo", "bar").iterator())
                .expectComplete()
                .verify();
    }

    @Test
    public void justError() {
        create(failed(DELIBERATE_EXCEPTION))
                .expectError(DeliberateException.class)
                .verify();
    }

    @Test
    public void justErrorPredicate() {
        create(failed(DELIBERATE_EXCEPTION))
                .expectError(cause -> cause instanceof DeliberateException)
                .verify();
    }

    @Test
    public void justErrorConsumer() {
        create(failed(DELIBERATE_EXCEPTION))
                .expectError(cause -> {
                    assertThat(cause, instanceOf(DeliberateException.class));
                })
                .verify();
    }

    @Test
    public void errorIgnoreNextConsumer() {
        create(from("foo").concat(failed(DELIBERATE_EXCEPTION)))
                .expectError(cause -> cause instanceof DeliberateException, false)
                .verify();
    }

    @Test
    public void completeIgnoreNextConsumer() {
        create(from("foo"))
                .expectComplete(false)
                .verify();
    }

    @Test(expected = AssertionError.class)
    public void justErrorTimeout() {
        verifyException(() -> create(never())
                .expectError(DeliberateException.class)
                .verify(ofNanos(10)));
    }

    @Test(expected = AssertionError.class)
    public void justErrorTimeoutExecutor() throws ExecutionException, InterruptedException {
        Executor executor = Executors.newFixedSizeExecutor(1);
        try {
            verifyException(() -> create(never())
                    .expectError(DeliberateException.class)
                    .verify(ofNanos(10), executor));
        } finally {
            executor.closeAsync().toFuture().get();
        }
    }

    @Test
    public void onNextThenError() {
        create(from("foo").concat(failed(DELIBERATE_EXCEPTION)))
                .expectNext("foo")
                .expectError(DeliberateException.class)
                .verify();
    }

    @Test(expected = AssertionError.class)
    public void incorrectOnNextExpectation() {
        verifyException(() -> create(from("foo"))
                    .expectNext("bar")
                    .expectComplete()
                    .verify());
    }

    @Test(expected = AssertionError.class)
    public void omitOnNextExpectation() {
        verifyException(() -> create(from("foo", "bar"))
                .expectNext("foo")
                .expectComplete()
                .verify());
    }

    @Test(expected = AssertionError.class)
    public void timeoutOnNever() {
        verifyException(() -> create(never())
            .expectComplete()
            .verify(ofNanos(10)));
    }

    @Test
    public void expectNextCount() {
        create(from("foo", "bar"))
                .expectNextCount(2)
                .expectComplete()
                .verify();
    }

    @Test
    public void expectNextConsumer() {
        create(from("foo"))
                .expectNext(next -> assertEquals("foo", next))
                .expectComplete()
                .verify();
    }

    @Test(expected = AssertionError.class)
    public void expectNextConsumerTimeout() {
        verifyException(() -> create(from("foo").concat(never()))
                .expectNext(next -> assertEquals("foo", next))
                .expectComplete()
                .verify(ofNanos(10)));
    }

    @Test(expected = AssertionError.class)
    public void expectNextConsumerFail() {
        verifyException(() -> create(from("foo"))
                .expectNext(next -> assertEquals("bar", next))
                .expectComplete()
                .verify());
    }

    @Test
    public void expectNextMultiConsumer() {
        create(from("foo", "bar"))
                .expectNext(2, nextIterable -> assertThat(nextIterable, contains("foo", "bar")))
                .expectComplete()
                .verify();
    }

    @Test(expected = AssertionError.class)
    public void expectNextMultiConsumerFail() {
        verifyException(() -> create(from("foo", "bar"))
                .expectNext(2, nextIterable -> assertThat(nextIterable, contains("foo")))
                .expectComplete()
                .verify());
    }

    @Test(expected = AssertionError.class)
    public void expectNextMultiConsumerTimeout() {
        verifyException(() -> create(from("foo", "bar").concat(never()))
                .expectNext(2, nextIterable -> assertThat(nextIterable, contains("foo", "bar")))
                .expectComplete()
                .verify(ofNanos(10)));
    }

    @Test(expected = AssertionError.class)
    public void expectOnErrorWhenOnComplete() {
        verifyException(() -> create(from("foo"))
                .expectNext("foo")
                .expectError(DeliberateException.class)
                .verify());
    }

    @Test(expected = AssertionError.class)
    public void expectOnCompleteWhenOnError() {
        verifyException(() -> create(failed(DELIBERATE_EXCEPTION))
                .expectComplete()
                .verify());
    }

    @Test
    public void expectNoOnNextOrTerminal() {
        create(from("foo").concat(never()))
                .expectNext("foo")
                .expectNoNextOrTerminal(ofMillis(100))
                .thenCancel()
                .verify();
    }

    @Test(expected = AssertionError.class)
    public void expectNoOnNextOrTerminalFailOnComplete() {
        verifyException(() -> create(from("foo"))
                .expectNext("foo")
                .expectNoNextOrTerminal(ofDays(1))
                .expectComplete()
                .verify());
    }

    @Test(expected = AssertionError.class)
    public void expectNoOnNextOrTerminalFailOnError() {
        verifyException(() -> create(failed(DELIBERATE_EXCEPTION))
                .expectNoNextOrTerminal(ofDays(1))
                .expectError(DeliberateException.class)
                .verify());
    }

    @Test
    public void thenCancel() {
        create(from("foo", "bar"))
                .expectNext("foo")
                .thenCancel()
                .verify();
    }

    @Test
    public void thenRequest() {
        create(from("foo", "bar"))
                .thenRequest(Long.MAX_VALUE)
                .expectNext("foo", "bar")
                .expectComplete()
                .verify();
    }

    @Test
    public void thenRequestInvalidZero() {
        thenRequestInvalid(0);
    }

    @Test
    public void thenRequestInvalidMin() {
        thenRequestInvalid(Long.MIN_VALUE);
    }

    private static void thenRequestInvalid(long invalidN) {
        create(from("foo", "bar"))
                .thenRequest(invalidN)
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    private static void verifyException(Supplier<Duration> verifier) {
        verifyException(verifier, PublisherStepVerifierTest.class.getName());
    }

    static void verifyException(Supplier<Duration> verifier, String classNamePrefix) {
        try {
            verifier.get();
            fail();
        } catch (AssertionError error) {
            StackTraceElement[] stackTraceElements = error.getStackTrace();
            assertThat(stackTraceElements.length, greaterThan(0));
            assertThat(stackTraceElements[0].getClassName(), startsWith(classNamePrefix));
            throw error;
        }
    }
}
