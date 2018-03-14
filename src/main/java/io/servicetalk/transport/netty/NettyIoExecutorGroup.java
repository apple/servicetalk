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

import io.netty.channel.EventLoopGroup;
import io.netty.util.internal.StringUtil;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.IoExecutorGroup;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * A {@link IoExecutorGroup} that wraps a {@link EventLoopGroup}.
 */
public final class NettyIoExecutorGroup implements IoExecutorGroup {
    final EventLoopGroup group;

    /**
     * Creates a new instance.
     *
     * @param group the {@link EventLoopGroup} to delegate to.
     */
    public NettyIoExecutorGroup(EventLoopGroup group) {
        this.group = Objects.requireNonNull(group);
    }

    @Override
    public Completable closeAsync(long quietPeriod, long timeout, TimeUnit unit) {
        return new NettyFutureCompletable(() -> group.shutdownGracefully(quietPeriod, timeout, unit));
    }

    @Override
    public Completable onClose() {
        return new NettyFutureCompletable(group::terminationFuture);
    }

    @Override
    public IoExecutor next() {
        return new NettyIoExecutor(group.next());
    }

    @Override
    public boolean isUnixDomainSocketSupported() {
        return NativeTransportUtil.isUnixDomainSocketSupported(group);
    }

    @Override
    public boolean isFileDescriptorSocketAddressSupported() {
        return NativeTransportUtil.isFileDescriptorSocketAddressSupported(group);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof NettyIoExecutorGroup) {
            return group.equals(((NettyIoExecutorGroup) o).group);
        }
        if (o instanceof AbstractNettyIoExecutor) {
            return group.equals(((AbstractNettyIoExecutor) o).executor);
        }
        return group.equals(o);
    }

    @Override
    public int hashCode() {
        return group.hashCode();
    }

    /**
     * Get the {@link EventLoopGroup} for an {@link IoExecutorGroup}.
     *
     * @param group the group.
     * @return the {@link EventLoopGroup}.
     */
    public static EventLoopGroup toGroup(IoExecutorGroup group) {
        try {
            return group instanceof NettyIoExecutorGroup ? ((NettyIoExecutorGroup) group).group : ((NettyIoExecutor) group).executor;
        } catch (Throwable cause) {
            throw new IllegalArgumentException("unsupported executor type: " + StringUtil.simpleClassName(group) +
                    " (expected: wrapper of " + StringUtil.simpleClassName(EventLoopGroup.class), cause);
        }
    }
}
