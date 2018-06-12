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

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static io.servicetalk.concurrent.api.Completable.completed;
import static org.mockito.Mockito.mock;

public final class TimeoutTestUtils {
    private TimeoutTestUtils() {
        // no instances
    }

    public static final class ScheduleQueueTestExecutor extends AbstractTestExecutor {
        public final Queue<ScheduleEvent> events = new ConcurrentLinkedQueue<>();

        @Override
        public Cancellable schedule(final Runnable task,
                                    final long delay,
                                    final TimeUnit unit) throws RejectedExecutionException {
            Cancellable c = mock(Cancellable.class);
            events.add(new ScheduleEvent(task, delay, unit, c));
            return c;
        }
    }

    public static final class ScheduleEvent {
        public final Runnable runnable;
        public final long delay;
        public final TimeUnit unit;
        public final Cancellable cancellable;

        ScheduleEvent(Runnable r, long delay, TimeUnit unit, Cancellable cancellable) {
            this.runnable = r;
            this.delay = delay;
            this.unit = unit;
            this.cancellable = cancellable;
        }

        public boolean delayEquals(long thatDelay, TimeUnit thatUnit) {
            return thatUnit.convert(delay, unit) == thatDelay;
        }
    }

    public abstract static class AbstractTestExecutor implements Executor {
        @Override
        public Cancellable execute(final Runnable task) throws RejectedExecutionException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Completable onClose() {
            return completed();
        }

        @Override
        public Completable closeAsync() {
            return completed();
        }
    }
}
