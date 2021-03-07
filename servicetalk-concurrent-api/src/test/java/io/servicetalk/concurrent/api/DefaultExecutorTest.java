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
import io.servicetalk.concurrent.internal.TimeoutTracingInfoExtension;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
import java.util.stream.Stream;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@ExtendWith(TimeoutTracingInfoExtension.class)
public final class DefaultExecutorTest {

    private static final int UNBOUNDED = -1;
    private Executor executor;

    public static Stream<Arguments> executors() {
        // Use new executor for each test per parameter.
        return Stream.of(
                newArguments(() -> newFixedSizeExecutor(2), "fixed-size-2", true, 2),
                newArguments(() -> io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor(), "cached",
                        true, UNBOUNDED),
                newArguments(() -> {
                    ExecutorService service = Executors.newCachedThreadPool();
                    //noinspection Convert2MethodRef,FunctionalExpressionCanBeFolded
                    return from(command -> service.execute(command));
                }, "simple-executor", false, UNBOUNDED),
                newArguments(() -> from(newScheduledThreadPool(2)), "scheduled",
                        true,
                        UNBOUNDED /*Size defines core size, else is unbounded*/),
                newArguments(() -> from(new ThreadPoolExecutor(2, 2, 60, SECONDS,
                                new SynchronousQueue<>()), newScheduledThreadPool(2)),
                        "different-executors", true, 2));
    }

