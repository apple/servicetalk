/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.grpc.netty;

import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.grpc.api.GrpcExceptionMapperServiceFilter;
import io.servicetalk.grpc.api.GrpcLifecycleObserver;
import io.servicetalk.grpc.api.GrpcLifecycleObserver.GrpcExchangeObserver;
import io.servicetalk.grpc.api.GrpcServerBuilder;
import io.servicetalk.grpc.api.GrpcStatus;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.http.netty.HttpLifecycleObserverServiceFilter;
import io.servicetalk.http.utils.TimeoutHttpRequesterFilter;
import io.servicetalk.http.utils.auth.BasicAuthHttpServiceFilter;
import io.servicetalk.transport.api.IoExecutor;

import org.slf4j.MDC;

import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * An HTTP service filter that tracks events during gRPC request/response lifecycle.
 * <p>
 * The result of the observed behavior will depend on the position of this filter in the execution chain.
 * This filter is recommended to be appended as one of the first filters using
 * {@link HttpServerBuilder#appendNonOffloadingServiceFilter(StreamingHttpServiceFilterFactory)} method via
 * {@link GrpcServerBuilder#initializeHttp(GrpcServerBuilder.HttpInitializer)} to account for all work done by other
 * filters and offloading of the requests processing. It can be appended at another position, considering the following:
 * <ul>
 *     <li>After a tracing filter or any other filter that populates
 *     {@link HttpRequestMetaData#context() request context}, {@link AsyncContext}, {@link MDC}, or alters
 *     {@link HttpRequestMetaData} if that information has to be available for {@link GrpcExchangeObserver}.</li>
 *     <li>After {@link TimeoutHttpRequesterFilter} if the timeout event should be observed as
 *     {@link GrpcExchangeObserver#onResponseCancel() cancellation} instead of an
 *     {@link GrpcExchangeObserver#onResponseError(Throwable) error}.</li>
 *     <li>After any filter that can reject requests, like {@link BasicAuthHttpServiceFilter}, if only allowed requests
 *     should be observed.</li>
 *     <li>Before any filter that can reject requests, like {@link BasicAuthHttpServiceFilter}, if all incoming requests
 *     have to be observed.</li>
 *     <li>Before any filter that populates {@link HttpResponseMetaData#context() response context} or alters
 *     {@link HttpResponseMetaData} if that information has to be available for {@link GrpcExchangeObserver}.</li>
 *     <li>Before any filter that maps/translates {@link HttpResponseMetaData} into an {@link Throwable exception} or
 *     throws an exception during {@link StreamingHttpResponse#transformPayloadBody(UnaryOperator) response payload body
 *     transformation} if that exception has to be observed by {@link GrpcExchangeObserver}.</li>
 *     <li>Before any exception mapping filter that maps an {@link Throwable exception} into a valid response, like
 *     {@link GrpcExceptionMapperServiceFilter}, if the {@link GrpcExchangeObserver} should see what {@link GrpcStatus}
 *     is returned to the client.</li>
 *     <li>Using {@link HttpServerBuilder#appendServiceFilter(StreamingHttpServiceFilterFactory)} if an exchange
 *     should be observed after it's offloaded from an {@link IoExecutor} (if offloading is enabled).</li>
 *     <li>Using
 *     {@link HttpServerBuilder#appendNonOffloadingServiceFilter(Predicate, StreamingHttpServiceFilterFactory)} or
 *     {@link HttpServerBuilder#appendServiceFilter(Predicate, StreamingHttpServiceFilterFactory)} if the observer
 *     should be applied conditionally.</li>
 * </ul>
 * An alternative way to install an {@link GrpcLifecycleObserver} is to use
 * {@link GrpcServerBuilder#lifecycleObserver(GrpcLifecycleObserver)}.
 *
 * @see GrpcServerBuilder#lifecycleObserver(GrpcLifecycleObserver)
 */
public final class GrpcLifecycleObserverServiceFilter extends HttpLifecycleObserverServiceFilter {

    /**
     * Create a new instance.
     *
     * @param observer The {@link GrpcLifecycleObserver observer} implementation that consumes gRPC lifecycle events.
     */
    public GrpcLifecycleObserverServiceFilter(final GrpcLifecycleObserver observer) {
        super(new GrpcToHttpLifecycleObserverBridge(observer));
    }
}
