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
package io.servicetalk.concurrent.api;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import static java.lang.Thread.NORM_PRIORITY;
import static java.util.Objects.requireNonNull;

/**
 * A {@link ThreadFactory} implementation.
 */
public final class DefaultThreadFactory implements ThreadFactory {

    private static final AtomicInteger factoryCount = new AtomicInteger();
    /**
     * The default prefix used for new thread names.
     */
    public static final String DEFAULT_NAME_PREFIX = "servicetalk-executor";

    private final String namePrefix;
    private final boolean daemon;
    private final int priority;
    @SuppressWarnings("unused")
    private final AtomicInteger threadCount = new AtomicInteger();

    /**
     * New instance that creates daemon threads with {@link Thread#NORM_PRIORITY} priority.
     */
    public DefaultThreadFactory() {
        this(true);
    }

    /**
     * New instance that creates threads with {@link Thread#NORM_PRIORITY} priority.
     *
     * @param daemon {@code true} if the created threads should be daemons.
     */
    public DefaultThreadFactory(boolean daemon) {
        this(daemon, NORM_PRIORITY);
    }

    /**
     * New instance that creates daemon threads.
     *
     * @param priority for the created threads.
     */
    public DefaultThreadFactory(int priority) {
        this(true, priority);
    }

    /**
     * Create a new instance.
     *
     * @param namePrefix for all created threads.
     */
    public DefaultThreadFactory(String namePrefix) {
        this(namePrefix, true, NORM_PRIORITY);
    }

    /**
     * New instance.
     *
     * @param daemon {@code true} if the created threads should be daemons.
     * @param priority for the created threads.
     */
    public DefaultThreadFactory(boolean daemon, int priority) {
        this(DEFAULT_NAME_PREFIX, daemon, priority);
    }

    /**
     * New instance.
     *
     * @param namePrefix for all created threads.
     * @param daemon {@code true} if the created threads should be daemons.
     * @param priority for the created threads.
     */
    public DefaultThreadFactory(String namePrefix, boolean daemon, int priority) {
        this.namePrefix = requireNonNull(namePrefix) + '-' + factoryCount.incrementAndGet() + '-';
        this.daemon = daemon;
        this.priority = priority;
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new AsyncContextHolderThread(r, namePrefix + threadCount.incrementAndGet());
        if (t.isDaemon() != daemon) {
            t.setDaemon(daemon);
        }
        if (t.getPriority() != priority) {
            t.setPriority(priority);
        }
        return t;
    }

    private static final class AsyncContextHolderThread extends Thread implements AsyncContextMapHolder {
        @Nullable
        private AsyncContextMap asyncContextMap;

        AsyncContextHolderThread(Runnable target, String name) {
            super(target, name);
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
