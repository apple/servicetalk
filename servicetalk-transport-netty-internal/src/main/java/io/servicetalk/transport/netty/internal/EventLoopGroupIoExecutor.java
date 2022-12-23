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

import io.netty.channel.EventLoopGroup;

final class EventLoopGroupIoExecutor extends AbstractNettyIoExecutor<EventLoopGroup>
        implements EventLoopAwareNettyIoExecutor {

    EventLoopGroupIoExecutor(EventLoopGroup eventLoopGroup, boolean interruptOnCancel, boolean isIoThreadSupported) {
        super(eventLoopGroup, interruptOnCancel, isIoThreadSupported);
    }

    @Override
    public boolean isCurrentThreadEventLoop() {
        return false; // We are in the group not a specific eventloop.
    }

    @Override
    public EventLoopGroup eventLoopGroup() {
        return eventLoop;
    }

    @Override
    public EventLoopAwareNettyIoExecutor next() {
        return new EventLoopIoExecutor(eventLoop.next(), interruptOnCancel, isIoThreadSupported);
    }
}
