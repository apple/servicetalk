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
package io.servicetalk.transport.netty.internal;

import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.IoThreadFactory;
import io.servicetalk.transport.api.IoThreadFactory.IoThread;

import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

import static io.servicetalk.transport.netty.internal.NativeTransportUtils.isEpollAvailable;
import static io.servicetalk.transport.netty.internal.NativeTransportUtils.isKQueueAvailable;
import static java.lang.Runtime.getRuntime;
import static java.util.Objects.requireNonNull;

/**
 * A static factory to create or convert to {@link NettyIoExecutor}.
 */
public final class NettyIoExecutors {

    private NettyIoExecutors() {
        // No instances.
    }

    /**
     * Create a new {@link NettyIoExecutor} with the default number of {@code ioThreads}.
     *
     * @return The created {@link IoExecutor}
     */
    public static EventLoopAwareNettyIoExecutor createIoExecutor() {
        return createIoExecutor(newIoThreadFactory());
    }

    /**
     * Create a new {@link NettyIoExecutor} with the default number of {@code ioThreads}.
     *
     * @param threadNamePrefix the name prefix used for the created {@link Thread}s.
     * @return The created {@link IoExecutor}
     */
    public static EventLoopAwareNettyIoExecutor createIoExecutor(String threadNamePrefix) {
        return createIoExecutor(newIoThreadFactory(threadNamePrefix));
    }

    /**
     * Create a new {@link NettyIoExecutor}.
     *
     * @param ioThreads number of threads.
     * @param threadNamePrefix the name prefix used for the created {@link Thread}s.
     * @return The created {@link IoExecutor}
     */
    public static EventLoopAwareNettyIoExecutor createIoExecutor(int ioThreads, String threadNamePrefix) {
        return createIoExecutor(ioThreads, newIoThreadFactory(threadNamePrefix));
    }

    /**
     * Create a new {@link NettyIoExecutor} with the default number of {@code ioThreads}.
     *
     * @param <T> Type of the IO thread instances created by factory.
     * @param threadFactory the {@link IoThreadFactory} to use. If possible you should use an instance of
     * {@link NettyIoThreadFactory} as it allows internal optimizations.
     * @return The created {@link IoExecutor}
     */
    public static <T extends Thread & IoThread> EventLoopAwareNettyIoExecutor createIoExecutor(
            IoThreadFactory<T> threadFactory) {
        return createIoExecutor(getRuntime().availableProcessors() * 2, threadFactory);
    }

    /**
     * Create a new {@link NettyIoExecutor}.
     *
     * @param <T> Type of the IO thread instances created by factory.
     * @param ioThreads number of threads.
     * @param threadFactory the {@link IoThreadFactory} to use. If possible you should use an instance of
     * {@link NettyIoThreadFactory} as it allows internal optimizations.
     * @return The created {@link IoExecutor}
     */
    public static <T extends Thread & IoThread> EventLoopAwareNettyIoExecutor createIoExecutor(
            int ioThreads, IoThreadFactory<T> threadFactory) {
        validateIoThreads(ioThreads);
        return new EventLoopGroupIoExecutor(createEventLoopGroup(ioThreads, threadFactory), true);
    }

    private static <T extends Thread & IoThread> EventLoopGroup createEventLoopGroup(int ioThreads,
            IoThreadFactory<T> threadFactory) {
        validateIoThreads(ioThreads);
        return isEpollAvailable() ? new EpollEventLoopGroup(ioThreads, threadFactory) :
                isKQueueAvailable() ? new KQueueEventLoopGroup(ioThreads, threadFactory) :
                        new NioEventLoopGroup(ioThreads, threadFactory);
    }

    /**
     * Attempts to convert the passed {@link IoExecutor} to a {@link NettyIoExecutor}.
     *
     * @param ioExecutor {@link IoExecutor} to convert.
     * @return {@link NettyIoExecutor} corresponding to the passed {@link IoExecutor}.
     * @throws IllegalArgumentException If {@link IoExecutor} is not of type {@link NettyIoExecutor}.
     */
    public static NettyIoExecutor toNettyIoExecutor(IoExecutor ioExecutor) {
        requireNonNull(ioExecutor);
        if (ioExecutor instanceof NettyIoExecutor) {
            return (NettyIoExecutor) ioExecutor;
        }
        throw new IllegalArgumentException("Incompatible IoExecutor: " + ioExecutor +
                ". Not a netty based IoExecutor.");
    }

    /**
     * Creates a new instance of {@link NettyIoExecutor} using the passed {@link EventLoop}.
     *
     * @param eventLoop {@link EventLoop} to use to create a new {@link NettyIoExecutor}.
     * @return New {@link NettyIoExecutor} using the passed {@link EventLoop}.
     */
    public static NettyIoExecutor fromNettyEventLoop(EventLoop eventLoop) {
        return new EventLoopIoExecutor(eventLoop, true);
    }

    /**
     * Creates a new instance of {@link NettyIoExecutor} using the passed {@link EventLoop}.
     *
     * @param eventLoop {@link EventLoop} to use to create a new {@link NettyIoExecutor}.
     * @param isIoThreadSupported if {@code true} then event loop threads are guaranteed to implement
     * {@link IoThreadFactory.IoThread} contract. Note: passing an incorrect value here may result in unexpected
     * behavior and incorrect offloading.
     * @return New {@link NettyIoExecutor} using the passed {@link EventLoop}.
     */
    public static NettyIoExecutor fromNettyEventLoop(EventLoop eventLoop, boolean isIoThreadSupported) {
        return new EventLoopIoExecutor(eventLoop, true, isIoThreadSupported);
    }

    /**
     * Creates a new instance of {@link NettyIoExecutor} using the passed {@link EventLoopGroup}.
     *
     * @param eventLoopGroup {@link EventLoopGroup} to use to create a new {@link NettyIoExecutor}.
     * @return New {@link NettyIoExecutor} using the passed {@link EventLoopGroup}.
     */
    public static NettyIoExecutor fromNettyEventLoopGroup(EventLoopGroup eventLoopGroup) {
        return new EventLoopGroupIoExecutor(eventLoopGroup, true);
    }

    /**
     * Creates a new instance of {@link NettyIoExecutor} using the passed {@link EventLoopGroup}.
     *
     * @param eventLoopGroup {@link EventLoopGroup} to use to create a new {@link NettyIoExecutor}.
     * @param isIoThreadSupported if {@code true} then event loop threads are guaranteed to implement
     * {@link IoThreadFactory.IoThread} contract. Note: passing an incorrect value here may result in unexpected
     * behavior and incorrect offloading.
     * @return New {@link NettyIoExecutor} using the passed {@link EventLoopGroup}.
     */
    public static NettyIoExecutor fromNettyEventLoopGroup(EventLoopGroup eventLoopGroup, boolean isIoThreadSupported) {
        return new EventLoopGroupIoExecutor(eventLoopGroup, true, isIoThreadSupported);
    }

    private static void validateIoThreads(final int ioThreads) {
        if (ioThreads <= 0) {
            throw new IllegalArgumentException("ioThreads: " + ioThreads + " (expected >0)");
        }
    }

    private static NettyIoThreadFactory newIoThreadFactory() {
        return newIoThreadFactory(NettyIoExecutor.class.getSimpleName());
    }

    private static NettyIoThreadFactory newIoThreadFactory(String prefix) {
        return new NettyIoThreadFactory(prefix);
    }
}
