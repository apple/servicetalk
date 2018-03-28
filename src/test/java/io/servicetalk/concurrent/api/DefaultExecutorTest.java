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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.concurrent.internal.TerminalNotification;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.api.Executors.newFixedSizeExecutor;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static io.servicetalk.concurrent.internal.TerminalNotification.error;
import static java.util.concurrent.Executors.newScheduledThreadPool;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assume.assumeTrue;

@RunWith(Parameterized.class)
public final class DefaultExecutorTest {

    private static final int UNBOUNDED = -1;
    @Rule
    public final ServiceTalkTestTimeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExpectedException expected = ExpectedException.none();

    private final Executor executor;
    private final String name;
    private final boolean supportsCancellation;
    private final int size;

    public DefaultExecutorTest(Supplier<Executor> executorSupplier, String name, boolean supportsCancellation, int size) {
        // Use new executor for each test per parameter.
        executor = executorSupplier.get();
        this.name = name;
        this.supportsCancellation = supportsCancellation;
        this.size = size;
    }

    @Parameterized.Parameters(name = "{index} - {1}")
    public static Collection<Object[]> executors() {
        List<Object[]> nameAndExecutorPairs = new ArrayList<>();
        nameAndExecutorPairs.add(newParams(() -> newFixedSizeExecutor(2), "fixed-size-2", true, 2));
        nameAndExecutorPairs.add(newParams(io.servicetalk.concurrent.api.Executors::newCachedThreadExecutor, "cached", true, UNBOUNDED));
        nameAndExecutorPairs.add(newParams(() -> {
            ExecutorService service = Executors.newCachedThreadPool();
            //noinspection Convert2MethodRef,FunctionalExpressionCanBeFolded
            return io.servicetalk.concurrent.api.Executors.from(command -> service.execute(command));
        }, "simple-executor", false, UNBOUNDED));
        nameAndExecutorPairs.add(newParams(() -> io.servicetalk.concurrent.api.Executors.from(newScheduledThreadPool(2)), "scheduled", true, UNBOUNDED /*Size defines core size, else is unbounded*/));
        nameAndExecutorPairs.add(newParams(() -> io.servicetalk.concurrent.api.Executors.from(new ThreadPoolExecutor(2, 2, 60, SECONDS, new SynchronousQueue<>()), newScheduledThreadPool(2)), "different-executors", true, 2));
        return nameAndExecutorPairs;
    }

    @After
    public void tearDown() {
        executor.closeAsync().subscribe();
    }

    @Test
    public void execution() throws Throwable {
        Task task = new Task();
        executor.execute(task);
        task.awaitDone();
    }

    @Test
    public void longRunningTasksDoesNotHaltOthers() throws Throwable {
        Task awaitForever = Task.newAwaitForeverTask();
        executor.execute(awaitForever);
        Task task = new Task();
        executor.execute(task);
        task.awaitDone();
    }

