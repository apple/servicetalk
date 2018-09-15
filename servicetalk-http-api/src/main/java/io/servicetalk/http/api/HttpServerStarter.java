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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ContextFilter;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.ServerContext;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import static io.servicetalk.http.api.BlockingUtils.blockingInvocation;
import static io.servicetalk.transport.api.ContextFilter.ACCEPT_ALL;

/**
 * Provides methods for binding an {@link StreamingHttpService} to a {@link SocketAddress}.
 */
public interface HttpServerStarter {

    /**
     * Starts this server and returns a {@link Single} that completes when the server is successfully started or
     * terminates with an error if the server could not be started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param executionContext The {@link ExecutionContext} that is used for the IO and asynchronous source creation.
     * @param address Listen address for the server.
     * @param service Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    default Single<ServerContext> startStreaming(ExecutionContext executionContext, SocketAddress address,
                                                 StreamingHttpRequestHandler service) {
        return startStreaming(executionContext, address, ACCEPT_ALL, service);
    }

    /**
     * Starts this server and returns a {@link Single} that completes when the server is successfully started or
     * terminates with an error if the server could not be started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     * The server is using a default {@link ExecutionContext}.
     *
     * @param address Listen address for the server.
     * @param service Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     * @see #startStreaming(ExecutionContext, SocketAddress, StreamingHttpRequestHandler)
     */
    default Single<ServerContext> startStreaming(SocketAddress address, StreamingHttpRequestHandler service) {
        return startStreaming(address, ACCEPT_ALL, service);
    }

    /**
     * Starts this server and returns a {@link Single} that completes when the server is successfully started or
     * terminates with an error if the server could not be started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param executionContext The {@link ExecutionContext} that is used for the IO and asynchronous source creation.
     * @param address Listen address for the server.
     * @param contextFilter to use for filtering accepted connections. The returned {@link ServerContext} manages the
     * lifecycle of the {@code contextFilter}, ensuring it is closed when the {@link ServerContext} is closed.
     * @param service Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    Single<ServerContext> startStreaming(ExecutionContext executionContext, SocketAddress address,
                                         ContextFilter contextFilter, StreamingHttpRequestHandler service);

    /**
     * Starts this server and returns a {@link Single} that completes when the server is successfully started or
     * terminates with an error if the server could not be started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     * The server is using a default {@link ExecutionContext}.
     *
     * @param address Listen address for the server.
     * @param contextFilter to use for filtering accepted connections. The returned {@link ServerContext} manages the
     * lifecycle of the {@code contextFilter}, ensuring it is closed when the {@link ServerContext} is closed.
     * @param service Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     * @see #startStreaming(ExecutionContext, SocketAddress, ContextFilter, StreamingHttpRequestHandler)
     */
    Single<ServerContext> startStreaming(SocketAddress address, ContextFilter contextFilter,
                                         StreamingHttpRequestHandler service);

    /**
     * Starts this server and returns a {@link Single} that completes when the server is successfully started or
     * terminates with an error if the server could not be started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param executionContext The {@link ExecutionContext} that is used for the IO and asynchronous source creation.
     * @param port Listen port for the server.
     * @param service Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    default Single<ServerContext> startStreaming(ExecutionContext executionContext, int port,
                                                 StreamingHttpRequestHandler service) {
        return startStreaming(executionContext, port, ACCEPT_ALL, service);
    }

    /**
     * Starts this server and returns a {@link Single} that completes when the server is successfully started or
     * terminates with an error if the server could not be started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     * The server is using a default {@link ExecutionContext}.
     *
     * @param port Listen port for the server.
     * @param service Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     * @see #startStreaming(ExecutionContext, int, StreamingHttpRequestHandler)
     */
    default Single<ServerContext> startStreaming(int port, StreamingHttpRequestHandler service) {
        return startStreaming(port, ACCEPT_ALL, service);
    }

