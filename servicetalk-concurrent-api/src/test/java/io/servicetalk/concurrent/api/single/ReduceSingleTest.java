/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.ExecutorRule;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.concurrent.test.internal.TestSingleSubscriber;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.rules.Verifier;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiFunction;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.lang.Thread.currentThread;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertFalse;

public class ReduceSingleTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExecutorRule executorRule = ExecutorRule.newRule();
    @Rule
    public final ReducerRule reducerRule = new ReducerRule();

    private final TestSingleSubscriber<String> listenerRule = new TestSingleSubscriber<>();
    private final TestPublisher<String> publisher = new TestPublisher<>();
    private final TestSubscription subscription = new TestSubscription();

    @Test
    public void testSingleItem() {
        reducerRule.listen(publisher, listenerRule);
        publisher.onNext("Hello");
        publisher.onComplete();
        assertThat(listenerRule.awaitOnSuccess(), is("Hello"));
    }

    @Test
    public void testEmpty() {
        reducerRule.listen(publisher, listenerRule);
        publisher.onComplete();
        assertThat(listenerRule.awaitOnSuccess(), is("")); // Empty string as exactly one item is required.
    }

    @Test
    public void testMultipleItems() {
        reducerRule.listen(publisher, listenerRule);
        publisher.onNext("Hello1", "Hello2", "Hello3");
        publisher.onComplete();
        assertThat(listenerRule.awaitOnSuccess(), is("Hello1Hello2Hello3"));
    }

    @Test
    public void testError() {
        reducerRule.listen(publisher, listenerRule);
        publisher.onError(DELIBERATE_EXCEPTION);
        assertThat(listenerRule.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testFactoryReturnsNull() {
        toSource(publisher.<String>collect(() -> null, (o, s) -> o)).subscribe(listenerRule);
        publisher.onNext("foo");
        publisher.onComplete();
        assertThat(listenerRule.awaitOnSuccess(), is(nullValue()));
    }

    @Test
    public void testAggregatorReturnsNull() {
        toSource(publisher.collect(() -> "", (o, s) -> null)).subscribe(listenerRule);
        publisher.onNext("foo");
        publisher.onComplete();
        assertThat(listenerRule.awaitOnSuccess(), is(nullValue()));
    }

    @Test
    public void testReducerExceptionCleanup() {
        final RuntimeException testException = new RuntimeException("fake exception");
        toSource(publisher.collect(() -> "", new BiFunction<String, String, String>() {
            private int callNumber;

            @Override
            public String apply(String o, String s) {
                if (++callNumber == 2) {
                    throw testException;
                }
                return o + s;
            }
        })).subscribe(listenerRule);

        publisher.onSubscribe(subscription);
        publisher.onNext("Hello1", "Hello2", "Hello3");
        assertThat(listenerRule.awaitOnError(), is(testException));
        assertFalse(subscription.isCancelled());
    }

    @Test
    public void subscribeOnOriginalIsPreserved() throws Exception {
        final Thread testThread = currentThread();
        final CountDownLatch analyzed = new CountDownLatch(1);
        ConcurrentLinkedQueue<AssertionError> errors = new ConcurrentLinkedQueue<>();
        from("Hello").beforeRequest(__ -> {
            if (currentThread() == testThread) {
                errors.add(new AssertionError("Invalid thread invoked request-n. Thread: " +
                        currentThread()));
            }
            analyzed.countDown();
        }).subscribeOn(executorRule.executor()).collect(ArrayList::new, (objects, s) -> {
            objects.add(s);
            return objects;
        }).toFuture().get();
        analyzed.await();
        assertThat("Unexpected errors observed: " + errors, errors, hasSize(0));
    }

    private static class ReducerRule extends Verifier {

        ReducerRule listen(TestPublisher<String> testPublisher, TestSingleSubscriber<String> listenerRule) {
            toSource(testPublisher.collect(() -> "", (r, s) -> r + s)).subscribe(listenerRule);
            return this;
        }
    }
}
