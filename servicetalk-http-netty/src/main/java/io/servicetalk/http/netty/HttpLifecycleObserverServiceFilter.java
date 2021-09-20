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
import io.servicetalk.http.api.HttpLifecycleObserver;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;

/**
 * An HTTP service filter that tracks events during request/response lifecycle.
 * <p>
 * This filter have to be used after the last exception mapping being done by {@link NettyHttpServer}.
 */
final class HttpLifecycleObserverServiceFilter extends AbstractLifecycleObserverHttpFilter implements
            StreamingHttpServiceFilterFactory {

    /**
     * Create a new instance.
     *
     * @param observer The {@link HttpLifecycleObserver observer} implementation that consumes HTTP lifecycle events.
     */
    HttpLifecycleObserverServiceFilter(final HttpLifecycleObserver observer) {
        super(observer, false);
    }

    @Override
    public StreamingHttpServiceFilter create(final StreamingHttpService service) {
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
