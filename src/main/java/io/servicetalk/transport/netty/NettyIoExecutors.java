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
import io.servicetalk.transport.api.IoExecutorGroup;

import java.util.concurrent.ThreadFactory;

import static java.lang.Runtime.getRuntime;

/**
 * Factory methods to create {@link IoExecutorGroup}s using netty as the transport.
 */
public final class NettyIoExecutors {

    private NettyIoExecutors() {
        // no instances
    }

    /**
     * Creates a new {@link IoExecutorGroup} with the specified number of {@code ioThreads}.
     *
     * @param ioThreads number of threads
     * @param threadFactory the {@link ThreadFactory} to use. If possible you should use an instance of {@link IoThreadFactory} as
     *                      allows internal optimizations.
     * @return group the created {@link IoExecutorGroup}
     *
     * @deprecated Use {@link #createExecutor(int, ThreadFactory)}
     */
    @Deprecated
    public static IoExecutorGroup createGroup(int ioThreads, ThreadFactory threadFactory) {
        return createExecutor(ioThreads, threadFactory);
    }

    /**
     * Creates a new {@link IoExecutorGroup} with the specified number of {@code ioThreads}.
     *
     * @param ioThreads number of threads
     * @return group the created {@link IoExecutorGroup}
     *
     * @deprecated Use {@link #createExecutor(int)}
     */
    @Deprecated
    public static IoExecutorGroup createGroup(int ioThreads) {
        return createExecutor(ioThreads);
    }

    /**
     * Creates a new {@link IoExecutorGroup} with the default number of {@code ioThreads}
     * ({@code Runtime.getRuntime().availableProcessors() * 2}).
     *
     * @return group the created {@link IoExecutorGroup}
     *
     * @deprecated Use {@link #createExecutor()}
     */
    @Deprecated
    public static IoExecutorGroup createGroup() {
        return createExecutor();
    }

    /**
     * Creates a new {@link IoExecutor} with the specified number of {@code ioThreads}.
     *
     * @param ioThreads number of threads
     * @param threadFactory the {@link ThreadFactory} to use. If possible you should use an instance of {@link IoThreadFactory} as
     *                      it allows internal optimizations.
     * @return The created {@link IoExecutor}
     */
    public static IoExecutor createExecutor(int ioThreads, ThreadFactory threadFactory) {
        return io.servicetalk.transport.netty.internal.NettyIoExecutors.createExecutor(ioThreads, threadFactory);
    }

    /**
     * Creates a new {@link IoExecutor} with the specified number of {@code ioThreads}.
     *
     * @param ioThreads number of threads
     * @return The created {@link IoExecutor}
     */
    public static IoExecutor createExecutor(int ioThreads) {
        return createExecutor(ioThreads, new IoThreadFactory(NettyIoExecutor.class.getSimpleName()));
    }

    /**
     * Creates a new {@link IoExecutor} with the default number of {@code ioThreads}
     * ({@code Runtime.getRuntime().availableProcessors() * 2}).
     *
     * @return The created {@link IoExecutor}
     */
    public static IoExecutor createExecutor() {
        return createExecutor(getRuntime().availableProcessors() * 2);
    }
}
