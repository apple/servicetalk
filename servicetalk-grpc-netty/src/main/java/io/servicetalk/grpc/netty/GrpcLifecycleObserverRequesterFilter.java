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
package io.servicetalk.grpc.netty;

import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.grpc.api.GrpcClientBuilder;
import io.servicetalk.grpc.api.GrpcLifecycleObserver;
import io.servicetalk.grpc.api.GrpcLifecycleObserver.GrpcExchangeObserver;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.netty.HttpLifecycleObserverRequesterFilter;
import io.servicetalk.http.netty.RetryingHttpRequesterFilter;
import io.servicetalk.http.utils.TimeoutHttpRequesterFilter;

import org.slf4j.MDC;

import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * An HTTP requester filter that tracks events during gRPC request/response lifecycle.
 * <p>
 * The result of the observed behavior will depend on the position of this filter in the execution chain.
 * This filter is recommended to be appended as the <b>first</b>
 * {@link SingleAddressHttpClientBuilder#appendClientFilter(StreamingHttpClientFilterFactory) client filter} using
 * {@link GrpcClientBuilder#initializeHttp(GrpcClientBuilder.HttpInitializer)} to account for all work done by other
 * filters. It can be appended at another position, considering the following:
 * <ul>
 *     <li>After a tracing filter or any other filter that populates
 *     {@link HttpRequestMetaData#context() request context}, {@link AsyncContext}, {@link MDC}, or alters
 *     {@link HttpRequestMetaData} if that information has to be available for {@link GrpcExchangeObserver}.</li>
 *     <li>After {@link RetryingHttpRequesterFilter} if each retry attempt should be observed as an independent
 *     {@link GrpcExchangeObserver exchange}.</li>
 *     <li>As a {@link SingleAddressHttpClientBuilder#appendConnectionFilter(StreamingHttpConnectionFilterFactory)
 *     connection filter} if no user-defined {@link RetryingHttpRequesterFilter} is appended manually but the default
 *     auto-retries should be observed as an independent {@link GrpcExchangeObserver exchange}.</li>
 *     <li>After {@link TimeoutHttpRequesterFilter} if the timeout event should be observed as
 *     {@link GrpcExchangeObserver#onResponseCancel() cancellation} instead of an
 *     {@link GrpcExchangeObserver#onResponseError(Throwable) error}.</li>
 *     <li>Before any filter that populates {@link HttpResponseMetaData#context() response context} or alters
 *     {@link HttpResponseMetaData} if that information has to be available for {@link GrpcExchangeObserver}.</li>
 *     <li>Before any filter that maps/translates {@link HttpResponseMetaData} into an {@link Throwable exception} or
 *     throws an exception during {@link StreamingHttpResponse#transformPayloadBody(UnaryOperator) response payload body
 *     transformation} if that exception has to be observed by {@link GrpcExchangeObserver}.</li>
 *     <li>Using
 *     {@link SingleAddressHttpClientBuilder#appendClientFilter(Predicate, StreamingHttpClientFilterFactory)} or
 *     {@link SingleAddressHttpClientBuilder#appendConnectionFilter(Predicate, StreamingHttpConnectionFilterFactory)} if
 *     the observer should be applied conditionally.</li>
 * </ul>
 */
public final class GrpcLifecycleObserverRequesterFilter extends HttpLifecycleObserverRequesterFilter {

    /**
     * Create a new instance.
     *
     * @param observer The {@link GrpcLifecycleObserver observer} implementation that consumes gRPC lifecycle events.
     */
    public GrpcLifecycleObserverRequesterFilter(final GrpcLifecycleObserver observer) {
        super(new GrpcToHttpLifecycleObserverBridge(observer));
    }
}
