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

import io.netty.channel.embedded.EmbeddedChannel;

/**
 * A factory for {@link CloseHandler}s.
 */
public final class CloseHandlers {

    private CloseHandlers() {
        // No instances
    }

    /**
     * Creates a new {@link CloseHandler} instance.
     *
     * @param channel The {@link EmbeddedChannel} to create the {@link CloseHandler} for, its
     * {@link EmbeddedChannel#config() config} may be modified to ensure proper behavior.
     * @return a new connection close handler with behavior for a pipelined request/response client or server
     */
    public static CloseHandler forPipelinedClient(final EmbeddedChannel channel) {
        channel.config().setAutoClose(false);
        return new RequestResponseCloseHandler(true, channel);
    }

    /**
     * Creates a new {@link CloseHandler} instance.
     *
     * @param channel The {@link EmbeddedChannel} to create the {@link CloseHandler} for, its
     * {@link EmbeddedChannel#config() config} may be modified to ensure proper behavior.
     * @return a new connection close handler with behavior for a pipelined request/response client or server
     */
    public static CloseHandler forPipelinedServer(final EmbeddedChannel channel) {
        channel.config().setAutoClose(false);
        return new RequestResponseCloseHandler(false, channel);
    }
}
