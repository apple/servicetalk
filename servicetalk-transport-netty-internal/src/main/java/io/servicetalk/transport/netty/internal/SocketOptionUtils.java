/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.transport.api.ServiceTalkSocketOptions;

import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelOption;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.epoll.EpollChannelOption;

import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;

import static java.util.function.Function.identity;

/**
 * Utilities to convert {@link SocketOption}s.
 */
public final class SocketOptionUtils {
    private static final Map<SocketOption<?>, OptConverter<?>> SOCKET_OPT_MAP = new HashMap<>();
    static {
        putOpt(ChannelOption.IP_MULTICAST_IF, StandardSocketOptions.IP_MULTICAST_IF);
        putOpt(ChannelOption.IP_MULTICAST_LOOP_DISABLED, StandardSocketOptions.IP_MULTICAST_LOOP,
                SocketOptionUtils::boolNot, SocketOptionUtils::boolNot);
        putOpt(ChannelOption.IP_MULTICAST_TTL, StandardSocketOptions.IP_MULTICAST_TTL);
        putOpt(ChannelOption.IP_TOS, StandardSocketOptions.IP_TOS);
        putOpt(ChannelOption.SO_BROADCAST, StandardSocketOptions.SO_BROADCAST);
        putOpt(ChannelOption.SO_KEEPALIVE, StandardSocketOptions.SO_KEEPALIVE);
        putOpt(ChannelOption.SO_LINGER, StandardSocketOptions.SO_LINGER);
        putOpt(ChannelOption.SO_RCVBUF, StandardSocketOptions.SO_RCVBUF);
        putOpt(ChannelOption.SO_REUSEADDR, StandardSocketOptions.SO_REUSEADDR);
        putOpt(ChannelOption.SO_SNDBUF, StandardSocketOptions.SO_SNDBUF);
        putOpt(ChannelOption.TCP_NODELAY, StandardSocketOptions.TCP_NODELAY);
        putOpt(ChannelOption.CONNECT_TIMEOUT_MILLIS, ServiceTalkSocketOptions.CONNECT_TIMEOUT);
        putOpt(ChannelOption.WRITE_BUFFER_WATER_MARK, ServiceTalkSocketOptions.WRITE_BUFFER_THRESHOLD,
                writeBufferWaterMark -> writeBufferWaterMark == null ? null : writeBufferWaterMark.high(),
                stThreshold -> new WriteBufferWaterMark(stThreshold >>> 1, stThreshold));
        putOpt(ChannelOption.TCP_FASTOPEN_CONNECT, ServiceTalkSocketOptions.TCP_FASTOPEN_CONNECT);
        putOpt(ChannelOption.SO_BACKLOG, ServiceTalkSocketOptions.SO_BACKLOG);
        putOpt(EpollChannelOption.TCP_FASTOPEN, ServiceTalkSocketOptions.TCP_FASTOPEN_BACKLOG);
    }

    private SocketOptionUtils() {
        // No instances
    }

    /**
     * Convert and add the given {@link SocketOption} and value to the {@link ChannelOption}s {@link Map}.
     *
     * @param channelOpts the {@link Map} into which add the converted {@link SocketOption}
     * @param option the {@link SocketOption} to convert and add
     * @param value the value to add
     * @param <T> the type of the {@link SocketOption} value
     * @throws IllegalArgumentException if the specified {@link SocketOption} is not supported
     */
    @SuppressWarnings("rawtypes")
    public static <T> void addOption(final Map<ChannelOption, Object> channelOpts, final SocketOption<T> option,
                                     final T value) {
        @SuppressWarnings("unchecked")
        OptConverter<T> converter = (OptConverter<T>) SOCKET_OPT_MAP.get(option);
        if (converter != null) {
            channelOpts.put(converter.option, converter.sockToChan.apply(value));
        } else {
            throw unsupported(option);
        }
    }

    /**
     * Get a {@link SocketOption} value from {@link ChannelConfig}.
     *
     * @param option the {@link SocketOption} to get
     * @param config the {@link ChannelConfig} to get the {@link SocketOption} from
     * @param idleTimeoutMs value for {@link ServiceTalkSocketOptions#IDLE_TIMEOUT IDLE_TIMEOUT} socket option
     * @param <T> the type of the {@link SocketOption} value
     * @return a value of the {@link SocketOption} of type {@code T} or {@code null} if the {@link ChannelConfig} does
     * not have a value for requested {@link SocketOption}
     * @throws IllegalArgumentException if the specified {@link SocketOption} is not supported
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public static <T> T getOption(final SocketOption<T> option, final ChannelConfig config,
                                  @Nullable final Long idleTimeoutMs) {
        @SuppressWarnings("unchecked")
        OptConverter<T> converter = (OptConverter<T>) SOCKET_OPT_MAP.get(option);
        if (converter != null) {
            return (T) converter.chanToSock.apply(config.getOption(converter.option));
        } else if (option == ServiceTalkSocketOptions.IDLE_TIMEOUT) {
            return (T) idleTimeoutMs;
        }
        throw unsupported(option);
    }

    private static <T> IllegalArgumentException unsupported(final SocketOption<T> option) {
        return new IllegalArgumentException("SocketOption(" + option.name() + ", " + option.type().getName() +
                ") is not supported");
    }

    private static <T> void putOpt(ChannelOption<T> channelOpt, SocketOption<T> socketOpt) {
        putOpt(channelOpt, socketOpt, identity(), identity());
    }

    private static <T, R> void putOpt(ChannelOption<T> channelOpt, SocketOption<R> socketOpt,
                                      Function<T, R> channelToSocket, Function<R, T> socketToChannel) {
        SOCKET_OPT_MAP.put(socketOpt, new OptConverter<>(channelOpt, channelToSocket, socketToChannel));
    }

    @Nullable
    private static Boolean boolNot(@Nullable Boolean v) {
        return v == null ? null : !v;
    }

    private static final class OptConverter<T> {
        private final ChannelOption<T> option;
        private final Function<T, Object> chanToSock;
        private final Function<Object, T> sockToChan;

        @SuppressWarnings("unchecked")
        private OptConverter(final ChannelOption<T> option, final Function<T, ?> chanToSock,
                             final Function<?, T> sockToChan) {
            this.option = option;
            this.chanToSock = (Function<T, Object>) chanToSock;
            this.sockToChan = (Function<Object, T>) sockToChan;
        }
    }
}
