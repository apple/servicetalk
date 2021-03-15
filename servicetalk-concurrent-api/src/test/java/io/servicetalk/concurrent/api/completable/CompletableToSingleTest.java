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
package io.servicetalk.concurrent.api.completable;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ExecutorExtension;
import io.servicetalk.concurrent.test.internal.TestSingleSubscriber;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static java.lang.Thread.currentThread;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

public class CompletableToSingleTest {
    @RegisterExtension
    final ExecutorExtension<Executor> executorExtension = ExecutorExtension.withCachedExecutor();

    private TestSingleSubscriber<Void> subscriber = new TestSingleSubscriber<>();

    @Test
    public void noTerminalSucceeds() {
        toSource(Completable.completed().toSingle()).subscribe(subscriber);
        assertThat(subscriber.awaitOnSuccess(), nullValue());
    }

    @Test
    public void subscribeOnOriginalIsPreserved() throws InterruptedException {
        final Thread testThread = currentThread();
        final CountDownLatch analyzed = new CountDownLatch(1);
        ConcurrentLinkedQueue<AssertionError> errors = new ConcurrentLinkedQueue<>();
        Completable.never().beforeCancel(() -> {
            if (currentThread() == testThread) {
                errors.add(new AssertionError("Invalid thread invoked cancel. Thread: " +
                        currentThread()));
            }
            analyzed.countDown();
        }).subscribeOn(executorExtension.executor()).toSingle().subscribe(__ -> { }).cancel();
        analyzed.await();
        assertThat("Unexpected errors observed: " + errors, errors, hasSize(0));
    }
}
