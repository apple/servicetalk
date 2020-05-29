/*
 * Copyright © 2018, 2020 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.transport.api.FileDescriptorSocketAddress;
import io.servicetalk.transport.api.HostAndPort;

import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollDomainSocketChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerDomainSocketChannel;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueueDatagramChannel;
import io.netty.channel.kqueue.KQueueDomainSocketChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueServerDomainSocketChannel;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.util.NetUtil;

import java.io.Closeable;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

import static java.net.InetSocketAddress.createUnresolved;

/**
 * Utilities which are used for builders.
 */
public final class BuilderUtils {
    private BuilderUtils() {
        // Utility methods only
    }

    /**
     * Returns {@code true} if native epoll transport should be used.
     *
     * @param group the used {@link EventLoopGroup}
     * @return {@code true} if native transport should be used
     */
    public static boolean useEpoll(EventLoopGroup group) {
        // Check if we should use the epoll transport. This is true if either the EpollEventLoopGroup is used directly
        // or if the passed group is a EventLoop and it's parent is an EpollEventLoopGroup.
        return group instanceof EpollEventLoopGroup || (group instanceof EventLoop &&
                ((EventLoop) group).parent() instanceof EpollEventLoopGroup);
    }

    /**
     * Returns {@code true} if native kqueue transport should be used.
     *
     * @param group the used {@link EventLoopGroup}
     * @return {@code true} if native transport should be used
     */
    public static boolean useKQueue(EventLoopGroup group) {
        // Check if we should use the kqueue transport. This is true if either the KQueueEventLoopGroup is used directly
        // or if the passed group is a EventLoop and it's parent is an KQueueEventLoopGroup.
        return group instanceof KQueueEventLoopGroup || (group instanceof EventLoop &&
                ((EventLoop) group).parent() instanceof KQueueEventLoopGroup);
    }

    /**
     * Returns the correct {@link Class} to use with the given {@link EventLoopGroup}.
     *
     * @param group        the {@link EventLoopGroup} for which the class is needed
     * @param addressClass The class of the address that the server socket will be bound to.
     * @return the class that should be used for bootstrapping
     */
    public static Class<? extends ServerChannel> serverChannel(EventLoopGroup group,
                                                               Class<? extends SocketAddress> addressClass) {
        if (useEpoll(group)) {
            return DomainSocketAddress.class.isAssignableFrom(addressClass) ? EpollServerDomainSocketChannel.class :
                    EpollServerSocketChannel.class;
        } else if (useKQueue(group)) {
            return DomainSocketAddress.class.isAssignableFrom(addressClass) ? KQueueServerDomainSocketChannel.class :
                    KQueueServerSocketChannel.class;
        } else {
            return NioServerSocketChannel.class;
        }
    }

    /**
     * Returns the correct {@link Class} to use with the given {@link EventLoopGroup}.
     *
     * @param group        the {@link EventLoopGroup} for which the class is needed
     * @param addressClass The class of the address that to connect to.
     * @return the class that should be used for bootstrapping
     */
    public static Class<? extends Channel> socketChannel(EventLoopGroup group,
                                                         Class<? extends SocketAddress> addressClass) {
        if (useEpoll(group)) {
            return DomainSocketAddress.class.isAssignableFrom(addressClass) ? EpollDomainSocketChannel.class :
                    EpollSocketChannel.class;
        } else if (useKQueue(group)) {
            return DomainSocketAddress.class.isAssignableFrom(addressClass) ? KQueueDomainSocketChannel.class :
                    KQueueSocketChannel.class;
        } else {
            return NioSocketChannel.class;
        }
    }

    /**
     * Returns the correct Channel that wraps the given filedescriptor or {@code null} if not supported.
     *
     * @param group        the {@link EventLoopGroup} for which the class is needed
     * @param address      the filedescriptor to wrap.
     * @return the class that should be used for bootstrapping
     */
    @Nullable
    public static Channel socketChannel(EventLoopGroup group, FileDescriptorSocketAddress address) {
        if (useEpoll(group)) {
            return new EpollSocketChannel(address.getValue());
        }
        if (useKQueue(group)) {
            return new KQueueSocketChannel(address.getValue());
        }
        return null;
    }

    /**
     * If {@code address} if a ServiceTalk specific address it is unwrapped into a Netty address.
     *
     * @param address the address to convert.
     * @return an address that Netty understands.
     */
    public static SocketAddress toNettyAddress(Object address) {
        // The order of the instance of checks is important because `DomainSocketAddress` is also of type
        // `SocketAddress`, and we want to identify the more specific types before returning the fallback
        // `SocketAddress` type.
        if (address instanceof io.servicetalk.transport.api.DomainSocketAddress) {
            return new DomainSocketAddress(((io.servicetalk.transport.api.DomainSocketAddress) address).getPath());
        }
        if (address instanceof SocketAddress) {
            return (SocketAddress) address;
        }
        if (address instanceof HostAndPort) {
            HostAndPort hostAndPort = (HostAndPort) address;
            return createUnresolved(hostAndPort.hostName(), hostAndPort.port());
        }
        throw new IllegalArgumentException("Unsupported address: " + address);
    }

    /**
     * Returns the correct {@link Class} to use with the given {@link EventLoopGroup}.
     *
     * @param group the {@link EventLoopGroup} for which the class is needed
     * @return the class that should be used for bootstrapping
     */
    public static Class<? extends DatagramChannel> datagramChannel(EventLoopGroup group) {
        if (useEpoll(group)) {
            return EpollDatagramChannel.class;
        } else if (useKQueue(group)) {
            return KQueueDatagramChannel.class;
        } else {
            return NioDatagramChannel.class;
        }
    }

    /**
     * Format an address into a canonical numeric format.
     *
     * @param address socket address
     * @return formatted address
     */
    public static String formatCanonicalAddress(SocketAddress address) {
        // Try to return the "raw" address (without resolved host name, etc)
        if (address instanceof InetSocketAddress) {
            InetSocketAddress inetSocketAddress = (InetSocketAddress) address;
            InetAddress inetAddress = inetSocketAddress.getAddress();
            // inetAddress could be null if SocketAddress is in an unresolved form
            if (inetAddress == null) {
                return address.toString();
            } else if (inetAddress instanceof Inet6Address) {
                return '[' + NetUtil.toAddressString(inetAddress) + "]:" + inetSocketAddress.getPort();
            } else {
                return NetUtil.toAddressString(inetAddress) + ':' + inetSocketAddress.getPort();
            }
        }
        return address.toString();
    }

    /**
     * Call {@link Closeable#close()} and re-throw an unchecked exception if a checked exception is thrown.
     * @param closable The object to close.
     */
    public static void closeAndRethrowUnchecked(@Nullable Closeable closable) {
        if (closable != null) {
            try {
                closable.close();
            } catch (IOException e) {
                throw new IllegalStateException(closable + " must be closed, but threw unexpectedly", e);
            }
        }
    }
}
