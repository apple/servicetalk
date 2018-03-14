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

import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.servicetalk.transport.api.IoExecutorGroup;

import java.util.concurrent.ThreadFactory;

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
     */
    public static IoExecutorGroup createGroup(int ioThreads, ThreadFactory threadFactory) {
        return new NettyIoExecutorGroup(Epoll.isAvailable() ? new EpollEventLoopGroup(ioThreads, threadFactory) :
                KQueue.isAvailable() ? new KQueueEventLoopGroup(ioThreads, threadFactory) :
                        new NioEventLoopGroup(ioThreads, threadFactory));
    }

    /**
     * Creates a new {@link IoExecutorGroup} with the specified number of {@code ioThreads}.
     *
     * @param ioThreads number of threads
     * @return group the created {@link IoExecutorGroup}
     */
    public static IoExecutorGroup createGroup(int ioThreads) {
        return createGroup(ioThreads, new IoThreadFactory(NettyIoExecutorGroup.class.getSimpleName()));
    }

    /**
     * Creates a new {@link IoExecutorGroup} with the default number of {@code ioThreads}
     * ({@code Runtime.getRuntime().availableProcessors() * 2}).
     *
     * @return group the created {@link IoExecutorGroup}
     */
    public static IoExecutorGroup createGroup() {
        return createGroup(Runtime.getRuntime().availableProcessors() * 2);
    }
}
