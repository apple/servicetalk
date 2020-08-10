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

import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.ConnectionObserver.SecurityHandshakeObserver;
import io.servicetalk.transport.api.TransportObserver;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.AttributeKey;

import javax.annotation.Nullable;

import static io.netty.util.AttributeKey.newInstance;

/**
 * Utilities for {@link TransportObserver}.
 */
public final class TransportObserverUtils {

    private static final AttributeKey<ConnectionObserver> CONNECTION_OBSERVER = newInstance("ConnectionObserver");
    private static final AttributeKey<Throwable> CONNECTION_ERROR = newInstance("ConnectionError");
    private static final AttributeKey<SecurityHandshakeObserver> SECURITY_HANDSHAKE_OBSERVER =
            newInstance("SecurityHandshakeObserver");

    private TransportObserverUtils() {
        // No instances
    }

    /**
     * Assigns a {@link ConnectionObserver} to the passed {@link Channel}.
     *
     * @param channel a {@link Channel} to assign a {@link ConnectionObserver} to
     * @param observer a {@link ConnectionObserver}
     */
    static void assignConnectionObserver(final Channel channel, final ConnectionObserver observer) {
        channel.attr(CONNECTION_OBSERVER).set(observer);
        channel.closeFuture().addListener((ChannelFutureListener) future -> {
            Throwable t = connectionError(channel);
            if (t == null) {
                observer.connectionClosed();
            } else {
                observer.connectionClosed(t);
            }
            channel.attr(CONNECTION_OBSERVER).set(null);
        });
    }

    /**
     * Returns {@link ConnectionObserver} if associated with the provided {@link Channel}.
     *
     * @param channel to look for a {@link ConnectionObserver}
     * @return {@link ConnectionObserver} if associated with the provided {@link Channel} or {@code null}
     */
    @Nullable
    public static ConnectionObserver connectionObserver(final Channel channel) {
        return channel.attr(CONNECTION_OBSERVER).get();
    }

    /**
     * Assigns a {@link Throwable} to the passed {@link Channel}.
     *
     * @param channel a {@link Channel} to assign a {@link Throwable} to
     * @param error a {@link Throwable}
     */
    public static void assignConnectionError(final Channel channel, final Throwable error) {
        if (connectionObserver(channel) != null) {
            channel.attr(CONNECTION_ERROR).setIfAbsent(error);
        }
    }

    /**
     * Returns an {@link Throwable error} assigned with the specified {@link Channel}.
     *
     * @param channel to look for a {@link Throwable}
     * @return an {@link Throwable error} assigned with the specified {@link Channel}.
     */
    @Nullable
    public static Throwable connectionError(final Channel channel) {
        return channel.attr(CONNECTION_ERROR).getAndSet(null);
    }

    static void reportSecurityHandshakeStarting(final Channel channel) {
        final ConnectionObserver observer = connectionObserver(channel);
        assert observer != null;
        channel.attr(SECURITY_HANDSHAKE_OBSERVER).set(observer.onSecurityHandshake());
    }

    @Nullable
    static SecurityHandshakeObserver securityHandshakeObserver(final Channel channel) {
        return channel.attr(SECURITY_HANDSHAKE_OBSERVER).get();
    }
}
