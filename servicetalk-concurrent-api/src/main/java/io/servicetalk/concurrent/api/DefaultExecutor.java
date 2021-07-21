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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static java.lang.Thread.NORM_PRIORITY;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * An implementation of {@link Executor} that uses an implementation of {@link java.util.concurrent.Executor} to execute
 * tasks.
 */
final class DefaultExecutor extends AbstractExecutor implements Consumer<Runnable> {

    private static final long DEFAULT_KEEP_ALIVE_TIME_SECONDS = 60;
    /**
     * We do not execute user code (potentially blocking/long running) on the scheduler thread and hence using a single
     * scheduler thread is usually ok. In cases, when it is not, one can always override the executor with a custom
     * scheduler.
     */
    private static final ScheduledExecutorService GLOBAL_SINGLE_THREADED_SCHEDULED_EXECUTOR =
            newSingleThreadScheduledExecutor(new DefaultThreadFactory("servicetalk-global-scheduler",
                    true, NORM_PRIORITY));
    private static final RejectedExecutionHandler DEFAULT_REJECTION_HANDLER = new AbortPolicy();

    private final InternalExecutor executor;
    private final InternalScheduler scheduler;

    DefaultExecutor(int coreSize, int maxSize, ThreadFactory threadFactory) {
        this(new ThreadPoolExecutor(coreSize, maxSize, DEFAULT_KEEP_ALIVE_TIME_SECONDS, SECONDS,
                new SynchronousQueue<>(), threadFactory, DEFAULT_REJECTION_HANDLER));
    }

    DefaultExecutor(java.util.concurrent.Executor jdkExecutor) {
        // Since we run blocking task, we should try interrupt when cancelled.
        this(jdkExecutor, true);
    }

    DefaultExecutor(java.util.concurrent.Executor jdkExecutor, boolean interruptOnCancel) {
        // Since we run blocking task, we should try interrupt when cancelled.
        this(jdkExecutor, new SingleThreadedScheduler(jdkExecutor), interruptOnCancel);
    }

    DefaultExecutor(java.util.concurrent.Executor jdkExecutor, ScheduledExecutorService scheduler) {
        // Since we run blocking task, we should try interrupt when cancelled.
        this(jdkExecutor, scheduler, true);
    }

    DefaultExecutor(java.util.concurrent.Executor jdkExecutor, ScheduledExecutorService scheduler,
                    boolean interruptOnCancel) {
        this(jdkExecutor, newScheduler(scheduler, interruptOnCancel), interruptOnCancel);
    }

    private DefaultExecutor(@Nullable java.util.concurrent.Executor jdkExecutor, @Nullable InternalScheduler scheduler,
                            boolean interruptOnCancel) {
        if (jdkExecutor == null) {
            if (scheduler != null) {
                scheduler.run();
            }
            throw new NullPointerException("jdkExecutor");
        } else if (scheduler == null) {
            shutdownExecutor(jdkExecutor);
            throw new NullPointerException("scheduler");
        }

        executor = newInternalExecutor(jdkExecutor, interruptOnCancel);
        this.scheduler = scheduler;
    }

    @Override
    public String toString() {
        return DefaultExecutor.class.getSimpleName() + "{executor=" + executor + ", scheduler=" + scheduler + '}';
    }

    @Override
    public Cancellable execute(Runnable task) {
        return executor.apply(task);
    }

    @Override
    public Cancellable schedule(final Runnable task, final long duration, final TimeUnit unit) {
        return scheduler.apply(task, duration, unit);
    }

    @Override
    void doClose() {
        try {
            executor.run();
        } finally {
            scheduler.run();
        }
    }

    @Override
    public void accept(final Runnable runnable) {
        execute(runnable);
    }

    /**
     * {@link Runnable} interface will invoke {@link ExecutorService#shutdown()}.
     */
    private interface InternalExecutor extends Function<Runnable, Cancellable>, Runnable {
    }

