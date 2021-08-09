/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.GracefulAutoCloseable;
import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.encoding.api.BufferDecoderGroup;
import io.servicetalk.encoding.api.BufferEncoder;
import io.servicetalk.grpc.api.GrpcRouter.RouteProviders;
import io.servicetalk.grpc.api.GrpcServiceFactory.ServerBinder;
import io.servicetalk.grpc.api.GrpcUtils.DefaultMethodDescriptor;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.router.api.NoOffloadsRouteExecutionStrategy;
import io.servicetalk.router.api.RouteExecutionStrategy;
import io.servicetalk.router.api.RouteExecutionStrategyFactory;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.ServerContext;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.grpc.api.GrpcExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.grpc.api.GrpcUtils.GRPC_PROTO_CONTENT_TYPE;
import static io.servicetalk.grpc.api.GrpcUtils.compressors;
import static io.servicetalk.grpc.api.GrpcUtils.decompressors;
import static io.servicetalk.grpc.api.GrpcUtils.defaultToInt;
import static io.servicetalk.grpc.api.GrpcUtils.serializerDeserializer;
import static io.servicetalk.router.utils.internal.DefaultRouteExecutionStrategyFactory.defaultStrategyFactory;
import static io.servicetalk.router.utils.internal.RouteExecutionStrategyUtils.getAndValidateRouteExecutionStrategyAnnotationIfPresent;
import static io.servicetalk.utils.internal.ReflectionUtils.retrieveMethod;

/**
 * A holder of <a href="https://www.grpc.io">gRPC</a> routes that constitutes a service.
 *
 * @param <Service> Type for service that these routes represent.
 */
public abstract class GrpcRoutes<Service extends GrpcService> {
    private static final GrpcExecutionStrategy NULL = new DefaultGrpcExecutionStrategy(
            HttpExecutionStrategies.noOffloadsStrategy());

    private final GrpcRouter.Builder routeBuilder;
    private final Set<String> errors;
    private final RouteExecutionStrategyFactory<GrpcExecutionStrategy> strategyFactory;

    /**
     * Create a new instance.
     */
    protected GrpcRoutes() {
        this(defaultStrategyFactory());
    }

    /**
     * Create new instance.
     *
     * @param strategyFactory a
     * {@link RouteExecutionStrategyFactory RouteExecutionStrategyFactory&lt;GrpcExecutionStrategy&gt;} for creating
     * {@link GrpcExecutionStrategy} instances that can be used for offloading the handling of request to resource
     * methods, as specified via {@link RouteExecutionStrategy} annotation
     */
    protected GrpcRoutes(final RouteExecutionStrategyFactory<GrpcExecutionStrategy> strategyFactory) {
        routeBuilder = new GrpcRouter.Builder();
        errors = new TreeSet<>();
        this.strategyFactory = strategyFactory;
    }

    private GrpcRoutes(final GrpcRouter.Builder routeBuilder, final Set<String> errors) {
        this.routeBuilder = routeBuilder;
        this.errors = errors;
        strategyFactory = defaultStrategyFactory();
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
    final Single<ServerContext> bind(final ServerBinder binder, final ExecutionContext executionContext) {
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Invalid execution strategy configuration found:\n" + errors);
        }
        return routeBuilder.build().bind(binder, executionContext);
    }

    /**
     * Register all routes contained in the passed {@link Service}.
     *
     * @param service {@link Service} for which routes have to be registered.
     */
    protected abstract void registerRoutes(Service service);

    /**
     * Create a new {@link Service} from the passed {@link AllGrpcRoutes}.
     *
     * @param routes {@link AllGrpcRoutes} for which a {@link Service} has to be created.
     * @return {@link Service} containing all the passed routes.
     */
    protected abstract Service newServiceFromRoutes(AllGrpcRoutes routes);

    /**
     * Returns a {@link AllGrpcRoutes} representing this {@link GrpcRoutes}. Any route registered that is not a
     * {@link StreamingRoute} will be converted to a {@link StreamingRoute}.
     * @return {@link AllGrpcRoutes} representing this {@link GrpcRoutes}.
     */
    AllGrpcRoutes drainToStreamingRoutes() {
        final RouteProviders routeProviders = routeBuilder.drainRoutes();
        return new AllGrpcRoutes() {
            @Override
            public <Req, Resp> StreamingRoute<Req, Resp> streamingRouteFor(
                    final String path) throws IllegalArgumentException {
                return routeProviders.routeProvider(path).asStreamingRoute();
            }

            @Override
            public <Req, Resp> Route<Req, Resp> routeFor(final String path)
                    throws IllegalArgumentException {
                return routeProviders.routeProvider(path).asRoute();
            }

            @Override
            public <Req, Resp> RequestStreamingRoute<Req, Resp>
            requestStreamingRouteFor(final String path) throws IllegalArgumentException {
                return routeProviders.routeProvider(path).asRequestStreamingRoute();
            }

            @Override
            public <Req, Resp> ResponseStreamingRoute<Req, Resp>
            responseStreamingRouteFor(final String path) throws IllegalArgumentException {
                return routeProviders.routeProvider(path).asResponseStreamingRoute();
            }

            @Override
            public Completable closeAsync() {
                return routeProviders.closeAsync();
            }

            @Override
            public Completable closeAsyncGracefully() {
                return routeProviders.closeAsyncGracefully();
            }
        };
    }

    static GrpcRoutes<?> merge(GrpcRoutes<?>... allRoutes) {
        final GrpcRouter.Builder[] builders = new GrpcRouter.Builder[allRoutes.length];
        final Set<String> errors = new TreeSet<>();
        for (int i = 0; i < allRoutes.length; i++) {
            builders[i] = allRoutes[i].routeBuilder;
            errors.addAll(allRoutes[i].errors);
        }
        return new GrpcRoutes<GrpcService>(GrpcRouter.Builder.merge(builders), errors) {
            @Override
            protected void registerRoutes(final GrpcService service) {
                throw new UnsupportedOperationException("Merged service factory can not register routes.");
            }

            @Override
            protected GrpcService newServiceFromRoutes(final AllGrpcRoutes routes) {
                throw new UnsupportedOperationException("Merged service factory can not create new service.");
            }
        };
    }

    @Nullable
    private GrpcExecutionStrategy executionStrategy(final String path, final Method method, final Class<?> clazz) {
        // Check if we already have a computed GrpcExecutionStrategy for this path. This happens when we re-register
        // filtered routes and have to use the original execution strategy for the route instead of analysing
        // annotations on a service-filter class. Because previously registered strategy could be null (if user did not
        // configure it using ServiceFactory.Builder methods or via @RouteExecutionStrategy annotation), we use NULL
        // object as a marker to understand there was no strategy for this path.
        final GrpcExecutionStrategy saved = routeBuilder.executionStrategyFor(path, NULL);
        if (saved != NULL) {
            return saved;
        }
        return getAndValidateRouteExecutionStrategyAnnotationIfPresent(method, clazz, strategyFactory, errors,
                noOffloadsStrategy());
    }

