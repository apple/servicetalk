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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

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
    final boolean nonblocking;
    final boolean interruptOnClose;
    private final WorkerThread thread;

    SingleThreadedExecutor() {
        this(DEFAULT_NAME_PREFIX);
    }

    SingleThreadedExecutor(String namePrefix) {
        this(namePrefix, true, true);
    }

    SingleThreadedExecutor(String namePrefix, boolean nonblocking, boolean interruptOnClose) {
        String name = namePrefix + "-" + INSTANCE_COUNT.incrementAndGet();
        this.nonblocking = nonblocking;
        this.interruptOnClose = interruptOnClose;
        thread = new WorkerThread(this, name);
        thread.start();
    }

    @Override
    public void execute(final Runnable command) {
        if (Thread.currentThread() == thread) {
            try {
                command.run();
            } catch (Throwable all) {
                LOGGER.warn("Uncaught throwable from command " + command, all);
            }
        } else {
            try {
                if (nonblocking) {
                    if (!thread.queue.offer(command, 100, TimeUnit.MICROSECONDS)) {
                        throw new RejectedExecutionException("Executor is busy");
                    }
                 } else {
                    thread.queue.put(command);
                }
            } catch (InterruptedException woken) {
                // Interrupt status is not cleared
                throw new RejectedExecutionException("Enqueue was interrupted", woken);
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
        static final int OPEN = 0;
        static final int CLOSED = 1;
        static final int EXITED = 2;

        final SynchronousQueue<Runnable> queue = new SynchronousQueue<>();
        /**
         * If the executor is discarded and GCed this reference will be cleared and the worker thread can exit.
         */
        private final WeakReference<SingleThreadedExecutor> owner;
        volatile int state;

        WorkerThread(SingleThreadedExecutor owner, String name) {
            super(name);
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
                    Runnable command = queue.poll(1L, TimeUnit.MINUTES);
                    if (null == command) {
                        continue;
                    }
                    try {
                        command.run();
                    } catch (Throwable all) {
                        LOGGER.warn("Uncaught throwable from command " + command, all);
                    }
                } catch (InterruptedException woken) {
                    Thread.interrupted();
                    // closing?
                } catch (Throwable all) {
                    LOGGER.warn("Uncaught throwable", all);
                }
            }
            int was = stateUpdater.getAndSet(this, EXITED);
            SingleThreadedExecutor.LOGGER.debug("WorkerThread exiting " + (CLOSED == was ? "cleanly" : "messily"));
        }
    }
}
