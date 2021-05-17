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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Supplier;

import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

/**
 * Executor that can execute one task at a time on a dedicated worker thread. While concurrent tasks are generally
 * rejected, the `execute(Runnable)` method provides special behavior if the invoking thread is the executor worker
 * thread: the `Runnable` will be executed immediately on the calling thread.
 */
class SingleThreadedExecutor implements java.util.concurrent.Executor, AutoCloseable {
    static final Logger LOGGER = LoggerFactory.getLogger(SingleThreadedExecutor.class);

    private static final String DEFAULT_NAME_PREFIX = "servicetalk-solo";
    private static final AtomicInteger INSTANCE_COUNT = new AtomicInteger();
    /**
     * Using plain {@link SynchronousQueue#offer(Object)} with only a single worker thread will result in occasional
     * spurious rejections as thread scheduling jitter can cause delay it entering {@link SynchronousQueue#poll()}
     * following a task. The "right" default is unfortunately difficult because the reasonable amount of time to wait is
     * dependent upon system environment and load and is, in any event, just a guess. The default is instead chosen to
     * reflect the cost of a rejected task.
     */
    private static final long DEFAULT_OFFER_WAIT = TimeUnit.MILLISECONDS.toNanos(1);

    /**
     * If less than {@link Long#MAX_VALUE} then the {@link #execute(Runnable)} queue operation will be non-blocking and
     * fast-fail if the command cannot be immediately executed.
     */
    final long offerWaitNanos;
    /**
     * If true then {@link #close()} will {@linkplain Thread#interrupt() interrupt} the worker
     * thread.
     */
    final boolean interruptOnClose;
    private final WorkerThread thread;

    /**
     * Construct new instance.
     */
    SingleThreadedExecutor() {
        this(DEFAULT_NAME_PREFIX);
    }

    /**
     * Construct new instance.
     */
    SingleThreadedExecutor(String namePrefix) {
        this(namePrefix, SynchronousQueue::new, DEFAULT_OFFER_WAIT, TimeUnit.NANOSECONDS, true);
    }

    /**
     * Construct new instance.
     *  @param namePrefix Prefix to use for name of worker thread. The prefix will always have a sequence number
     * appended.
     * @param queueSupplier Supplier for the queue used for tasks.
     * @param offerWait If less than {@link Long#MAX_VALUE} nanos then the {@link #execute(Runnable)} queue operation
     * will be non-blocking and fast-fail if the command cannot be immediately executed.
     * @param offerWaitUnits units for offer wait time.
     * @param interruptOnClose If true then {@link #close()} will {@linkplain Thread#interrupt() interrupt} the worker
     * thread to close it more quickly.
     */
    SingleThreadedExecutor(String namePrefix, Supplier<BlockingQueue> queueSupplier,
                           long offerWait, TimeUnit offerWaitUnits,
                           boolean interruptOnClose) {
        String name = namePrefix + "-" + INSTANCE_COUNT.incrementAndGet();
        this.offerWaitNanos = offerWaitUnits.toNanos(offerWait);
        this.interruptOnClose = interruptOnClose;
        thread = new WorkerThread(this, queueSupplier, name);
        thread.start();
    }

    /**
     * {@inheritDoc}
     *
     * <p>If invoked on the worker thread the provided command will be executed syncrhonously immediately.
     *
     * @param command {@inheritDoc}
     * @throws RejectedExecutionException if executor is closed, thread enqueuing task was interrupted, or the task
     * could not be enqueued
     */
    @Override
    public void execute(final Runnable command) throws RejectedExecutionException {
        if (WorkerThread.OPEN != thread.state) {
            throw new RejectedExecutionException("Executor closed");
        }

        if (Thread.currentThread() == thread) {
            try {
                command.run();
            } catch (Throwable all) {
                LOGGER.warn("Uncaught throwable from command {}", command, all);
            }
        } else {
            try {
                if (offerWaitNanos < Long.MAX_VALUE) {
                    // offer() with a (typically) very small wait to give worker a chance to enter poll().
                    // Using non-waiting offer() resulted in spurious rejections for sequential tasks.
                    if (!thread.queue.offer(command, offerWaitNanos, TimeUnit.NANOSECONDS)) {
                        // worker thread is probably busy with another task.
                        throw new RejectedExecutionException("Enqueue rejected, refusing to wait");
                    }
                 } else {
                    thread.queue.put(command);
                }
            } catch (InterruptedException woken) {
                // Interrupt status is not cleared
                throw new RejectedExecutionException("Task enqueue was interrupted", woken);
            }
        }
    }

    @Override
    public void close() {
        if (thread.isAlive() &&
                WorkerThread.stateUpdater.compareAndSet(thread, WorkerThread.OPEN, WorkerThread.CLOSED) &&
                interruptOnClose &&
                Thread.currentThread() != thread) {
            thread.interrupt();
        }
    }

    private static class WorkerThread extends AsyncContextHolderThread {
        static final AtomicIntegerFieldUpdater<WorkerThread> stateUpdater =
                newUpdater(WorkerThread.class, "state");
        static final int OPEN = 0; // running tasks
        static final int CLOSED = 1; // executor has closed
        static final int ORPHANED = 2; // executor GCed without close()
        static final int EXITED = 3; // Worker thread exit

        final BlockingQueue<Runnable> queue;
        /**
         * If the executor is discarded and GCed this reference will be cleared and the worker thread can exit.
         */
        private final WeakReference<SingleThreadedExecutor> owner;

        volatile int state;

        WorkerThread(SingleThreadedExecutor owner, Supplier<BlockingQueue> queueSupplier, String name) {
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

        public void run() {
            while (OPEN == state && null != owner.get()) {
                try {
                    // poll vs. take because we need to occasionally check if closed or abandoned
                    Runnable command = queue.poll(1L, TimeUnit.MINUTES);
                    if (null == command) {
                        continue;
                    }
                    try {
                        command.run();
                    } catch (Throwable all) {
                        LOGGER.warn("Uncaught throwable from command {}", command, all);
                    }
                } catch (InterruptedException woken) {
                    Thread.interrupted();
                    // closing?
                } catch (Throwable all) {
                    LOGGER.warn("Uncaught throwable in worker thread", all);
                }
            }
            int was = stateUpdater.getAndSet(this, null != owner.get() ? ORPHANED : EXITED);
            LOGGER.debug("WorkerThread exiting " + (CLOSED == was ? "cleanly" : "messily"));
        }
    }
}
