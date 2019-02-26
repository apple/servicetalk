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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

public class TestExecutor implements Executor {

    private final List<RunnableWrapper> tasks = new ArrayList<>();
    private final ConcurrentNavigableMap<Long, List<RunnableWrapper>> scheduledTasksByNano = new ConcurrentSkipListMap<>();
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

        final List<RunnableWrapper> tasksForNanos = scheduledTasksByNano.computeIfAbsent(scheduledNanos,
                k -> new ArrayList<>());
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

    public long currentNanos() {
        return currentNanos;
    }

    public long currentMillis() {
        return currentTime(NANOSECONDS);
    }

    public long currentTime(final TimeUnit unit) {
        return unit.convert(currentNanos, NANOSECONDS);
    }

    public TestExecutor advanceTimeBy(final long time, final TimeUnit unit) {
        advanceTimeByNoExecuteTasks(time, unit);
        executeTasks();
        executeScheduledTasks();
        return this;
    }

    public TestExecutor advanceTimeByNoExecuteTasks(final long time, final TimeUnit unit) {
        if (time <= 0) {
            throw new IllegalArgumentException("time (" + time + ") must be >0");
        }
        currentNanos += unit.toNanos(time);
        return this;
    }

    public TestExecutor executeTasks() {
        execute(tasks);
        return this;
    }

    public TestExecutor executeNextTask() {
        if (!executeOne(tasks)) {
            throw new IllegalStateException("No tasks to execute");
        }
        return this;
    }

    public TestExecutor executeScheduledTasks() {
        SortedMap<Long, List<RunnableWrapper>> headMap = scheduledTasksByNano.headMap(currentNanos + 1);

        for (Iterator<Map.Entry<Long, List<RunnableWrapper>>> i = headMap.entrySet().iterator(); i.hasNext();) {
            final Map.Entry<Long, List<RunnableWrapper>> entry = i.next();
            execute(entry.getValue());
            i.remove();
        }
        return this;
    }

    public TestExecutor executeNextScheduledTask() {
        SortedMap<Long, List<RunnableWrapper>> headMap = scheduledTasksByNano.headMap(currentNanos + 1);

        for (Iterator<Map.Entry<Long, List<RunnableWrapper>>> i = headMap.entrySet().iterator(); i.hasNext();) {
            final Map.Entry<Long, List<RunnableWrapper>> entry = i.next();
            if (executeOne(entry.getValue())) {
                return this;
            } else {
                i.remove();
            }
        }
        throw new IllegalStateException("No scheduled tasks to execute");
    }

    private static void execute(List<RunnableWrapper> tasks) {
        for (Iterator<RunnableWrapper> i = tasks.iterator(); i.hasNext();) {
            final Runnable task = i.next();
            i.remove();
            task.run();
        }
    }

    @Nullable
    private static boolean executeOne(List<RunnableWrapper> tasks) {
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
