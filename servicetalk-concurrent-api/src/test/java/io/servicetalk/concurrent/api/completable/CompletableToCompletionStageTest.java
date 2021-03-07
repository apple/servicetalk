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
package io.servicetalk.concurrent.api.completable;

import io.servicetalk.concurrent.api.LegacyTestCompletable;
import io.servicetalk.concurrent.internal.TimeoutTracingInfoExtension;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(TimeoutTracingInfoExtension.class)
public class CompletableToCompletionStageTest {
    private static ExecutorService jdkExecutor;

    private LegacyTestCompletable source;

    @BeforeAll
    public static void beforeClass() {
        jdkExecutor = java.util.concurrent.Executors.newCachedThreadPool();
    }

    @AfterAll
    public static void afterClass() {
        if (jdkExecutor != null) {
            jdkExecutor.shutdown();
        }
    }

    @BeforeEach
    public void setUp() {
        source = new LegacyTestCompletable();
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
        CompletionStage<Void> stage = source.toCompletionStage();
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
        CompletionStage<Void> stage = source.toCompletionStage();
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
        Future<Void> f = source.toFuture();
        jdkExecutor.execute(source::onComplete);
        f.get();
    }

    @Test
    public void futureFail() {
        Future<Void> f = source.toFuture();
        jdkExecutor.execute(() -> source.onError(DELIBERATE_EXCEPTION));
        Exception e = assertThrows(ExecutionException.class, () -> f.get());
        assertThat(e.getCause(), is(DELIBERATE_EXCEPTION));
    }
}
