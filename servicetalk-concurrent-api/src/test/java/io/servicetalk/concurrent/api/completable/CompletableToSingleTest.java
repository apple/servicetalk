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
package io.servicetalk.concurrent.api.completable;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.ExecutorRule;
import io.servicetalk.concurrent.api.TestSingleSubscriber;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.lang.Thread.currentThread;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertTrue;

public class CompletableToSingleTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExecutorRule executorRule = ExecutorRule.newRule();

    private TestSingleSubscriber<String> subscriber = new TestSingleSubscriber<>();

    @Test
    public void valueSupplier() {
        toSource(Completable.completed().toSingle(() -> "foo")).subscribe(subscriber);
        assertTrue(subscriber.hasResult());
        assertThat(subscriber.takeResult(), is("foo"));
    }

    @Test
    public void valueSupplierThrows() {
        toSource(Completable.completed().<String>toSingle(() -> {
            throw DELIBERATE_EXCEPTION;
        })).subscribe(subscriber);
        assertTrue(subscriber.isErrored());
        assertThat(subscriber.takeError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void noTerminalSucceeds() throws Exception {
        assertThat(Completable.completed().toVoidSingle().toFuture().get(), nullValue());
    }

    @Test
    public void subscribeOnOriginalIsPreserved() throws InterruptedException {
        final Thread testThread = currentThread();
        final CountDownLatch analyzed = new CountDownLatch(1);
        ConcurrentLinkedQueue<AssertionError> errors = new ConcurrentLinkedQueue<>();
        Completable.never().doBeforeCancel(() -> {
            if (currentThread() == testThread) {
                errors.add(new AssertionError("Invalid thread invoked cancel. Thread: " +
                        currentThread()));
            }
            analyzed.countDown();
        }).subscribeOn(executorRule.executor()).toVoidSingle().subscribe(__ -> { }).cancel();
        analyzed.await();
        assertThat("Unexpected errors observed: " + errors, errors, hasSize(0));
    }
}
