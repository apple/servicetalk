/**
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

import io.netty.channel.EventLoop;
import io.netty.util.internal.StringUtil;
import io.servicetalk.transport.api.IoExecutor;

/**
 * {@link IoExecutor} implementation which delegates to an {@link EventLoop}.
 */
public final class NettyIoExecutor extends AbstractNettyIoExecutor<EventLoop> {
    /**
     * Create a new instance.
     * @param loop the {@link EventLoop} to delegate to.
     */
    public NettyIoExecutor(EventLoop loop) {
        super(loop);
    }

    /**
     * Get the {@link EventLoop} for an {@link IoExecutor}.
     * @param executor the executor.
     * @return the {@link EventLoop}.
     */
    public static EventLoop toEventLoop(IoExecutor executor) {
        try {
            return ((NettyIoExecutor) executor).executor;
        } catch (ClassCastException cause) {
            throw new IllegalArgumentException("unsupported executor type: " + StringUtil.simpleClassName(executor) +
                    " (expected: derived from " + StringUtil.simpleClassName(EventLoop.class), cause);
        }
    }
}
