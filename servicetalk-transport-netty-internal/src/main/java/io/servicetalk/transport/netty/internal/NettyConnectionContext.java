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
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ConnectionContext;

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
     * new {@link FlushStrategy}. This {@link UnaryOperator} <strong>MAY</strong> be invoked multiple times for a single
     * call to this method and is expected to be idempotent.
     *
     * @return A {@link Cancellable} that will cancel this update and revert the {@link FlushStrategy} for this
     * connection to a default value.
     */
    Cancellable updateFlushStrategy(UnaryOperator<FlushStrategy> strategyProvider);

    /**
     * Returns a {@link Single}&lt;{@link Throwable}&gt; that may complete when an error is observed at the transport.
     *
     * @return a {@link Single}&lt;{@link Throwable}&gt; that may complete when an error is observed at the transport.
     */
    Single<Throwable> transportError();
}
