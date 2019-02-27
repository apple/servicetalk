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
import io.servicetalk.concurrent.api.MockedSingleListenerRule;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.hamcrest.MatcherAssert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.rules.Verifier;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.lang.Thread.currentThread;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

public class ReduceSingleTest {

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExecutorRule executorRule = ExecutorRule.newRule();

    @Rule
    public final MockedSingleListenerRule<String> listenerRule = new MockedSingleListenerRule<>();

    @Rule
    public final ReducerRule reducerRule = new ReducerRule();

    private final TestPublisher<String> publisher = new TestPublisher<>();
    private final TestSubscription subscription = new TestSubscription();

    @Test
    public void testSingleItem() {
        reducerRule.listen(publisher, listenerRule);
        publisher.onNext("Hello");
        publisher.onComplete();
        listenerRule.verifySuccess("Hello");
    }

    @Test
    public void testEmpty() {
        reducerRule.listen(publisher, listenerRule);
        publisher.onComplete();
        listenerRule.verifySuccess(""); // Empty string as exactly one item is required.
    }

    @Test
    public void testMultipleItems() {
        reducerRule.listen(publisher, listenerRule);
        publisher.onNext("Hello1", "Hello2", "Hello3");
        publisher.onComplete();
        listenerRule.verifySuccess("Hello1Hello2Hello3");
    }

    @Test
    public void testError() {
        reducerRule.listen(publisher, listenerRule);
        publisher.onError(DELIBERATE_EXCEPTION);
        listenerRule.verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testFactoryReturnsNull() {
        listenerRule.listen(publisher.reduce(() -> null, (o, s) -> o));
        publisher.onNext("foo");
        publisher.onComplete();
        listenerRule.verifySuccess(null);
    }

    @Test
    public void testAggregatorReturnsNull() {
        listenerRule.listen(publisher.reduce(() -> "", (o, s) -> null));
        publisher.onNext("foo");
        publisher.onComplete();
        listenerRule.verifySuccess(null);
    }

    @Test
    public void testReducerExceptionCleanup() {
        final RuntimeException testException = new RuntimeException("fake exception");
        listenerRule.listen(publisher.reduce(() -> "", new BiFunction<String, String, String>() {
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
        }).subscribeOn(executorRule.executor()).reduce(ArrayList::new, (objects, s) -> {
            objects.add(s);
            return objects;
        }).toFuture().get();
        analyzed.await();
        MatcherAssert.assertThat("Unexpected errors observed: " + errors, errors, hasSize(0));
    }

    private void verifyThrows(Consumer<Throwable> assertFunction) {
        publisher.onSubscribe(subscription);
        try {
            publisher.onNext("Hello1", "Hello2", "Hello3");
            fail();
        } catch (Throwable cause) {
            assertFunction.accept(cause);
            assertFalse(subscription.isCancelled());
            // Now simulate failing the publisher by emit onError(...)
            publisher.onError(cause);
            listenerRule.verifyFailure(cause);
        }
    }

    private static class ReducerRule extends Verifier {

        ReducerRule listen(TestPublisher<String> testPublisher, MockedSingleListenerRule<String> listenerRule) {
            listenerRule.listen(testPublisher.reduce(() -> "", (r, s) -> r + s));
            return this;
        }
    }
}
