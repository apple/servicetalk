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

import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.util.concurrent.EventExecutorGroup;

final class NativeTransportUtil {

    private NativeTransportUtil() {
        // no instances
    }

    /**
     * Determine if the {@code group} supports UDS.
     * @param group the group to test.
     * @return {@code true} if UDS are supported by {@code group}.
     */
    static boolean isUnixDomainSocketSupported(EventExecutorGroup group) {
        return group instanceof EpollEventLoopGroup || group instanceof KQueueEventLoopGroup;
    }

    /**
     * Determine if {@code FileDescriptorSocketAddress} is supported.
     * @param group the group to test.
     * @return {@code true} if {@code FileDescriptorSocketAddress} are supported by {@code group}.
     */
    static boolean isFileDescriptorSocketAddressSupported(EventExecutorGroup group) {
        return group instanceof EpollEventLoopGroup || group instanceof KQueueEventLoopGroup;
    }
}
