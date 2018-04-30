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
package io.servicetalk.http.api;

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ContextFilter;
import io.servicetalk.transport.api.ServerContext;

import java.net.SocketAddress;

/**
 * Provides methods for binding an {@link HttpService} to a {@link SocketAddress}.
 */
public interface HttpServerStarter {
    /**
     * Starts this server and returns a {@link Single} that completes when the server is successfully started or
     * terminates with an error if the server could not be started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param address Listen address for the server.
     * @param executor The {@link Executor} for invoking {@code service}. The returned {@link ServerContext} manages the
     * lifecycle of the {@code executor}, ensuring it is closed when the {@link ServerContext} is closed. Note: This
     * may change as this API is refined.
     * @param service Service invoked for every request received by this server.  The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    Single<ServerContext> start(SocketAddress address, Executor executor, HttpService<HttpPayloadChunk, HttpPayloadChunk> service);

    /**
     * Starts this server and returns a {@link Single} that completes when the server is successfully started or
     * terminates with an error if the server could not be started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param address Listen address for the server.
     * @param contextFilter to use for filtering accepted connections.  The returned {@link ServerContext} manages the
     * lifecycle of the {@code contextFilter}, ensuring it is closed when the {@link ServerContext} is closed.
     * @param executor The {@link Executor} for invoking {@code service}. The returned {@link ServerContext} manages the
     * lifecycle of the {@code executor}, ensuring it is closed when the {@link ServerContext} is closed. Note: This
     * may change as this API is refined.
     * @param service Service invoked for every request received by this server.  The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    Single<ServerContext> start(SocketAddress address, ContextFilter contextFilter, Executor executor, HttpService<HttpPayloadChunk, HttpPayloadChunk> service);
}
