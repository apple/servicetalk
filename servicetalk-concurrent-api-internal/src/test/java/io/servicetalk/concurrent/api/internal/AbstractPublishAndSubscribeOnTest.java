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
import io.servicetalk.concurrent.api.ExecutorRule;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.hamcrest.Matcher;
import org.junit.Rule;
import org.junit.rules.Timeout;

import java.util.concurrent.Callable;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.servicetalk.concurrent.api.Executors.from;
import static io.servicetalk.test.resources.TestUtils.matchThreadNamePrefix;

public abstract class AbstractPublishAndSubscribeOnTest {

    protected static final String APP_EXECUTOR_PREFIX = "app";
    protected static final String OFFLOAD_EXECUTOR_PREFIX = "offload";
    protected static final Matcher<Thread> APP_EXECUTOR = matchThreadNamePrefix(APP_EXECUTOR_PREFIX);
    protected static final Matcher<Thread> OFFLOAD_EXECUTOR = matchThreadNamePrefix(OFFLOAD_EXECUTOR_PREFIX);

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExecutorRule<Executor> app = ExecutorRule.withNamePrefix(APP_EXECUTOR_PREFIX);
    protected AtomicInteger offloadsStarted = new AtomicInteger();
    protected AtomicInteger offloadsFinished = new AtomicInteger();
    protected volatile Runnable afterOffload;
    protected final ThreadPoolExecutor offloadExecutorService = new ThreadPoolExecutor(0, Integer.MAX_VALUE,
            60L, TimeUnit.SECONDS,
            new SynchronousQueue<Runnable>(),
            new DefaultThreadFactory(OFFLOAD_EXECUTOR_PREFIX)) {

        @Override
        protected void beforeExecute(final Thread t, final Runnable r) {
            super.beforeExecute(t, r);
            offloadsStarted.getAndIncrement();
        }

        @Override
        protected void afterExecute(final Runnable r, final Throwable t) {
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
    @Rule
    public final ExecutorRule<Executor> offload = ExecutorRule.withExecutor(() -> from(offloadExecutorService));

    protected final CaptureThreads capturedThreads;

    protected AbstractPublishAndSubscribeOnTest(CaptureThreads captures) {
        capturedThreads = captures;
    }
}
