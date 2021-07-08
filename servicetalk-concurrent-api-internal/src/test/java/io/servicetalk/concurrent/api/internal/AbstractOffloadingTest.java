/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api.internal;

import io.servicetalk.concurrent.api.DefaultThreadFactory;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ExecutorExtension;
import io.servicetalk.concurrent.internal.DeliberateException;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.servicetalk.concurrent.api.ExecutorExtension.withCachedExecutor;
import static io.servicetalk.concurrent.api.ExecutorExtension.withExecutor;
import static io.servicetalk.concurrent.api.Executors.from;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.test.resources.TestUtils.matchThreadNamePrefix;
import static org.hamcrest.CoreMatchers.is;

public abstract class AbstractOffloadingTest {

    protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractOffloadingTest.class);

    protected static final DeliberateException DELIBERATE_EXCEPTION = new DeliberateException();

    protected enum CaptureSlot {
        APP_THREAD,
        ORIGINAL_SUBSCRIBE_THREAD,
        OFFLOADED_SUBSCRIBE_THREAD,
        ORIGINAL_SUBSCRIBER_THREAD,
        OFFLOADED_SUBSCRIBER_THREAD,
        ORIGINAL_SUBSCRIPTION_THREAD,
        OFFLOADED_SUBSCRIPTION_THREAD
    }

    protected enum TerminalOperation {
        CANCEL,
        COMPLETE,
        ERROR
    }

    /**
     * If true then "application" is executed on a separate thread from the test.
     */
    protected static final boolean APP_ISOLATION = true;
    protected static final String APP_EXECUTOR_PREFIX = "app";
    protected static final String OFFLOAD_EXECUTOR_PREFIX = "offload";
    protected static final Matcher<Thread> APP_EXECUTOR = APP_ISOLATION ?
            matchThreadNamePrefix(APP_EXECUTOR_PREFIX) :
            is(Thread.currentThread());

    protected static final Matcher<Thread> OFFLOAD_EXECUTOR = matchThreadNamePrefix(OFFLOAD_EXECUTOR_PREFIX);

    @RegisterExtension
    public final ExecutorExtension<Executor> app = APP_ISOLATION ?
            withCachedExecutor(APP_EXECUTOR_PREFIX) :
            ExecutorExtension.withExecutor(() -> immediate());
    protected final AtomicInteger offloadsStarted = new AtomicInteger();
    protected final AtomicInteger offloadsFinished = new AtomicInteger();
    protected volatile Runnable afterOffload;
    protected final ThreadPoolExecutor offloadExecutorService = new ThreadPoolExecutor(0, Integer.MAX_VALUE,
            60L, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            new DefaultThreadFactory(OFFLOAD_EXECUTOR_PREFIX)) {

        @Override
        protected void beforeExecute(final Thread t, final Runnable r) {
            super.beforeExecute(t, r);
            offloadsStarted.getAndIncrement();
            LOGGER.trace("Executing offload of {} on {}", r, t);
        }

        @Override
        protected void afterExecute(final Runnable r, final Throwable t) {
            LOGGER.trace("Finished offload of {}", r);
            offloadsFinished.getAndIncrement();
            super.afterExecute(r, t);
        }

        @Override
        protected <T> RunnableFuture<T> newTaskFor(final Runnable runnable, final T value) {
            Runnable after = () -> {
                try {
                    runnable.run();
                } finally {
                    Runnable executeAfterOffload = afterOffload;
                    if (null != executeAfterOffload) {
                        executeAfterOffload.run();
                    }
                }
            };
            return super.newTaskFor(after, value);
        }

        @Override
        protected <T> RunnableFuture<T> newTaskFor(final Callable<T> callable) {
            Callable<T> after = () -> {
                try {
                    return callable.call();
                } finally {
                    Runnable executeAfterOffload = afterOffload;
                    if (null != executeAfterOffload) {
                        executeAfterOffload.run();
                    }
                }
            };
            return super.newTaskFor(after);
        }
    };
    @RegisterExtension
    public final ExecutorExtension<Executor> offload = withExecutor(() -> from(offloadExecutorService));

    protected final CaptureThreads<CaptureSlot> capturedThreads;

    protected AbstractOffloadingTest() {
        this(new CaptureThreads(CaptureSlot.class) {
            @Override
            public void assertCaptured() {
            }
        });
    }

    protected AbstractOffloadingTest(CaptureThreads<CaptureSlot> capturedThreads) {
        this.capturedThreads = capturedThreads;
    }
}
