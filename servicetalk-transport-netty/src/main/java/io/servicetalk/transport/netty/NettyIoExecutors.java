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
package io.servicetalk.transport.netty;

import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.IoThreadFactory;
import io.servicetalk.transport.netty.internal.NettyIoExecutor;
import io.servicetalk.transport.netty.internal.NettyIoThreadFactory;

import java.util.concurrent.ThreadFactory;

/**
 * Factory methods to create {@link IoExecutor}s using netty as the transport.
 */
public final class NettyIoExecutors {

    private NettyIoExecutors() {
        // no instances
    }

    /**
     * Creates a new {@link IoExecutor} with the specified number of {@code ioThreads}.
     *
     * @param ioThreads number of threads.
     * @param threadFactory the {@link ThreadFactory} to use. If possible you should use an instance
     * of {@link NettyIoThreadFactory} as it allows internal optimizations.
     * @return The created {@link IoExecutor}
     * @deprecated Future versions of ServiceTalk will require a {@link IoThreadFactory} for creating
     * {@link IoExecutor} threads.
     */
    @Deprecated
    public static IoExecutor createIoExecutor(int ioThreads, ThreadFactory threadFactory) {
        return io.servicetalk.transport.netty.internal.NettyIoExecutors.createIoExecutor(ioThreads, threadFactory);
    }

    /**
     * Creates a new {@link IoExecutor} with the specified number of {@code ioThreads}.
     *
     * @param <T> Type of the IO thread instances created by factory.
     * @param ioThreads number of threads.
     * @param threadFactory the {@link ThreadFactory} to use. If possible you should use an instance
     * of {@link NettyIoThreadFactory} as it allows internal optimizations.
     * @return The created {@link IoExecutor}
     */
    public static <T extends Thread & IoThreadFactory.IoThread> IoExecutor createIoExecutor(int ioThreads,
            IoThreadFactory<T> threadFactory) {
        return io.servicetalk.transport.netty.internal.NettyIoExecutors.createIoExecutor(ioThreads, threadFactory);
    }

    /**
     * Creates a new {@link IoExecutor} with the specified number of {@code ioThreads}.
     *
     * @param ioThreads number of threads.
     * @return The created {@link IoExecutor}
     */
    public static IoExecutor createIoExecutor(int ioThreads) {
        return createIoExecutor(ioThreads, newIoThreadFactory());
    }

    /**
     * Creates a new {@link IoExecutor} with the default number of {@code ioThreads}.
     *
     * @param <T> Type of the IO thread instances created by factory.
     * @param threadFactory the {@link ThreadFactory} to use. If possible you should use an instance
     * of {@link NettyIoThreadFactory} as it allows internal optimizations.
     * @return The created {@link IoExecutor}
     */
    public static <T extends Thread & IoThreadFactory.IoThread> IoExecutor createIoExecutor(
            IoThreadFactory<T> threadFactory) {
        return io.servicetalk.transport.netty.internal.NettyIoExecutors.createIoExecutor(threadFactory);
    }

    /**
     * Creates a new {@link IoExecutor} with the default number of {@code ioThreads}.
     *
     * @param threadFactory the {@link ThreadFactory} to use. If possible you should use an instance
     * of {@link NettyIoThreadFactory} as it allows internal optimizations.
     * @return The created {@link IoExecutor}
     * @deprecated Future versions of ServiceTalk will require a {@link IoThreadFactory} for creating
     * {@link IoExecutor} threads.
     */
    @Deprecated
    public static IoExecutor createIoExecutor(ThreadFactory threadFactory) {
        return io.servicetalk.transport.netty.internal.NettyIoExecutors.createIoExecutor(threadFactory);
    }

    /**
     * Creates a new {@link IoExecutor} with the default number of {@code ioThreads}.
     *
     * @return The created {@link IoExecutor}
     */
    public static IoExecutor createIoExecutor() {
        return createIoExecutor(newIoThreadFactory());
    }

    private static NettyIoThreadFactory newIoThreadFactory() {
        return new NettyIoThreadFactory(NettyIoExecutor.class.getSimpleName());
    }
}
