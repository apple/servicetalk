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

import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * An {@link Executor} implementation that provides methods for controlling execution of queued and schedules tasks,
 * for testing.
 */
public class TestExecutor implements Executor {

    private final Queue<RunnableWrapper> tasks = new ConcurrentLinkedQueue<>();
    private final ConcurrentNavigableMap<Long, Queue<RunnableWrapper>> scheduledTasksByNano = new ConcurrentSkipListMap<>();
    private long currentNanos = ThreadLocalRandom.current().nextLong();
    private CompletableProcessor closeProcessor = new CompletableProcessor();

    @Override
    public Cancellable execute(final Runnable task) throws RejectedExecutionException {
        final RunnableWrapper wrappedTask = new RunnableWrapper(task);
        tasks.add(wrappedTask);
        return () -> tasks.remove(wrappedTask);
    }

    @Override
    public Cancellable schedule(final Runnable task, final long delay, final TimeUnit unit)
            throws RejectedExecutionException {
        final RunnableWrapper wrappedTask = new RunnableWrapper(task);
        final long scheduledNanos = currentNanos() + unit.toNanos(delay);

        final Queue<RunnableWrapper> tasksForNanos = scheduledTasksByNano.computeIfAbsent(scheduledNanos,
                k -> new ConcurrentLinkedQueue<>());
        tasksForNanos.add(wrappedTask);

        return () -> scheduledTasksByNano.computeIfPresent(scheduledNanos, (k, tasks) -> {
            tasks.remove(wrappedTask);
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
        execute(tasks);
        return this;
    }

    /**
     * Execute the next queued ({@code execute}/{@code submit} methods) task.  Any exceptions thrown by the task will
     * propagate.
     *
     * @return this.
     */
    public TestExecutor executeNextTask() {
        if (!executeOne(tasks)) {
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
        SortedMap<Long, Queue<RunnableWrapper>> headMap = scheduledTasksByNano.headMap(currentNanos + 1);

        for (Iterator<Map.Entry<Long, Queue<RunnableWrapper>>> i = headMap.entrySet().iterator(); i.hasNext();) {
            final Map.Entry<Long, Queue<RunnableWrapper>> entry = i.next();
            execute(entry.getValue());
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
        SortedMap<Long, Queue<RunnableWrapper>> headMap = scheduledTasksByNano.headMap(currentNanos + 1);

        for (Iterator<Map.Entry<Long, Queue<RunnableWrapper>>> i = headMap.entrySet().iterator(); i.hasNext();) {
            final Map.Entry<Long, Queue<RunnableWrapper>> entry = i.next();
            if (executeOne(entry.getValue())) {
                return this;
            } else {
                i.remove();
            }
        }
        throw new IllegalStateException("No scheduled tasks to execute");
    }

    private static void execute(Queue<RunnableWrapper> tasks) {
        for (Iterator<RunnableWrapper> i = tasks.iterator(); i.hasNext();) {
            final Runnable task = i.next();
            i.remove();
            task.run();
        }
    }

    @Nullable
    private static boolean executeOne(Queue<RunnableWrapper> tasks) {
        Iterator<RunnableWrapper> i = tasks.iterator();
        if (i.hasNext()) {
            final Runnable task = i.next();
            i.remove();
            task.run();
            return true;
        }
        return false;
    }

    // Wraps Runnables to ensure that object-equality (and hashcode) is used for removal from Lists.
    // Also ensures a unique object each time, so the same Runnable can be executed multiple times.
    private static final class RunnableWrapper implements Runnable {
        private final Runnable delegate;

        private RunnableWrapper(final Runnable delegate) {
            this.delegate = delegate;
        }

        @Override
        public void run() {
            delegate.run();
        }
    }
}
