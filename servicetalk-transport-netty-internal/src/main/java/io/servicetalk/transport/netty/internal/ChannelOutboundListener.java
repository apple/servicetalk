/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

/**
 * An interface which provides methods that are invoked when outbound channel events occur. The implementors of
 * this interface are effectively "listening" to these events via method calls.
 */
interface ChannelOutboundListener {
    /**
     * Notification that the writability of the channel has changed.
     * <p>
     * Always called from the event loop thread.
     */
    void channelWritable();

    /**
     * Notification that the channel's outbound side has been closed and will no longer accept writes.
     * <p>
     * Always called from the event loop thread.
     */
    void channelOutboundClosed();

    /**
     * Notification that the channel has been closed.
     * <p>
     * This may not always be called from the event loop thread. For example if the channel is closed when a new
     * write happens then this method will be called from the writer thread.
     *
     * @param closedException the exception which describes the close rational.
     */
    void channelClosed(Throwable closedException);

    /**
     * Called if there is already a {@link ChannelOutboundListener} active and this listener will be discarded.
     * @param cause A cause describing the rational.
     */
    void listenerDiscard(Throwable cause);
}
