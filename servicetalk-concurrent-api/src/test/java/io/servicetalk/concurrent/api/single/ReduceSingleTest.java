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
package io.servicetalk.concurrent.api.single;

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ExecutorRule;
import io.servicetalk.concurrent.api.MockedSingleListenerRule;
import io.servicetalk.concurrent.api.OffloaderAwareExecutor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.PublisherRule;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.hamcrest.MatcherAssert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.rules.Verifier;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static io.servicetalk.concurrent.api.Executors.from;
import static io.servicetalk.concurrent.api.Executors.newFixedSizeExecutor;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.lang.Thread.currentThread;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class ReduceSingleTest {

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExecutorRule executorRule = new ExecutorRule();

    @Rule
    public final MockedSingleListenerRule<String> listenerRule = new MockedSingleListenerRule<>();

    @Rule
    public final PublisherRule<String> publisherRule = new PublisherRule<>();

    @Rule
    public final ReducerRule reducerRule = new ReducerRule();

    @Test
    public void testSingleItem() {
        reducerRule.listen(publisherRule, listenerRule);
        publisherRule.sendItems("Hello").complete();
        listenerRule.verifySuccess("Hello");
    }

    @Test
    public void testEmpty() {
        reducerRule.listen(publisherRule, listenerRule);
        publisherRule.complete();
        listenerRule.verifySuccess(""); // Empty string as exactly one item is required.
    }

    @Test
    public void testMultipleItems() {
        reducerRule.listen(publisherRule, listenerRule);
        publisherRule.sendItems("Hello1", "Hello2", "Hello3").complete();
        listenerRule.verifySuccess("Hello1Hello2Hello3");
    }

    @Test
    public void testError() {
        reducerRule.listen(publisherRule, listenerRule);
        publisherRule.fail();
        listenerRule.verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testFactoryReturnsNull() {
        listenerRule.listen(publisherRule.getPublisher().reduce(() -> null, (o, s) -> o));
        publisherRule.sendItems("foo").complete();
        listenerRule.verifySuccess(null);
    }

    @Test
    public void testAggregatorReturnsNull() {
        listenerRule.listen(publisherRule.getPublisher().reduce(() -> "", (o, s) -> null));
        publisherRule.sendItems("foo").complete();
        listenerRule.verifySuccess(null);
    }

    @Test
    public void testReducerExceptionCleanup() {
        final RuntimeException testException = new RuntimeException("fake exception");
        listenerRule.listen(publisherRule.getPublisher().reduce(() -> "", new BiFunction<String, String, String>() {
            private int callNumber;

            @Override
            public String apply(String o, String s) {
                if (++callNumber == 2) {
                    throw testException;
                }
                return o + s;
            }
        }));

        verifyThrows(cause -> assertSame(testException, cause));
    }

    @Test
    public void reduceShouldOffloadOnce() throws Exception {
        Executor executor = newFixedSizeExecutor(1);
        AtomicInteger taskCount = new AtomicInteger();
        Executor wrapped = new OffloaderAwareExecutor(from(task -> {
            taskCount.incrementAndGet();
            executor.execute(task);
        }), true);
        int sum = Publisher.from(1, 2, 3, 4).publishAndSubscribeOn(wrapped)
                .reduce(() -> 0, (cumulative, integer) -> cumulative + integer).toFuture().get();
        assertThat("Unexpected sum.", sum, is(10));
        assertThat("Unexpected tasks submitted.", taskCount.get(), is(1));
    }

    @Test
    public void subscribeOnOriginalIsPreserved() throws Exception {
        final Thread testThread = currentThread();
        final CountDownLatch analyzed = new CountDownLatch(1);
        ConcurrentLinkedQueue<AssertionError> errors = new ConcurrentLinkedQueue<>();
        just("Hello").doBeforeRequest(__ -> {
            if (currentThread() == testThread) {
                errors.add(new AssertionError("Invalid thread invoked request-n. Thread: " +
                        currentThread()));
            }
            analyzed.countDown();
        }).subscribeOn(executorRule.getExecutor()).reduce(ArrayList::new, (objects, s) -> {
            objects.add(s);
            return objects;
        }).toFuture().get();
        analyzed.await();
        MatcherAssert.assertThat("Unexpected errors observed: " + errors, errors, hasSize(0));
    }

    private void verifyThrows(Consumer<Throwable> assertFunction) {
        try {
            publisherRule.sendItems("Hello1", "Hello2", "Hello3");
            fail();
        } catch (Throwable cause) {
            assertFunction.accept(cause);
            // Now simulate failing the publisher by emit onError(...)
            publisherRule.fail(false, cause);
            listenerRule.verifyFailure(cause);
        }
    }

    private static class ReducerRule extends Verifier {

        ReducerRule listen(PublisherRule<String> publisherRule, MockedSingleListenerRule<String> listenerRule) {
            listenerRule.listen(publisherRule.getPublisher().reduce(() -> "", (r, s) -> r + s));
            return this;
        }
    }
}
