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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpExceptionMapperServiceFilter;
import io.servicetalk.http.api.HttpLifecycleObserver;
import io.servicetalk.http.api.HttpLifecycleObserver.HttpResponseObserver;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.http.utils.TimeoutHttpServiceFilter;

/**
 * An HTTP service filter that tracks events during request/response lifecycle.
 * <p>
 * When this filter is used the result of the observed behavior will depend on the position of the filter in the
 * execution chain. Moving it before or after other filters, such as {@link TimeoutHttpServiceFilter}, may result in
 * different {@link HttpLifecycleObserver} callbacks being triggered
 * (seeing {@link HttpResponseObserver#onResponseCancel()} vs {@link  HttpResponseObserver#onResponseError(Throwable)}).
 * If any of the prior filters short circuit the request processing or modify {@link HttpResponseMetaData}, those won't
 * be observed. Presence of the tracing or MDC information also depends on position of this filter compare to filters
 * that populate context.
 * <p>
 * If observing the real {@link HttpResponseStatus} returned from the server is desired, consider using
 * {@link HttpServerBuilder#lifecycleObserver(HttpLifecycleObserver)} instead or place
 * {@link HttpExceptionMapperServiceFilter} right after this filter to make sure all {@link Throwable}(s) are mapped
 * into an HTTP response.
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
