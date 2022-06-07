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
package io.servicetalk.http.router.predicate;

import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceToOffloadedStreamingHttpService;
import io.servicetalk.transport.api.IoThreadFactory;

import java.util.List;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadAll;
import static java.util.Objects.requireNonNull;

/**
 * A {@link StreamingHttpService} implementation which routes requests to a number of other
 * {@link StreamingHttpService}s based on predicates.
 * <p>
 * The predicates from the specified {@link Route}s are evaluated in order, and the service from the
 * first one which returns {@code true} is used to handle the request. If no predicates match, the fallback service
 * specified is used.
 */
final class InOrderRouter implements StreamingHttpService {

    private final StreamingHttpService fallbackService;
    private final Route[] routes;
    private final AsyncCloseable closeable;

    /**
     * Constructs a router service with the specified fallback service, and predicate-service pairs to evaluate.
     * @param fallbackService the service to use to handle requests if no predicates match.
     * @param routes the list of predicate-service pairs to use for handling requests.
     */
    InOrderRouter(final StreamingHttpService fallbackService, final List<Route> routes) {
        this.fallbackService = requireNonNull(fallbackService);
        this.routes = routes.toArray(new Route[0]);
        this.closeable = newCompositeCloseable()
                .mergeAll(fallbackService)
                .mergeAll(routes.stream().map(Route::service).toArray(StreamingHttpService[]::new));
    }

    @Override
    public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                final StreamingHttpRequest request,
                                                final StreamingHttpResponseFactory factory) {
        for (final Route pair : routes) {
            if (pair.predicate().test(ctx, request)) {
                StreamingHttpService service = pair.service();
                final HttpExecutionStrategy strategy = pair.routeStrategy();
                HttpExecutionContext useContext = ctx.executionContext();
                if (null != strategy && useContext.executionStrategy().missing(strategy).hasOffloads()) {
                    // Additional offloading needed
                    service = StreamingHttpServiceToOffloadedStreamingHttpService.offloadService(strategy,
                            useContext.executor(), IoThreadFactory.IoThread::currentThreadIsIoThread, service);
                }
                return service.handle(ctx, request, factory);
            }
        }
        return fallbackService.handle(ctx, request, factory);
    }

    /**
     * {@inheritDoc}
     * @return {@link HttpExecutionStrategies#offloadAll()} as default safe behavior for predicates and routes. Apps
     * will typically use {@link HttpExecutionStrategies#offloadNone()} as
     * {@link io.servicetalk.http.api.HttpServerBuilder#executionStrategy(HttpExecutionStrategy)} to override if either
     * no offloading is required or diverse strategies are needed for various routes.
     */
    @Override
    public HttpExecutionStrategy requiredOffloads() {
        return offloadAll();
    }

    @Override
    public Completable closeAsync() {
        return closeable.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return closeable.closeAsyncGracefully();
    }
}
