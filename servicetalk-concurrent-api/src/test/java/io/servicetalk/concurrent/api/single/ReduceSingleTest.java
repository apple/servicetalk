/*
 * Copyright © 2018-2019, 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.ExecutorExtension;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.test.internal.TestSingleSubscriber;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiFunction;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.test.resources.TestUtils.assertNoAsyncErrors;
import static java.lang.Thread.currentThread;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ReduceSingleTest {
    @RegisterExtension
    static final ExecutorExtension<Executor> EXEC = ExecutorExtension.withCachedExecutor().setClassLevel(true);

    private final TestSingleSubscriber<String> listenerRule = new TestSingleSubscriber<>();
    private final TestPublisher<String> publisher = new TestPublisher<>();
    private final TestSubscription subscription = new TestSubscription();

    @Test
    void testSingleItem() {
        listen(publisher, listenerRule);
        publisher.onNext("Hello");
        publisher.onComplete();
        assertThat(listenerRule.awaitOnSuccess(), is("Hello"));
    }

    @Test
    void testEmpty() {
        listen(publisher, listenerRule);
        publisher.onComplete();
        assertThat(listenerRule.awaitOnSuccess(), is("")); // Empty string as exactly one item is required.
    }

    @Test
    void testMultipleItems() {
        listen(publisher, listenerRule);
        publisher.onNext("Hello1", "Hello2", "Hello3");
        publisher.onComplete();
        assertThat(listenerRule.awaitOnSuccess(), is("Hello1Hello2Hello3"));
    }

    @Test
    void testError() {
        listen(publisher, listenerRule);
        publisher.onError(DELIBERATE_EXCEPTION);
        assertThat(listenerRule.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void testFactoryReturnsNull() {
        toSource(publisher.<String>collect(() -> null, (o, s) -> o)).subscribe(listenerRule);
        publisher.onNext("foo");
        publisher.onComplete();
        assertThat(listenerRule.awaitOnSuccess(), is(nullValue()));
    }

    @Test
    void testAggregatorReturnsNull() {
        toSource(publisher.collect(() -> "", (o, s) -> null)).subscribe(listenerRule);
        publisher.onNext("foo");
        publisher.onComplete();
        assertThat(listenerRule.awaitOnSuccess(), is(nullValue()));
    }

    @Test
    void testReducerExceptionCleanup() {
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
    void subscribeOnOriginalIsPreserved() throws Exception {
        final Thread testThread = currentThread();
        final CountDownLatch analyzed = new CountDownLatch(1);
        ConcurrentLinkedQueue<AssertionError> errors = new ConcurrentLinkedQueue<>();
        from("Hello").beforeRequest(__ -> {
            if (currentThread() == testThread) {
                errors.add(new AssertionError("Invalid thread invoked request-n. Thread: " +
                                              currentThread()));
            }
            analyzed.countDown();
        }).subscribeOn(EXEC.executor()).collect(ArrayList::new, (objects, s) -> {
            objects.add(s);
            return objects;
        }).toFuture().get();
        analyzed.await();
        assertNoAsyncErrors(errors);
    }

    private void listen(TestPublisher<String> testPublisher, TestSingleSubscriber<String> listenerRule) {
        toSource(testPublisher.collect(() -> "", (r, s) -> r + s)).subscribe(listenerRule);
    }
}
