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

final class EventLoopIoExecutor extends AbstractNettyIoExecutor<EventLoop> implements EventLoopAwareNettyIoExecutor {

    EventLoopIoExecutor(EventLoop eventLoop, boolean interruptOnCancel) {
        super(eventLoop, interruptOnCancel);
    }

    EventLoopIoExecutor(EventLoop eventLoop, boolean interruptOnCancel, boolean isIoThreadSupported) {
        super(eventLoop, interruptOnCancel, isIoThreadSupported);
    }

    @Override
    public boolean isCurrentThreadEventLoop() {
        return eventLoop.inEventLoop();
    }

    @Override
    public EventLoopGroup eventLoopGroup() {
        return eventLoop;
    }

    @Override
    public EventLoopAwareNettyIoExecutor next() {
        return this;
    }
}
