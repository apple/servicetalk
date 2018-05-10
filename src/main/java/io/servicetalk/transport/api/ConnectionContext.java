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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;

import java.net.SocketAddress;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

/**
 * A service execution context.
 */
public interface ConnectionContext extends ListenableAsyncCloseable {
    /**
     * The {@link SocketAddress} to which the associated connection is bound.
     * @return The {@link SocketAddress} to which the associated connection is bound.
     */
    SocketAddress getLocalAddress();

    /**
     * The {@link SocketAddress} to which the associated connection is connected.
     * @return The {@link SocketAddress} to which the associated connection is connected.
     */
    SocketAddress getRemoteAddress();

    /**
     * Get the {@link SSLSession} for this connection.
     *
     * @return The {@link SSLSession} if SSL/TLS is enabled, or {@code null} otherwise.
     */
    @Nullable
    SSLSession getSslSession();

    /**
     * Get the {@link ExecutionContext} for this {@link ConnectionContext}.
     * <p>
     * The {@link ExecutionContext#getIoExecutor()} will represent the thread responsible for IO for this
     * {@link ConnectionContext}. Note that this maybe different that what was used to create this object because
     * at this time a specific {@link IoExecutor} has been selected.
     * @return the {@link ExecutionContext} for this {@link ConnectionContext}.
     */
    ExecutionContext getExecutionContext();

    /**
     * Return the {@link BufferAllocator} used by this {@link ConnectionContext} to allocate {@link Buffer}s.
     * @return the {@link BufferAllocator} used by this {@link ConnectionContext}.
     */
    default BufferAllocator getBufferAllocator() {
        return getExecutionContext().getBufferAllocator();
    }

    /**
     * Get the {@link IoExecutor} that is used to handle the IO for this {@link ConnectionContext}.
     * @return The {@link IoExecutor} that is used to handle the IO for this {@link ConnectionContext}.
     */
    default IoExecutor getIoExecutor() {
        return getExecutionContext().getIoExecutor();
    }

    /**
     * Get the {@link Executor} that is used to create any asynchronous sources by this {@link ConnectionContext}.
     * @return The {@link Executor} that is used to create any asynchronous sources by this {@link ConnectionContext}.
     */
    default Executor getExecutor() {
        return getExecutionContext().getExecutor();
    }
}
