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
package io.servicetalk.transport.api;

import java.net.SocketAddress;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.nio.channels.ServerSocketChannel;

import static java.util.Objects.requireNonNull;

/**
 * {@link SocketOption}s that can be used beside {@link StandardSocketOptions}.
 */
public final class ServiceTalkSocketOptions {

    /**
     * The connect timeout in milliseconds.
     */
    public static final SocketOption<Integer> CONNECT_TIMEOUT =
            new ServiceTalkSocketOption<>("CONNECT_TIMEOUT", Integer.class);

    /**
     * The threshold after which the the Endpoint is not writable anymore.
     */
    public static final SocketOption<Integer> WRITE_BUFFER_THRESHOLD =
            new ServiceTalkSocketOption<>("WRITE_BUFFER_THRESHOLD", Integer.class);

    /**
     * Allow to idle timeout in milli seconds after which the connection is closed.
     */
    public static final SocketOption<Long> IDLE_TIMEOUT = new ServiceTalkSocketOption<>("IDLE_TIMEOUT", Long.class);

    /**
     * Enable TCP fast open connect on the client side as described in
     * <a href="https://tools.ietf.org/html/rfc7413#appendix-A.1">RFC 7413 Active Open</a>.
     * <p>
     * Note the following caveats of this option:
     * <ul>
     *     <li>it may not be supported by the underlying transport (e.g. supported by Netty's
     *     <a href="https://netty.io/wiki/native-transports.html#using-the-linux-native-transport">linux EPOLL
     *     transport</a>)</li>
     *     <li>the data that is written in TFO must be idempotent (see
     *     <a href="https://lwn.net/Articles/508865/">LWN article</a> for more info). The TLS client_hello
     *     <a href="https://tools.ietf.org/html/rfc7413#section-6.3.2">is idempotent</a> and this option is therefore
     *     safe to use with TLS.</li>
     *     <li>data must be written before attempting to connect the socket (clarifies data is idempotent)</li>
     * </ul>
     */
    public static final SocketOption<Boolean> TCP_FASTOPEN_CONNECT =
            new ServiceTalkSocketOption<>("TCP_FASTOPEN_CONNECT", Boolean.class);

    // -- Server/listen socket specific options --

    /**
     * The number of pending accepted connections for server sockets. For example this value is used for methods like
     * {@link ServerSocketChannel#bind(SocketAddress, int)} and
     * <a href="https://man7.org/linux/man-pages/man2/listen.2.html">listen(int sockfd, int backlog)</a>.
     */
    public static final SocketOption<Integer> SO_BACKLOG = new ServiceTalkSocketOption<>("SO_BACKLOG", Integer.class);

    /**
     * The number of pending SYNs with data payload for server sockets as described in
     * <a href="https://tools.ietf.org/html/rfc7413#appendix-A.2">RFC 7413 Passive Open</a>.
     * <p>
     * Note this option may not be supported by the underlying transport (e.g. supported by Netty's
     * <a href="https://netty.io/wiki/native-transports.html#using-the-linux-native-transport">linux EPOLL
     * transport)</a>.
     */
    public static final SocketOption<Integer> TCP_FASTOPEN_BACKLOG =
            new ServiceTalkSocketOption<>("TCP_FASTOPEN_BACKLOG", Integer.class);

    private ServiceTalkSocketOptions() {
    }

    private static final class ServiceTalkSocketOption<T> implements SocketOption<T> {
        private final String name;
        private final Class<T> type;

        ServiceTalkSocketOption(String name, Class<T> type) {
            this.name = requireNonNull(name);
            this.type = requireNonNull(type);
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Class<T> type() {
            return type;
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ServiceTalkSocketOption<?> that = (ServiceTalkSocketOption<?>) o;
            return name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }
}
