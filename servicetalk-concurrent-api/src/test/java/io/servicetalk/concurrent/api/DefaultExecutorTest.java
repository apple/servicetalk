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
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.concurrent.internal.TerminalNotification;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.BlockingTestUtils.awaitIndefinitelyNonNull;
import static io.servicetalk.concurrent.api.Executors.from;
import static io.servicetalk.concurrent.api.Executors.newFixedSizeExecutor;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static io.servicetalk.concurrent.internal.TerminalNotification.error;
import static java.time.Duration.ofNanos;
import static java.util.concurrent.Executors.newScheduledThreadPool;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertNull;
import static org.junit.Assume.assumeThat;
import static org.junit.Assume.assumeTrue;

@RunWith(Parameterized.class)
public final class DefaultExecutorTest {

    private static final int UNBOUNDED = -1;
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExpectedException expected = ExpectedException.none();

    private final Executor executor;
    private final String name;
    private final boolean supportsCancellation;
    private final int size;

    public DefaultExecutorTest(Supplier<Executor> executorSupplier, String name, boolean supportsCancellation,
                               int size) {
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
        nameAndExecutorPairs.add(newParams(io.servicetalk.concurrent.api.Executors::newCachedThreadExecutor, "cached",
                true, UNBOUNDED));
        nameAndExecutorPairs.add(newParams(() -> {
            ExecutorService service = Executors.newCachedThreadPool();
            //noinspection Convert2MethodRef,FunctionalExpressionCanBeFolded
            return from(command -> service.execute(command));
        }, "simple-executor", false, UNBOUNDED));
        nameAndExecutorPairs.add(newParams(() -> from(newScheduledThreadPool(2)), "scheduled", true,
                UNBOUNDED /*Size defines core size, else is unbounded*/));
        nameAndExecutorPairs.add(newParams(() -> from(new ThreadPoolExecutor(2, 2, 60, SECONDS,
                new SynchronousQueue<>()), newScheduledThreadPool(2)), "different-executors", true, 2));
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
        executor.schedule(scheduled, 1, MILLISECONDS);
        scheduled.awaitDone();
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
        Executor executor = from(new RejectAllScheduler());
        expected.expect(RejectedExecutionException.class);
        executor.schedule(() -> { }, 1, SECONDS);
    }

    @Test
    public void timerRaw() throws Exception {
        executor.timer(1, NANOSECONDS).toFuture().get();
    }

    @Test
    public void timerDuration() throws Exception {
        executor.timer(ofNanos(1)).toFuture().get();
    }

    @Test
    public void timerRawCancel() throws InterruptedException {
        timerCancel(executor.timer(100, MILLISECONDS));
    }

    @Test
    public void timerDurationCancel() throws InterruptedException {
        timerCancel(executor.timer(ofNanos(1)));
    }

    private void timerCancel(Completable timer) throws InterruptedException {
        AtomicReference<Throwable> refCause = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        toSource(timer.afterCancel(latch::countDown))
                .subscribe(new CompletableSource.Subscriber() {
                    @Override
                    public void onSubscribe(final Cancellable cancellable) {
                        cancellable.cancel();
                    }

                    @Override
                    public void onComplete() {
                        // We don't want the timer to actually fire.
                        refCause.compareAndSet(null, DELIBERATE_EXCEPTION);
                    }

                    @Override
                    public void onError(final Throwable t) {
                        refCause.compareAndSet(null, t);
                    }
                });
        latch.await();
        // Give the timer some time to fire, to see if the cancel actually takes effect. If we raced and the timer did
        // fire before we cancelled we just ignore the test.
        Thread.sleep(100);
        Throwable cause = refCause.get();
        assumeThat(cause, is(not(DELIBERATE_EXCEPTION)));
        assertNull(cause);
    }

    @Test
    public void timerRawRejected() throws Exception {
        Executor executor = from(new RejectAllScheduler());
        expected.expect(ExecutionException.class);
        expected.expectCause(instanceOf(RejectedExecutionException.class));
        executor.timer(1, NANOSECONDS).toFuture().get();
    }

    @Test
    public void timerDurationRejected() throws Exception {
        Executor executor = from(new RejectAllScheduler());
        expected.expect(ExecutionException.class);
        expected.expectCause(instanceOf(RejectedExecutionException.class));
        executor.timer(ofNanos(1)).toFuture().get();
    }

    @Test
    public void submitRunnable() throws Throwable {
        Task submitted = new Task();
        executor.submit(submitted).toFuture().get();
        submitted.awaitDone();
    }

    @Test
    public void submitRunnableSupplier() throws Throwable {
        Task submitted1 = new Task();
        Task submitted2 = new Task();
        AtomicBoolean returnedSubmitted1 = new AtomicBoolean();
        Supplier<Runnable> runnableSupplier = () -> returnedSubmitted1.getAndSet(true) ? submitted2 : submitted1;
        executor.submitRunnable(runnableSupplier).toFuture().get();
        submitted1.awaitDone();
        executor.submitRunnable(runnableSupplier).toFuture().get();
        submitted2.awaitDone();
    }

