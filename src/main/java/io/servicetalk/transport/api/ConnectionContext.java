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

import io.servicetalk.buffer.Buffer;
import io.servicetalk.buffer.BufferAllocator;
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
     * Return the {@link BufferAllocator} that can be used to allocate {@link Buffer}s if needed.
     * @return the {@link BufferAllocator} to use
     */
    BufferAllocator getAllocator();

    /**
     * Get the {@link SSLSession} for this connection.
     *
     * @return The {@link SSLSession} if SSL/TLS is enabled, or {@code null} otherwise.
     */
    @Nullable
    SSLSession getSslSession();

    /**
     * Get the {@link IoExecutor} that is used to handle the IO for the connection.
     * @return The {@link IoExecutor} that is used to handle the IO for the connection.
     */
    IoExecutor getIoExecutor();
}