    /**
     * Adds a {@link Route} to this factory.
     * @deprecated Use {@link #addRoute(Class, MethodDescriptor, BufferDecoderGroup, List, Route)}.
     * @param path for this route.
     * @param serviceClass {@link Class} of the gRPC service.
     * @param methodName the name of gRPC method.
     * @param route {@link Route} to add.
     * @param requestClass {@link Class} for the request object.
     * @param responseClass {@link Class} for the response object.
     * @param serializationProvider {@link GrpcSerializationProvider} for the route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    @Deprecated
    protected final <Req, Resp> void addRoute(
            final String path, final Class<?> serviceClass, final String methodName, final Route<Req, Resp> route,
            final Class<Req> requestClass, final Class<Resp> responseClass,
            final GrpcSerializationProvider serializationProvider) {
        addRoute(serviceClass, new DefaultMethodDescriptor<>(path, methodName,
                        false, false, requestClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, requestClass), defaultToInt(),
                        false, true, responseClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, responseClass), defaultToInt()),
                decompressors(serializationProvider.supportedMessageCodings()),
                compressors(serializationProvider.supportedMessageCodings()), route);
    }

    /**
     * Adds a {@link Route} to this factory.
     * @param serviceClass {@link Class} of the gRPC service which can be used to extract annotations to override
     * offloading behavior (e.g. {@link NoOffloadsRouteExecutionStrategy}, {@link RouteExecutionStrategy}).
     * @param methodDescriptor Describes the method routing and serialization.
     * @param decompressors Indicates the supported decompression applied to each request.
     * @param compressors Indicates the supported compression can be applied to each response.
     * @param route The interface to invoke when data is received for this route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    protected final <Req, Resp> void addRoute(
            Class<?> serviceClass, MethodDescriptor<Req, Resp> methodDescriptor,
            BufferDecoderGroup decompressors, List<BufferEncoder> compressors, Route<Req, Resp> route) {
        final Method method = retrieveMethod(serviceClass, methodDescriptor.javaMethodName(), GrpcServiceContext.class,
                methodDescriptor.requestDescriptor().parameterClass());
        routeBuilder.addRoute(methodDescriptor, decompressors, compressors,
                executionStrategy(methodDescriptor.httpPath(), method, serviceClass), route);
    }

    /**
     * Adds a {@link Route} to this factory.
     * @deprecated Use {@link #addRoute(GrpcExecutionStrategy, MethodDescriptor, BufferDecoderGroup, List, Route)}.
     * @param path for this route.
     * @param executionStrategy {@link GrpcExecutionStrategy} to use.
     * @param route {@link Route} to add.
     * @param requestClass {@link Class} for the request object.
     * @param responseClass {@link Class} for the response object.
     * @param serializationProvider {@link GrpcSerializationProvider} for the route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    @Deprecated
    protected final <Req, Resp> void addRoute(
            final String path, final GrpcExecutionStrategy executionStrategy, final Route<Req, Resp> route,
            final Class<Req> requestClass, final Class<Resp> responseClass,
            final GrpcSerializationProvider serializationProvider) {
        addRoute(executionStrategy, new DefaultMethodDescriptor<>(path,
                        false, false, requestClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, requestClass), defaultToInt(),
                        false, true, responseClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, responseClass), defaultToInt()),
                decompressors(serializationProvider.supportedMessageCodings()),
                compressors(serializationProvider.supportedMessageCodings()), route);
    }

    /**
     * Adds a {@link Route} to this factory.
     * @param executionStrategy The execution strategy to use for this route.
     * @param methodDescriptor Describes the method routing and serialization.
     * @param decompressors Indicates the supported decompression applied to each request.
     * @param compressors Indicates the supported compression can be applied to each response.
     * @param route The interface to invoke when data is received for this route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    protected final <Req, Resp> void addRoute(
            final GrpcExecutionStrategy executionStrategy, MethodDescriptor<Req, Resp> methodDescriptor,
            BufferDecoderGroup decompressors, List<BufferEncoder> compressors, Route<Req, Resp> route) {
        routeBuilder.addRoute(methodDescriptor, decompressors, compressors, executionStrategy, route);
    }

    /**
     * Adds a {@link StreamingRoute} to this factory.
     * @deprecated Use {@link #addStreamingRoute(Class, MethodDescriptor, BufferDecoderGroup, List, StreamingRoute)}.
     * @param path for this route.
     * @param serviceClass {@link Class} of the gRPC service.
     * @param methodName the name of gRPC method.
     * @param route {@link StreamingRoute} to add.
     * @param requestClass {@link Class} for the request object.
     * @param responseClass {@link Class} for the response object.
     * @param serializationProvider {@link GrpcSerializationProvider} for the route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    @Deprecated
    protected final <Req, Resp> void addStreamingRoute(
            final String path, final Class<?> serviceClass, final String methodName,
            final StreamingRoute<Req, Resp> route, final Class<Req> requestClass, final Class<Resp> responseClass,
            final GrpcSerializationProvider serializationProvider) {
        addStreamingRoute(serviceClass, new DefaultMethodDescriptor<>(path, methodName,
                        true, true, requestClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, requestClass), defaultToInt(),
                        true, true, responseClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, responseClass), defaultToInt()),
                decompressors(serializationProvider.supportedMessageCodings()),
                compressors(serializationProvider.supportedMessageCodings()), route);
    }

    /**
     * Adds a {@link StreamingRoute} to this factory.
     * @param serviceClass {@link Class} of the gRPC service which can be used to extract annotations to override
     * offloading behavior (e.g. {@link NoOffloadsRouteExecutionStrategy}, {@link RouteExecutionStrategy}).
     * @param methodDescriptor Describes the method routing and serialization.
     * @param decompressors Indicates the supported decompression applied to each request.
     * @param compressors Indicates the supported compression can be applied to responses.
     * @param route The interface to invoke when data is received for this route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    protected final <Req, Resp> void addStreamingRoute(
            Class<?> serviceClass, MethodDescriptor<Req, Resp> methodDescriptor,
            BufferDecoderGroup decompressors, List<BufferEncoder> compressors, StreamingRoute<Req, Resp> route) {
        final Method method = retrieveMethod(serviceClass, methodDescriptor.javaMethodName(), GrpcServiceContext.class,
                Publisher.class);
        routeBuilder.addStreamingRoute(methodDescriptor, decompressors, compressors,
                executionStrategy(methodDescriptor.httpPath(), method, serviceClass), route);
    }

    /**
     * Adds a {@link StreamingRoute} to this factory.
     * @deprecated Use {@link #addStreamingRoute(GrpcExecutionStrategy, MethodDescriptor, BufferDecoderGroup, List,
     * StreamingRoute)}
     * @param path for this route.
     * @param executionStrategy {@link GrpcExecutionStrategy} to use.
     * @param route {@link StreamingRoute} to add.
     * @param requestClass {@link Class} for the request object.
     * @param responseClass {@link Class} for the response object.
     * @param serializationProvider {@link GrpcSerializationProvider} for the route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    @Deprecated
    protected final <Req, Resp> void addStreamingRoute(
            final String path, final GrpcExecutionStrategy executionStrategy,
            final StreamingRoute<Req, Resp> route, final Class<Req> requestClass,
            final Class<Resp> responseClass, final GrpcSerializationProvider serializationProvider) {
        addStreamingRoute(executionStrategy, new DefaultMethodDescriptor<>(path,
                        true, true, requestClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, requestClass), defaultToInt(),
                        true, true, responseClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, responseClass), defaultToInt()),
                decompressors(serializationProvider.supportedMessageCodings()),
                compressors(serializationProvider.supportedMessageCodings()), route);
    }

    /**
     * Adds a {@link StreamingRoute} to this factory.
     * @param executionStrategy The execution strategy to use for this route.
     * @param methodDescriptor Describes the method routing and serialization.
     * @param decompressors Indicates the supported decompression applied to each request.
     * @param compressors Indicates the supported compression can be applied to responses.
     * @param route The interface to invoke when data is received for this route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    protected final <Req, Resp> void addStreamingRoute(
            final GrpcExecutionStrategy executionStrategy, MethodDescriptor<Req, Resp> methodDescriptor,
            BufferDecoderGroup decompressors, List<BufferEncoder> compressors, StreamingRoute<Req, Resp> route) {
        routeBuilder.addStreamingRoute(methodDescriptor, decompressors, compressors, executionStrategy, route);
    }

    /**
     * Adds a {@link RequestStreamingRoute} to this factory.
     * @deprecated Use
     * {@link #addRequestStreamingRoute(Class, MethodDescriptor, BufferDecoderGroup, List, RequestStreamingRoute)}.
     * @param path for this route.
     * @param serviceClass {@link Class} of the gRPC service.
     * @param methodName the name of gRPC method.
     * @param route {@link RequestStreamingRoute} to add.
     * @param requestClass {@link Class} for the request object.
     * @param responseClass {@link Class} for the response object.
     * @param serializationProvider {@link GrpcSerializationProvider} for the route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    @Deprecated
    protected final <Req, Resp> void addRequestStreamingRoute(
            final String path, final Class<?> serviceClass, final String methodName,
            final RequestStreamingRoute<Req, Resp> route, final Class<Req> requestClass,
            final Class<Resp> responseClass, final GrpcSerializationProvider serializationProvider) {
        addRequestStreamingRoute(serviceClass, new DefaultMethodDescriptor<>(path, methodName,
                        true, true, requestClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, requestClass), defaultToInt(),
                        false, true, responseClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, responseClass), defaultToInt()),
                decompressors(serializationProvider.supportedMessageCodings()),
                compressors(serializationProvider.supportedMessageCodings()), route);
    }

    /**
     * Adds a {@link RequestStreamingRoute} to this factory.
     * @param serviceClass {@link Class} of the gRPC service which can be used to extract annotations to override
     * offloading behavior (e.g. {@link NoOffloadsRouteExecutionStrategy}, {@link RouteExecutionStrategy}).
     * @param methodDescriptor Describes the method routing and serialization.
     * @param decompressors Indicates the supported decompression applied to each request.
     * @param compressors Indicates the supported compression can be applied to responses.
     * @param route The interface to invoke when data is received for this route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    protected final <Req, Resp> void addRequestStreamingRoute(
            Class<?> serviceClass, MethodDescriptor<Req, Resp> methodDescriptor,
            BufferDecoderGroup decompressors, List<BufferEncoder> compressors,
            RequestStreamingRoute<Req, Resp> route) {
        final Method method = retrieveMethod(serviceClass, methodDescriptor.javaMethodName(), GrpcServiceContext.class,
                Publisher.class);
        routeBuilder.addRequestStreamingRoute(methodDescriptor, decompressors, compressors,
                executionStrategy(methodDescriptor.httpPath(), method, serviceClass), route);
    }

    /**
     * Adds a {@link RequestStreamingRoute} to this factory.
     * @deprecated Use
     * {@link #addRequestStreamingRoute(GrpcExecutionStrategy, MethodDescriptor, BufferDecoderGroup, List,
     * RequestStreamingRoute)}.
     * @param path for this route.
     * @param executionStrategy {@link GrpcExecutionStrategy} to use.
     * @param route {@link RequestStreamingRoute} to add.
     * @param requestClass {@link Class} for the request object.
     * @param responseClass {@link Class} for the response object.
     * @param serializationProvider {@link GrpcSerializationProvider} for the route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    @Deprecated
    protected final <Req, Resp> void addRequestStreamingRoute(
            final String path, final GrpcExecutionStrategy executionStrategy,
            final RequestStreamingRoute<Req, Resp> route, final Class<Req> requestClass,
            final Class<Resp> responseClass, final GrpcSerializationProvider serializationProvider) {
        addRequestStreamingRoute(executionStrategy, new DefaultMethodDescriptor<>(path,
                        true, true, requestClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, requestClass), defaultToInt(),
                        false, true, responseClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, responseClass), defaultToInt()),
                decompressors(serializationProvider.supportedMessageCodings()),
                compressors(serializationProvider.supportedMessageCodings()), route);
    }

    /**
     * Adds a {@link RequestStreamingRoute} to this factory.
     * @param executionStrategy The execution strategy to use for this route.
     * @param methodDescriptor Describes the method routing and serialization.
     * @param decompressors Indicates the supported decompression applied to each request.
     * @param compressors Indicates the supported compression can be applied to responses.
     * @param route The interface to invoke when data is received for this route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    protected final <Req, Resp> void addRequestStreamingRoute(
            final GrpcExecutionStrategy executionStrategy, MethodDescriptor<Req, Resp> methodDescriptor,
            BufferDecoderGroup decompressors, List<BufferEncoder> compressors,
            RequestStreamingRoute<Req, Resp> route) {
        routeBuilder.addRequestStreamingRoute(methodDescriptor, decompressors, compressors, executionStrategy, route);
    }

    /**
     * Adds a {@link ResponseStreamingRoute} to this factory.
     * @deprecated Use
     * {@link #addResponseStreamingRoute(Class, MethodDescriptor, BufferDecoderGroup, List, ResponseStreamingRoute)}.
     * @param path for this route.
     * @param serviceClass {@link Class} of the gRPC service.
     * @param methodName the name of gRPC method.
     * @param route {@link ResponseStreamingRoute} to add.
     * @param requestClass {@link Class} for the request object.
     * @param responseClass {@link Class} for the response object.
     * @param serializationProvider {@link GrpcSerializationProvider} for the route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    @Deprecated
    protected final <Req, Resp> void addResponseStreamingRoute(
            final String path, final Class<?> serviceClass, final String methodName,
            final ResponseStreamingRoute<Req, Resp> route, final Class<Req> requestClass,
            final Class<Resp> responseClass, final GrpcSerializationProvider serializationProvider) {
        addResponseStreamingRoute(serviceClass, new DefaultMethodDescriptor<>(path, methodName,
                        false, false, requestClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, requestClass), defaultToInt(),
                        true, true, responseClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, responseClass), defaultToInt()),
                decompressors(serializationProvider.supportedMessageCodings()),
                compressors(serializationProvider.supportedMessageCodings()), route);
    }

    /**
     * Adds a {@link ResponseStreamingRoute} to this factory.
     * @param serviceClass {@link Class} of the gRPC service which can be used to extract annotations to override
     * offloading behavior (e.g. {@link NoOffloadsRouteExecutionStrategy}, {@link RouteExecutionStrategy}).
     * @param methodDescriptor Describes the method routing and serialization.
     * @param decompressors Indicates the supported decompression applied to each request.
     * @param compressors Indicates the supported compression can be applied to responses.
     * @param route The interface to invoke when data is received for this route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    protected final <Req, Resp> void addResponseStreamingRoute(
            Class<?> serviceClass, MethodDescriptor<Req, Resp> methodDescriptor,
            BufferDecoderGroup decompressors, List<BufferEncoder> compressors,
            ResponseStreamingRoute<Req, Resp> route) {
        final Method method = retrieveMethod(serviceClass, methodDescriptor.javaMethodName(), GrpcServiceContext.class,
                methodDescriptor.requestDescriptor().parameterClass());
        routeBuilder.addResponseStreamingRoute(methodDescriptor, decompressors, compressors,
                executionStrategy(methodDescriptor.httpPath(), method, serviceClass), route);
    }

    /**
     * Adds a {@link ResponseStreamingRoute} to this factory.
     * @deprecated Use
     * {@link #addResponseStreamingRoute(GrpcExecutionStrategy, MethodDescriptor, BufferDecoderGroup, List,
     * ResponseStreamingRoute)}.
     * @param path for this route.
     * @param executionStrategy {@link GrpcExecutionStrategy} to use.
     * @param route {@link ResponseStreamingRoute} to add.
     * @param requestClass {@link Class} for the request object.
     * @param responseClass {@link Class} for the response object.
     * @param serializationProvider {@link GrpcSerializationProvider} for the route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    @Deprecated
    protected final <Req, Resp> void addResponseStreamingRoute(
            final String path, final GrpcExecutionStrategy executionStrategy,
            final ResponseStreamingRoute<Req, Resp> route, final Class<Req> requestClass,
            final Class<Resp> responseClass, final GrpcSerializationProvider serializationProvider) {
        addResponseStreamingRoute(executionStrategy, new DefaultMethodDescriptor<>(path,
                        false, false, requestClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, requestClass), defaultToInt(),
                        true, true, responseClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, responseClass), defaultToInt()),
                decompressors(serializationProvider.supportedMessageCodings()),
                compressors(serializationProvider.supportedMessageCodings()), route);
    }

    /**
     * Adds a {@link ResponseStreamingRoute} to this factory.
     * @param executionStrategy The execution strategy to use for this route.
     * @param methodDescriptor Describes the method routing and serialization.
     * @param decompressors Indicates the supported decompression applied to each request.
     * @param compressors Indicates the supported compression can be applied to responses.
     * @param route The interface to invoke when data is received for this route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    protected final <Req, Resp> void addResponseStreamingRoute(
            final GrpcExecutionStrategy executionStrategy, MethodDescriptor<Req, Resp> methodDescriptor,
            BufferDecoderGroup decompressors, List<BufferEncoder> compressors,
            ResponseStreamingRoute<Req, Resp> route) {
        routeBuilder.addResponseStreamingRoute(methodDescriptor, decompressors, compressors, executionStrategy, route);
    }

    /**
     * Adds a {@link BlockingRoute} to this factory.
     * @deprecated Use {@link #addBlockingRoute(Class, MethodDescriptor, BufferDecoderGroup, List, BlockingRoute)}.
     * @param path for this route.
     * @param serviceClass {@link Class} of the gRPC service.
     * @param methodName the name of gRPC method.
     * @param route {@link BlockingRoute} to add.
     * @param requestClass {@link Class} for the request object.
     * @param responseClass {@link Class} for the response object.
     * @param serializationProvider {@link GrpcSerializationProvider} for the route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    @Deprecated
    protected final <Req, Resp> void addBlockingRoute(
            final String path, final Class<?> serviceClass, final String methodName,
            final BlockingRoute<Req, Resp> route, final Class<Req> requestClass, final Class<Resp> responseClass,
            final GrpcSerializationProvider serializationProvider) {
        addBlockingRoute(serviceClass, new DefaultMethodDescriptor<>(path, methodName,
                        false, false, requestClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, requestClass), defaultToInt(),
                        false, false, responseClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, responseClass), defaultToInt()),
                decompressors(serializationProvider.supportedMessageCodings()),
                compressors(serializationProvider.supportedMessageCodings()), route);
    }

    /**
     * Adds a {@link BlockingRoute} to this factory.
     * @param serviceClass {@link Class} of the gRPC service which can be used to extract annotations to override
     * offloading behavior (e.g. {@link NoOffloadsRouteExecutionStrategy}, {@link RouteExecutionStrategy}).
     * @param methodDescriptor Describes the method routing and serialization.
     * @param decompressors Indicates the supported decompression applied to each request.
     * @param compressors Indicates the supported compression can be applied to responses.
     * @param route The interface to invoke when data is received for this route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    protected final <Req, Resp> void addBlockingRoute(
            Class<?> serviceClass, MethodDescriptor<Req, Resp> methodDescriptor,
            BufferDecoderGroup decompressors, List<BufferEncoder> compressors,
            BlockingRoute<Req, Resp> route) {
        final Method method = retrieveMethod(serviceClass, methodDescriptor.javaMethodName(), GrpcServiceContext.class,
                methodDescriptor.requestDescriptor().parameterClass());
        routeBuilder.addBlockingRoute(methodDescriptor, decompressors, compressors,
                executionStrategy(methodDescriptor.httpPath(), method, serviceClass), route);
    }

    /**
     * Adds a {@link BlockingRoute} to this factory.
     * @deprecated Use {@link #addBlockingRoute(GrpcExecutionStrategy, MethodDescriptor, BufferDecoderGroup, List,
     * BlockingRoute)}.
     * @param path for this route.
     * @param executionStrategy {@link GrpcExecutionStrategy} to use.
     * @param route {@link BlockingRoute} to add.
     * @param requestClass {@link Class} for the request object.
     * @param responseClass {@link Class} for the response object.
     * @param serializationProvider {@link GrpcSerializationProvider} for the route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    @Deprecated
    protected final <Req, Resp> void addBlockingRoute(
            final String path, final GrpcExecutionStrategy executionStrategy, final BlockingRoute<Req, Resp> route,
            final Class<Req> requestClass, final Class<Resp> responseClass,
            final GrpcSerializationProvider serializationProvider) {
        addBlockingRoute(executionStrategy, new DefaultMethodDescriptor<>(path,
                        false, false, requestClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, requestClass), defaultToInt(),
                        false, false, responseClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, responseClass), defaultToInt()),
                decompressors(serializationProvider.supportedMessageCodings()),
                compressors(serializationProvider.supportedMessageCodings()), route);
    }

    /**
     * Adds a {@link BlockingRoute} to this factory.
     * @param executionStrategy The execution strategy to use for this route.
     * @param methodDescriptor Describes the method routing and serialization.
     * @param decompressors Indicates the supported decompression applied to each request.
     * @param compressors Indicates the supported compression can be applied to responses.
     * @param route The interface to invoke when data is received for this route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    protected final <Req, Resp> void addBlockingRoute(
            final GrpcExecutionStrategy executionStrategy, MethodDescriptor<Req, Resp> methodDescriptor,
            BufferDecoderGroup decompressors, List<BufferEncoder> compressors,
            BlockingRoute<Req, Resp> route) {
        routeBuilder.addBlockingRoute(methodDescriptor, decompressors, compressors, executionStrategy, route);
    }

    /**
     * Adds a {@link BlockingStreamingRoute} to this factory.
     * @deprecated Use
     * {@link #addBlockingStreamingRoute(Class, MethodDescriptor, BufferDecoderGroup, List, BlockingStreamingRoute)}.
     * @param path for this route.
     * @param serviceClass {@link Class} of the gRPC service.
     * @param methodName the name of gRPC method.
     * @param route {@link BlockingStreamingRoute} to add.
     * @param requestClass {@link Class} for the request object.
     * @param responseClass {@link Class} for the response object.
     * @param serializationProvider {@link GrpcSerializationProvider} for the route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    @Deprecated
    protected final <Req, Resp> void addBlockingStreamingRoute(
            final String path, final Class<?> serviceClass, final String methodName,
            final BlockingStreamingRoute<Req, Resp> route, final Class<Req> requestClass,
            final Class<Resp> responseClass, final GrpcSerializationProvider serializationProvider) {
        addBlockingStreamingRoute(serviceClass, new DefaultMethodDescriptor<>(path, methodName,
                        true, false, requestClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, requestClass), defaultToInt(),
                        true, false, responseClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, responseClass), defaultToInt()),
                decompressors(serializationProvider.supportedMessageCodings()),
                compressors(serializationProvider.supportedMessageCodings()), route);
    }

    /**
     * Adds a {@link BlockingStreamingRoute} to this factory.
     * @param serviceClass {@link Class} of the gRPC service which can be used to extract annotations to override
     * offloading behavior (e.g. {@link NoOffloadsRouteExecutionStrategy}, {@link RouteExecutionStrategy}).
     * @param methodDescriptor Describes the method routing and serialization.
     * @param decompressors Indicates the supported decompression applied to each request.
     * @param compressors Indicates the supported compression can be applied to responses.
     * @param route The interface to invoke when data is received for this route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    protected final <Req, Resp> void addBlockingStreamingRoute(
            Class<?> serviceClass, MethodDescriptor<Req, Resp> methodDescriptor,
            BufferDecoderGroup decompressors, List<BufferEncoder> compressors,
            BlockingStreamingRoute<Req, Resp> route) {
        final Method method = retrieveMethod(serviceClass, methodDescriptor.javaMethodName(), GrpcServiceContext.class,
                BlockingIterable.class, GrpcPayloadWriter.class);
        routeBuilder.addBlockingStreamingRoute(methodDescriptor, decompressors, compressors,
                executionStrategy(methodDescriptor.httpPath(), method, serviceClass), route);
    }

    /**
     * Adds a {@link BlockingStreamingRoute} to this factory.
     * @deprecated Use
     * {@link #addBlockingStreamingRoute(GrpcExecutionStrategy, MethodDescriptor, BufferDecoderGroup, List,
     * BlockingStreamingRoute)}.
     * @param path for this route.
     * @param executionStrategy {@link GrpcExecutionStrategy} to use.
     * @param route {@link BlockingStreamingRoute} to add.
     * @param requestClass {@link Class} for the request object.
     * @param responseClass {@link Class} for the response object.
     * @param serializationProvider {@link GrpcSerializationProvider} for the route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    @Deprecated
    protected final <Req, Resp> void addBlockingStreamingRoute(
            final String path, final GrpcExecutionStrategy executionStrategy,
            final BlockingStreamingRoute<Req, Resp> route, final Class<Req> requestClass,
            final Class<Resp> responseClass, final GrpcSerializationProvider serializationProvider) {
        addBlockingStreamingRoute(executionStrategy, new DefaultMethodDescriptor<>(path,
                        true, false, requestClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, requestClass), defaultToInt(),
                        true, false, responseClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, responseClass), defaultToInt()),
                decompressors(serializationProvider.supportedMessageCodings()),
                compressors(serializationProvider.supportedMessageCodings()), route);
    }

    /**
     * Adds a {@link BlockingStreamingRoute} to this factory.
     * @param executionStrategy The execution strategy to use for this route.
     * @param methodDescriptor Describes the method routing and serialization.
     * @param decompressors Indicates the supported decompression applied to each request.
     * @param compressors Indicates the supported compression can be applied to responses.
     * @param route The interface to invoke when data is received for this route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    protected final <Req, Resp> void addBlockingStreamingRoute(
            final GrpcExecutionStrategy executionStrategy, MethodDescriptor<Req, Resp> methodDescriptor,
            BufferDecoderGroup decompressors, List<BufferEncoder> compressors,
            BlockingStreamingRoute<Req, Resp> route) {
        routeBuilder.addBlockingStreamingRoute(methodDescriptor, decompressors, compressors, executionStrategy, route);
    }

    /**
     * Adds a {@link BlockingRequestStreamingRoute} to this factory.
     * @deprecated Use
     * {@link #addBlockingStreamingRoute(Class, MethodDescriptor, BufferDecoderGroup, List, BlockingStreamingRoute)}.
     * @param path for this route.
     * @param serviceClass {@link Class} of the gRPC service.
     * @param methodName the name of gRPC method.
     * @param route {@link BlockingRequestStreamingRoute} to add.
     * @param requestClass {@link Class} for the request object.
     * @param responseClass {@link Class} for the response object.
     * @param serializationProvider {@link GrpcSerializationProvider} for the route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    @Deprecated
    protected final <Req, Resp> void addBlockingRequestStreamingRoute(
            final String path, final Class<?> serviceClass, final String methodName,
            final BlockingRequestStreamingRoute<Req, Resp> route, final Class<Req> requestClass,
            final Class<Resp> responseClass, final GrpcSerializationProvider serializationProvider) {
        addBlockingRequestStreamingRoute(serviceClass, new DefaultMethodDescriptor<>(path, methodName,
                        true, false, requestClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, requestClass), defaultToInt(),
                        false, false, responseClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, responseClass), defaultToInt()),
                decompressors(serializationProvider.supportedMessageCodings()),
                compressors(serializationProvider.supportedMessageCodings()), route);
    }

    /**
     * Adds a {@link BlockingRequestStreamingRoute} to this factory.
     * @param serviceClass {@link Class} of the gRPC service which can be used to extract annotations to override
     * offloading behavior (e.g. {@link NoOffloadsRouteExecutionStrategy}, {@link RouteExecutionStrategy}).
     * @param methodDescriptor Describes the method routing and serialization.
     * @param decompressors Indicates the supported decompression applied to each request.
     * @param compressors Indicates the supported compression can be applied to responses.
     * @param route The interface to invoke when data is received for this route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    protected final <Req, Resp> void addBlockingRequestStreamingRoute(
            Class<?> serviceClass, MethodDescriptor<Req, Resp> methodDescriptor,
            BufferDecoderGroup decompressors, List<BufferEncoder> compressors,
            BlockingRequestStreamingRoute<Req, Resp> route) {
        final Method method = retrieveMethod(serviceClass, methodDescriptor.javaMethodName(), GrpcServiceContext.class,
                BlockingIterable.class);
        routeBuilder.addBlockingRequestStreamingRoute(methodDescriptor, decompressors, compressors,
                executionStrategy(methodDescriptor.httpPath(), method, serviceClass), route);
    }

    /**
     * Adds a {@link BlockingRequestStreamingRoute} to this factory.
     * @deprecated Use
     * {@link #addBlockingRequestStreamingRoute(GrpcExecutionStrategy, MethodDescriptor, BufferDecoderGroup, List,
     * BlockingRequestStreamingRoute)}.
     * @param path for this route.
     * @param executionStrategy {@link GrpcExecutionStrategy} to use.
     * @param route {@link BlockingRequestStreamingRoute} to add.
     * @param requestClass {@link Class} for the request object.
     * @param responseClass {@link Class} for the response object.
     * @param serializationProvider {@link GrpcSerializationProvider} for the route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    @Deprecated
    protected final <Req, Resp> void addBlockingRequestStreamingRoute(
            final String path, final GrpcExecutionStrategy executionStrategy,
            final BlockingRequestStreamingRoute<Req, Resp> route, final Class<Req> requestClass,
            final Class<Resp> responseClass, final GrpcSerializationProvider serializationProvider) {
        addBlockingRequestStreamingRoute(executionStrategy, new DefaultMethodDescriptor<>(path,
                        true, false, requestClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, requestClass), defaultToInt(),
                        false, false, responseClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, responseClass), defaultToInt()),
                decompressors(serializationProvider.supportedMessageCodings()),
                compressors(serializationProvider.supportedMessageCodings()), route);
    }

    /**
     * Adds a {@link BlockingRequestStreamingRoute} to this factory.
     * @param executionStrategy The execution strategy to use for this route.
     * @param methodDescriptor Describes the method routing and serialization.
     * @param decompressors Indicates the supported decompression applied to each request.
     * @param compressors Indicates the supported compression can be applied to responses.
     * @param route The interface to invoke when data is received for this route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    protected final <Req, Resp> void addBlockingRequestStreamingRoute(
            final GrpcExecutionStrategy executionStrategy, MethodDescriptor<Req, Resp> methodDescriptor,
            BufferDecoderGroup decompressors, List<BufferEncoder> compressors,
            BlockingRequestStreamingRoute<Req, Resp> route) {
        routeBuilder.addBlockingRequestStreamingRoute(methodDescriptor, decompressors, compressors, executionStrategy,
                route);
    }

    /**
     * Adds a {@link BlockingResponseStreamingRoute} to this factory.
     * @deprecated Use
     * {@link #addBlockingResponseStreamingRoute(Class, MethodDescriptor, BufferDecoderGroup, List,
     * BlockingResponseStreamingRoute)}.
     * @param path for this route.
     * @param serviceClass {@link Class} of the gRPC service.
     * @param methodName the name of gRPC method.
     * @param route {@link BlockingResponseStreamingRoute} to add.
     * @param requestClass {@link Class} for the request object.
     * @param responseClass {@link Class} for the response object.
     * @param serializationProvider {@link GrpcSerializationProvider} for the route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    @Deprecated
    protected final <Req, Resp> void addBlockingResponseStreamingRoute(
            final String path, final Class<?> serviceClass, final String methodName,
            final BlockingResponseStreamingRoute<Req, Resp> route, final Class<Req> requestClass,
            final Class<Resp> responseClass, final GrpcSerializationProvider serializationProvider) {
        addBlockingResponseStreamingRoute(serviceClass, new DefaultMethodDescriptor<>(path, methodName,
                        false, false, requestClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, requestClass), defaultToInt(),
                        true, false, responseClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, responseClass), defaultToInt()),
                decompressors(serializationProvider.supportedMessageCodings()),
                compressors(serializationProvider.supportedMessageCodings()), route);
    }

    /**
     * Adds a {@link BlockingResponseStreamingRoute} to this factory.
     * @param serviceClass {@link Class} of the gRPC service which can be used to extract annotations to override
     * offloading behavior (e.g. {@link NoOffloadsRouteExecutionStrategy}, {@link RouteExecutionStrategy}).
     * @param methodDescriptor Describes the method routing and serialization.
     * @param decompressors Indicates the supported decompression applied to each request.
     * @param compressors Indicates the supported compression can be applied to responses.
     * @param route The interface to invoke when data is received for this route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    protected final <Req, Resp> void addBlockingResponseStreamingRoute(
            Class<?> serviceClass, MethodDescriptor<Req, Resp> methodDescriptor,
            BufferDecoderGroup decompressors, List<BufferEncoder> compressors,
            BlockingResponseStreamingRoute<Req, Resp> route) {
        final Method method = retrieveMethod(serviceClass, methodDescriptor.javaMethodName(), GrpcServiceContext.class,
                methodDescriptor.requestDescriptor().parameterClass(), GrpcPayloadWriter.class);
        routeBuilder.addBlockingResponseStreamingRoute(methodDescriptor, decompressors, compressors,
                executionStrategy(methodDescriptor.httpPath(), method, serviceClass), route);
    }

    /**
     * Adds a {@link BlockingResponseStreamingRoute} to this factory.
     * @deprecated Use
     * {@link #addBlockingResponseStreamingRoute(GrpcExecutionStrategy, MethodDescriptor, BufferDecoderGroup, List,
     * BlockingResponseStreamingRoute)}.
     * @param path for this route.
     * @param executionStrategy {@link GrpcExecutionStrategy} to use.
     * @param route {@link BlockingResponseStreamingRoute} to add.
     * @param requestClass {@link Class} for the request object.
     * @param responseClass {@link Class} for the response object.
     * @param serializationProvider {@link GrpcSerializationProvider} for the route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    @Deprecated
    protected final <Req, Resp> void addBlockingResponseStreamingRoute(
            final String path, final GrpcExecutionStrategy executionStrategy,
            final BlockingResponseStreamingRoute<Req, Resp> route, final Class<Req> requestClass,
            final Class<Resp> responseClass, final GrpcSerializationProvider serializationProvider) {
        addBlockingResponseStreamingRoute(executionStrategy, new DefaultMethodDescriptor<>(path,
                        false, false, requestClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, requestClass), defaultToInt(),
                        true, false, responseClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, responseClass), defaultToInt()),
                decompressors(serializationProvider.supportedMessageCodings()),
                compressors(serializationProvider.supportedMessageCodings()), route);
    }

    /**
     * Adds a {@link BlockingResponseStreamingRoute} to this factory.
     * @param executionStrategy The execution strategy to use for this route.
     * @param methodDescriptor Describes the method routing and serialization.
     * @param decompressors Indicates the supported decompression applied to each request.
     * @param compressors Indicates the supported compression can be applied to responses.
     * @param route The interface to invoke when data is received for this route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    protected final <Req, Resp> void addBlockingResponseStreamingRoute(
            final GrpcExecutionStrategy executionStrategy, MethodDescriptor<Req, Resp> methodDescriptor,
            BufferDecoderGroup decompressors, List<BufferEncoder> compressors,
            BlockingResponseStreamingRoute<Req, Resp> route) {
        routeBuilder.addBlockingResponseStreamingRoute(methodDescriptor, decompressors, compressors, executionStrategy,
                route);
    }

    /**
     * An asynchronous <a href="https://www.grpc.io">gRPC</a> route.
     *
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    @FunctionalInterface
    protected interface Route<Req, Resp> extends AsyncCloseable {

        /**
         * Handles the passed {@link Req}.
         *
         * @param ctx {@link GrpcServiceContext} for this call.
         * @param request {@link Req} to handle.
         * @return {@link Single} containing the response.
         */
        Single<Resp> handle(GrpcServiceContext ctx, Req request);

