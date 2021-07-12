/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import javax.annotation.Nullable;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * An {@link Executor} implementation that provides methods for controlling execution of queued and schedules tasks,
 * for testing.
 */
public class TestExecutor implements Executor {

    private static final AtomicInteger INSTANCES = new AtomicInteger();
    private final Queue<RunnableWrapper> tasks = new ConcurrentLinkedQueue<>();
    private final ConcurrentNavigableMap<Long, Queue<RunnableWrapper>> scheduledTasksByNano =
            new ConcurrentSkipListMap<>();
    private final long nanoOffset;
    private long currentNanos;
    private final CompletableProcessor closeProcessor = new CompletableProcessor();
    private final AtomicInteger tasksExecuted = new AtomicInteger();
    private final AtomicInteger scheduledTasksExecuted = new AtomicInteger();
    private final String instanceName = getClass().getSimpleName() + "-" + INSTANCES.incrementAndGet();

    /**
     * Create a new instance.
     */
    public TestExecutor() {
        this(ThreadLocalRandom.current().nextLong());
    }

    TestExecutor(final long epochNanos) {
        currentNanos = epochNanos;
        nanoOffset = epochNanos - Long.MIN_VALUE;
    }

    @Override
    public Cancellable execute(final Runnable task) throws RejectedExecutionException {
        final RunnableWrapper wrappedTask = new RunnableWrapper(instanceName, task);
        tasks.add(wrappedTask);
        return () -> tasks.remove(wrappedTask);
    }

    @Override
    public Cancellable schedule(final Runnable task, final long delay, final TimeUnit unit)
            throws RejectedExecutionException {
        final RunnableWrapper wrappedTask = new RunnableWrapper(instanceName, task);
        final long scheduledNanos = currentScheduledNanos() + unit.toNanos(delay);
        final Queue<RunnableWrapper> tasksForNanos = scheduledTasksByNano.computeIfAbsent(scheduledNanos,
                k -> new ConcurrentLinkedQueue<>());
        tasksForNanos.add(wrappedTask);

        return () -> scheduledTasksByNano.computeIfPresent(scheduledNanos, (k, tasks) -> {
            if (tasks.remove(wrappedTask) && tasks.isEmpty()) {
                removedScheduledQueue(scheduledNanos);
            }
            return tasks;
        });
    }

    @Override
    public Completable onClose() {
        return closeProcessor;
    }

    @Override
    public Completable closeAsync() {
        return new Completable() {
            @Override
            protected void handleSubscribe(final CompletableSource.Subscriber subscriber) {
                closeProcessor.subscribe(subscriber);
                closeProcessor.onComplete();
            }
        };
    }

    /**
     * What we want to accomplish is using a {@link ConcurrentNavigableMap} and leverage the strict ordering to obtain
     * the next scheduled task. We therefore shift the valid set of numbers for long such that the starting value
     * (aka epoch) maps to {@code 0}. Use the set of {@code long} numbers above, lets assume the epoch is
     * {@code MAX_VALUE-1}. That results in the following re-mapping.
     * <pre>
     *   MIN_VALUE, MIN_VALUE+1, ... 0, 1, 2, ... MAX_VALUE-1, MAX_VALUE <- valid long numbers
     *   MAX_VALUE-1, MAX_VALUE, MIN_VALUE, MIN_VALUE+1, ... -1, 0, 1, MAX_VALUE-2 <- remapped
     * </pre>
     * So if the {@link #currentNanos()} time is {@code MIN_VALUE+1} that means we overflowed
     * (which is OK and expected), however the adjusted time for scheduling should be {@code MIN_VALUE+3}.
     * {@code (MIN_VALUE+1) - epoch - MIN_VALUE} translates to
     * {@code (MIN_VALUE+1) - (MAX_VALUE-1) - MIN_VALUE = (MIN_VALUE+3)}. The {@code epoch - MIN_VALUE} is computed
     * upfront as {@link #nanoOffset}.
     *
     * @return the time used for scheduling and interaction with {@link #scheduledTasksByNano}.
     */
    private long currentScheduledNanos() {
        return currentNanos() - nanoOffset;
    }

    /**
     * Returns the internal clock time in nanoseconds.
     *
     * @return the internal clock time in nanoseconds.
     */
    public long currentNanos() {
        return currentNanos;
    }

    /**
     * Returns the internal clock time in milliseconds.
     *
     * @return the internal clock time in milliseconds.
     */
    public long currentMillis() {
        return currentTime(NANOSECONDS);
    }

