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

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitelyNonNull;
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
     * @param address Listen address for the server.
     * @param service Service invoked for every request received by this server.  The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    default Single<ServerContext> start(SocketAddress address, HttpService service) {
        return start(address, ACCEPT_ALL, newCachedThreadExecutor(), service);
    }

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
    default Single<ServerContext> start(SocketAddress address, Executor executor, HttpService service) {
        return start(address, ACCEPT_ALL, executor, service);
    }

    /**
     * Starts this server and returns a {@link Single} that completes when the server is successfully started or
     * terminates with an error if the server could not be started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param address Listen address for the server.
     * @param contextFilter to use for filtering accepted connections.  The returned {@link ServerContext} manages the
     * lifecycle of the {@code contextFilter}, ensuring it is closed when the {@link ServerContext} is closed.
     * @param service Service invoked for every request received by this server.  The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    default Single<ServerContext> start(SocketAddress address, ContextFilter contextFilter, HttpService service) {
        return start(address, contextFilter, newCachedThreadExecutor(), service);
    }

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
    Single<ServerContext> start(SocketAddress address, ContextFilter contextFilter, Executor executor,
                                HttpService service);

    /**
     * Starts this server and returns a {@link Single} that completes when the server is successfully started or
     * terminates with an error if the server could not be started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param port Listen port for the server.
     * @param service Service invoked for every request received by this server.  The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    default Single<ServerContext> start(int port, HttpService service) {
        return start(port, ACCEPT_ALL, newCachedThreadExecutor(), service);
    }

    /**
     * Starts this server and returns a {@link Single} that completes when the server is successfully started or
     * terminates with an error if the server could not be started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param port Listen port for the server.
     * @param executor The {@link Executor} for invoking {@code service}. The returned {@link ServerContext} manages the
     * lifecycle of the {@code executor}, ensuring it is closed when the {@link ServerContext} is closed. Note: This
     * may change as this API is refined.
     * @param service Service invoked for every request received by this server.  The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    default Single<ServerContext> start(int port, Executor executor, HttpService service) {
        return start(port, ACCEPT_ALL, executor, service);
    }

    /**
     * Starts this server and returns a {@link Single} that completes when the server is successfully started or
     * terminates with an error if the server could not be started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param port Listen port for the server.
     * @param contextFilter to use for filtering accepted connections.  The returned {@link ServerContext} manages the
     * lifecycle of the {@code contextFilter}, ensuring it is closed when the {@link ServerContext} is closed.
     * @param service Service invoked for every request received by this server.  The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    default Single<ServerContext> start(int port, ContextFilter contextFilter, HttpService service) {
        return start(new InetSocketAddress(port), contextFilter, newCachedThreadExecutor(), service);
    }

    /**
     * Starts this server and returns a {@link Single} that completes when the server is successfully started or
     * terminates with an error if the server could not be started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param port Listen port for the server.
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
    default Single<ServerContext> start(int port, ContextFilter contextFilter, Executor executor, HttpService service) {
        return start(new InetSocketAddress(port), contextFilter, executor, service);
    }

    /**
     * Starts this server and returns a {@link Single} that completes when the server is successfully started or
     * terminates with an error if the server could not be started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param address Listen address for the server.
     * @param service Service invoked for every request received by this server.  The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    default Single<ServerContext> start(SocketAddress address, AggregatedHttpService service) {
        return start(address, ACCEPT_ALL, newCachedThreadExecutor(), service);
    }

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
    default Single<ServerContext> start(SocketAddress address, Executor executor, AggregatedHttpService service) {
        return start(address, ACCEPT_ALL, executor, service);
    }

    /**
     * Starts this server and returns a {@link Single} that completes when the server is successfully started or
     * terminates with an error if the server could not be started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param address Listen address for the server.
     * @param contextFilter to use for filtering accepted connections.  The returned {@link ServerContext} manages the
     * lifecycle of the {@code contextFilter}, ensuring it is closed when the {@link ServerContext} is closed.
     * @param service Service invoked for every request received by this server.  The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    default Single<ServerContext> start(SocketAddress address, ContextFilter contextFilter,
                                        AggregatedHttpService service) {
        return start(address, contextFilter, newCachedThreadExecutor(), service);
    }

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
    default Single<ServerContext> start(SocketAddress address, ContextFilter contextFilter, Executor executor,
                                        AggregatedHttpService service) {
        return start(address, contextFilter, executor, service.asService());
    }

    /**
     * Starts this server and returns a {@link Single} that completes when the server is successfully started or
     * terminates with an error if the server could not be started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param port Listen port for the server.
     * @param service Service invoked for every request received by this server.  The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    default Single<ServerContext> start(int port, AggregatedHttpService service) {
        return start(port, ACCEPT_ALL, newCachedThreadExecutor(), service);
    }

    /**
     * Starts this server and returns a {@link Single} that completes when the server is successfully started or
     * terminates with an error if the server could not be started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param port Listen port for the server.
     * @param executor The {@link Executor} for invoking {@code service}. The returned {@link ServerContext} manages the
     * lifecycle of the {@code executor}, ensuring it is closed when the {@link ServerContext} is closed. Note: This
     * may change as this API is refined.
     * @param service Service invoked for every request received by this server.  The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    default Single<ServerContext> start(int port, Executor executor, AggregatedHttpService service) {
        return start(port, ACCEPT_ALL, executor, service);
    }

    /**
     * Starts this server and returns a {@link Single} that completes when the server is successfully started or
     * terminates with an error if the server could not be started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param port Listen port for the server.
     * @param contextFilter to use for filtering accepted connections.  The returned {@link ServerContext} manages the
     * lifecycle of the {@code contextFilter}, ensuring it is closed when the {@link ServerContext} is closed.
     * @param service Service invoked for every request received by this server.  The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    default Single<ServerContext> start(int port, ContextFilter contextFilter, AggregatedHttpService service) {
        return start(new InetSocketAddress(port), contextFilter, newCachedThreadExecutor(), service);
    }

    /**
     * Starts this server and returns a {@link Single} that completes when the server is successfully started or
     * terminates with an error if the server could not be started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param port Listen port for the server.
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
    default Single<ServerContext> start(int port, ContextFilter contextFilter, Executor executor,
                                        AggregatedHttpService service) {
        return start(new InetSocketAddress(port), contextFilter, executor, service);
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param address Listen address for the server.
     * @param service Service invoked for every request received by this server.  The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link ServerContext} if the server starts successfully.
     * @throws Exception If the server could not be started.
     */
    default ServerContext start(SocketAddress address, BlockingHttpService service) throws Exception {
        return start(address, ACCEPT_ALL, newCachedThreadExecutor(), service);
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param address Listen address for the server.
     * @param executor The {@link Executor} for invoking {@code service}. The returned {@link ServerContext} manages the
     * lifecycle of the {@code executor}, ensuring it is closed when the {@link ServerContext} is closed. Note: This
     * may change as this API is refined.
     * @param service Service invoked for every request received by this server.  The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link ServerContext} if the server starts successfully.
     * @throws Exception If the server could not be started.
     */
    default ServerContext start(SocketAddress address, Executor executor, BlockingHttpService service)
            throws Exception {
        return start(address, ACCEPT_ALL, executor, service);
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param address Listen address for the server.
     * @param contextFilter to use for filtering accepted connections.  The returned {@link ServerContext} manages the
     * lifecycle of the {@code contextFilter}, ensuring it is closed when the {@link ServerContext} is closed.
     * @param service Service invoked for every request received by this server.  The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link ServerContext} if the server starts successfully.
     * @throws Exception If the server could not be started.
     */
    default ServerContext start(SocketAddress address, ContextFilter contextFilter, BlockingHttpService service)
            throws Exception {
        return start(address, contextFilter, newCachedThreadExecutor(), service);
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
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
     * @return A {@link ServerContext} if the server starts successfully.
     * @throws Exception If the server could not be started.
     */
    default ServerContext start(SocketAddress address, ContextFilter contextFilter, Executor executor,
                                BlockingHttpService service) throws Exception {
        return awaitIndefinitelyNonNull(start(address, contextFilter, executor, service.asAsynchronousService()));
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param port Listen port for the server.
     * @param service Service invoked for every request received by this server.  The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link ServerContext} if the server starts successfully.
     * @throws Exception If the server could not be started.
     */
    default ServerContext start(int port, BlockingHttpService service) throws Exception {
        return start(port, ACCEPT_ALL, newCachedThreadExecutor(), service);
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param port Listen port for the server.
     * @param executor The {@link Executor} for invoking {@code service}. The returned {@link ServerContext} manages the
     * lifecycle of the {@code executor}, ensuring it is closed when the {@link ServerContext} is closed. Note: This
     * may change as this API is refined.
     * @param service Service invoked for every request received by this server.  The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link ServerContext} if the server starts successfully.
     * @throws Exception If the server could not be started.
     */
    default ServerContext start(int port, Executor executor, BlockingHttpService service) throws Exception {
        return start(port, ACCEPT_ALL, executor, service);
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param port Listen port for the server.
     * @param contextFilter to use for filtering accepted connections.  The returned {@link ServerContext} manages the
     * lifecycle of the {@code contextFilter}, ensuring it is closed when the {@link ServerContext} is closed.
     * @param service Service invoked for every request received by this server.  The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link ServerContext} if the server starts successfully.
     * @throws Exception If the server could not be started.
     */
    default ServerContext start(int port, ContextFilter contextFilter, BlockingHttpService service) throws Exception {
        return start(new InetSocketAddress(port), contextFilter, newCachedThreadExecutor(), service);
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param port Listen port for the server.
     * @param contextFilter to use for filtering accepted connections.  The returned {@link ServerContext} manages the
     * lifecycle of the {@code contextFilter}, ensuring it is closed when the {@link ServerContext} is closed.
     * @param executor The {@link Executor} for invoking {@code service}. The returned {@link ServerContext} manages the
     * lifecycle of the {@code executor}, ensuring it is closed when the {@link ServerContext} is closed. Note: This
     * may change as this API is refined.
     * @param service Service invoked for every request received by this server.  The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link ServerContext} if the server starts successfully.
     * @throws Exception If the server could not be started.
     */
    default ServerContext start(int port, ContextFilter contextFilter, Executor executor, BlockingHttpService service)
            throws Exception {
        return start(new InetSocketAddress(port), contextFilter, executor, service);
    }
}
