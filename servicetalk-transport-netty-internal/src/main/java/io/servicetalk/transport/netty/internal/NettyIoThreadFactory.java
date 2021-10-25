/*
 * Copyright © 2018-2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.AsyncContextMap;
import io.servicetalk.concurrent.api.AsyncContextMapHolder;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.context.api.ContextMapHolder;
import io.servicetalk.transport.api.IoThreadFactory;

import io.netty.util.concurrent.FastThreadLocalThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import static java.lang.Thread.NORM_PRIORITY;
import static java.util.Objects.requireNonNull;

/**
 * Default {@link IoThreadFactory} to create IO {@link NettyIoThread}s.
 */
public final class NettyIoThreadFactory implements IoThreadFactory<NettyIoThreadFactory.NettyIoThread> {
    private static final AtomicInteger factoryCount = new AtomicInteger();

    private final AtomicInteger threadCount = new AtomicInteger();
    private final String namePrefix;
    private final boolean daemon;
    @Nullable
    private final ThreadGroup threadGroup;

    /**
     * Create a new instance.
     * @param threadNamePrefix the name prefix used for the created {@link Thread}s.
     */
    public NettyIoThreadFactory(String threadNamePrefix) {
        this(threadNamePrefix, true);
    }

    /**
     * Create a new instance.
     * @param threadNamePrefix the name prefix used for the created {@link Thread}s.
     * @param daemon {@code true} if the created {@link Thread} should be a daemon thread.
     */
    @SuppressWarnings("PMD.AvoidThreadGroup")
    public NettyIoThreadFactory(String threadNamePrefix, boolean daemon) {
        this(threadNamePrefix, daemon,
                System.getSecurityManager() == null ?
                        Thread.currentThread().getThreadGroup() : System.getSecurityManager().getThreadGroup()
        );
    }

    /**
     * Create a new instance.
     * @param threadNamePrefix the name prefix used for the created {@link IoThread}s.
     * @param daemon {@code true} if the created {@link Thread} should be a daemon thread.
     * @param threadGroup the {@link ThreadGroup} to which all created threads will belong, or null for default group
     */
    @SuppressWarnings("PMD.AvoidThreadGroup")
    NettyIoThreadFactory(String threadNamePrefix, boolean daemon, @Nullable ThreadGroup threadGroup) {
        this.namePrefix = requireNonNull(threadNamePrefix) + '-' + factoryCount.incrementAndGet() + '-';
        this.daemon = daemon;
        this.threadGroup = threadGroup;
    }

    @Override
    public NettyIoThread newThread(Runnable r) {
        NettyIoThread t = new NettyIoThread(threadGroup, r, namePrefix + threadCount.incrementAndGet());
        if (t.isDaemon() != daemon) {
            t.setDaemon(daemon);
        }
        if (t.getPriority() != NORM_PRIORITY) {
            t.setPriority(NORM_PRIORITY);
        }
        return t;
    }

    @Override
    public String toString() {
        return NettyIoThreadFactory.class.getSimpleName() +
                "{namePrefix='" + namePrefix + '\'' +
                ", daemon=" + daemon +
                ", threadGroup=" + threadGroup +
                ", threadCount=" + threadCount +
                '}';
    }

    static final class NettyIoThread extends FastThreadLocalThread implements IoThreadFactory.IoThread,
                                                                              ContextMapHolder {

        private static final Logger LOGGER = LoggerFactory.getLogger(NettyIoThread.class);
        private static final AtomicBoolean ASYNC_CONTEXT_MAP_DETECTED = new AtomicBoolean();

        @Nullable
        private ContextMap context;
        @Nullable
        private AsyncContextMap asyncContextMap;

        NettyIoThread(@Nullable ThreadGroup group, Runnable target, String name) {
            super(group, target, name);
        }

        @Nullable
        @Override
        public ContextMap context() {
            return context;
        }

        @Override
        public NettyIoThread context(@Nullable final ContextMap context) {
            this.context = context;
            return this;
        }

        @Override
        public void asyncContextMap(@Nullable final AsyncContextMap asyncContextMap) {
            logWarning();
            this.asyncContextMap = asyncContextMap;
        }

        @Nullable
        @Override
        public AsyncContextMap asyncContextMap() {
            logWarning();
            return asyncContextMap;
        }

        private static void logWarning() {
            if (ASYNC_CONTEXT_MAP_DETECTED.compareAndSet(false, true)) {
                LOGGER.warn("Detected usage of deprecated {}, migrate your code to {}",
                        AsyncContextMapHolder.class.getCanonicalName(), ContextMapHolder.class.getCanonicalName(),
                        new RuntimeException("Stack trace where AsyncContextMapHolder was used"));
            }
        }
    }
}
