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
package io.servicetalk.concurrent.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TransferQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Supplier;

import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

/**
 * Executor that can execute one task at a time on a dedicated worker thread. While concurrent tasks may be generally
 * rejected, the {@link Executor#execute(Runnable)} method provides special behavior if the invoking thread is the
 * executor worker thread: the {@code Runnable} will be executed synchronously on the worker thread.
 */
class SingleThreadedExecutor implements java.util.concurrent.Executor, AutoCloseable {
    static final Logger LOGGER = LoggerFactory.getLogger(SingleThreadedExecutor.class);

    private static final String DEFAULT_NAME_PREFIX = "servicetalk-solo";
    private static final AtomicInteger INSTANCE_COUNT = new AtomicInteger();

    /**
     * Using plain {@link TransferQueue#tryTransfer(Object)} with only a single consumer thread will result in
     * occasional spurious rejections as thread scheduling jitter can cause delay it entering
     * {@link TransferQueue#poll()} following a task. The "right" default is unfortunately difficult because the
     * reasonable amount of time to wait is dependent upon system environment and load and is, in any event, just a
     * guess. The default is instead chosen to reflect the cost of a rejected task.
     */
    private static final long DEFAULT_OFFER_WAIT = TimeUnit.MILLISECONDS.toNanos(1);

    /**
     * If less than {@link Long#MAX_VALUE} then the {@link #execute(Runnable)} queue operation will be non-blocking and
     * fast-fail if the command cannot be immediately executed.
     */
    private final long enqueueWaitNanos;
    /**
     * If true then {@link #close()} will {@linkplain Thread#interrupt() interrupt} the worker
     * thread.
     */
    private final boolean interruptOnClose;
    private final WorkerThread thread;

    /**
     * Construct new instance.
     */
    SingleThreadedExecutor() {
        this(DEFAULT_NAME_PREFIX);
    }

    /**
     * Construct new instance.
     *
     * @param namePrefix Prefix to use for name of worker thread. The prefix will always have a sequence number
     * appended.
     */
    SingleThreadedExecutor(String namePrefix) {
        this(namePrefix, LinkedTransferQueue::new, DEFAULT_OFFER_WAIT, TimeUnit.NANOSECONDS, true);
    }

    /**
     * Construct new instance.
     *
     * @param namePrefix Prefix to use for name of worker thread. The prefix will always have a sequence number
     * appended.
     * @param queueSupplier Supplier for the {@link TransferQueue} used for tasks. If {@link #enqueueWaitNanos} is less
     * than {@link Long#MAX_VALUE} then the queue will be only used for synchronous consumer-waiting task transfers
     * otherwise tasks will be enqueued for asynchronous execution.
     * @param enqueueWait If less than {@link Long#MAX_VALUE} nanos then the {@link #execute(Runnable)} enqueue
     * operation will fail if the command cannot be enqueued in the specified time interval. If {@link Long#MAX_VALUE}
     * then the enqueue operation may block indefinitely as necessary until the {@link #execute(Runnable)} is enqueued
     * (and subject to potentially deadlocking).
     * @param enqueueWaitUnits units for enqueue wait time.
     * @param interruptOnClose If true then {@link #close()} will {@linkplain Thread#interrupt() interrupt} the worker
     * thread to close it more quickly.
     */
    SingleThreadedExecutor(String namePrefix, Supplier<? extends TransferQueue<Runnable>> queueSupplier,
                           long enqueueWait, TimeUnit enqueueWaitUnits,
                           boolean interruptOnClose) {
        String name = namePrefix + "-" + INSTANCE_COUNT.incrementAndGet();
        this.enqueueWaitNanos = enqueueWaitUnits.toNanos(enqueueWait);
        this.interruptOnClose = interruptOnClose;
        thread = new WorkerThread(this, queueSupplier, name);
    }

