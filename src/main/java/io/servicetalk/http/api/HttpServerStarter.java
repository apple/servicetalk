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
 * Provides methods for binding an {@link HttpService} to a {@link SocketAddress}.
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
    default Single<ServerContext> start(ExecutionContext executionContext, SocketAddress address, HttpService service) {
        return start(executionContext, address, ACCEPT_ALL, service);
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
    Single<ServerContext> start(ExecutionContext executionContext, SocketAddress address, ContextFilter contextFilter,
                                HttpService service);

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
    default Single<ServerContext> start(ExecutionContext executionContext, int port, HttpService service) {
        return start(executionContext, port, ACCEPT_ALL, service);
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
                                        HttpService service) {
        return start(executionContext, new InetSocketAddress(port), contextFilter, service);
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
                                        AggregatedHttpService service) {
        return start(executionContext, address, ACCEPT_ALL, service);
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
                                        ContextFilter contextFilter, AggregatedHttpService service) {
        return start(executionContext, address, contextFilter, service.asService());
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
    default Single<ServerContext> start(ExecutionContext executionContext, int port, AggregatedHttpService service) {
        return start(executionContext, port, ACCEPT_ALL, service);
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
                                        AggregatedHttpService service) {
        return start(executionContext, new InetSocketAddress(port), contextFilter, service);
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
    default ServerContext start(ExecutionContext executionContext, SocketAddress address, BlockingHttpService service)
            throws Exception {
        return start(executionContext, address, ACCEPT_ALL, service);
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
    default ServerContext start(ExecutionContext executionContext, SocketAddress address, ContextFilter contextFilter,
                                BlockingHttpService service) throws Exception {
        return blockingInvocation(start(executionContext, address, contextFilter, service.asService()));
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
    default ServerContext start(ExecutionContext executionContext, int port, BlockingHttpService service)
            throws Exception {
        return start(executionContext, port, ACCEPT_ALL, service);
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
    default ServerContext start(ExecutionContext executionContext, int port, ContextFilter contextFilter,
                                BlockingHttpService service) throws Exception {
        return start(executionContext, new InetSocketAddress(port), contextFilter, service);
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
    default ServerContext start(ExecutionContext executionContext, SocketAddress address,
                                BlockingAggregatedHttpService service) throws Exception {
        return start(executionContext, address, ACCEPT_ALL, service);
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
    default ServerContext start(ExecutionContext executionContext, SocketAddress address, ContextFilter contextFilter,
                                BlockingAggregatedHttpService service) throws Exception {
        return blockingInvocation(start(executionContext, address, contextFilter, service.asService()));
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
    default ServerContext start(ExecutionContext executionContext, int port, BlockingAggregatedHttpService service)
            throws Exception {
        return start(executionContext, port, ACCEPT_ALL, service);
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
    default ServerContext start(ExecutionContext executionContext, int port, ContextFilter contextFilter,
                                BlockingAggregatedHttpService service) throws Exception {
        return start(executionContext, new InetSocketAddress(port), contextFilter, service);
    }
}