        @Override
        default Completable closeAsync() {
            return completed();
        }

        /**
         * Convenience method to wrap a raw {@link Route} instance with a passed detached close implementation
         * of {@link AsyncCloseable}.
         *
         * @param rawRoute {@link Route} instance that has a detached close implementation.
         * @param closeable {@link AsyncCloseable} implementation for the passed {@code rawRoute}.
         * @param <Req> Type of request.
         * @param <Resp> Type of response.
         * @return A new {@link Route} that attaches the passed {@code closeable} to the passed {@code rawRoute}.
         */
        static <Req, Resp> Route<Req, Resp> wrap(final Route<Req, Resp> rawRoute, final AsyncCloseable closeable) {
            return new Route<Req, Resp>() {

                @Override
                public Single<Resp> handle(final GrpcServiceContext ctx, final Req request) {

                    return rawRoute.handle(ctx, request);
                }

                @Override
                public Completable closeAsync() {
                    return closeable.closeAsync();
                }

                @Override
                public Completable closeAsyncGracefully() {
                    return closeable.closeAsyncGracefully();
                }
            };
        }
    }

    /**
     * An asynchronous <a href="https://www.grpc.io">gRPC</a> route with bi-directional streaming.
     *
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    @FunctionalInterface
    protected interface StreamingRoute<Req, Resp> extends AsyncCloseable {

        /**
         * Handles the passed {@link Req}.
         *
         * @param ctx {@link GrpcServiceContext} for this call.
         * @param request {@link Publisher} of {@link Req} to handle.
         * @return {@link Single} containing the response.
         */
        Publisher<Resp> handle(GrpcServiceContext ctx, Publisher<Req> request);

        @Override
        default Completable closeAsync() {
            return completed();
        }

        /**
         * Convenience method to wrap a raw {@link StreamingRoute} instance with a passed detached close implementation
         * of {@link AsyncCloseable}.
         *
         * @param rawRoute {@link StreamingRoute} instance that has a detached close implementation.
         * @param closeable {@link AsyncCloseable} implementation for the passed {@code rawRoute}.
         * @param <Req> Type of request.
         * @param <Resp> Type of response.
         * @return A new {@link StreamingRoute} that attaches the passed {@code closeable} to the passed
         * {@code rawRoute}.
         */
        static <Req, Resp> StreamingRoute<Req, Resp> wrap(final StreamingRoute<Req, Resp> rawRoute,
                                                          final AsyncCloseable closeable) {
            return new StreamingRoute<Req, Resp>() {

                @Override
                public Publisher<Resp> handle(final GrpcServiceContext ctx, final Publisher<Req> request) {
                    return rawRoute.handle(ctx, request);
                }

                @Override
                public Completable closeAsync() {
                    return closeable.closeAsync();
                }

                @Override
                public Completable closeAsyncGracefully() {
                    return closeable.closeAsyncGracefully();
                }
            };
        }
    }

    /**
     * An asynchronous <a href="https://www.grpc.io">gRPC</a> route with request streaming.
     *
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    @FunctionalInterface
    protected interface RequestStreamingRoute<Req, Resp>
            extends AsyncCloseable {

        /**
         * Handles the passed {@link Req}.
         *
         * @param ctx {@link GrpcServiceContext} for this call.
         * @param request {@link Publisher} of {@link Req} to handle.
         * @return {@link Single} containing the response.
         */
        Single<Resp> handle(GrpcServiceContext ctx, Publisher<Req> request);

        @Override
        default Completable closeAsync() {
            return completed();
        }

        /**
         * Convenience method to wrap a raw {@link RequestStreamingRoute} instance with a passed detached close
         * implementation of {@link AsyncCloseable}.
         *
         * @param rawRoute {@link RequestStreamingRoute} instance that has a detached close implementation.
         * @param closeable {@link AsyncCloseable} implementation for the passed {@code rawRoute}.
         * @param <Req> Type of request.
         * @param <Resp> Type of response.
         * @return A new {@link RequestStreamingRoute} that attaches the passed {@code closeable} to the passed
         * {@code rawRoute}.
         */
        static <Req, Resp> RequestStreamingRoute<Req, Resp> wrap(final RequestStreamingRoute<Req, Resp> rawRoute,
                                                                 final AsyncCloseable closeable) {
            return new RequestStreamingRoute<Req, Resp>() {

                @Override
                public Single<Resp> handle(final GrpcServiceContext ctx, final Publisher<Req> request) {
                    return rawRoute.handle(ctx, request);
                }

                @Override
                public Completable closeAsync() {
                    return closeable.closeAsync();
                }

                @Override
                public Completable closeAsyncGracefully() {
                    return closeable.closeAsyncGracefully();
                }
            };
        }
    }

    /**
     * An asynchronous <a href="https://www.grpc.io">gRPC</a> route with response streaming.
     *
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    @FunctionalInterface
    protected interface ResponseStreamingRoute<Req, Resp>
            extends AsyncCloseable {

        /**
         * Handles the passed {@link Req}.
         *
         * @param ctx {@link GrpcServiceContext} for this call.
         * @param request {@link Req} to handle.
         * @return {@link Publisher} containing the response.
         */
        Publisher<Resp> handle(GrpcServiceContext ctx, Req request);

        @Override
        default Completable closeAsync() {
            return completed();
        }

        /**
         * Convenience method to wrap a raw {@link ResponseStreamingRoute} instance with a passed detached close
         * implementation of {@link AsyncCloseable}.
         *
         * @param rawRoute {@link ResponseStreamingRoute} instance that has a detached close implementation.
         * @param closeable {@link AsyncCloseable} implementation for the passed {@code rawRoute}.
         * @param <Req> Type of request.
         * @param <Resp> Type of response.
         * @return A new {@link ResponseStreamingRoute} that attaches the passed {@code closeable} to the passed
         * {@code rawRoute}.
         */
        static <Req, Resp> ResponseStreamingRoute<Req, Resp> wrap(final ResponseStreamingRoute<Req, Resp> rawRoute,
                                                                  final AsyncCloseable closeable) {
            return new ResponseStreamingRoute<Req, Resp>() {

                @Override
                public Publisher<Resp> handle(final GrpcServiceContext ctx, final Req request) {
                    return rawRoute.handle(ctx, request);
                }

                @Override
                public Completable closeAsync() {
                    return closeable.closeAsync();
                }

                @Override
                public Completable closeAsyncGracefully() {
                    return closeable.closeAsyncGracefully();
                }
            };
        }
    }

    /**
     * A blocking <a href="https://www.grpc.io">gRPC</a> route.
     *
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    @FunctionalInterface
    protected interface BlockingRoute<Req, Resp>
            extends GracefulAutoCloseable {
        /**
         * Handles the passed {@link Req}.
         *
         * @param ctx {@link GrpcServiceContext} for this call.
         * @param request {@link Req} to handle.
         * @return The response.
         * @throws Exception If an exception occurs during request processing.
         */
        Resp handle(GrpcServiceContext ctx, Req request) throws Exception;

        @Override
        default void close() throws Exception {
            // No op
        }

        /**
         * Convenience method to wrap a raw {@link BlockingRoute} instance with a passed detached close
         * implementation of {@link GracefulAutoCloseable}.
         *
         * @param rawRoute {@link BlockingRoute} instance that has a detached close implementation.
         * @param closeable {@link GracefulAutoCloseable} implementation for the passed {@code rawRoute}.
         * @param <Req> Type of request.
         * @param <Resp> Type of response.
         * @return A new {@link BlockingRoute} that attaches the passed {@code closeable} to the passed
         * {@code rawRoute}.
         */
        static <Req, Resp> BlockingRoute<Req, Resp> wrap(final BlockingRoute<Req, Resp> rawRoute,
                                                         final GracefulAutoCloseable closeable) {
            return new BlockingRoute<Req, Resp>() {

                @Override
                public Resp handle(final GrpcServiceContext ctx, final Req request) throws Exception {
                    return rawRoute.handle(ctx, request);
                }

                @Override
                public void close() throws Exception {
                    closeable.close();
                }

                @Override
                public void closeGracefully() throws Exception {
                    closeable.closeGracefully();
                }
            };
        }
    }

    /**
     * A blocking <a href="https://www.grpc.io">gRPC</a> route with bi-directional streaming.
     *
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    @FunctionalInterface
    protected interface BlockingStreamingRoute<Req, Resp>
            extends GracefulAutoCloseable {

        /**
         * Handles the passed {@link Req}.
         *
         * @param ctx {@link GrpcServiceContext} for this call.
         * @param request {@link BlockingIterable} of {@link Req} to handle.
         * @param responseWriter {@link GrpcPayloadWriter} to write the response.
         * @throws Exception If an exception occurs during request processing.
         */
        void handle(GrpcServiceContext ctx, BlockingIterable<Req> request,
                    GrpcPayloadWriter<Resp> responseWriter) throws Exception;

        @Override
        default void close() throws Exception {
            // No op
        }

        /**
         * Convenience method to wrap a raw {@link BlockingStreamingRoute} instance with a passed detached close
         * implementation of {@link GracefulAutoCloseable}.
         *
         * @param rawRoute {@link BlockingStreamingRoute} instance that has a detached close implementation.
         * @param closeable {@link GracefulAutoCloseable} implementation for the passed {@code rawRoute}.
         * @param <Req> Type of request.
         * @param <Resp> Type of response.
         * @return A new {@link BlockingStreamingRoute} that attaches the passed {@code closeable} to the passed
         * {@code rawRoute}.
         */
        static <Req, Resp> BlockingStreamingRoute<Req, Resp> wrap(final BlockingStreamingRoute<Req, Resp> rawRoute,
                                                                  final GracefulAutoCloseable closeable) {
            return new BlockingStreamingRoute<Req, Resp>() {
                @Override
                public void handle(final GrpcServiceContext ctx, final BlockingIterable<Req> request,
                                   final GrpcPayloadWriter<Resp> responseWriter) throws Exception {
                    rawRoute.handle(ctx, request, responseWriter);
                }

                @Override
                public void close() throws Exception {
                    closeable.close();
                }

                @Override
                public void closeGracefully() throws Exception {
                    closeable.closeGracefully();
                }
            };
        }
    }

    /**
     * A blocking <a href="https://www.grpc.io">gRPC</a> route with request streaming.
     *
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    @FunctionalInterface
    protected interface BlockingRequestStreamingRoute<Req, Resp>
            extends GracefulAutoCloseable {

        /**
         * Handles the passed {@link Req}.
         *
         * @param ctx {@link GrpcServiceContext} for this call.
         * @param request {@link BlockingIterable} of {@link Req} to handle.
         * @return The response.
         * @throws Exception If an exception occurs during request processing.
         */
        Resp handle(GrpcServiceContext ctx, BlockingIterable<Req> request) throws Exception;

        @Override
        default void close() throws Exception {
            // No op
        }

        /**
         * Convenience method to wrap a raw {@link BlockingRequestStreamingRoute} instance with a passed detached close
         * implementation of {@link GracefulAutoCloseable}.
         *
         * @param rawRoute {@link BlockingRequestStreamingRoute} instance that has a detached close implementation.
         * @param closeable {@link GracefulAutoCloseable} implementation for the passed {@code rawRoute}.
         * @param <Req> Type of request.
         * @param <Resp> Type of response.
         * @return A new {@link BlockingRequestStreamingRoute} that attaches the passed {@code closeable} to the passed
         * {@code rawRoute}.
         */
        static <Req, Resp> BlockingRequestStreamingRoute<Req, Resp> wrap(
                final BlockingRequestStreamingRoute<Req, Resp> rawRoute, final GracefulAutoCloseable closeable) {
            return new BlockingRequestStreamingRoute<Req, Resp>() {

                @Override
                public Resp handle(final GrpcServiceContext ctx, final BlockingIterable<Req> request) throws Exception {
                    return rawRoute.handle(ctx, request);
                }

                @Override
                public void close() throws Exception {
                    closeable.close();
                }

                @Override
                public void closeGracefully() throws Exception {
                    closeable.closeGracefully();
                }
            };
        }
    }

    /**
     * A blocking <a href="https://www.grpc.io">gRPC</a> route with response streaming.
     *
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    @FunctionalInterface
    protected interface BlockingResponseStreamingRoute<Req, Resp>
            extends GracefulAutoCloseable {

        /**
         * Handles the passed {@link Req}.
         *
         * @param ctx {@link GrpcServiceContext} for this call.
         * @param request {@link Req} to handle.
         * @param responseWriter {@link GrpcPayloadWriter} to write the response.
         * @throws Exception If an exception occurs during request processing.
         */
        void handle(GrpcServiceContext ctx, Req request, GrpcPayloadWriter<Resp> responseWriter) throws Exception;

        @Override
        default void close() throws Exception {
            // No op
        }

        /**
         * Convenience method to wrap a raw {@link BlockingResponseStreamingRoute} instance with a passed detached close
         * implementation of {@link GracefulAutoCloseable}.
         *
         * @param rawRoute {@link BlockingResponseStreamingRoute} instance that has a detached close implementation.
         * @param closeable {@link GracefulAutoCloseable} implementation for the passed {@code rawRoute}.
         * @param <Req> Type of request.
         * @param <Resp> Type of response.
         * @return A new {@link BlockingResponseStreamingRoute} that attaches the passed {@code closeable} to the passed
         * {@code rawRoute}.
         */
        static <Req, Resp> BlockingResponseStreamingRoute<Req, Resp> wrap(
                final BlockingResponseStreamingRoute<Req, Resp> rawRoute, final GracefulAutoCloseable closeable) {
            return new BlockingResponseStreamingRoute<Req, Resp>() {

                @Override
                public void handle(final GrpcServiceContext ctx, final Req request,
                                   final GrpcPayloadWriter<Resp> responseWriter) throws Exception {
                    rawRoute.handle(ctx, request, responseWriter);
                }

                @Override
                public void close() throws Exception {
                    closeable.close();
                }

                @Override
                public void closeGracefully() throws Exception {
                    closeable.closeGracefully();
                }
            };
        }
    }

    /**
     * A collection of route corresponding to the enclosing {@link GrpcRoutes}.
     */
    protected interface AllGrpcRoutes extends AsyncCloseable {

        /**
         * Returns the registered {@link StreamingRoute} for the passed {@code path}. If a route with a different
         * programming model is registered, it will be converted to a {@link StreamingRoute}.
         *
         * @param path for the route.
         * @param <Req> Type of request.
         * @param <Resp> Type of response.
         * @return Registered {@link StreamingRoute} for the passed {@code path}.
         * @throws IllegalArgumentException If the route does not exist.
         */
        <Req, Resp> StreamingRoute<Req, Resp> streamingRouteFor(String path)
                throws IllegalArgumentException;

        /**
         * Returns the registered {@link Route} for the passed {@code path}. If a route with a different
         * programming model is registered, it will be converted to a {@link Route}.
         *
         * @param path for the route.
         * @param <Req> Type of request.
         * @param <Resp> Type of response.
         * @return Registered {@link Route} for the passed {@code path}.
         * @throws IllegalArgumentException If the route does not exist.
         */
        <Req, Resp> Route<Req, Resp> routeFor(String path)
                throws IllegalArgumentException;

        /**
         * Returns the registered {@link RequestStreamingRoute} for the passed {@code path}. If a route with a different
         * programming model is registered, it will be converted to a {@link RequestStreamingRoute}.
         *
         * @param path for the route.
         * @param <Req> Type of request.
         * @param <Resp> Type of response.
         * @return Registered {@link RequestStreamingRoute} for the passed {@code path}.
         * @throws IllegalArgumentException If the route does not exist.
         */
        <Req, Resp> RequestStreamingRoute<Req, Resp> requestStreamingRouteFor(String path)
                throws IllegalArgumentException;

        /**
         * Returns the registered {@link ResponseStreamingRoute} for the passed {@code path}. If a route with a
         * different programming model is registered, it will be converted to a {@link ResponseStreamingRoute}.
         *
         * @param path for the route.
         * @param <Req> Type of request.
         * @param <Resp> Type of response.
         * @return Registered {@link ResponseStreamingRoute} for the passed {@code path}.
         * @throws IllegalArgumentException If the route does not exist.
         */
        <Req, Resp> ResponseStreamingRoute<Req, Resp> responseStreamingRouteFor(String path)
                throws IllegalArgumentException;
    }
}