    /**
     * Starts this server and returns a {@link Single} that completes when the server is successfully started or
     * terminates with an error if the server could not be started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param executionContext The {@link ExecutionContext} that is used for the IO and asynchronous source creation.
     * @param port Listen port for the server.
     * @param contextFilter to use for filtering accepted connections. The returned {@link ServerContext} manages the
     * lifecycle of the {@code contextFilter}, ensuring it is closed when the {@link ServerContext} is closed.
     * @param service Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    default Single<ServerContext> startStreaming(ExecutionContext executionContext, int port,
                                                 ContextFilter contextFilter, StreamingHttpRequestHandler service) {
        return startStreaming(executionContext, new InetSocketAddress(port), contextFilter, service);
    }

    /**
     * Starts this server and returns a {@link Single} that completes when the server is successfully started or
     * terminates with an error if the server could not be started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     * The server is using a default {@link ExecutionContext}.
     *
     * @param port Listen port for the server.
     * @param contextFilter to use for filtering accepted connections. The returned {@link ServerContext} manages the
     * lifecycle of the {@code contextFilter}, ensuring it is closed when the {@link ServerContext} is closed.
     * @param service Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     * @see #startStreaming(ExecutionContext, int, ContextFilter, StreamingHttpRequestHandler)
     */
    default Single<ServerContext> startStreaming(int port, ContextFilter contextFilter,
                                                 StreamingHttpRequestHandler service) {
        return startStreaming(new InetSocketAddress(port), contextFilter, service);
    }

    /**
     * Starts this server and returns a {@link Single} that completes when the server is successfully started or
     * terminates with an error if the server could not be started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param executionContext The {@link ExecutionContext} that is used for the IO and asynchronous source creation.
     * @param address Listen address for the server.
     * @param service Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    default Single<ServerContext> start(ExecutionContext executionContext, SocketAddress address,
                                        HttpRequestHandler service) {
        return start(executionContext, address, ACCEPT_ALL, service);
    }

    /**
     * Starts this server and returns a {@link Single} that completes when the server is successfully started or
     * terminates with an error if the server could not be started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     * The server is using a default {@link ExecutionContext}.
     *
     * @param address Listen address for the server.
     * @param service Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     * @see #start(ExecutionContext, SocketAddress, HttpRequestHandler)
     */
    default Single<ServerContext> start(SocketAddress address, HttpRequestHandler service) {
        return start(address, ACCEPT_ALL, service);
    }

    /**
     * Starts this server and returns a {@link Single} that completes when the server is successfully started or
     * terminates with an error if the server could not be started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param executionContext The {@link ExecutionContext} that is used for the IO and asynchronous source creation.
     * @param address Listen address for the server.
     * @param contextFilter to use for filtering accepted connections. The returned {@link ServerContext} manages the
     * lifecycle of the {@code contextFilter}, ensuring it is closed when the {@link ServerContext} is closed.
     * @param service Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    default Single<ServerContext> start(ExecutionContext executionContext, SocketAddress address,
                                        ContextFilter contextFilter, HttpRequestHandler service) {
        return startStreaming(executionContext, address, contextFilter,
                HttpService.wrap(service).asStreamingService());
    }

    /**
     * Starts this server and returns a {@link Single} that completes when the server is successfully started or
     * terminates with an error if the server could not be started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     * The server is using a default {@link ExecutionContext}.
     *
     * @param address Listen address for the server.
     * @param contextFilter to use for filtering accepted connections. The returned {@link ServerContext} manages the
     * lifecycle of the {@code contextFilter}, ensuring it is closed when the {@link ServerContext} is closed.
     * @param service Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     * @see #start(ExecutionContext, SocketAddress, ContextFilter, HttpRequestHandler)
     */
    default Single<ServerContext> start(SocketAddress address,
                                        ContextFilter contextFilter, HttpRequestHandler service) {
        return startStreaming(address, contextFilter, HttpService.wrap(service).asStreamingService());
    }