    @AfterEach
    public void tearDown() {
        if (executor != null) {
            executor.closeAsync().subscribe();
            executor = null;
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] - {1}")
    @MethodSource("executors")
    public void execution(Supplier<Executor> executorSupplier, String name, boolean supportsCancellation,
                          int size, TestReporter testReporter) throws Throwable {
        executor = executorSupplier.get();
        Task task = new Task();
        executor.execute(task);
        task.awaitDone();
    }

    @ParameterizedTest(name = "{displayName} [{index}] - {1}")
    @MethodSource("executors")
    public void longRunningTasksDoesNotHaltOthers(Supplier<Executor> executorSupplier, String name,
                                                  boolean supportsCancellation, int size) throws Throwable {
        executor = executorSupplier.get();
        Task awaitForever = Task.newAwaitForeverTask();
        executor.execute(awaitForever);
        Task task = new Task();
        executor.execute(task);
        task.awaitDone();
    }

    @ParameterizedTest(name = "{displayName} [{index}] - {1}")
    @MethodSource("executors")
    public void interDependentTasksCanRun(Supplier<Executor> executorSupplier, String name,
                                          boolean supportsCancellation, int size) throws Throwable {
        executor = executorSupplier.get();
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

    @ParameterizedTest(name = "{displayName} [{index}] - {1}")
    @MethodSource("executors")
    public void scheduledTasks(Supplier<Executor> executorSupplier, String name, boolean supportsCancellation,
                               int size) throws Throwable {
        executor = executorSupplier.get();
        Task scheduled = new Task();
        executor.schedule(scheduled, 1, MILLISECONDS);
        scheduled.awaitDone();
    }

    @ParameterizedTest(name = "{displayName} [{index}] - {1}")
    @MethodSource("executors")
    public void cancelExecute(Supplier<Executor> executorSupplier, String name, boolean supportsCancellation,
                              int size) throws Throwable {
        executor = executorSupplier.get();
        assumeTrue(supportsCancellation, () -> "Ignoring executor: " + name + ", it does not support cancellation.");
        CountDownLatch latch = new CountDownLatch(1);
        Task awaitTillCancelled = Task.awaitFor(latch);
        Cancellable cancellable = executor.execute(awaitTillCancelled);
        awaitTillCancelled.awaitStart();
        cancellable.cancel();
        Executable executable = () -> awaitTillCancelled.awaitDone();
        Exception e = assertThrows(Exception.class, executable);
        assertThat(e.getCause(), instanceOf(InterruptedException.class));
    }

    @ParameterizedTest(name = "{displayName} [{index}] - {1}")
    @MethodSource("executors")
    public void executeRejection(Supplier<Executor> executorSupplier, String name,
                                 boolean supportsCancellation, int size) {
        executor = executorSupplier.get();
        assumeTrue(size > 0, () -> "Ignoring executor: " + name + ", it has an unbounded thread pool.");

        for (int i = 0; i < size; i++) {
            executor.execute(Task.newAwaitForeverTask());
        }
        Task reject = new Task();
        assertThrows(RejectedExecutionException.class, () -> executor.execute(reject));
    }

    @Test
    public void rejectSchedule() {
        executor = from(new RejectAllScheduler());
        assertThrows(RejectedExecutionException.class, () -> {
            executor.schedule(() -> {
            }, 1, SECONDS);
        });
    }

    @ParameterizedTest(name = "{displayName} [{index}] - {1}")
    @MethodSource("executors")
    public void timerRaw(Supplier<Executor> executorSupplier, String name,
                         boolean supportsCancellation, int size) throws Exception {
        executor = executorSupplier.get();
        executor.timer(1, NANOSECONDS).toFuture().get();
    }

    @ParameterizedTest(name = "{displayName} [{index}] - {1}")
    @MethodSource("executors")
    public void timerDuration(Supplier<Executor> executorSupplier, String name,
                              boolean supportsCancellation, int size) throws Exception {
        executor = executorSupplier.get();
        executor.timer(ofNanos(1)).toFuture().get();
    }

    @ParameterizedTest(name = "{displayName} [{index}] - {1}")
    @MethodSource("executors")
    public void timerRawCancel(Supplier<Executor> executorSupplier, String name,
                               boolean supportsCancellation, int size) throws InterruptedException {
        executor = executorSupplier.get();
        timerCancel(executor.timer(100, MILLISECONDS));
    }

    @ParameterizedTest(name = "{displayName} [{index}] - {1}")
    @MethodSource("executors")
    public void timerDurationCancel(Supplier<Executor> executorSupplier, String name, boolean supportsCancellation,
                                    int size) throws InterruptedException {
        executor = executorSupplier.get();
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
        assumeTrue(cause != DELIBERATE_EXCEPTION);
        assertNull(cause);
    }

    @Test
    public void timerRawRejected() {
        executor = from(new RejectAllScheduler());
        Executable executable = () -> executor.timer(1, NANOSECONDS).toFuture().get();
        assertThrowsExecutionException(executable, RejectedExecutionException.class);
    }

    @Test
    public void timerDurationRejected() {
        executor = from(new RejectAllScheduler());
        Executable executable = () -> executor.timer(ofNanos(1)).toFuture().get();
        assertThrowsExecutionException(executable, RejectedExecutionException.class);
    }

    @ParameterizedTest(name = "{displayName} [{index}] - {1}")
    @MethodSource("executors")
    public void submitRunnable(Supplier<Executor> executorSupplier, String name, boolean supportsCancellation,
                               int size) throws Throwable {
        executor = executorSupplier.get();
        Task submitted = new Task();
        executor.submit(submitted).toFuture().get();
        submitted.awaitDone();
    }

    @ParameterizedTest(name = "{displayName} [{index}] - {1}")
    @MethodSource("executors")
    public void submitRunnableSupplier(Supplier<Executor> executorSupplier, String name, boolean supportsCancellation,
                                       int size) throws Throwable {
        executor = executorSupplier.get();
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
    public void submitRunnableRejected() {
        executor = from(new RejectAllExecutor());
        Executable executable = () -> executor.submit(() -> { }).toFuture().get();
        assertThrowsExecutionException(executable, RejectedExecutionException.class);
    }

    @Test
    public void submitRunnableSupplierRejected() {
        executor = from(new RejectAllExecutor());
        Executable executable = () -> executor.submitRunnable(() -> () -> { }).toFuture().get();
        assertThrowsExecutionException(executable, RejectedExecutionException.class);
    }

    @ParameterizedTest(name = "{displayName} [{index}] - {1}")
    @MethodSource("executors")
    public void submitRunnableThrows(Supplier<Executor> executorSupplier, String name, boolean supportsCancellation,
                                     int size) {
        executor = executorSupplier.get();
        Executable executable = () -> executor.submit((Runnable) () -> {
            throw DELIBERATE_EXCEPTION;
        }).toFuture().get();
        assertThrowsExecutionException(executable, DeliberateException.class);
    }

    @ParameterizedTest(name = "{displayName} [{index}] - {1}")
    @MethodSource("executors")
    public void submitRunnableSupplierThrows(Supplier<Executor> executorSupplier, String name,
                                             boolean supportsCancellation,
                                             int size) {
        executor = executorSupplier.get();
        Executable executable = () -> executor.submitRunnable(() -> () -> {
            throw DELIBERATE_EXCEPTION;
        }).toFuture().get();
        assertThrowsExecutionException(executable, DeliberateException.class);
    }

    @ParameterizedTest(name = "{displayName} [{index}] - {1}")
    @MethodSource("executors")
    public void submitCallable(Supplier<Executor> executorSupplier, String name, boolean supportsCancellation,
                               int size) throws Throwable {
        executor = executorSupplier.get();
        CallableTask<Integer> submitted = new CallableTask<>(() -> 1);
        Integer result = awaitIndefinitelyNonNull(executor.submit(submitted));
        submitted.awaitDone();
        assertThat(result, is(1));
    }

    @ParameterizedTest(name = "{displayName} [{index}] - {1}")
    @MethodSource("executors")
    public void submitCallableSupplier(Supplier<Executor> executorSupplier, String name, boolean supportsCancellation,
                                       int size) throws Throwable {
        executor = executorSupplier.get();
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
    public void submitCallableRejected() {
        executor = from(new RejectAllExecutor());
        Executable executable = () -> executor.submit(() -> 1).toFuture().get();
        assertThrowsExecutionException(executable, RejectedExecutionException.class);
    }

    @Test
    public void submitCallableSupplierRejected() {
        executor = from(new RejectAllExecutor());
        Executable executable = () -> executor.submitCallable(() -> () -> 1).toFuture().get();
        assertThrowsExecutionException(executable, RejectedExecutionException.class);
    }

    @ParameterizedTest(name = "{displayName} [{index}] - {1}")
    @MethodSource("executors")
    public void submitCallableThrows(Supplier<Executor> executorSupplier, String name,
                                     boolean supportsCancellation, int size) {
        executor = executorSupplier.get();
        Executable executable = () -> executor.submit((Callable<Integer>) () -> {
            throw DELIBERATE_EXCEPTION;
        }).toFuture().get();
        assertThrowsExecutionException(executable, DeliberateException.class);
    }

    @ParameterizedTest(name = "{displayName} [{index}] - {1}")
    @MethodSource("executors")
    public void submitCallableSupplierThrows(Supplier<Executor> executorSupplier, String name,
                                             boolean supportsCancellation, int size) {
        executor = executorSupplier.get();
        Executable executable = () -> executor.submitCallable(() -> (Callable<Integer>) () -> {
            throw DELIBERATE_EXCEPTION;
        }).toFuture().get();
        assertThrowsExecutionException(executable, DeliberateException.class);
    }

    private static void assertThrowsExecutionException(Executable executable, Class cause) {
        ExecutionException e = assertThrows(ExecutionException.class, executable);
        assertThat(e.getCause(), instanceOf(cause));
    }

    private static Arguments newArguments(Supplier<Executor> executorSupplier, String name,
                                          boolean supportsCancellation, int size) {
        return Arguments.of(executorSupplier, name, supportsCancellation, size);
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
