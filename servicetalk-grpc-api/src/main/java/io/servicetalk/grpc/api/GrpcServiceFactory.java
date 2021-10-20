/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.grpc.api.GrpcRoutes.AllGrpcRoutes;
import io.servicetalk.http.api.BlockingHttpService;
import io.servicetalk.http.api.BlockingStreamingHttpService;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.ServerContext;

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A factory for binding a <a href="https://www.grpc.io">gRPC</a> service to a server using a {@link ServerBinder}.
 *
 * @param <Filter> Type for service filter
 * @param <Service> Type for service
 * @param <FilterFactory> Type for filter factory
 */
public abstract class GrpcServiceFactory<Filter extends Service, Service extends GrpcService,
        FilterFactory extends GrpcServiceFilterFactory<Filter, Service>> {

    private final GrpcRoutes<Service> routes;
    @Nullable
    private FilterFactory filterFactory;

    /**
     * Creates new instance.
     *
     * @param routes {@link GrpcRoutes} that will hold the routes for the constructed service.
     */
    protected GrpcServiceFactory(final GrpcRoutes<Service> routes) {
        this.routes = routes;
    }

    /**
     * Convert multiple {@link GrpcServiceFactory factories} into a single instance.
     * @param factories instanes of {@link GrpcServiceFactory} to merge.
     * @return An aggregate {@link GrpcServiceFactory}.
     */
    @SuppressWarnings("unchecked")
    public static GrpcServiceFactory<?, ?, ?> merge(final GrpcServiceFactory<?, ?, ?>... factories) {
        if (factories.length == 1) {
            return factories[0];
        }
        final GrpcRoutes<?>[] routes = new GrpcRoutes[factories.length];
        for (int i = 0; i < factories.length; i++) {
            final GrpcServiceFactory factory = factories[i];
            if (factory.filterFactory != null) {
                factory.applyFilterToRoutes(factory.filterFactory);
            }
            routes[i] = factory.routes;
        }
        return new MergedServiceFactory(routes);
    }

    /**
     * Use the passed {@link ServerBinder} to bind an appropriate
     * <a href="https://www.grpc.io">gRPC</a> service for the server.
     *
     * @param binder {@link ServerBinder} to bind <a href="https://www.grpc.io">gRPC</a> service to the server.
     * @param executionContext {@link ExecutionContext} to use for the service.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    public final Single<ServerContext> bind(final ServerBinder binder, final ExecutionContext executionContext) {
        if (filterFactory == null) {
            return routes.bind(binder, executionContext);
        }
        applyFilterToRoutes(filterFactory);
        return routes.bind(binder, executionContext);
    }

    /**
     * Appends the passed {@link FilterFactory} to this factory.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     filter1.append(filter2).append(filter3)
     * </pre>
     * making a request to a client wrapped by this filter chain the order of invocation of these filters will be:
     * <pre>
     *     filter1 ⇒ filter2 ⇒ filter3 ⇒ client
     * </pre>
     *
     * @param before the factory to apply before this factory is applied
     * @return {@code this}
     * @deprecated gRPC Service Filters will be removed in future release of ServiceTalk. We encourage the use of
     * {@link StreamingHttpServiceFilterFactory} and if the access to the decoded payload is necessary, then performing
     * that logic can be done in the particular {@link GrpcService service implementation}.
     * Please use
     * {@link io.servicetalk.http.api.HttpServerBuilder#appendServiceFilter(StreamingHttpServiceFilterFactory)}
     * upon the {@code builder} obtained using
     * {@link GrpcServerBuilder#initializeHttp(GrpcServerBuilder.HttpInitializer)} if HTTP filters are acceptable
     * in your use case.
     */
    @Deprecated
    public GrpcServiceFactory<Filter, Service, FilterFactory> appendServiceFilter(FilterFactory before) {
        requireNonNull(before);
        if (filterFactory == null) {
            filterFactory = before;
        } else {
            this.filterFactory = appendServiceFilterFactory(filterFactory, before);
        }
        return this;
    }

    /**
     * Appends the passed {@link FilterFactory} to this service factory.
     *
     * @param existing Existing {@link FilterFactory}.
     * @param append {@link FilterFactory} to append to {@code existing}.
     * @return a composed factory that first applies the {@code before} factory and then applies {@code existing}
     * factory
     * @deprecated gRPC Service Filters will be removed in future release of ServiceTalk. We encourage the use of
     * {@link StreamingHttpServiceFilterFactory} and if the access to the decoded payload is necessary, then performing
     * that logic can be done in the particular {@link GrpcService service implementation}.
     * Please use
     * {@link io.servicetalk.http.api.HttpServerBuilder#appendServiceFilter(StreamingHttpServiceFilterFactory)}
     * upon the {@code builder} obtained using
     * {@link GrpcServerBuilder#initializeHttp(GrpcServerBuilder.HttpInitializer)} if HTTP filters are acceptable
     * in your use case.
     */
    @Deprecated
    protected abstract FilterFactory appendServiceFilterFactory(FilterFactory existing, FilterFactory append);

    private void applyFilterToRoutes(final FilterFactory filterFactory) {
        // We will call the routes again to register the new filtered routes, so clear the existing routes and return
        // them in AllGrpcRoutes.
        final AllGrpcRoutes streamingRoutes = routes.drainToStreamingRoutes();
        final Service fromRoutes = routes.newServiceFromRoutes(streamingRoutes);
        final Filter filter = filterFactory.create(fromRoutes);
        routes.registerRoutes(filter);
    }

    /**
     * A utility to bind an HTTP service for <a href="https://www.grpc.io">gRPC</a> with an
     * appropriate programming model.
     */
    public interface ServerBinder {

        /**
         * Binds an {@link HttpService} to the associated server.
         * <p>
         * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
         *
         * @param service {@link HttpService} to bind.
         * @return A {@link Single} that completes when the server is successfully started or terminates with an error
         * if the server could not be started.
         */
        Single<ServerContext> bind(HttpService service);

        /**
         * Binds a {@link StreamingHttpService} to the associated server.
         * <p>
         * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
         *
         * @param service {@link StreamingHttpService} to bind.
         * @return A {@link Single} that completes when the server is successfully started or terminates with an error
         * if the server could not be started.
         */
        Single<ServerContext> bindStreaming(StreamingHttpService service);

        /**
         * Binds a {@link BlockingHttpService} to the associated server.
         * <p>
         * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
         *
         * @param service {@link BlockingHttpService} to bind.
         * @return A {@link Single} that completes when the server is successfully started or terminates with an error
         * if the server could not be started.
         */
        Single<ServerContext> bindBlocking(BlockingHttpService service);

        /**
         * Binds a {@link BlockingStreamingHttpService} to the associated server.
         * <p>
         * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
         *
         * @param service {@link BlockingStreamingHttpService} to bind.
         * @return A {@link Single} that completes when the server is successfully started or terminates with an error
         * if the server could not be started.
         */
        Single<ServerContext> bindBlockingStreaming(BlockingStreamingHttpService service);
    }

    private static final class MergedServiceFactory extends GrpcServiceFactory {

        @SuppressWarnings("unchecked")
        MergedServiceFactory(final GrpcRoutes... routes) {
            super(GrpcRoutes.merge(routes));
        }

        @Override
        protected GrpcServiceFilterFactory appendServiceFilterFactory(final GrpcServiceFilterFactory existing,
                                                                      final GrpcServiceFilterFactory append) {
            throw new UnsupportedOperationException("Merged service factory can not register routes.");
        }
    }
}
