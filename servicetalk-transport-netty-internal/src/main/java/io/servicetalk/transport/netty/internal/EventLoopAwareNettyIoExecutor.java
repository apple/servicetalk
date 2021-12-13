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
package io.servicetalk.transport.netty.internal;

import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;

/**
 * A special {@link NettyIoExecutor} that exposes the underlying netty {@link EventLoopGroup}.
 */
public interface EventLoopAwareNettyIoExecutor extends NettyIoExecutor {

    /**
     * Checks if the calling thread is an I/O thread managed by this {@link NettyIoExecutor}.
     *
     * @return {@code true} if the calling thread is an I/O thread managed by this {@link NettyIoExecutor}.
     */
    boolean isCurrentThreadEventLoop();

    /**
     * Returns the underlying {@link EventLoopGroup}.
     *
     * @return {@link EventLoopGroup} used by this {@link EventLoopAwareNettyIoExecutor}.
     */
    EventLoopGroup eventLoopGroup();

    /**
     * Returns a {@link EventLoopAwareNettyIoExecutor} that is tied to a single {@link EventLoop} and not a
     * {@link EventLoopGroup}.
     *
     * @return {@link EventLoopAwareNettyIoExecutor} that is tied to a single {@link EventLoop} and not a
     * {@link EventLoopGroup}.
     */
    EventLoopAwareNettyIoExecutor next();
}
