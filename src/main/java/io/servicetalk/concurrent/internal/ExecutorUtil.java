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
package io.servicetalk.concurrent.internal;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.Completable;
import io.servicetalk.concurrent.Completable.Subscriber;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

public final class ExecutorUtil {

    private ExecutorUtil() {
        // No instances.
    }

    /**
     * Executes the passed {@link Runnable} on the passed {@link ExecutorService}.
     *
     * @param service {@link ExecutorService} for executing the task.
     * @param task {@link Runnable} to execute.
     * @param interruptOnCancel Whether to interrup the thread when {@link Subscriber} cancels the tick.
     *
     * @return {@link Cancellable} to use for cancelling the execution.
     */
    public static Cancellable executeOnService(ExecutorService service, Runnable task, boolean interruptOnCancel) {
        Future<?> future = service.submit(task);
        return () -> future.cancel(interruptOnCancel);
    }

    /**
     * Schedules a tick on the passed {@link ScheduledExecutorService} and completes the passed {@link Subscriber} when
     * the tick is fired.
     *
     * @param subscriber {@link Subscriber} to terminate when the scheduled tick is fired.
     * @param service {@link ScheduledExecutorService} to use for tick scheduling.
     * @param interruptOnCancel Whether to interrup the thread when {@link Subscriber} cancels the tick.
     * @param duration duration of the tick in the passed {@link TimeUnit}.
     * @param timeUnit {@link TimeUnit} for the tick duration.
     */
    public static void scheduleForSubscriber(Subscriber subscriber, ScheduledExecutorService service,
                                             boolean interruptOnCancel, long duration, TimeUnit timeUnit) {
        CancellableTask task = new CancellableTask(subscriber, interruptOnCancel);
        final ScheduledFuture future;
        try {
            future = service.schedule(task, duration, timeUnit);
        } catch (Throwable t) {
            task.fail(service, t);
            return;
        }
        task.setUpstreamFuture(future);
    }

    private static final class CancellableTask implements Cancellable, Runnable {
        private static final Logger LOGGER = LoggerFactory.getLogger(CancellableTask.class);

        private final Completable.Subscriber subscriber;
        private final boolean interruptOnCancel;
        @Nullable
        private volatile ScheduledFuture upstreamFuture;
        private volatile boolean cancelled;

        CancellableTask(Completable.Subscriber subscriber, boolean interruptOnCancel) {
            this.subscriber = subscriber;
            this.interruptOnCancel = interruptOnCancel;

            // This will always happen before the schedule operation is invoked. So that means it will always happen
            // before we call terminal methods on the subscriber.
            subscriber.onSubscribe(this);
        }

        @Override
        public void cancel() {
            cancelled = true;
            ScheduledFuture upstreamFuture = this.upstreamFuture;
            if (upstreamFuture != null) {
                // It is assumed concurrent invocation of cancel is supported, and multiple invocation will result in a no-op.
                upstreamFuture.cancel(interruptOnCancel);
            }
        }

        void setUpstreamFuture(ScheduledFuture upstreamFuture) {
            this.upstreamFuture = upstreamFuture;
            if (cancelled) {
                // It is assumed concurrent invocation of cancel is supported, and multiple invocation will result in a no-op.
                upstreamFuture.cancel(interruptOnCancel);
            }
        }

        @Override
        public void run() {
            // It is assumed if the ScheduledExecutorService throws an exception, it will never run the task. This means
            // we don't have to have any concurrency protection around the subscriber (between failing it below).
            subscriber.onComplete();
        }

        void fail(Object scheduler, Throwable cause) {
            // The Scheduler API currently doesn't make any guarantees about what thread will be used to invoke the
            // subscriber. It maybe the the thread used by the underlying Scheduler and it maybe the calling thread.
            // The threading model used to invoke user code is ultimately determined by the Completable created to
            // represent the async operation, and may be inherited via the current execution context.
            subscriber.onError(cause);
            LOGGER.warn("Failed to schedule a task on the scheduler {}", scheduler, cause);
        }
    }
}
