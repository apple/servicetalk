/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.transport.netty.internal.IoThreadFactory.NettyIoThread;

import io.netty.util.concurrent.FastThreadLocalThread;

import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import static java.lang.Thread.NORM_PRIORITY;
import static java.util.Objects.requireNonNull;

/**
 * Default {@link io.servicetalk.transport.api.IoThreadFactory} to create IO {@link IoThread}s.
 *
 * @deprecated Use {@link NettyIoThreadFactory}.
 * @see NettyIoThreadFactory
 */
@Deprecated
public class IoThreadFactory implements java.util.concurrent.ThreadFactory,
                                        io.servicetalk.transport.api.IoThreadFactory<NettyIoThread> {
    private static final AtomicInteger factoryCount = new AtomicInteger();
    private final AtomicInteger threadCount = new AtomicInteger();
    private final String namePrefix;
    private final boolean daemon;
    private final int priority;
    private final ThreadGroup threadGroup;

    /**
     * Create a new instance.
     * @param threadNamePrefix the name prefix used for the created {@link Thread}s.
     * @deprecated Consider using {@code io.servicetalk.transport.netty.NettyIoExecutors#createIoExecutor(String)}.
     */
    @Deprecated
    public IoThreadFactory(String threadNamePrefix) {
        this(threadNamePrefix, true);
    }

    /**
     * Create a new instance.
     * @param threadNamePrefix the name prefix used for the created {@link Thread}s.
     * @param daemon {@code true} if the created {@link Thread} should be a daemon thread.
     */
    @SuppressWarnings("PMD.AvoidThreadGroup")
    public IoThreadFactory(String threadNamePrefix, boolean daemon) {
        this.namePrefix = requireNonNull(threadNamePrefix) + '-' + factoryCount.incrementAndGet() + '-';
        this.daemon = daemon;
        this.threadGroup = System.getSecurityManager() == null ?
                Thread.currentThread().getThreadGroup() : System.getSecurityManager().getThreadGroup();
        this.priority = NORM_PRIORITY;
    }

    @Override
    public NettyIoThread newThread(Runnable r) {
        NettyIoThread t = new NettyIoThread(threadGroup, r, namePrefix + threadCount.incrementAndGet());
        if (t.isDaemon() != daemon) {
            t.setDaemon(daemon);
        }
        if (t.getPriority() != priority) {
            t.setPriority(priority);
        }
        return t;
    }

    public static final class NettyIoThread extends FastThreadLocalThread
            implements io.servicetalk.transport.api.IoThreadFactory.IoThread {

        @Nullable
        private AsyncContextMap asyncContextMap;

        NettyIoThread(ThreadGroup group, Runnable target, String name) {
            super(group, target, name);
        }

        @Override
        public void asyncContextMap(@Nullable final AsyncContextMap asyncContextMap) {
            this.asyncContextMap = asyncContextMap;
        }

        @Nullable
        @Override
        public AsyncContextMap asyncContextMap() {
            return asyncContextMap;
        }
    }
}
