/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api.publisher;

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.api.DeferredEmptySubscription;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ExecutorExtension;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.TerminalNotification;
import io.servicetalk.concurrent.test.internal.TestCompletableSubscriber;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static io.servicetalk.test.resources.TestUtils.assertNoAsyncErrors;
import static java.lang.Thread.currentThread;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class PubToCompletableIgnoreTest {
    @RegisterExtension
    final ExecutorExtension<Executor> executorExtension = ExecutorExtension.withCachedExecutor();
    private final TestCompletableSubscriber listenerRule = new TestCompletableSubscriber();

    @Test
    void testSuccess() {
        listen(from("Hello"));
        listenerRule.awaitOnComplete();
    }

    @Test
    void testError() {
        listen(Publisher.failed(DELIBERATE_EXCEPTION));
        assertThat(listenerRule.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void testEmpty() {
        listen(Publisher.empty());
        listenerRule.awaitOnComplete();
    }

    @Test
    void testEmptyFromRequestN() {
        listen(new Publisher<String>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super String> subscriber) {
                subscriber.onSubscribe(new DeferredEmptySubscription(subscriber, complete()));
            }
        });
        listenerRule.awaitOnComplete();
    }

    @Test
    void testErrorFromRequestN() {
        listen(new Publisher<String>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super String> subscriber) {
                subscriber.onSubscribe(new DeferredEmptySubscription(subscriber,
                        TerminalNotification.error(DELIBERATE_EXCEPTION)));
            }
        });
        assertThat(listenerRule.awaitOnError(), is(DELIBERATE_EXCEPTION));
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
        }).subscribeOn(executorExtension.executor()).ignoreElements().toFuture().get();
        analyzed.await();
        assertNoAsyncErrors(errors);
    }

    private void listen(Publisher<String> src) {
        toSource(src.ignoreElements()).subscribe(listenerRule);
    }
}
