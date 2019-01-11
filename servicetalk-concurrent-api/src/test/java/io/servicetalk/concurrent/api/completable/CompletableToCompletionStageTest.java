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
package io.servicetalk.concurrent.api.completable;

import io.servicetalk.concurrent.api.TestCompletable;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertSame;

public class CompletableToCompletionStageTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    private static ExecutorService jdkExecutor;

    private TestCompletable source;

    @BeforeClass
    public static void beforeClass() {
        jdkExecutor = java.util.concurrent.Executors.newCachedThreadPool();
    }

    @AfterClass
    public static void afterClass() {
        if (jdkExecutor != null) {
            jdkExecutor.shutdown();
        }
    }

    @Before
    public void setUp() throws Exception {
        source = new TestCompletable();
    }

    @Test
    public void listenBeforeComplete() throws InterruptedException {
        verifyComplete(false);
    }

    @Test
    public void completeBeforeListen() throws InterruptedException {
        verifyComplete(true);
    }

    private void verifyComplete(boolean completeBeforeListen) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        CompletionStage<String> stage = source.toCompletionStage();
        if (completeBeforeListen) {
            source.onComplete();
            stage.thenRun(latch::countDown);
        } else {
            stage.thenRun(latch::countDown);
            source.onComplete();
        }
        latch.await();
    }

    @Test
    public void listenBeforeError() throws InterruptedException {
        verifyError(false);
    }

    @Test
    public void errorBeforeListen() throws InterruptedException {
        verifyError(true);
    }

    private void verifyError(boolean errorBeforeListen) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> causeRef = new AtomicReference<>();
        CompletionStage<String> stage = source.toCompletionStage();
        if (errorBeforeListen) {
            source.onError(DELIBERATE_EXCEPTION);
            stage.exceptionally(cause -> {
                causeRef.compareAndSet(null, cause);
                latch.countDown();
                return null;
            });
        } else {
            stage.exceptionally(cause -> {
                causeRef.compareAndSet(null, cause);
                latch.countDown();
                return null;
            });
            source.onError(DELIBERATE_EXCEPTION);
        }
        latch.await();
        assertSame(DELIBERATE_EXCEPTION, causeRef.get());
    }

    @Test
    public void futureComplete() throws Exception {
        Future<String> f = source.toFuture();
        jdkExecutor.execute(source::onComplete);
        f.get();
    }

    @Test
    public void futureFail() throws Exception {
        Future<String> f = source.toFuture();
        jdkExecutor.execute(() -> source.onError(DELIBERATE_EXCEPTION));
        thrown.expect(ExecutionException.class);
        thrown.expectCause(is(DELIBERATE_EXCEPTION));
        f.get();
    }
}