    @Test
    public void interDependentTasksCanRun() throws Throwable {
        Task first = new Task();
        Task second = new Task(() -> {
            try {
                first.awaitDone();
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        });
        executor.execute(first);
        executor.execute(second);
        second.awaitDone();
    }

    @Test
    public void scheduledTasks() throws Throwable {
        Task scheduled = new Task();
        MockedCompletableListenerRule subscriber = new MockedCompletableListenerRule();
        subscriber.listen(executor.schedule(1, MILLISECONDS).doAfterComplete(scheduled));
        scheduled.awaitDone();
        subscriber.verifyCompletion();
    }

    @Test
    public void cancelExecute() throws Throwable {
        assumeTrue("Ignoring executor: " + name + ", it does not support cancellation.", supportsCancellation);
        CountDownLatch latch = new CountDownLatch(1);
        Task awaitTillCancelled = Task.awaitFor(latch);
        Cancellable cancellable = executor.execute(awaitTillCancelled);
        awaitTillCancelled.awaitStart();
        cancellable.cancel();
        expected.expectCause(instanceOf(InterruptedException.class));
        awaitTillCancelled.awaitDone();
    }

    @Test
    public void executeRejection() {
        assumeTrue("Ignoring executor: " + name + ", it has an unbounded thread pool.", size > 0);
        for (int i = 0; i < size; i++) {
            executor.execute(Task.newAwaitForeverTask());
        }
        Task reject = new Task();
        expected.expect(RejectedExecutionException.class);
        executor.execute(reject);
    }

    @Test
    public void rejectSchedule() {
        Executor executor = io.servicetalk.concurrent.api.Executors.from(new RejectAllScheduler());
        MockedCompletableListenerRule sub = new MockedCompletableListenerRule();
        sub.listen(executor.schedule(1, SECONDS)).verifyFailure(RejectedExecutionException.class);
    }

    @Test
    public void cancelShouldNotPreventSuccessfulCompletion() throws InterruptedException {
        Executor executor = io.servicetalk.concurrent.api.Executors.from(new WaitForCancelScheduler());
        MockedCompletableListenerRule sub = new MockedCompletableListenerRule();
        CountDownLatch latch = new CountDownLatch(1);
        sub.listen(executor.schedule(1, SECONDS).doAfterComplete(latch::countDown));
        sub.cancel();
        latch.await();
    }

    private static Object[] newParams(Supplier<Executor> executorSupplier, String name, boolean supportsCancellation, int size) {
        return new Object[]{executorSupplier, name, supportsCancellation, size};
    }

    private static final class Task implements Runnable {

        private static final Runnable NOOP_TASK = () -> { };
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch done = new CountDownLatch(1);
        @Nullable
        private volatile TerminalNotification terminalNotification;
        private final Runnable actualTask;

        Task() {
            this(NOOP_TASK);
        }

        Task(Runnable actualTask) {
            this.actualTask = actualTask;
        }

        @Override
        public void run() {
            started.countDown();
            try {
                actualTask.run();
                terminalNotification = complete();
            } catch (Throwable t) {
                terminalNotification = error(t);
            } finally {
                done.countDown();
            }
        }

        static Task newAwaitForeverTask() {
            return awaitFor(new CountDownLatch(1));
        }

        static Task awaitFor(CountDownLatch latch) {
            return new Task(() -> {
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            });
        }

        void awaitStart() throws InterruptedException {
            started.await();
        }

        void awaitDone() throws Throwable {
            done.await();
            TerminalNotification terminalNotification = this.terminalNotification;
            assert terminalNotification != null;
            if (terminalNotification.getCause() != null) {
                throw terminalNotification.getCause();
            }
        }
    }

    private static final class RejectAllScheduler extends ThreadPoolExecutor implements ScheduledExecutorService {

        RejectAllScheduler() {
            super(2, 2, 60, SECONDS, new SynchronousQueue<>());
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            throw new RejectedExecutionException(DELIBERATE_EXCEPTION);
        }

        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            throw new RejectedExecutionException(DELIBERATE_EXCEPTION);
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            throw new RejectedExecutionException(DELIBERATE_EXCEPTION);
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            throw new RejectedExecutionException(DELIBERATE_EXCEPTION);
        }
    }

    private static final class WaitForCancelScheduler extends ThreadPoolExecutor implements ScheduledExecutorService {
        WaitForCancelScheduler() {
            super(2, 2, 60, SECONDS, new SynchronousQueue<>());
        }

        private static final class IgnoreCancelScheduledFuture implements ScheduledFuture<Object> {
            private final long startTime = System.nanoTime();
            final CountDownLatch latch = new CountDownLatch(1);
            volatile boolean done;

            @Override
            public long getDelay(TimeUnit unit) {
                return NANOSECONDS.toMillis(System.nanoTime() - startTime);
            }

            @Override
            public int compareTo(Delayed o) {
                return Long.compare(getDelay(NANOSECONDS), o.getDelay(NANOSECONDS));
            }

            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                latch.countDown();
                return false;
            }

            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public boolean isDone() {
                return done;
            }

            @Override
            public Object get() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Object get(long timeout, TimeUnit unit) {
                throw new UnsupportedOperationException();
            }
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            IgnoreCancelScheduledFuture future = new IgnoreCancelScheduledFuture();
            super.execute(() -> {
                try {
                    future.latch.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                command.run();
                future.done = true;
            });
            return future;
        }

        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }
    }
}