    /**
     * {@inheritDoc}
     *
     * <p>If invoked on the worker thread the provided command will be executed synchronously immediately.
     *
     * @param command {@inheritDoc}
     * @throws RejectedExecutionException if executor is closed, thread enqueuing task was interrupted, or the task
     * could not be enqueued
     * @throws NullPointerException if command is null
     */
    @Override
    public void execute(final Runnable command) throws RejectedExecutionException {
        Objects.requireNonNull(command, "command");

        if (WorkerThread.OPEN != thread.state) {
            if (WorkerThread.CREATED == thread.state) {
                if (WorkerThread.stateUpdater.compareAndSet(thread, WorkerThread.CREATED, WorkerThread.OPEN)) {
                    // We get to start the worker thread!
                    thread.start();
                }
            } else {
                throw new RejectedExecutionException(thread.getName() + ": Executor closed");
            }
        }

        if (Thread.currentThread() == thread) {
            try {
                command.run();
            } catch (Throwable all) {
                LOGGER.warn("Uncaught throwable from command {}", command, all);
            }
        } else {
            try {
                if (enqueueWaitNanos < Long.MAX_VALUE) {
                    // Using non-waiting tryTransfer() sometimes results in spurious rejections for sequential tasks.
                    if (!thread.queue.hasWaitingConsumer() || !thread.queue.tryTransfer(command)) {
                        // So if tryTransfer() fails, try again giving the worker thread a better chance to be ready and
                        // waiting in poll() before rejecting execution.
                        Thread.yield();
                        // tryTransfer() again with a (typically) very small wait to give worker thread a chance to
                        // poll().
                        if (!thread.queue.tryTransfer(command, enqueueWaitNanos, TimeUnit.NANOSECONDS)) {
                            // worker thread is probably busy with another task.
                            throw new RejectedExecutionException(
                                    thread.getName() + ": Refusing to wait longer; enqueue rejected for " + command);
                        }
                    }
                } else {
                    // blocking enqueue
                    thread.queue.put(command);
                }
            } catch (InterruptedException woken) {
                // Interrupt status is not cleared
                throw new RejectedExecutionException(thread.getName() + ": Task enqueue was interrupted", woken);
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>Note:</b> It is unspecified whether tasks already (or concurrently being) enqueued will be executed after
     * this method is invoked.
     */
    @Override
    public void close() {
        if (thread.isAlive()) {
            if (WorkerThread.stateUpdater.compareAndSet(thread, WorkerThread.OPEN, WorkerThread.CLOSED) &&
                    interruptOnClose &&
                    Thread.currentThread() != thread) {
                thread.interrupt();
            }
        } else {
            // closing without ever having started
            WorkerThread.stateUpdater.compareAndSet(thread, WorkerThread.CREATED, WorkerThread.EXITED);
        }
    }

    private static class WorkerThread extends AsyncContextHolderThread {
        static final AtomicIntegerFieldUpdater<WorkerThread> stateUpdater =
                newUpdater(WorkerThread.class, "state");
        private volatile int state;
        static final int CREATED = 0; // not started
        static final int OPEN = 1; // running tasks
        static final int CLOSED = 2; // executor has closed
        static final int ORPHANED = 3; // executor GCed without close()
        static final int EXITED = 4; // Worker thread exit

        private final TransferQueue<Runnable> queue;
        /**
         * If the executor is discarded and GCed this reference will be cleared and the worker thread can exit.
         */
        private final WeakReference<SingleThreadedExecutor> owner;

        WorkerThread(SingleThreadedExecutor owner,
                     Supplier<? extends TransferQueue<Runnable>> queueSupplier,
                     String name) {
            super(name);
            this.queue = queueSupplier.get();
            this.owner = new WeakReference<>(owner);

            if (!isDaemon()) {
                setDaemon(true);
            }

            if (Thread.NORM_PRIORITY != getPriority()) {
                setPriority(Thread.NORM_PRIORITY);
            }
        }

        @Override
        public void run() {
            while (OPEN == state && null != owner.get()) {
                try {
                    // poll() vs. take() because we need to occasionally check if closed or orphaned
                    Runnable command = queue.poll(1L, TimeUnit.MINUTES);
                    if (null == command) {
                        continue;
                    }
                    try {
                        command.run();
                    } catch (Throwable all) {
                        LOGGER.warn("Uncaught throwable from command {}", command, all);
                    } finally {
                        // clear the map in case somebody forgot.
                        asyncContextMap(null);
                    }
                } catch (InterruptedException woken) {
                    Thread.interrupted();
                    // closing?
                } catch (Throwable all) {
                    LOGGER.warn("Uncaught throwable in worker thread", all);
                }
            }
            if (queue.size() > 0) {
                LOGGER.warn("WorkerThread exiting with {} enqueued", queue.size());
            }
            int terminal = null != owner.get() ? ORPHANED : EXITED;
            int was = stateUpdater.getAndSet(this, terminal);
            if (CLOSED == was) {
                LOGGER.debug("WorkerThread exiting cleanly");
            } else {
                LOGGER.warn("WorkerThread exiting messily " + was + " → " + terminal);
            }
        }
    }
}