    /**
     * Starts this server and returns a {@link Single} that completes when the server is successfully started or
     * terminates with an error if the server could not be started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param executionContext The {@link ExecutionContext} that is used for the IO and asynchronous source creation.
     * @param port Listen port for the server.
     * @param service Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    default Single<ServerContext> start(ExecutionContext executionContext, int port, HttpRequestHandler service) {
        return start(executionContext, port, ACCEPT_ALL, service);
    }

    /**
     * Starts this server and returns a {@link Single} that completes when the server is successfully started or
     * terminates with an error if the server could not be started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     * The server is using a default {@link ExecutionContext}.
     *
     * @param port Listen port for the server.
     * @param service Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     * @see #start(ExecutionContext, int, HttpRequestHandler)
     */
    default Single<ServerContext> start(int port, HttpRequestHandler service) {
        return start(port, ACCEPT_ALL, service);
    }

    /**
     * Starts this server and returns a {@link Single} that completes when the server is successfully started or
     * terminates with an error if the server could not be started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param executionContext The {@link ExecutionContext} that is used for the IO and asynchronous source creation.
     * @param port Listen port for the server.
     * @param contextFilter to use for filtering accepted connections. The returned {@link ServerContext} manages the
     * lifecycle of the {@code contextFilter}, ensuring it is closed when the {@link ServerContext} is closed.
     * @param service Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    default Single<ServerContext> start(ExecutionContext executionContext, int port, ContextFilter contextFilter,
                                        HttpRequestHandler service) {
        return start(executionContext, new InetSocketAddress(port), contextFilter, service);
    }

    /**
     * Starts this server and returns a {@link Single} that completes when the server is successfully started or
     * terminates with an error if the server could not be started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     * The server is using a default {@link ExecutionContext}.
     *
     * @param port Listen port for the server.
     * @param contextFilter to use for filtering accepted connections. The returned {@link ServerContext} manages the
     * lifecycle of the {@code contextFilter}, ensuring it is closed when the {@link ServerContext} is closed.
     * @param service Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     * @see #start(ExecutionContext, int, ContextFilter, HttpRequestHandler)
     */
    default Single<ServerContext> start(int port, ContextFilter contextFilter, HttpRequestHandler service) {
        return start(new InetSocketAddress(port), contextFilter, service);
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param executionContext The {@link ExecutionContext} that is used for the IO and asynchronous source creation.
     * @param address Listen address for the server.
     * @param service Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link ServerContext} if the server starts successfully.
     * @throws Exception If the server could not be started.
     */
    default ServerContext startBlockingStreaming(ExecutionContext executionContext, SocketAddress address,
                                                 BlockingStreamingHttpRequestHandler service)
            throws Exception {
        return startBlockingStreaming(executionContext, address, ACCEPT_ALL, service);
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     * The server is using a default {@link ExecutionContext}.
     *
     * @param address Listen address for the server.
     * @param service Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link ServerContext} if the server starts successfully.
     * @throws Exception If the server could not be started.
     * @see #startBlockingStreaming(ExecutionContext, SocketAddress, BlockingStreamingHttpRequestHandler)
     */
    default ServerContext startBlockingStreaming(SocketAddress address,
                                                 BlockingStreamingHttpRequestHandler service) throws Exception {
        return startBlockingStreaming(address, ACCEPT_ALL, service);
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param executionContext The {@link ExecutionContext} that is used for the IO and asynchronous source creation.
     * @param address Listen address for the server.
     * @param contextFilter to use for filtering accepted connections. The returned {@link ServerContext} manages the
     * lifecycle of the {@code contextFilter}, ensuring it is closed when the {@link ServerContext} is closed.
     * @param service Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link ServerContext} if the server starts successfully.
     * @throws Exception If the server could not be started.
     */
    default ServerContext startBlockingStreaming(ExecutionContext executionContext, SocketAddress address,
                                                 ContextFilter contextFilter,
                                                 BlockingStreamingHttpRequestHandler service) throws Exception {
        return blockingInvocation(startStreaming(executionContext, address, contextFilter,
                BlockingStreamingHttpService.wrap(service).asStreamingService()));
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     * The server is using a default {@link ExecutionContext}.
     *
     * @param address Listen address for the server.
     * @param contextFilter to use for filtering accepted connections. The returned {@link ServerContext} manages the
     * lifecycle of the {@code contextFilter}, ensuring it is closed when the {@link ServerContext} is closed.
     * @param service Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link ServerContext} if the server starts successfully.
     * @throws Exception If the server could not be started.
     * @see #startBlockingStreaming(ExecutionContext, SocketAddress, ContextFilter,
     * BlockingStreamingHttpRequestHandler)
     */
    default ServerContext startBlockingStreaming(SocketAddress address, ContextFilter contextFilter,
                                                 BlockingStreamingHttpRequestHandler service) throws Exception {
        return blockingInvocation(startStreaming(address, contextFilter,
                BlockingStreamingHttpService.wrap(service).asStreamingService()));
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param executionContext The {@link ExecutionContext} that is used for the IO and asynchronous source creation.
     * @param port Listen port for the server.
     * @param service Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link ServerContext} if the server starts successfully.
     * @throws Exception If the server could not be started.
     */
    default ServerContext startBlockingStreaming(ExecutionContext executionContext, int port,
                                                 BlockingStreamingHttpRequestHandler service)
            throws Exception {
        return startBlockingStreaming(executionContext, port, ACCEPT_ALL, service);
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     * The server is using a default {@link ExecutionContext}.
     *
     * @param port Listen port for the server.
     * @param service Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link ServerContext} if the server starts successfully.
     * @throws Exception If the server could not be started.
     * @see #startBlockingStreaming(ExecutionContext, int, BlockingStreamingHttpRequestHandler)
     */
    default ServerContext startBlockingStreaming(int port, BlockingStreamingHttpRequestHandler service)
            throws Exception {
        return startBlockingStreaming(port, ACCEPT_ALL, service);
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param executionContext The {@link ExecutionContext} that is used for the IO and asynchronous source creation.
     * @param port Listen port for the server.
     * @param contextFilter to use for filtering accepted connections. The returned {@link ServerContext} manages the
     * lifecycle of the {@code contextFilter}, ensuring it is closed when the {@link ServerContext} is closed.
     * @param service Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link ServerContext} if the server starts successfully.
     * @throws Exception If the server could not be started.
     */
    default ServerContext startBlockingStreaming(ExecutionContext executionContext, int port,
                                                 ContextFilter contextFilter,
                                                 BlockingStreamingHttpRequestHandler service) throws Exception {
        return startBlockingStreaming(executionContext, new InetSocketAddress(port), contextFilter, service);
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     * The server is using a default {@link ExecutionContext}.
     *
     * @param port Listen port for the server.
     * @param contextFilter to use for filtering accepted connections. The returned {@link ServerContext} manages the
     * lifecycle of the {@code contextFilter}, ensuring it is closed when the {@link ServerContext} is closed.
     * @param service Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link ServerContext} if the server starts successfully.
     * @throws Exception If the server could not be started.
     * @see #startBlockingStreaming(ExecutionContext, int, ContextFilter, BlockingStreamingHttpRequestHandler)
     */
    default ServerContext startBlockingStreaming(int port, ContextFilter contextFilter,
                                                 BlockingStreamingHttpRequestHandler service) throws Exception {
        return startBlockingStreaming(new InetSocketAddress(port), contextFilter, service);
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param executionContext The {@link ExecutionContext} that is used for the IO and asynchronous source creation.
     * @param address Listen address for the server.
     * @param service Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link ServerContext} if the server starts successfully.
     * @throws Exception If the server could not be started.
     */
    default ServerContext startBlocking(ExecutionContext executionContext, SocketAddress address,
                                        BlockingHttpRequestHandler service) throws Exception {
        return startBlocking(executionContext, address, ACCEPT_ALL, service);
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     * The server is using a default {@link ExecutionContext}.
     *
     * @param address Listen address for the server.
     * @param service Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link ServerContext} if the server starts successfully.
     * @throws Exception If the server could not be started.
     * @see #startBlocking(ExecutionContext, SocketAddress, BlockingHttpRequestHandler)
     */
    default ServerContext startBlocking(SocketAddress address, BlockingHttpRequestHandler service) throws Exception {
        return startBlocking(address, ACCEPT_ALL, service);
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param executionContext The {@link ExecutionContext} that is used for the IO and asynchronous source creation.
     * @param address Listen address for the server.
     * @param contextFilter to use for filtering accepted connections. The returned {@link ServerContext} manages the
     * lifecycle of the {@code contextFilter}, ensuring it is closed when the {@link ServerContext} is closed.
     * @param service Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link ServerContext} if the server starts successfully.
     * @throws Exception If the server could not be started.
     */
    default ServerContext startBlocking(ExecutionContext executionContext, SocketAddress address,
                                        ContextFilter contextFilter,
                                        BlockingHttpRequestHandler service) throws Exception {
        return blockingInvocation(startStreaming(executionContext, address, contextFilter,
                BlockingHttpService.wrap(service).asStreamingService()));
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     * The server is using a default {@link ExecutionContext}.
     *
     * @param address Listen address for the server.
     * @param contextFilter to use for filtering accepted connections. The returned {@link ServerContext} manages the
     * lifecycle of the {@code contextFilter}, ensuring it is closed when the {@link ServerContext} is closed.
     * @param service Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link ServerContext} if the server starts successfully.
     * @throws Exception If the server could not be started.
     * @see #startBlocking(ExecutionContext, SocketAddress, ContextFilter, BlockingHttpRequestHandler)
     */
    default ServerContext startBlocking(SocketAddress address, ContextFilter contextFilter,
                                        BlockingHttpRequestHandler service) throws Exception {
        return blockingInvocation(startStreaming(address, contextFilter,
                BlockingHttpService.wrap(service).asStreamingService()));
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param executionContext The {@link ExecutionContext} that is used for the IO and asynchronous source creation.
     * @param port Listen port for the server.
     * @param service Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link ServerContext} if the server starts successfully.
     * @throws Exception If the server could not be started.
     */
    default ServerContext startBlocking(ExecutionContext executionContext, int port,
                                        BlockingHttpRequestHandler service) throws Exception {
        return startBlocking(executionContext, port, ACCEPT_ALL, service);
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     * The server is using a default {@link ExecutionContext}.
     *
     * @param port Listen port for the server.
     * @param service Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link ServerContext} if the server starts successfully.
     * @throws Exception If the server could not be started.
     * @see #startBlocking(ExecutionContext, int, BlockingHttpRequestHandler)
     */
    default ServerContext startBlocking(int port, BlockingHttpRequestHandler service) throws Exception {
        return startBlocking(port, ACCEPT_ALL, service);
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param executionContext The {@link ExecutionContext} that is used for the IO and asynchronous source creation.
     * @param port Listen port for the server.
     * @param contextFilter to use for filtering accepted connections. The returned {@link ServerContext} manages the
     * lifecycle of the {@code contextFilter}, ensuring it is closed when the {@link ServerContext} is closed.
     * @param service Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link ServerContext} if the server starts successfully.
     * @throws Exception If the server could not be started.
     */
    default ServerContext startBlocking(ExecutionContext executionContext, int port, ContextFilter contextFilter,
                                        BlockingHttpRequestHandler service) throws Exception {
        return startBlocking(executionContext, new InetSocketAddress(port), contextFilter, service);
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     * The server is using a default {@link ExecutionContext}.
     *
     * @param port Listen port for the server.
     * @param contextFilter to use for filtering accepted connections. The returned {@link ServerContext} manages the
     * lifecycle of the {@code contextFilter}, ensuring it is closed when the {@link ServerContext} is closed.
     * @param service Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link ServerContext} if the server starts successfully.
     * @throws Exception If the server could not be started.
     * @see #startBlocking(ExecutionContext, int, ContextFilter, BlockingHttpRequestHandler)
     */
    default ServerContext startBlocking(int port, ContextFilter contextFilter,
                                        BlockingHttpRequestHandler service) throws Exception {
        return startBlocking(new InetSocketAddress(port), contextFilter, service);
    }
}