    @Test
    public void submitRunnableRejected() throws Throwable {
        Executor executor = from(new RejectAllExecutor());
        expected.expect(ExecutionException.class);
        expected.expectCause(instanceOf(RejectedExecutionException.class));
        executor.submit(() -> { }).toFuture().get();
    }

    @Test
    public void submitRunnableSupplierRejected() throws Throwable {
        Executor executor = from(new RejectAllExecutor());
        expected.expect(ExecutionException.class);
        expected.expectCause(instanceOf(RejectedExecutionException.class));
        executor.submitRunnable(() -> () -> { }).toFuture().get();
    }

    @Test
    public void submitRunnableThrows() throws Throwable {
        expected.expect(ExecutionException.class);
        expected.expectCause(instanceOf(DeliberateException.class));
        executor.submit((Runnable) () -> {
            throw DELIBERATE_EXCEPTION;
        }).toFuture().get();
    }

    @Test
    public void submitRunnableSupplierThrows() throws Throwable {
        expected.expect(ExecutionException.class);
        expected.expectCause(instanceOf(DeliberateException.class));
        executor.submitRunnable(() -> () -> {
            throw DELIBERATE_EXCEPTION;
        }).toFuture().get();
    }

    @Test
    public void submitCallable() throws Throwable {
        CallableTask<Integer> submitted = new CallableTask<>(() -> 1);
        Integer result = awaitIndefinitelyNonNull(executor.submit(submitted));
        submitted.awaitDone();
        assertThat(result, is(1));
    }

    @Test
    public void submitCallableSupplier() throws Throwable {
        CallableTask<Integer> submitted1 = new CallableTask<>(() -> 1);
        CallableTask<Integer> submitted2 = new CallableTask<>(() -> 2);
        AtomicBoolean returnedSubmitted1 = new AtomicBoolean();
        Supplier<Callable<Integer>> callableSupplier =
                () -> returnedSubmitted1.getAndSet(true) ? submitted2 : submitted1;
        Integer result = awaitIndefinitelyNonNull(executor.submitCallable(callableSupplier));
        submitted1.awaitDone();
        assertThat(result, is(1));
        result = awaitIndefinitelyNonNull(executor.submitCallable(callableSupplier));
        submitted2.awaitDone();
        assertThat(result, is(2));
    }

    @Test
    public void submitCallableRejected() throws Throwable {
        Executor executor = from(new RejectAllExecutor());
        expected.expect(ExecutionException.class);
        expected.expectCause(instanceOf(RejectedExecutionException.class));
        executor.submit(() -> 1).toFuture().get();
    }

    @Test
    public void submitCallableSupplierRejected() throws Throwable {
        Executor executor = from(new RejectAllExecutor());
        expected.expect(ExecutionException.class);
        expected.expectCause(instanceOf(RejectedExecutionException.class));
        executor.submitCallable(() -> () -> 1).toFuture().get();
    }

    @Test
    public void submitCallableThrows() throws Throwable {
        expected.expect(ExecutionException.class);
        expected.expectCause(instanceOf(DeliberateException.class));
        executor.submit((Callable<Integer>) () -> {
            throw DELIBERATE_EXCEPTION;
        }).toFuture().get();
    }

    @Test
    public void submitCallableSupplierThrows() throws Throwable {
        expected.expect(ExecutionException.class);
        expected.expectCause(instanceOf(DeliberateException.class));
        executor.submitCallable(() -> (Callable<Integer>) () -> {
            throw DELIBERATE_EXCEPTION;
        }).toFuture().get();
    }

    private static Object[] newParams(Supplier<Executor> executorSupplier, String name, boolean supportsCancellation,
                                      int size) {
        return new Object[]{executorSupplier, name, supportsCancellation, size};
    }

    private static final class CallableTask<V> implements Callable<V> {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch done = new CountDownLatch(1);
        private final Callable<V> delegate;

        CallableTask(Callable<V> delegate) {
            this.delegate = delegate;
        }

        @Override
        public V call() throws Exception {
            started.countDown();
            try {
                return delegate.call();
            } finally {
                done.countDown();
            }
        }

        void awaitDone() throws Throwable {
            done.await();
        }
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
            if (terminalNotification.cause() != null) {
                throw terminalNotification.cause();
            }
        }
    }

    private static final class RejectAllExecutor implements java.util.concurrent.Executor {
        @Override
        public void execute(final Runnable command) {
            throw new RejectedExecutionException(DELIBERATE_EXCEPTION);
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
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay,
                                                         TimeUnit unit) {
            throw new RejectedExecutionException(DELIBERATE_EXCEPTION);
        }
    }
}
