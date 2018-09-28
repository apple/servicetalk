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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.transport.api.ConnectionContext;

import org.reactivestreams.Subscriber;

import java.util.function.UnaryOperator;

/**
 * A specialized {@link ConnectionContext} for netty based transports.
 */
public interface NettyConnectionContext extends ConnectionContext {

    /**
     * Updates {@link FlushStrategy} associated with this connection. Updated {@link FlushStrategy} will be used in any
     * subsequent writes on this connection.
     *
     * @param strategyProvider {@link UnaryOperator} that given the current {@link FlushStrategy}, returns the
     * {@link FlushStrategy} to be updated. This {@link UnaryOperator} <em>MAY</em> be invoked multiple times for a
     * single call to this method.
     *
     * @return A {@link Cancellable} that will cancel this update and revert the {@link FlushStrategy} for this
     * connection to a default value.
     */
    Cancellable updateFlushStrategy(UnaryOperator<FlushStrategy> strategyProvider);

    /**
     * Returns a {@link Publisher} that emits various {@link ConnectionEvent}s happening on this connection.
     * <p>
     * <b>All methods of a {@link Subscriber} to this {@link Publisher} will be invoked on the event loop.
     * Presence of blocking operations within these methods will negatively impact responsiveness of that event
     * loop.</b>
     *
     * <h2>Flow control</h2>
     * Typically consuming {@link ConnectionEvent}s from the returned {@link Publisher} is not expected to be flow
     * controlled (i.e. there should always be sufficient demand). However, if there is not enough demand to
     * publishReadComplete generated {@link ConnectionEvent}s, they will be dropped.
     *
     * @return {@link Publisher} that emits various {@link ConnectionEvent}s happening on this connection.
     */
    Publisher<ConnectionEvent> getConnectionEvents();

    /**
     * Events happening on a connection.
     */
    enum ConnectionEvent {
        /**
         * A batch of read has now completed on this connection.<p>
         * This does <b>not</b> indicate liveness of the connection or whether there will be any more reads done on
         * this connection. If reads are done in batches, this event indicates, that such a batch has now ended.
         * One may see zero or more occurrences of this event on any connection.
         */
        ReadComplete,
   }
}
