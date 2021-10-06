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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.concurrent.internal.TerminalNotification;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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
import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static java.time.Duration.ofNanos;
import static java.util.concurrent.Executors.newScheduledThreadPool;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class DefaultExecutorTest {

    private static final int UNBOUNDED = -1;
    private Executor executor;

    private enum ExecutorParam {
        FIXED_SIZE {
            @Override
            boolean supportsCancellation() {
                return true;
            }

            @Override
            Executor get() {
                return newFixedSizeExecutor(2);
            }

            @Override
            int size() {
                return 2;
            }
        },
        CACHED {
            @Override
            boolean supportsCancellation() {
                return true;
            }

            @Override
            Executor get() {
                return io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor();
            }

            @Override
            int size() {
                return UNBOUNDED;
            }
        },
        SIMPLE {
            @Override
            boolean supportsCancellation() {
                return false;
            }

            @Override
            Executor get() {
                ExecutorService service = Executors.newCachedThreadPool();
                //noinspection Convert2MethodRef,FunctionalExpressionCanBeFolded
                return from(command -> service.execute(command));
            }

            @Override
            int size() {
                return UNBOUNDED;
            }
        },
        SCHEDULED {
            @Override
            boolean supportsCancellation() {
                return true;
            }

            @Override
            Executor get() {
                return from(newScheduledThreadPool(2));
            }

            @Override
            int size() {
                return UNBOUNDED;
            }
        },
        DIFFERENT_EXECUTORS {
            @Override
            boolean supportsCancellation() {
                return true;
            }

            @Override
            Executor get() {
                return from(new ThreadPoolExecutor(2, 2, 60, SECONDS,
                        new SynchronousQueue<>()), newScheduledThreadPool(2));
            }

            @Override
            int size() {
                return 2;
            }
        };

        abstract boolean supportsCancellation();

        abstract Executor get();

        abstract int size();
    }

    @AfterEach
    void tearDown() {
        executor.closeAsync().subscribe();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(ExecutorParam.class)
    void execution(ExecutorParam executorParam) throws Throwable {
        executor = executorParam.get();
        Task task = new Task();
        executor.execute(task);
        task.awaitDone();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(ExecutorParam.class)
    void longRunningTasksDoesNotHaltOthers(ExecutorParam executorParam) throws Throwable {
        executor = executorParam.get();
        Task awaitForever = Task.newAwaitForeverTask();
        executor.execute(awaitForever);
        Task task = new Task();
        executor.execute(task);
        task.awaitDone();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(ExecutorParam.class)
    void interDependentTasksCanRun(ExecutorParam executorParam) throws Throwable {
        executor = executorParam.get();
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

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(ExecutorParam.class)
    void scheduledTasks(ExecutorParam executorParam) throws Throwable {
        executor = executorParam.get();
        Task scheduled = new Task();
        executor.schedule(scheduled, 1, MILLISECONDS);
        scheduled.awaitDone();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(ExecutorParam.class)
    void cancelExecute(ExecutorParam executorParam) throws Throwable {
        executor = executorParam.get();
        assumeTrue(executorParam.supportsCancellation(),
                () -> "Ignoring executor: " + executorParam + ", it does not support cancellation.");
        CountDownLatch latch = new CountDownLatch(1);
        Task awaitTillCancelled = Task.awaitFor(latch);
        Cancellable cancellable = executor.execute(awaitTillCancelled);
        awaitTillCancelled.awaitStart();
        cancellable.cancel();
        Executable executable = () -> awaitTillCancelled.awaitDone();
        assertThrows(InterruptedException.class, executable);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(ExecutorParam.class)
    void executeRejection(ExecutorParam executorParam) {
        executor = executorParam.get();
        assumeTrue(executorParam.size() > 0,
                () -> "Ignoring executor: " + executorParam + ", it has an unbounded thread pool.");

        for (int i = 0; i < executorParam.size(); i++) {
            executor.execute(Task.newAwaitForeverTask());
        }
        Task reject = new Task();
        assertThrows(RejectedExecutionException.class, () -> executor.execute(reject));
    }

    @Test
    void rejectSchedule() {
        executor = from(new RejectAllScheduler());
        assertThrows(RejectedExecutionException.class, () -> executor.schedule(() -> {
        }, 1, SECONDS));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(ExecutorParam.class)
    void timerRaw(ExecutorParam executorParam) throws Exception {
        executor = executorParam.get();
        executor.timer(1, NANOSECONDS).toFuture().get();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(ExecutorParam.class)
    void timerDuration(ExecutorParam executorParam) throws Exception {
        executor = executorParam.get();
        executor.timer(ofNanos(1)).toFuture().get();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(ExecutorParam.class)
    void timerRawCancel(ExecutorParam executorParam) throws InterruptedException {
        executor = executorParam.get();
        timerCancel(executor.timer(100, MILLISECONDS));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(ExecutorParam.class)
    void timerDurationCancel(ExecutorParam executorParam) throws InterruptedException {
        executor = executorParam.get();
        timerCancel(executor.timer(ofNanos(1)));
    }

    private static void timerCancel(Completable timer) throws InterruptedException {
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
        assumeTrue(cause != DELIBERATE_EXCEPTION);
        assertNull(cause);
    }

    @Test
    void timerRawRejected() {
        executor = from(new RejectAllScheduler());
        Executable executable = () -> executor.timer(1, NANOSECONDS).toFuture().get();
        assertThrowsExecutionException(executable, RejectedExecutionException.class);
    }

    @Test
    void timerDurationRejected() {
        executor = from(new RejectAllScheduler());
        Executable executable = () -> executor.timer(ofNanos(1)).toFuture().get();
        assertThrowsExecutionException(executable, RejectedExecutionException.class);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(ExecutorParam.class)
    void submitRunnable(ExecutorParam executorParam) throws Throwable {
        executor = executorParam.get();
        Task submitted = new Task();
        executor.submit(submitted).toFuture().get();
        submitted.awaitDone();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(ExecutorParam.class)
    void submitRunnableSupplier(ExecutorParam executorParam) throws Throwable {
        executor = executorParam.get();
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
    void submitRunnableRejected() {
        executor = from(new RejectAllExecutor());
        Executable executable = () -> executor.submit(() -> { }).toFuture().get();
        assertThrowsExecutionException(executable, RejectedExecutionException.class);
    }

    @Test
    void submitRunnableSupplierRejected() {
        executor = from(new RejectAllExecutor());
        Executable executable = () -> executor.submitRunnable(() -> () -> { }).toFuture().get();
        assertThrowsExecutionException(executable, RejectedExecutionException.class);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(ExecutorParam.class)
    void submitRunnableThrows(ExecutorParam executorParam) {
        executor = executorParam.get();
        Executable executable = () -> executor.submit((Runnable) () -> {
            throw DELIBERATE_EXCEPTION;
        }).toFuture().get();
        assertThrowsExecutionException(executable, DeliberateException.class);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(ExecutorParam.class)
    void submitRunnableSupplierThrows(ExecutorParam executorParam) {
        executor = executorParam.get();
        Executable executable = () -> executor.submitRunnable(() -> () -> {
            throw DELIBERATE_EXCEPTION;
        }).toFuture().get();
        assertThrowsExecutionException(executable, DeliberateException.class);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(ExecutorParam.class)
    void submitCallable(ExecutorParam executorParam) throws Throwable {
        executor = executorParam.get();
        CallableTask<Integer> submitted = new CallableTask<>(() -> 1);
        Integer result = awaitIndefinitelyNonNull(executor.submit(submitted));
        submitted.awaitDone();
        assertThat(result, is(1));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(ExecutorParam.class)
    void submitCallableSupplier(ExecutorParam executorParam) throws Throwable {
        executor = executorParam.get();
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
    void submitCallableRejected() {
        executor = from(new RejectAllExecutor());
        Executable executable = () -> executor.submit(() -> 1).toFuture().get();
        assertThrowsExecutionException(executable, RejectedExecutionException.class);
    }

    @Test
    void submitCallableSupplierRejected() {
        executor = from(new RejectAllExecutor());
        Executable executable = () -> executor.submitCallable(() -> () -> 1).toFuture().get();
        assertThrowsExecutionException(executable, RejectedExecutionException.class);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(ExecutorParam.class)
    void submitCallableThrows(ExecutorParam executorParam) {
        executor = executorParam.get();
        Executable executable = () -> executor.submit((Callable<Integer>) () -> {
            throw DELIBERATE_EXCEPTION;
        }).toFuture().get();
        assertThrowsExecutionException(executable, DeliberateException.class);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(ExecutorParam.class)
    void submitCallableSupplierThrows(ExecutorParam executorParam) {
        executor = executorParam.get();
        Executable executable = () -> executor.submitCallable(() -> (Callable<Integer>) () -> {
            throw DELIBERATE_EXCEPTION;
        }).toFuture().get();
        assertThrowsExecutionException(executable, DeliberateException.class);
    }

    private static void assertThrowsExecutionException(Executable executable, Class cause) {
        ExecutionException e = assertThrows(ExecutionException.class, executable);
        assertThat(e.getCause(), instanceOf(cause));
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
                    throwException(e);
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
