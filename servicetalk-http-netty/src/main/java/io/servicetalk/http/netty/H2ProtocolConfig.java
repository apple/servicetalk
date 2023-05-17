/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.netty;

import io.servicetalk.http.api.Http2Settings;
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.logging.api.UserDataLoggerConfig;

import java.time.Duration;
import java.util.function.BiPredicate;
import javax.annotation.Nullable;

/**
 * Configuration for <a href="https://tools.ietf.org/html/rfc7540">HTTP/2</a> protocol.
 *
 * @see HttpProtocolConfigs#h2Default()
 */
public interface H2ProtocolConfig extends HttpProtocolConfig {

    @Override
    default String alpnId() {
        return AlpnIds.HTTP_2;
    }

    /**
     * Sensitivity detector to determine if a header {@code name}/{@code value} pair should be treated as
     * <a href="https://tools.ietf.org/html/rfc7541#section-7.1.3">sensitive</a>.
     *
     * @return {@link BiPredicate}&lt;{@link CharSequence}, {@link CharSequence}&gt; that
     * returns {@code true} if a header &lt;{@code name}, {@code value}&gt; pair should be treated as
     * <a href="https://tools.ietf.org/html/rfc7541#section-7.1.3">sensitive</a>, {@code false} otherwise
     */
    BiPredicate<CharSequence, CharSequence> headersSensitivityDetector();

    /**
     * Get the logger configuration for HTTP/2 frames.
     *
     * @return the logger configuration to use for HTTP/2 frames or {@code null} to disable it.
     */
    @Nullable
    UserDataLoggerConfig frameLoggerConfig();

    /**
     * Configured {@link KeepAlivePolicy}.
     *
     * @return configured {@link KeepAlivePolicy}.
     */
    KeepAlivePolicy keepAlivePolicy();

    /**
     * Get the {@link Http2Settings} that provides a hint for the initial settings. Note that some settings may be
     * ignored if not supported (e.g. push promise).
     * @return the {@link Http2Settings} that provides a hint for the initial settings. Note that some settings may be
     * ignored if not supported (e.g. push promise).
     */
    default Http2Settings initialSettings() {   // FIXME: 0.43 - consider removing default impl
        throw new UnsupportedOperationException("H2ProtocolConfig#initialSettings() is not supported by " + getClass());
    }

    /**
     * Provide a hint on the number of bytes that the flow controller will attempt to give to a stream for each
     * allocation (assuming the stream has this much eligible data).
     * <p>
     * This maybe useful because the amount of bytes the local peer is permitted to write is limited based upon the
     * remote peer's flow control window for each stream. Some flow controllers iterate over all streams (and may
     * consider <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-5.3">stream priority</a>) that have data
     * pending to write and allow them to write a subset of the available flow control window (aka a "quantum"). The
     * larger this value may result in increased goodput/throughput on the connection, but increase latency on some
     * streams (if flow control windows become constrained).
     * @return number of bytes.
     */
    default int flowControlQuantum() {   // FIXME: 0.43 - consider removing default impl
        throw new UnsupportedOperationException("H2ProtocolConfig#flowControlQuantum() is not supported by " +
                getClass());
    }

    /**
     * Number of bytes to increment via <a href="https://www.rfc-editor.org/rfc/rfc7540#section-6.9">WINDOW_UPDATE</a>
     * for the connection. This value is applied on top of {@link Http2Settings#initialWindowSize()} from
     * {@link #initialSettings()} for the connection (as opposed to individual request streams). This can be helpful to
     * avoid a single stream consuming all the flow control credits.
     * @return The number of bytes to increment the local flow control window for the connection.
     */
    default int flowControlWindowIncrement() {   // FIXME: 0.43 - consider removing default impl
        throw new UnsupportedOperationException("H2ProtocolConfig#flowControlWindowIncrement() is not supported by " +
                getClass());
    }

    /**
     * A policy for sending <a href="https://tools.ietf.org/html/rfc7540#section-6.7">PING frames</a> to the peer.
     * <p>
     * While {@link #idleDuration()} and {@link #ackTimeout()}} can be configured independently, users should keep
     * them reasonably aligned. When {@link #idleDuration() idle duration} is positive, the system expects to receive
     * {@code PING} acknowledgment before it can send the following {@code PING} frames. A good practice is to keep
     * {@link #ackTimeout()} less than or equal to {@link #idleDuration()}}. Otherwise, the following {@code PING}
     * frames can be delayed awaiting acknowledgment of the previous one.
     */
    interface KeepAlivePolicy {
        /**
         * {@link Duration} of time the connection has to be idle before a
         * <a href="https://tools.ietf.org/html/rfc7540#section-6.7">ping</a> is sent.
         * <p>
         * Too short idle duration can be used for testing but may cause unnecessarily high network traffic in real
         * environments. {@link Duration#ZERO} disables keep-alive {@code PING} frames. In this case,
         * {@link #ackTimeout()} is still used for {@code PING} acknowledgment during graceful closure process between
         * two <a href="https://datatracker.ietf.org/doc/html/rfc9113#section-6.8">GOAWAY</a> frames.
         *
         * @return {@link Duration} of time the connection has to be idle before a
         * <a href="https://tools.ietf.org/html/rfc7540#section-6.7">ping</a> is sent or {@link Duration#ZERO} to
         * disable keep-alive {@code PING} frames.
         */
        Duration idleDuration();

        /**
         * {@link Duration} to wait for acknowledgment from the peer after a
         * <a href="https://tools.ietf.org/html/rfc7540#section-6.7">ping</a> is sent. If no acknowledgment is received
         * within the configured timeout, a connection will be closed.
         * <p>
         * This duration must be positive. Too short ack timeout can cause undesirable connection closures. Too long ack
         * timeout can add unnecessary delay when the remote peer is unresponsive or the network connection is broken,
         * because under normal circumstances {@code PING} frames ara acknowledged immediately.
         * <p>
         * When {@link #idleDuration()} is {@link Duration#ZERO zero}, this timeout is still used for {@code PING}
         * acknowledgment during graceful closure process between two
         * <a href="https://datatracker.ietf.org/doc/html/rfc9113#section-6.8">GOAWAY</a> frames.
         *
         * @return {@link Duration} to wait for acknowledgment from the peer after a
         * <a href="https://tools.ietf.org/html/rfc7540#section-6.7">ping</a> is sent.
         */
        Duration ackTimeout();

        /**
         * Whether this policy allows to send <a href="https://tools.ietf.org/html/rfc7540#section-6.7">pings</a>
         * even if there are no streams active on the connection.
         *
         * @return {@code true} if this policy allows to send
         * <a href="https://tools.ietf.org/html/rfc7540#section-6.7">pings</a> even if there are no streams active on
         * the connection.
         */
        boolean withoutActiveStreams();
    }
}
