/*
 * Copyright © 2021, 2025 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.grpc.api;

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpLifecycleObserver;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.transport.api.IoExecutor;

/**
 * An observer interface that provides visibility into gRPC lifecycle events.
 * <p>
 * To deliver events at accurate time, callbacks on this interface can be invoked from the {@link IoExecutor}.
 * Implementation of this observer <b>must</b> be non-blocking. If the
 * consumer of events may block (uses a blocking library or
 * <a href="https://logging.apache.org/log4j/2.x/manual/async.html">logger configuration is not async</a>), it must
 * offload publications to another {@link Executor} <b>after</b> capturing timing of events. If blocking code is
 * executed inside callbacks without offloading, it will negatively impact {@link IoExecutor}.
 * <p>
 * To install this observer for the server use
 * {@link GrpcServerBuilder#lifecycleObserver(GrpcLifecycleObserver) lifecycleObserver} method or
 * {@link HttpServerBuilder#appendNonOffloadingServiceFilter(StreamingHttpServiceFilterFactory)
 * appendNonOffloadingServiceFilter} with
 * {@code io.servicetalk.grpc.netty.GrpcLifecycleObserverServiceFilter} which can be configured using
 * {@link GrpcServerBuilder#initializeHttp(GrpcServerBuilder.HttpInitializer)}. For the client use either
 * {@link SingleAddressHttpClientBuilder#appendClientFilter(StreamingHttpClientFilterFactory) appendClientFilter} or
 * {@link SingleAddressHttpClientBuilder#appendConnectionFilter(StreamingHttpConnectionFilterFactory)
 * appendConnectionFilter} with {@code io.servicetalk.grpc.netty.GrpcLifecycleObserverRequesterFilter}
 * which can be configured using {@link GrpcClientBuilder#initializeHttp(GrpcClientBuilder.HttpInitializer)}.
 */
@FunctionalInterface
public interface GrpcLifecycleObserver extends HttpLifecycleObserver {

    /**
     * Callback when a new gRPC exchange starts.
     *
     * @return an {@link GrpcExchangeObserver} that provides visibility into exchange events
     */
    @Override
    GrpcExchangeObserver onNewExchange();

    /**
     * An observer interface that provides visibility into events associated with a single gRPC exchange.
     * <p>
     * An exchange is represented by a {@link GrpcRequestObserver request} and a {@link GrpcResponseObserver response}.
     * Both can be observed independently via their corresponding observers and may publish their events concurrently
     * with each other because connections are full-duplex. The {@link #onExchangeFinally() final event} for the
     * exchange is signaled only after both nested observers terminate. It guarantees visibility of both observers
     * internal states inside the final callback.
     */
    interface GrpcExchangeObserver extends HttpExchangeObserver {

        @Override
        default GrpcRequestObserver onRequest(HttpRequestMetaData requestMetaData) {
            return NoopGrpcLifecycleObservers.NOOP_GRPC_REQUEST_OBSERVER;
        }

        @Override
        default GrpcResponseObserver onResponse(HttpResponseMetaData responseMetaData) {
            return NoopGrpcLifecycleObservers.NOOP_GRPC_RESPONSE_OBSERVER;
        }
    }

    /**
     * An observer interface that provides visibility into events associated with a single gRPC request.
     * <p>
     * The request is considered complete when one of the terminal events is invoked. It is guaranteed that only one
     * terminal event will be invoked per request.
     */
    interface GrpcRequestObserver extends HttpRequestObserver {

        /**
         * {@inheritDoc}
         * <p>
         * <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md">gRPC over HTTP2</a> protocol does
         * not define trailers in the request. This method is not expected to be invoked. However, it might be useful if
         * the server listens to both gRPC and HTTP traffic or receives non-gRPC requests from untrusted peers.
         */
        @Override
        default void onRequestTrailers(HttpHeaders trailers) {
        }
    }

    /**
     * An observer interface that provides visibility into events associated with a single gRPC response.
     * <p>
     * The response is considered complete when one of the terminal events is invoked. It is guaranteed that only one
     * terminal event will be invoked per response.
     */
    interface GrpcResponseObserver extends HttpResponseObserver {

        /**
         * Callback when {@link GrpcStatus} was observed.
         *
         * @param status the corresponding {@link GrpcStatus}
         */
        default void onGrpcStatus(GrpcStatus status) {
        }
    }
}
