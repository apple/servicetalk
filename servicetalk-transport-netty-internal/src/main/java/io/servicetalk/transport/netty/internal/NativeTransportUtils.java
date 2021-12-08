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

import io.servicetalk.transport.api.DomainSocketAddress;
import io.servicetalk.transport.api.FileDescriptorSocketAddress;

import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.util.internal.PlatformDependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.netty.util.internal.PlatformDependent.normalizedArch;
import static java.lang.Boolean.getBoolean;

/**
 * Utility to check availability of Netty <a href="https://netty.io/wiki/native-transports.html">native transports</a>.
 * <p>
 * It also prevents the load of classes and libraries when OS does not support it, and logs when OS supports but
 * libraries are not available.
 */
final class NativeTransportUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(NativeTransportUtils.class);
    private static final String NETTY_NO_NATIVE_NAME = "io.netty.transport.noNative";
    private static final boolean NETTY_NO_NATIVE = getBoolean(NETTY_NO_NATIVE_NAME);

    private static final boolean IS_LINUX;
    private static final boolean IS_OSX_OR_BSD;

    static {
        final String os = PlatformDependent.normalizedOs();
        IS_LINUX = "linux".equals(os);
        IS_OSX_OR_BSD = "osx".equals(os) || os.contains("bsd");

        if (IS_LINUX && !Epoll.isAvailable()) {
            logUnavailability("epoll", os, Epoll.unavailabilityCause());
        } else if (IS_OSX_OR_BSD && !KQueue.isAvailable()) {
            logUnavailability("kqueue", "osx", KQueue.unavailabilityCause());
        }
    }

    private NativeTransportUtils() {
        // No instances
    }

    private static void logUnavailability(final String transport, final String os, final Throwable cause) {
        if (NETTY_NO_NATIVE) {
            LOGGER.info("io.netty:netty-transport-native-{} is explicitly disabled with \"-D{}=true\". Note that it " +
                    "may impact responsiveness, reliability, and performance of the application. For more information" +
                    ", see https://netty.io/wiki/native-transports.html", transport, NETTY_NO_NATIVE_NAME);
            return;
        }
        LOGGER.warn("Can not load \"io.netty:netty-transport-native-{}:$nettyVersion:{}-{}\", it may impact " +
                        "responsiveness, reliability, and performance of the application. In future releases " +
                        "ServiceTalk will fail to start without transport-native library unless \"-D{}=true\" system " +
                        "property is explicitly set. For more information, see " +
                        "https://netty.io/wiki/native-transports.html",
                transport, os, normalizedArch(), NETTY_NO_NATIVE_NAME, cause);
        // FIXME: 0.42 - throw an exception and adjust the message
    }

    /**
     * Determine if {@link Epoll} is available.
     *
     * @return {@code true} if {@link Epoll} is available
     */
    static boolean isEpollAvailable() {
        return IS_LINUX && Epoll.isAvailable();
    }

    /**
     * Determine if {@link KQueue} is available.
     *
     * @return {@code true} if {@link KQueue} is available
     */
    static boolean isKQueueAvailable() {
        return IS_OSX_OR_BSD && KQueue.isAvailable();
    }

    /**
     * Returns {@code true} if native {@link Epoll} transport could be used.
     *
     * @param group the used {@link EventLoopGroup}
     * @return {@code true} if native {@link Epoll} transport could be used
     */
    static boolean useEpoll(final EventLoopGroup group) {
        if (!isEpollAvailable()) {
            return false;
        }
        // Check if we should use the epoll transport. This is true if either the EpollEventLoopGroup is used directly
        // or if the passed group is a EventLoop and it's parent is an EpollEventLoopGroup.
        return group instanceof EpollEventLoopGroup || (group instanceof EventLoop &&
                ((EventLoop) group).parent() instanceof EpollEventLoopGroup);
    }

    /**
     * Returns {@code true} if native {@link KQueue} transport could be used.
     *
     * @param group the used {@link EventLoopGroup}
     * @return {@code true} if native {@link KQueue} transport could be used
     */
    static boolean useKQueue(final EventLoopGroup group) {
        if (!isKQueueAvailable()) {
            return false;
        }
        // Check if we should use the kqueue transport. This is true if either the KQueueEventLoopGroup is used directly
        // or if the passed group is a EventLoop and it's parent is an KQueueEventLoopGroup.
        return group instanceof KQueueEventLoopGroup || (group instanceof EventLoop &&
                ((EventLoop) group).parent() instanceof KQueueEventLoopGroup);
    }

    /**
     * Determine if {@link DomainSocketAddress} is supported.
     *
     * @param group the group to test.
     * @return {@code true} if {@link DomainSocketAddress} is supported by {@code group}
     */
    static boolean isUnixDomainSocketSupported(final EventLoopGroup group) {
        return useEpoll(group) || useKQueue(group);
    }

    /**
     * Determine if {@link FileDescriptorSocketAddress} is supported.
     *
     * @param group the group to test.
     * @return {@code true} if {@link FileDescriptorSocketAddress} is supported by {@code group}
     */
    static boolean isFileDescriptorSocketAddressSupported(final EventLoopGroup group) {
        return useEpoll(group) || useKQueue(group);
    }
}
