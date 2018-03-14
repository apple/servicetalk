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
package io.servicetalk.transport.netty.internal;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static io.servicetalk.concurrent.internal.PlatformDependent.newMpscQueue;
import static io.servicetalk.concurrent.internal.PlatformDependent.newUnboundedMpscQueue;

/**
 * A task queue that executes <em>asynchronous</em> tasks sequentially. Since the tasks executed by this queue are potentially
 * asynchronous, it is required for the tasks to call {@link #postTaskTermination()} after they are done either successfully or with a failure.
 *
 * @param <T> Type of tasks executed by this queue.
 */
public abstract class SequentialTaskQueue<T> {

    public static final int UNBOUNDED = 0;

    private static final AtomicIntegerFieldUpdater<SequentialTaskQueue> lockUpdater =
            AtomicIntegerFieldUpdater.newUpdater(SequentialTaskQueue.class, "lock");

    @SuppressWarnings("unused") private volatile int lock;
    private final Queue<T> queue;
    /**
     * This is used to protect against re-entry resulting in stack overflow. It must be created per object and cannot be
     * static, otherwise the {@link ThreadLocal} for different objects would conflict with each other.
     */
    private final ThreadLocal<Boolean> waitingForExecute = ThreadLocal.withInitial(() -> false);

    /**
     * New instance.
     *
     * @param initialCapacity Initial capacity for the queue.
     * @param maxCapacity Optional maximum capacity. {@link #UNBOUNDED} if the queue needs to be unbounded.
     */
    protected SequentialTaskQueue(int initialCapacity, int maxCapacity) {
        if (maxCapacity == UNBOUNDED) {
            queue = newUnboundedMpscQueue(initialCapacity);
        } else {
            queue = newMpscQueue(initialCapacity, maxCapacity);
        }
    }

    /**
     * Adds the passed {@code task} to this queue and triggers execution if there is no task currently executing.
     *
     * @param task to execute.
     * @return {@code true} if the task was successfully added to this queue.
     */
    public boolean offerAndTryExecute(T task) {
        if (queue.offer(task)) {
            executeNextTask();
            return true;
        }
        return false;
    }

    /**
     * Callback to inform this queue that one of the task executed by this queue has terminated either successfully or with a failure.
     */
    public final void postTaskTermination() {
        if (lockUpdater.compareAndSet(this, 1, 0)) {
            boolean wasWaitingForExecute = waitingForExecute.get();
            waitingForExecute.remove();
            if (!wasWaitingForExecute) {
                executeNextTask();
            }
        }
    }

    /**
     * Executes the passed task and call {@link #postTaskTermination()} when the task has finished execution, synchronously or asynchronously.
     *
     * @param toExecute Task to execute.
     */
    protected abstract void execute(T toExecute);

    private void executeNextTask() {
        do {
            if (!lockUpdater.compareAndSet(this, 0, 1)) {
                return;
            }

            T next = queue.poll();
            if (next != null) {
                try {
                    do {
                        waitingForExecute.set(true);
                        execute(next);
                        if (waitingForExecute.get() || !lockUpdater.compareAndSet(this, 0, 1)) {
                            return;
                        }
                    } while ((next = queue.poll()) != null);
                } finally {
                    waitingForExecute.remove();
                }
            }
            lock = 0;
        } while (!queue.isEmpty());
    }
}