    /**
     * Returns the internal clock time in the specified {@code unit}.
     *
     * @param unit the time unit to calculate
     * @return the internal clock time in the specified {@code unit}.
     */
    public long currentTime(final TimeUnit unit) {
        return unit.convert(currentNanos, NANOSECONDS);
    }

    /**
     * Advance the internal clock time by {@code time} in the specified {@code unit}s, executing scheduled tasks
     * whose time has come.
     * <p>
     * Queued tasks ({@code execute}/{@code submit} methods as opposed to {@code schedule}/{@code timer} methods) are
     * not executed.
     *
     * @param time the duration to advance by
     * @param unit The units for {@code time}.
     * @return this.
     */
    public TestExecutor advanceTimeBy(final long time, final TimeUnit unit) {
        advanceTimeByNoExecuteTasks(time, unit);
        executeScheduledTasks();
        return this;
    }

    /**
     * Advance the internal clock time by {@code time} in the specified {@code unit}s, <b>without</b> executing
     * scheduled tasks.
     *
     * @param time the duration to advance by
     * @param unit The units for {@code time}.
     * @return this.
     */
    public TestExecutor advanceTimeByNoExecuteTasks(final long time, final TimeUnit unit) {
        if (time <= 0) {
            throw new IllegalArgumentException("time (" + time + ") must be >0");
        }
        currentNanos += unit.toNanos(time);
        return this;
    }

    /**
     * Execute all queued ({@code execute}/{@code submit} methods) tasks.  Any exceptions thrown by tasks will
     * propagate, preventing execution of any further tasks.
     *
     * @return this.
     */
    public TestExecutor executeTasks() {
        execute(tasks, tasksExecuted);
        return this;
    }

    /**
     * Execute the next queued ({@code execute}/{@code submit} methods) task.  Any exceptions thrown by the task will
     * propagate.
     *
     * @return this.
     */
    public TestExecutor executeNextTask() {
        if (!executeOne(tasks, tasksExecuted)) {
            throw new IllegalStateException("No tasks to execute");
        }
        return this;
    }

    /**
     * Execute all scheduled ({@code schedule}/{@code timer} methods) tasks whose time has come.  Any exceptions thrown
     * by tasks will propagate, preventing execution of any further tasks.
     *
     * @return this.
     */
    public TestExecutor executeScheduledTasks() {
        SortedMap<Long, Queue<RunnableWrapper>> headMap = scheduledTasksByNano.headMap(currentScheduledNanos(), true);
        for (Iterator<Entry<Long, Queue<RunnableWrapper>>> i = headMap.entrySet().iterator(); i.hasNext();) {
            final Entry<Long, Queue<RunnableWrapper>> entry = i.next();
            execute(entry.getValue(), scheduledTasksExecuted);
            i.remove();
        }
        return this;
    }

    /**
     * Execute the next scheduled ({@code schedule}/{@code timer} methods) task whose time has come.  Any exceptions
     * thrown by the task will propagate.
     *
     * @return this.
     */
    public TestExecutor executeNextScheduledTask() {
        ConcurrentNavigableMap<Long, Queue<RunnableWrapper>> headMap =
                scheduledTasksByNano.headMap(currentScheduledNanos(), true);
        Entry<Long, Queue<RunnableWrapper>> entry = headMap.firstEntry();
        if (entry != null && executeOne(entry.getValue(), scheduledTasksExecuted)) {
            if (entry.getValue().isEmpty()) {
                removedScheduledQueue(entry.getKey());
            }
            return this;
        }
        throw new IllegalStateException("No scheduled tasks to execute");
    }

    /**
     * Returns the number of queued ({@code execute}/{@code submit} methods) tasks currently pending.
     *
     * @return the number of queued ({@code execute}/{@code submit} methods) tasks currently pending.
     */
    public int queuedTasksPending() {
        return tasks.size();
    }

    /**
     * Returns the number of scheduled ({@code schedule}/{@code timer} methods) tasks currently pending.
     *
     * @return the number of scheduled ({@code schedule}/{@code timer} methods) tasks currently pending.
     */
    public int scheduledTasksPending() {
        return scheduledTasksByNano.values().stream().mapToInt(Collection::size).sum();
    }

    /**
     * Returns the number of queued ({@code execute}/{@code submit} methods) tasks that have been executed.
     *
     * @return the number of queued ({@code execute}/{@code submit} methods) tasks that have been executed.
     */
    public int queuedTasksExecuted() {
        return tasksExecuted.get();
    }

