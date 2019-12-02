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
package io.servicetalk.http.router.jersey;

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.BlockingHttpService;
import io.servicetalk.http.api.BlockingStreamingHttpService;
import io.servicetalk.http.api.HttpApiConversions;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.router.api.RouteExecutionStrategy;
import io.servicetalk.transport.api.ConnectionContext;

import java.io.InputStream;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.ws.rs.core.Application;

import static io.servicetalk.http.utils.HttpRequestUriUtils.getBaseRequestUri;
import static java.util.Objects.requireNonNull;

/**
 * Builds an {@link StreamingHttpService} which routes requests to JAX-RS annotated classes,
 * using Jersey as the routing engine, eg.
 * <pre>{@code
 * final StreamingHttpService router = new HttpJerseyRouterBuilder()
 *     .buildStreaming(application);
 * }</pre>
 */
public final class HttpJerseyRouterBuilder {
    private int publisherInputStreamQueueCapacity = 16;
    private BiFunction<ConnectionContext, HttpRequestMetaData, String> baseUriFunction =
            (ctx, req) -> getBaseRequestUri(ctx, req, false);
    private Function<String, HttpExecutionStrategy> routeStrategyFactory = __ -> null;

    /**
     * Set the hint for the capacity of the intermediary queue that stores items when adapting {@link Publisher}s
     * into {@link InputStream}s.
     *
     * @param publisherInputStreamQueueCapacity the capacity hint.
     * @return this
     */
    public HttpJerseyRouterBuilder publisherInputStreamQueueCapacity(final int publisherInputStreamQueueCapacity) {
        if (publisherInputStreamQueueCapacity <= 0) {
            throw new IllegalArgumentException("Invalid queue capacity: " + publisherInputStreamQueueCapacity
                    + " (expected > 0).");
        }
        this.publisherInputStreamQueueCapacity = publisherInputStreamQueueCapacity;
        return this;
    }

    /**
     * Set the function used to compute the base URI for the incoming HTTP request.
     * <b>The computed base URI must have {@code /} as path, and no query nor fragment.</b>
     *
     * @param baseUriFunction a {@link BiFunction} that computes a base URI {@link String}
     * for the provided {@link ConnectionContext} and {@link HttpRequestMetaData}.
     * @return this
     * @see <a href="https://tools.ietf.org/html/rfc3986#section-3">URI Syntax Components</a>
     */
    public HttpJerseyRouterBuilder baseUriFunction(
            final BiFunction<ConnectionContext, HttpRequestMetaData, String> baseUriFunction) {

        this.baseUriFunction = requireNonNull(baseUriFunction);
        return this;
    }

    /**
     * Set a {@link Function Function&lt;String, HttpExecutionStrategy&gt;} used as a factory for
     * creating {@link HttpExecutionStrategy} instances that can be used for offloading the handling of request to
     * resource methods, as specified via {@link RouteExecutionStrategy} annotations.
     *
     * @param routeStrategyFactory a {@link Function Function&lt;String, Executor&gt;}
     * @return this
     * @see RouteExecutionStrategy
     */
    public HttpJerseyRouterBuilder routeExecutionStrategyFactory(
            final Function<String, HttpExecutionStrategy> routeStrategyFactory) {
        this.routeStrategyFactory = requireNonNull(routeStrategyFactory);
        return this;
    }

    /**
     * Build the {@link HttpService} for the specified JAX-RS {@link Application}.
     *
     * @param application the {@link Application} to route requests to.
     * @return the {@link HttpService}.
     */
    public HttpService build(final Application application) {
        return toAggregated(from(application));
    }

    /**
     * Build the {@link HttpService} for the specified JAX-RS {@link Application} class.
     *
     * @param applicationClass the {@link Application} class to instantiate and route requests to.
     * @return the {@link HttpService}.
     */
    public HttpService build(final Class<? extends Application> applicationClass) {
        return toAggregated(from(applicationClass));
    }

    /**
     * Build the {@link StreamingHttpService} for the specified JAX-RS {@link Application}.
     *
     * @param application the {@link Application} to route requests to.
     * @return the {@link StreamingHttpService}.
     */
    public StreamingHttpService buildStreaming(final Application application) {
        return from(application);
    }

    /**
     * Build the {@link StreamingHttpService} for the specified JAX-RS {@link Application} class.
     *
     * @param applicationClass the {@link Application} class to instantiate and route requests to.
     * @return the {@link StreamingHttpService}.
     */
    public StreamingHttpService buildStreaming(final Class<? extends Application> applicationClass) {
        return from(applicationClass);
    }

    /**
     * Build the {@link BlockingHttpService} for the specified JAX-RS {@link Application}.
     *
     * @param application the {@link Application} to route requests to.
     * @return the {@link BlockingHttpService}.
     */
    public BlockingHttpService buildBlocking(final Application application) {
        return toBlocking(from(application));
    }

    /**
     * Build the {@link BlockingHttpService} for the specified JAX-RS {@link Application} class.
     *
     * @param applicationClass the {@link Application} class to instantiate and route requests to.
     * @return the {@link BlockingHttpService}.
     */
    public BlockingHttpService buildBlocking(final Class<? extends Application> applicationClass) {
        return toBlocking(from(applicationClass));
    }

    /**
     * Build the {@link BlockingStreamingHttpService} for the specified JAX-RS {@link Application}.
     *
     * @param application the {@link Application} to route requests to.
     * @return the {@link BlockingStreamingHttpService}.
     */
    public BlockingStreamingHttpService buildBlockingStreaming(final Application application) {
        return toBlockingStreaming(from(application));
    }

    /**
     * Build the {@link BlockingStreamingHttpService} for the specified JAX-RS {@link Application} class.
     *
     * @param applicationClass the {@link Application} class to instantiate and route requests to.
     * @return the {@link BlockingStreamingHttpService}.
     */
    public BlockingStreamingHttpService buildBlockingStreaming(final Class<? extends Application> applicationClass) {
        return toBlockingStreaming(from(applicationClass));
    }

    // Some tests need access to router.configuration() which is no longer exposed after conversion/wrapping so we let
    // tests do the API conversions using these methods. This ensures that once this implementation changes (or
    // potentially API conversions may no longer be needed in the future) that we don't forget to update the tests
    // accordingly.

    DefaultJerseyStreamingHttpRouter from(final Class<? extends Application> applicationClass) {
        return new DefaultJerseyStreamingHttpRouter(applicationClass, publisherInputStreamQueueCapacity,
                baseUriFunction, routeStrategyFactory);
    }

    DefaultJerseyStreamingHttpRouter from(final Application application) {
        return new DefaultJerseyStreamingHttpRouter(application, publisherInputStreamQueueCapacity, baseUriFunction,
                routeStrategyFactory);
    }

    static HttpService toAggregated(DefaultJerseyStreamingHttpRouter router) {
        return HttpApiConversions.toHttpService(router);
    }

    static BlockingHttpService toBlocking(DefaultJerseyStreamingHttpRouter router) {
        return HttpApiConversions.toBlockingHttpService(router);
    }

    static BlockingStreamingHttpService toBlockingStreaming(DefaultJerseyStreamingHttpRouter router) {
        return HttpApiConversions.toBlockingStreamingHttpService(router);
    }
}
