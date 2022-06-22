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
package io.servicetalk.http.netty;

import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableReservedStreamingHttpConnection;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpLifecycleObserver;
import io.servicetalk.http.api.HttpLifecycleObserver.HttpExchangeObserver;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.ReservedStreamingHttpConnectionFilter;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.utils.RedirectingHttpRequesterFilter;
import io.servicetalk.http.utils.TimeoutHttpRequesterFilter;

import org.slf4j.MDC;

import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * An HTTP requester filter that tracks events during request/response lifecycle.
 * <p>
 * The result of the observed behavior will depend on the position of this filter in the execution chain.
 * This filter is recommended to be appended as the <b>first</b>
 * {@link SingleAddressHttpClientBuilder#appendClientFilter(StreamingHttpClientFilterFactory) client filter} on the
 * builder to account for all work done by other filters. It can be appended at another position, considering the
 * following:
 * <ul>
 *     <li>After a tracing filter or any other filter that populates
 *     {@link HttpRequestMetaData#context() request context}, {@link AsyncContext}, {@link MDC}, or alters
 *     {@link HttpRequestMetaData} if that information has to be available for {@link HttpExchangeObserver}.</li>
 *     <li>After {@link RedirectingHttpRequesterFilter} if each redirect should be observed as an independent
 *     {@link HttpExchangeObserver exchange}.</li>
 *     <li>After {@link RetryingHttpRequesterFilter} if each retry attempt should be observed as an independent
 *     {@link HttpExchangeObserver exchange}.</li>
 *     <li>As a {@link SingleAddressHttpClientBuilder#appendConnectionFilter(StreamingHttpConnectionFilterFactory)
 *     connection filter} if no user-defined {@link RetryingHttpRequesterFilter} is appended manually but the default
 *     auto-retries should be observed as an independent {@link HttpExchangeObserver exchange}.</li>
 *     <li>As the last
 *     {@link SingleAddressHttpClientBuilder#appendConnectionFilter(StreamingHttpConnectionFilterFactory) connection
 *     filter} if only network interactions should be observed without accounting for work of any other filters.</li>
 *     <li>After {@link TimeoutHttpRequesterFilter} if the timeout event should be observed as
 *     {@link HttpExchangeObserver#onResponseCancel() cancellation} instead of an
 *     {@link HttpExchangeObserver#onResponseError(Throwable) error}.</li>
 *     <li>Before any filter that populates {@link HttpResponseMetaData#context() response context} or alters
 *     {@link HttpResponseMetaData} if that information has to be available for {@link HttpExchangeObserver}.</li>
 *     <li>Before any filter that maps/translates {@link HttpResponseMetaData} into an {@link Throwable exception} or
 *     throws an exception during {@link StreamingHttpResponse#transformPayloadBody(UnaryOperator) response payload body
 *     transformation} if that exception has to be observed by {@link HttpExchangeObserver}.</li>
 *     <li>Using
 *     {@link SingleAddressHttpClientBuilder#appendClientFilter(Predicate, StreamingHttpClientFilterFactory)} or
 *     {@link SingleAddressHttpClientBuilder#appendConnectionFilter(Predicate, StreamingHttpConnectionFilterFactory)} if
 *     the observer should be applied conditionally.</li>
 * </ul>
 */
public class HttpLifecycleObserverRequesterFilter extends AbstractLifecycleObserverHttpFilter implements
        StreamingHttpClientFilterFactory, StreamingHttpConnectionFilterFactory {

    /**
     * Create a new instance.
     *
     * @param observer The {@link HttpLifecycleObserver observer} implementation that consumes HTTP lifecycle events.
     */
    public HttpLifecycleObserverRequesterFilter(final HttpLifecycleObserver observer) {
        super(observer, true);
    }

    @Override
    public final StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
        return new StreamingHttpClientFilter(client) {

            @Override
            protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                            final StreamingHttpRequest request) {
                return trackLifecycle(null, request, delegate::request);
            }

            @Override
            public Single<? extends FilterableReservedStreamingHttpConnection> reserveConnection(
                    final HttpRequestMetaData metaData) {
                return delegate().reserveConnection(metaData)
                        .map(r -> new ReservedStreamingHttpConnectionFilter(r) {
                            @Override
                            public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
                                return trackLifecycle(connectionContext(), request, r -> delegate().request(r));
                            }
                        });
            }
        };
    }

    @Override
    public final StreamingHttpConnectionFilter create(final FilterableStreamingHttpConnection connection) {
        return new StreamingHttpConnectionFilter(connection) {

            @Override
            public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
                return trackLifecycle(connectionContext(), request, r -> delegate().request(r));
            }
        };
    }
}
