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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestTimedOutException;

import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import static java.lang.Boolean.parseBoolean;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Standard timeout shared by test classes. The {@link #lookForStuckThread} setting is ignored.
 */
public final class ServiceTalkTestTimeout extends Timeout {
    public static final boolean CI = parseBoolean(System.getenv("CI"));
    public static final int DEFAULT_TIMEOUT_SECONDS = CI ? 90 : 10;
    public static final String THREAD_PREFIX = "Time-limited test";
    private final Runnable onTimeout;

    public ServiceTalkTestTimeout() {
        this(DEFAULT_TIMEOUT_SECONDS, SECONDS);
    }

    public ServiceTalkTestTimeout(long timeout, TimeUnit unit) {
        this(timeout, unit, () -> { });
    }

    public ServiceTalkTestTimeout(long timeout, TimeUnit unit, Runnable onTimeout) {
        super(timeout, unit);
        this.onTimeout = requireNonNull(onTimeout);
    }

    @Override
    public Statement apply(Statement base, Description description) {
        // Check if multiple Timeout are present and annotated with @Rule.
        Class<?> clazz = description.getTestClass();
        List<Class<?>> timeoutRuleClasses = new ArrayList<>(2);
        do {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(Rule.class) && Timeout.class.isAssignableFrom(field.getType())) {
                    timeoutRuleClasses.add(clazz);
                }
            }
        } while ((clazz = clazz.getSuperclass()) != Object.class);
        if (timeoutRuleClasses.size() > 1) {
            StringBuilder sb = new StringBuilder(256)
                    .append("Only one @Rule for a Timeout is allowed, but ")
                    .append(timeoutRuleClasses.size())
                    .append(" were detected in types: ");
            for (Class<?> clazz2 : timeoutRuleClasses) {
                sb.append(clazz2.getName()).append(", ");
            }
            sb.setLength(sb.length() - 2);
            throw new IllegalStateException(sb.toString());
        }
        // If timeout is specified in @Test, let that have precedence over the global timeout.
        Test testAnnotation = description.getAnnotation(Test.class);
        if (testAnnotation != null) {
            long timeout = testAnnotation.timeout();
            if (timeout > 0) {
                return new TimeoutStatement(base, timeout, TimeUnit.MILLISECONDS, onTimeout);
            }
        }
        return new TimeoutStatement(base, getTimeout(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS, onTimeout);
    }

    private static final class TimeoutStatement extends Statement {
        private final Statement original;
        private final long timeout;
        private final TimeUnit timeUnit;
        private final Runnable onTimeout;

        TimeoutStatement(Statement original, long timeout, TimeUnit timeUnit, Runnable onTimeout) {
            this.original = requireNonNull(original);
            this.timeout = timeout;
            this.timeUnit = requireNonNull(timeUnit);
            this.onTimeout = requireNonNull(onTimeout);
        }

        @Override
        public void evaluate() throws Throwable {
            CallableStatement callable = new CallableStatement();
            FutureTask<Throwable> task = new FutureTask<>(callable);
            Thread thread = new Thread(task, THREAD_PREFIX);
            thread.setDaemon(true);
            thread.start();
            callable.awaitStarted();
            Throwable throwable = getResult(task, thread);
            if (throwable != null) {
                throw throwable;
            }
        }

        @Nullable
        private Throwable getResult(FutureTask<Throwable> task, Thread thread) {
            try {
                if (timeout > 0) {
                    return task.get(timeout, timeUnit);
                } else {
                    return task.get();
                }
            } catch (InterruptedException e) {
                return e; // caller will re-throw; no need to call Thread.interrupt()
            } catch (ExecutionException e) {
                // test failed; have caller re-throw the exception thrown by the test
                return e.getCause();
            } catch (TimeoutException e) {
                dumpAllStacks(); // dump all stacks before interrupting any thread
                onTimeout.run();
                return createTimeoutException(thread);
            }
        }

        private Exception createTimeoutException(Thread thread) {
            StackTraceElement[] stackTrace = thread.getStackTrace();
            Exception currThreadException = new TestTimedOutException(timeout, timeUnit);
            if (stackTrace != null) {
                currThreadException.setStackTrace(stackTrace);
                thread.interrupt();
            }
            return currThreadException;
        }

        private final class CallableStatement implements Callable<Throwable> {
            private final CountDownLatch startLatch = new CountDownLatch(1);

            @Override
            @Nullable
            public Throwable call() throws Exception {
                try {
                    startLatch.countDown();
                    original.evaluate();
                } catch (Exception e) {
                    throw e;
                } catch (Throwable e) {
                    return e;
                }
                return null;
            }

            void awaitStarted() throws InterruptedException {
                startLatch.await();
            }
        }

        private static void dumpAllStacks() {
            ThreadMXBean bean = ManagementFactory.getThreadMXBean();
            List<ThreadInfo> threadInfos = Stream.of(bean.getThreadInfo(bean.getAllThreadIds(),
                    bean.isObjectMonitorUsageSupported(), bean.isSynchronizerUsageSupported()))
                    .filter(Objects::nonNull) // filter out dead threads
                    .sorted(Comparator.comparing(ThreadInfo::getThreadName))
                    .collect(Collectors.toList());
            StringBuilder sb = new StringBuilder(threadInfos.size() * 4096);
            for (ThreadInfo info : threadInfos) {
                sb.append('"').append(info.getThreadName()).append('"');
                sb.append(" #").append(info.getThreadId());
                sb.append(" ").append(info.getThreadState().toString().toLowerCase());
                if (info.getLockName() != null) {
                    sb.append(" on ").append(info.getLockName());
                }
                if (info.getLockOwnerName() != null) {
                    sb.append(" owned by \"").append(info.getLockOwnerName()).append("\" #")
                            .append(info.getLockOwnerId());
                }
                if (info.isSuspended()) {
                    sb.append(" (suspended)");
                }
                if (info.isInNative()) {
                    sb.append(" (in native)");
                }
                sb.append("\n");

                sb.append("  java.lang.Thread.State: ").append(info.getThreadState()).append("\n");
                StackTraceElement[] stackTrace = info.getStackTrace();
                for (int i = 0; i < stackTrace.length; ++i) {
                    sb.append("\t  at ").append(stackTrace[i]).append("\n");
                    for (MonitorInfo mi : info.getLockedMonitors()) {
                        if (mi.getLockedStackDepth() == i) {
                            sb.append("\t  - locked ").append(mi).append("\n");
                        }
                    }
                }
                sb.append("\n");

                LockInfo[] locks = info.getLockedSynchronizers();
                if (locks.length > 0) {
                    sb.append("\t  Number of locked synchronizers = ").append(locks.length).append("\n");
                    for (LockInfo li : locks) {
                        sb.append("\t  - ").append(li).append("\n");
                    }
                    sb.append("\n");
                }
            }
            System.out.println(sb.toString());
        }
    }
}
