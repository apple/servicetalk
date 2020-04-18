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
package io.servicetalk.benchmark.concurrent;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource.Processor;
import io.servicetalk.concurrent.CompletableSource.Subscriber;
import io.servicetalk.concurrent.api.AsyncContext;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.servicetalk.concurrent.api.Processors.newCompletableProcessor;

@Fork(1)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 5)
@Measurement(iterations = 5, time = 5)
public class CompletableProcessorBenchmark {
    static {
        AsyncContext.disable();
    }

    private ExecutorService executorService;

    @Setup(Level.Trial)
    public void setup() {
        executorService = Executors.newCachedThreadPool();
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    @Benchmark
    public void stAdd3Complete() throws InterruptedException {
        Processor processor = newCompletableProcessor();
        CountDownLatch latch = new CountDownLatch(3);
        TerminateCountDownSubscriber subscriber = new TerminateCountDownSubscriber(latch);
        processor.subscribe(subscriber);
        processor.subscribe(subscriber);
        processor.subscribe(subscriber);
        processor.onComplete();
        latch.await();
    }

    @Benchmark
    public void stAdd20Complete() throws InterruptedException {
        Processor processor = newCompletableProcessor();
        final int subscribers = 20;
        CountDownLatch latch = new CountDownLatch(subscribers);
        TerminateCountDownSubscriber subscriber = new TerminateCountDownSubscriber(latch);
        for (int i = 0; i < subscribers; ++i) {
            processor.subscribe(subscriber);
        }
        processor.onComplete();
        latch.await();
    }

    @Benchmark
    public void stAdd3Cancel() throws InterruptedException {
        Processor processor = newCompletableProcessor();
        CountDownLatch latch = new CountDownLatch(3);
        processor.subscribe(new CancelTerminateCountDownSubscriber(latch));
        processor.subscribe(new CancelTerminateCountDownSubscriber(latch));
        processor.subscribe(new CancelTerminateCountDownSubscriber(latch));
        processor.onComplete();
        latch.await();
    }

    @Benchmark
    public void mtAdd3Complete() throws InterruptedException {
        Processor processor = newCompletableProcessor();
        CountDownLatch latch = new CountDownLatch(3);
        TerminateCountDownSubscriber subscriber = new TerminateCountDownSubscriber(latch);
        executorService.execute(processor::onComplete); // execute before to race with subscribe
        processor.subscribe(subscriber);
        processor.subscribe(subscriber);
        processor.subscribe(subscriber);
        latch.await();
    }

    @Benchmark
    public void mtAdd20Complete() throws InterruptedException {
        Processor processor = newCompletableProcessor();
        final int subscribers = 20;
        CountDownLatch latch = new CountDownLatch(20);
        TerminateCountDownSubscriber subscriber = new TerminateCountDownSubscriber(latch);
        executorService.execute(processor::onComplete); // execute before to race with subscribe
        for (int i = 0; i < subscribers; ++i) {
            processor.subscribe(subscriber);
        }
        latch.await();
    }

    @Benchmark
    public void mtAdd3Cancel() throws InterruptedException {
        Processor processor = newCompletableProcessor();
        MtCancelTerminateCountDownSubscriber s1 = new MtCancelTerminateCountDownSubscriber(executorService);
        MtCancelTerminateCountDownSubscriber s2 = new MtCancelTerminateCountDownSubscriber(executorService);
        MtCancelTerminateCountDownSubscriber s3 = new MtCancelTerminateCountDownSubscriber(executorService);
        processor.subscribe(s1);
        processor.subscribe(s2);
        processor.subscribe(s3);
        executorService.execute(processor::onComplete); // execute after to race with cancel task executed in subscribe
        s1.latch.await();
        s2.latch.await();
        s3.latch.await();
    }

    private static final class TerminateCountDownSubscriber implements Subscriber {
        private final CountDownLatch latch;

        private TerminateCountDownSubscriber(final CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void onSubscribe(final Cancellable cancellable) {
        }

        @Override
        public void onComplete() {
            latch.countDown();
        }

        @Override
        public void onError(final Throwable t) {
            latch.countDown();
        }
    }

    private static final class CancelTerminateCountDownSubscriber implements Subscriber {
        private final CountDownLatch latch;
        private boolean terminated;

        private CancelTerminateCountDownSubscriber(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void onSubscribe(final Cancellable cancellable) {
            cancellable.cancel();
            if (!terminated) {
                terminated = true;
                latch.countDown();
            }
        }

        @Override
        public void onComplete() {
            if (!terminated) {
                terminated = true;
                latch.countDown();
            }
        }

        @Override
        public void onError(final Throwable t) {
            if (!terminated) {
                terminated = true;
                latch.countDown();
            }
        }
    }

    private static final class MtCancelTerminateCountDownSubscriber implements Subscriber {
        private final CountDownLatch latch;
        private final Executor executor;

        private MtCancelTerminateCountDownSubscriber(Executor executor) {
            this.latch = new CountDownLatch(1);
            this.executor = executor;
        }

        @Override
        public void onSubscribe(final Cancellable cancellable) {
            executor.execute(() -> {
                cancellable.cancel();
                latch.countDown();
            });
        }

        @Override
        public void onComplete() {
            latch.countDown();
        }

        @Override
        public void onError(final Throwable t) {
            latch.countDown();
        }
    }
}
