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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;

import static java.util.Objects.requireNonNull;

final class StreamingHttpServiceToHttpService extends HttpService {
    private final StreamingHttpService service;
    private final HttpExecutionStrategy effectiveStrategy;

    private StreamingHttpServiceToHttpService(final StreamingHttpService service,
                                              final HttpExecutionStrategy effectiveStrategy) {
        this.service = requireNonNull(service);
        this.effectiveStrategy = effectiveStrategy;
    }

    @Override
    public Single<HttpResponse> handle(final HttpServiceContext ctx, final HttpRequest request,
                                       final HttpResponseFactory responseFactory) {
        return service.handle(ctx, request.toStreamingRequest(), ctx.streamingResponseFactory())
                .flatMap(StreamingHttpResponse::toResponse);
    }

    @Override
    public Completable closeAsync() {
        return service.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return service.closeAsyncGracefully();
    }

    @Override
    StreamingHttpService asStreamingServiceInternal() {
        return service;
    }

    @Override
    public HttpExecutionStrategy executionStrategy() {
        return effectiveStrategy;
    }

    static HttpService transform(final StreamingHttpService service) {
        final HttpExecutionStrategy defaultStrategy = service instanceof StreamingHttpServiceFilter ?
                ((StreamingHttpServiceFilter) service)
                        .effectiveExecutionStrategy(DEFAULT_SERVICE_STRATEGY) : DEFAULT_SERVICE_STRATEGY;
        return new StreamingHttpServiceToHttpService(service, defaultStrategy);
    }
}