    /**
     * {@link Runnable} interface will invoke {@link ScheduledExecutorService#shutdown()}.
     */
    private interface InternalScheduler extends Runnable {
        Cancellable apply(Runnable task, long delay, TimeUnit unit);
    }

    private static void shutdownExecutor(java.util.concurrent.Executor jdkExecutor) {
        if (jdkExecutor instanceof ExecutorService) {
            ((ExecutorService) jdkExecutor).shutdown();
        } else if (jdkExecutor instanceof AutoCloseable) {
            try {
                ((AutoCloseable) jdkExecutor).close();
            } catch (Exception e) {
                throw new RuntimeException("unexpected exception while closing executor: " + jdkExecutor, e);
            }
        }
    }

    private static InternalExecutor newInternalExecutor(java.util.concurrent.Executor jdkExecutor,
                                                        boolean interruptOnCancel) {
        if (jdkExecutor instanceof ExecutorService) {
            return new InternalExecutor() {
                private final ExecutorService service = (ExecutorService) jdkExecutor;

                @Override
                public String toString() {
                    return "InternalExecutor{service=ExecutorService@" +
                            Integer.toHexString(System.identityHashCode(jdkExecutor)) + '}';
                }

                @Override
                public void run() {
                    service.shutdown();
                }

                @Override
                public Cancellable apply(Runnable runnable) {
                    Future<?> future = service.submit(runnable);
                    return () -> future.cancel(interruptOnCancel);
                }
            };
        }
        return new InternalExecutor() {
            @Override
            public String toString() {
                return "InternalExecutor{service=Executor@" +
                        Integer.toHexString(System.identityHashCode(jdkExecutor)) + '}';
            }

            @Override
            public void run() {
                shutdownExecutor(jdkExecutor);
            }

            @Override
            public Cancellable apply(Runnable runnable) {
                jdkExecutor.execute(runnable);
                return IGNORE_CANCEL;
            }
        };
    }

    private static InternalScheduler newScheduler(ScheduledExecutorService service, boolean interruptOnCancel) {
        return new InternalScheduler() {
            @Override
            public String toString() {
                return "InternalScheduler{service=ScheduledExecutorService@" +
                        Integer.toHexString(System.identityHashCode(service)) + '}';
            }

            @Override
            public void run() {
                service.shutdown();
            }

            @Override
            public Cancellable apply(final Runnable task, final long delay, final TimeUnit unit) {
                ScheduledFuture<?> future = service.schedule(task, delay, unit);
                return () -> future.cancel(interruptOnCancel);
            }
        };
    }

    private static final class SingleThreadedScheduler implements InternalScheduler {

        private final java.util.concurrent.Executor offloadExecutor;

        SingleThreadedScheduler(final java.util.concurrent.Executor offloadExecutor) {
            this.offloadExecutor = offloadExecutor;
        }

        @Override
        public String toString() {
            return "SingleThreadedScheduler{offload=Executor@" +
                    Integer.toHexString(System.identityHashCode(offloadExecutor)) + '}';
        }

        @Override
        public void run() {
            // This uses shared scheduled executor service and hence there is no clear lifetime, so, we ignore shutdown.
            // Since GLOBAL_SINGLE_THREADED_SCHEDULED_EXECUTOR uses daemon threads, the threads will be shutdown on JVM
            // shutdown.
        }

        @Override
        public Cancellable apply(final Runnable task, final long delay, final TimeUnit unit) {
            // When using the global scheduler, offload timer ticks to the user specified Executor since user code
            // executed on the timer tick can block.
            ScheduledFuture<?> future = GLOBAL_SINGLE_THREADED_SCHEDULED_EXECUTOR.schedule(
                    () -> offloadExecutor.execute(task), delay, unit);
            // Schedulers are only used to generate a tick and do not execute any user code. This means they will never
            // run any blocking code and hence it does not matter whether we use the interruptOnCancel as sent by the
            // user upon creation in the scheduler. User code (completion of Completable on tick) will be executed on
            // the configured executor and not the Scheduler thread.
            return () -> future.cancel(true);
        }
    }
}