    /**
     * Returns the number of scheduled ({@code schedule}/{@code timer} methods) tasks that have been executed.
     *
     * @return the number of scheduled ({@code schedule}/{@code timer} methods) tasks that have been executed.
     */
    public int scheduledTasksExecuted() {
        return scheduledTasksExecuted.get();
    }

    private void removedScheduledQueue(Long scheduledNanos) {
        final Queue<RunnableWrapper> removedQueue = scheduledTasksByNano.remove(scheduledNanos);

        // There maybe concurrent access to this Executor and other tasks schedule, so if in the mean time
        // someone inserts something into the queue we should attempt to add it back to the Map.
        if (!removedQueue.isEmpty()) {
            final Queue<RunnableWrapper> existingQueue =
                    scheduledTasksByNano.putIfAbsent(scheduledNanos, removedQueue);
            if (existingQueue != null) {
                existingQueue.addAll(removedQueue);
            }
        }
    }

    private static void execute(Queue<RunnableWrapper> tasks, AtomicInteger taskCount) {
        for (Iterator<RunnableWrapper> i = tasks.iterator(); i.hasNext();) {
            final Runnable task = i.next();
            i.remove();
            taskCount.incrementAndGet();
            task.run();
        }
    }

    @Nullable
    private static boolean executeOne(Queue<RunnableWrapper> tasks, AtomicInteger taskCount) {
        Iterator<RunnableWrapper> i = tasks.iterator();
        if (i.hasNext()) {
            final Runnable task = i.next();
            i.remove();
            taskCount.incrementAndGet();
            task.run();
            return true;
        }
        return false;
    }

    /**
     *  Wraps Runnables to ensure that object-equality (and hashcode) is used for removal from Lists.
     *  Also ensures a unique object each time, so the same Runnable can be executed multiple times.
     *  Sets the thread name to {@code TestExecutor-#} while running the task so that capturing the thread name makes
     *  sense and during debugging the execution context is more obvious.
     *  Adversarially set the {@link AsyncContextMap} to a hostile instance to ensure that any use of
     *  {@link AsyncContextMap} within the context of the Runnable includes appropriate setting/restoring of the
     *  context by the task.
     */
    private static final class RunnableWrapper implements Runnable {
        private final String threadName;
        private final Runnable delegate;

        private RunnableWrapper(final String threadName, final Runnable delegate) {
            this.threadName = threadName;
            this.delegate = delegate;
        }

        @Override
        public void run() {
            Thread current = Thread.currentThread();
            String oldName = current.getName();
            current.setName(threadName);
            try {
                AsyncContext.provider().wrapRunnable(delegate, InvalidAsyncContextMap.INSTANCE).run();
            } finally {
                current.setName(oldName);
            }
        }
    }

    /**
     * Any access of this {@link AsyncContextMap} instance is invalid and was meant for some other instance.
     */
    private static final class InvalidAsyncContextMap implements AsyncContextMap {
        static final AsyncContextMap INSTANCE = new InvalidAsyncContextMap();

        private InvalidAsyncContextMap() {
            // singleton
        }

        private AssertionError invalidAccess() {
            return new AssertionError("Invalid access of AsyncContextMap");
        }

        @Nullable
        @Override
        public <T> T get(final Key<T> key) {
            throw invalidAccess();
        }

        @Override
        public boolean containsKey(final Key<?> key) {
            throw invalidAccess();
        }

        @Override
        public boolean isEmpty() {
            throw invalidAccess();
        }

        @Override
        public int size() {
            throw invalidAccess();
        }

        @Nullable
        @Override
        public <T> T put(final Key<T> key, @Nullable final T value) {
            throw invalidAccess();
        }

        @Override
        public void putAll(final Map<Key<?>, Object> map) {
            throw invalidAccess();
        }

        @Override
        public <T> T remove(final Key<T> key) {
            throw invalidAccess();
        }

        @Override
        public boolean removeAll(final Iterable<Key<?>> entries) {
            throw invalidAccess();
        }

        @Override
        public void clear() {
            throw invalidAccess();
        }

        @Nullable
        @Override
        public Key<?> forEach(final BiPredicate<Key<?>, Object> consumer) {
            throw invalidAccess();
        }

        @Override
        public AsyncContextMap copy() {
            throw invalidAccess();
        }
    }
}
