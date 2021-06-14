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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ConnectionContext;

import io.netty.channel.Channel;

/**
 * A specialized {@link ConnectionContext} for netty based transports.
 */
public interface NettyConnectionContext extends ConnectionContext {

    /**
     * Updates {@link FlushStrategy} associated with this connection. Updated {@link FlushStrategy} will be used in any
     * subsequent writes on this connection.
     *
     * @param strategyProvider {@link FlushStrategyProvider} to provide a new {@link FlushStrategy}.
     * {@link FlushStrategyProvider#computeFlushStrategy(FlushStrategy, boolean)} <strong>MAY</strong> be invoked
     * multiple times for a single call to this method and is expected to be idempotent.
     *
     * @return A {@link Cancellable} that will cancel this update.
     */
    Cancellable updateFlushStrategy(FlushStrategyProvider strategyProvider);

    /**
     * Returns the {@link FlushStrategy} used by default for this {@link NettyConnectionContext}.
     *
     * @return The {@link FlushStrategy} used by default for this {@link NettyConnectionContext}.
     */
    FlushStrategy defaultFlushStrategy();

    /**
     * Returns a {@link Single}&lt;{@link Throwable}&gt; that may terminate with an error, if an error is observed at
     * the transport.
     * <p>
     * <b>Note:</b>The {@code Single} is not required to be blocking-safe and should be offloaded if the
     * {@link io.servicetalk.concurrent.SingleSource.Subscriber} may block.
     *
     * @return a {@link Single}&lt;{@link Throwable}&gt; that may terminate with an error, if an error is observed at
     * the transport.
     */
    Single<Throwable> transportError();

    /**
     * Returns a {@link Completable} that notifies when the connection has begun its closing sequence.
     *
     * @return a {@link Completable} that notifies when the connection has begun its closing sequence. A configured
     * {@link CloseHandler} will determine whether more reads or writes will be allowed on this
     * {@link NettyConnectionContext}.
     */
    Completable onClosing();

    /**
     * Return the Netty {@link Channel} backing this connection.
     *
     * @return the Netty {@link Channel} backing this connection.
     */
    Channel nettyChannel();

    /**
     * A provider of {@link FlushStrategy} to update the {@link FlushStrategy} for a {@link NettyConnectionContext}.
     */
    @FunctionalInterface
    interface FlushStrategyProvider {

        /**
         * Given the current {@link FlushStrategy} associated with this {@link NettyConnectionContext}, return a new
         * {@link FlushStrategy}. This method is expected to be idempotent.
         *
         * @param current Current {@link FlushStrategy} associated with the {@link NettyConnectionContext}.
         * @param isCurrentOriginal {@code true} if the supplied {@code current} {@link FlushStrategy} is the same
         * {@link FlushStrategy} that the associated {@link NettyConnectionContext} was created with. This is useful if
         * the implementations do not wish to override a strategy already updated by another call.
         * @return {@link FlushStrategy} to use if successfully updated by
         * {@link NettyConnectionContext#updateFlushStrategy(FlushStrategyProvider)}.
         */
        FlushStrategy computeFlushStrategy(FlushStrategy current, boolean isCurrentOriginal);
    }
}
