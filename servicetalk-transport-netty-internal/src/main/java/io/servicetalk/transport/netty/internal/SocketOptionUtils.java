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

import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Utilities to convert {@link SocketOption}s.
 */
public final class SocketOptionUtils {

    private SocketOptionUtils() {
        // No instances
    }

    /**
     * Convert and add the given {@link SocketOption} and value to the {@link ChannelOption}s {@link Map}.
     *
     * @param channelOpts the {@link Map} into which add the converted {@link SocketOption}
     * @param option the {@link SocketOption} to convert and add
     * @param value the value to add
     */
    @SuppressWarnings("rawtypes")
    public static void addOption(final Map<ChannelOption, Object> channelOpts, final SocketOption option,
                                 final Object value) {
        if (option == StandardSocketOptions.IP_MULTICAST_IF) {
            channelOpts.put(ChannelOption.IP_MULTICAST_IF, value);
        } else if (option == StandardSocketOptions.IP_MULTICAST_LOOP) {
            channelOpts.put(ChannelOption.IP_MULTICAST_LOOP_DISABLED, !(Boolean) value);
        } else if (option == StandardSocketOptions.IP_MULTICAST_TTL) {
            channelOpts.put(ChannelOption.IP_MULTICAST_TTL, value);
        } else if (option == StandardSocketOptions.IP_TOS) {
            channelOpts.put(ChannelOption.IP_TOS, value);
        } else if (option == StandardSocketOptions.SO_BROADCAST) {
            channelOpts.put(ChannelOption.SO_BROADCAST, value);
        } else if (option == StandardSocketOptions.SO_KEEPALIVE) {
            channelOpts.put(ChannelOption.SO_KEEPALIVE, value);
        } else if (option == StandardSocketOptions.SO_LINGER) {
            channelOpts.put(ChannelOption.SO_LINGER, value);
        } else if (option == StandardSocketOptions.SO_RCVBUF) {
            channelOpts.put(ChannelOption.SO_RCVBUF, value);
        } else if (option == StandardSocketOptions.SO_REUSEADDR) {
            channelOpts.put(ChannelOption.SO_REUSEADDR, value);
        } else if (option == StandardSocketOptions.SO_SNDBUF) {
            channelOpts.put(ChannelOption.SO_SNDBUF, value);
        } else if (option == StandardSocketOptions.TCP_NODELAY) {
            channelOpts.put(ChannelOption.TCP_NODELAY, value);
        } else if (option == ServiceTalkSocketOptions.CONNECT_TIMEOUT) {
            channelOpts.put(ChannelOption.CONNECT_TIMEOUT_MILLIS, value);
        } else if (option == ServiceTalkSocketOptions.WRITE_BUFFER_THRESHOLD) {
            Integer writeBufferThreshold = (Integer) value;
            channelOpts.put(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(writeBufferThreshold >>> 1,
                    writeBufferThreshold));
        } else {
            throw new IllegalArgumentException("SocketOption " + option + " not supported");
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
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public static <T> T getOption(final SocketOption<T> option, final ChannelConfig config,
                                  @Nullable final Long idleTimeoutMs) {
        if (option == StandardSocketOptions.IP_MULTICAST_IF) {
            return (T) config.getOption(ChannelOption.IP_MULTICAST_IF);
        }
        if (option == StandardSocketOptions.IP_MULTICAST_LOOP) {
            final Boolean result = config.getOption(ChannelOption.IP_MULTICAST_LOOP_DISABLED);
            return result == null ? null : (T) Boolean.valueOf(!result);
        }
        if (option == StandardSocketOptions.IP_MULTICAST_TTL) {
            return (T) config.getOption(ChannelOption.IP_MULTICAST_TTL);
        }
        if (option == StandardSocketOptions.IP_TOS) {
            return (T) config.getOption(ChannelOption.IP_TOS);
        }
        if (option == StandardSocketOptions.SO_BROADCAST) {
            return (T) config.getOption(ChannelOption.SO_BROADCAST);
        }
        if (option == StandardSocketOptions.SO_KEEPALIVE) {
            return (T) config.getOption(ChannelOption.SO_KEEPALIVE);
        }
        if (option == StandardSocketOptions.SO_LINGER) {
            return (T) config.getOption(ChannelOption.SO_LINGER);
        }
        if (option == StandardSocketOptions.SO_RCVBUF) {
            return (T) config.getOption(ChannelOption.SO_RCVBUF);
        }
        if (option == StandardSocketOptions.SO_REUSEADDR) {
            return (T) config.getOption(ChannelOption.SO_REUSEADDR);
        }
        if (option == StandardSocketOptions.SO_SNDBUF) {
            return (T) config.getOption(ChannelOption.SO_SNDBUF);
        }
        if (option == StandardSocketOptions.TCP_NODELAY) {
            return (T) config.getOption(ChannelOption.TCP_NODELAY);
        }
        if (option == ServiceTalkSocketOptions.CONNECT_TIMEOUT) {
            return (T) config.getOption(ChannelOption.CONNECT_TIMEOUT_MILLIS);
        }
        if (option == ServiceTalkSocketOptions.WRITE_BUFFER_THRESHOLD) {
            final WriteBufferWaterMark result = config.getOption(ChannelOption.WRITE_BUFFER_WATER_MARK);
            return result == null ? null : (T) Integer.valueOf(result.high());
        }
        if (option == ServiceTalkSocketOptions.IDLE_TIMEOUT) {
            return idleTimeoutMs == null ? null : (T) idleTimeoutMs;    // TODO: return (T) idleTimeoutMs;
        }
        // Try to look for a ChannelOption with the same name and type:
        try {
            return config.getOption(ChannelOption.valueOf(option.name()));
        } catch (ClassCastException e) {
            return null;
        }
    }
}
