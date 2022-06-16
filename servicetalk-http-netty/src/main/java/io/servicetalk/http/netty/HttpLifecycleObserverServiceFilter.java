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
import io.servicetalk.http.api.HttpExceptionMapperServiceFilter;
import io.servicetalk.http.api.HttpLifecycleObserver;
import io.servicetalk.http.api.HttpLifecycleObserver.HttpExchangeObserver;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.http.utils.TimeoutHttpRequesterFilter;
import io.servicetalk.http.utils.auth.BasicAuthHttpServiceFilter;
import io.servicetalk.transport.api.IoExecutor;

import org.slf4j.MDC;

import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * An HTTP service filter that tracks events during request/response lifecycle.
 * <p>
 * The result of the observed behavior will depend on the position of this filter in the execution chain.
 * This filter is recommended to be appended as one of the first filters using
 * {@link HttpServerBuilder#appendNonOffloadingServiceFilter(StreamingHttpServiceFilterFactory)} method to account for
 * all work done by other filters and offloading of the requests processing. It can be appended at another position,
 * considering the following:
 * <ul>
 *     <li>After a tracing filter or any other filter that populates
 *     {@link HttpRequestMetaData#context() request context}, {@link AsyncContext}, {@link MDC}, or alters
 *     {@link HttpRequestMetaData} if that information has to be available for {@link HttpExchangeObserver}.</li>
 *     <li>After {@link TimeoutHttpRequesterFilter} if the timeout event should be observed as
 *     {@link HttpExchangeObserver#onResponseCancel() cancellation} instead of an
 *     {@link HttpExchangeObserver#onResponseError(Throwable) error}.</li>
 *     <li>After any filter that can reject requests, like {@link BasicAuthHttpServiceFilter}, if only allowed requests
 *     should be observed.</li>
 *     <li>Before any filter that can reject requests, like {@link BasicAuthHttpServiceFilter}, if all incoming requests
 *     have to be observed.</li>
 *     <li>Before any filter that populates {@link HttpResponseMetaData#context() response context} or alters
 *     {@link HttpResponseMetaData} if that information has to be available for {@link HttpExchangeObserver}.</li>
 *     <li>Before any filter that maps/translates {@link HttpResponseMetaData} into an {@link Throwable exception} or
 *     throws an exception during {@link StreamingHttpResponse#transformPayloadBody(UnaryOperator) response payload body
 *     transformation} if that exception has to be observed by {@link HttpExchangeObserver}.</li>
 *     <li>Before any exception mapping filter that maps an {@link Throwable exception} into a valid response, like
 *     {@link HttpExceptionMapperServiceFilter}, if the {@link HttpExchangeObserver} should see what status code is
 *     returned to the client.</li>
 *     <li>Using {@link HttpServerBuilder#appendServiceFilter(StreamingHttpServiceFilterFactory)} if an exchange
 *     should be observed after it's offloaded from an {@link IoExecutor} (if offloading is enabled).</li>
 *     <li>Using
 *     {@link HttpServerBuilder#appendNonOffloadingServiceFilter(Predicate, StreamingHttpServiceFilterFactory)} or
 *     {@link HttpServerBuilder#appendServiceFilter(Predicate, StreamingHttpServiceFilterFactory)} if the observer
 *     should be applied conditionally.</li>
 *     <li>As the last {@link HttpServerBuilder#appendServiceFilter(StreamingHttpServiceFilterFactory)} if only service
 *     business logic should be observed without accounting for work of any other filters.</li>
 * </ul>
 * An alternative way to install an {@link HttpLifecycleObserver} is to use
 * {@link HttpServerBuilder#lifecycleObserver(HttpLifecycleObserver)}.
 *
 * @see HttpServerBuilder#lifecycleObserver(HttpLifecycleObserver)
 */
public class HttpLifecycleObserverServiceFilter extends AbstractLifecycleObserverHttpFilter implements
            StreamingHttpServiceFilterFactory {

    /**
     * Create a new instance.
     *
     * @param observer The {@link HttpLifecycleObserver observer} implementation that consumes HTTP lifecycle events.
     */
    public HttpLifecycleObserverServiceFilter(final HttpLifecycleObserver observer) {
        super(observer, false);
    }

    @Override
    public final StreamingHttpServiceFilter create(final StreamingHttpService service) {
        return new StreamingHttpServiceFilter(service) {
            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        final StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {
                return trackLifecycle(ctx, request, r -> delegate().handle(ctx, r, responseFactory));
            }
        };
    }
}
