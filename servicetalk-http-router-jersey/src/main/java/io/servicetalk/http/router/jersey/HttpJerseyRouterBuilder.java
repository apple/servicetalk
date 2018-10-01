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

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.transport.api.ConnectionContext;

import java.io.InputStream;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.ws.rs.core.Application;

import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
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
    private BiFunction<ConnectionContext, StreamingHttpRequest, String> baseUriFunction =
            (ctx, req) -> getBaseRequestUri(ctx, req, false);
    private Function<String, Executor> executorFactory = __ -> null;
    private HttpExecutionStrategy strategy = defaultStrategy();

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
     * Set the function used to compute the base URI for incoming {@link StreamingHttpRequest}s.
     * <b>The computed base URI must have {@code /} as path, and no query nor fragment.</b>
     *
     * @param baseUriFunction a {@link BiFunction} that computes a base URI {@link String}
     * for the provided {@link ConnectionContext} and {@link StreamingHttpRequest}.
     * @return this
     * @see <a href="https://tools.ietf.org/html/rfc3986#section-3">URI Syntax Components</a>
     */
    public HttpJerseyRouterBuilder baseUriFunction(
            final BiFunction<ConnectionContext, StreamingHttpRequest, String> baseUriFunction) {

        this.baseUriFunction = requireNonNull(baseUriFunction);
        return this;
    }

    /**
     * Set the {@link HttpExecutionStrategy} for this router.
     *
     * @param strategy {@link HttpExecutionStrategy} to use.
     * @return this
     */
    public HttpJerseyRouterBuilder executionStrategy(HttpExecutionStrategy strategy) {
        this.strategy = strategy;
        return this;
    }

    /**
     * Set a {@link Function Function&lt;String, Executor&gt;} used as a factory for creating {@link Executor} instances
     * that can be used for offloading the handling of request to resource methods, as specified
     * via {@link ExecutionStrategy} annotations.
     *
     * @param executorFactory a {@link Function Function&lt;String, Executor&gt;}
     * @return this
     * @see ExecutionStrategy
     */
    public HttpJerseyRouterBuilder setExecutorFactory(final Function<String, Executor> executorFactory) {
        this.executorFactory = requireNonNull(executorFactory);
        return this;
    }

    /**
     * Build the {@link StreamingHttpService} for the specified JAX-RS {@link Application}.
     *
     * @param application the {@link Application} to route requests to.
     * @return the {@link StreamingHttpService}.
     */
    public StreamingHttpService build(final Application application) {
        return new DefaultJerseyStreamingHttpRouter(application, publisherInputStreamQueueCapacity, baseUriFunction,
                executorFactory, strategy);
    }

    /**
     * Build the {@link StreamingHttpService} for the specified JAX-RS {@link Application} class.
     *
     * @param applicationClass the {@link Application} class to instantiate and route requests to.
     * @return the {@link StreamingHttpService}.
     */
    public StreamingHttpService build(final Class<? extends Application> applicationClass) {
        return new DefaultJerseyStreamingHttpRouter(applicationClass, publisherInputStreamQueueCapacity,
                baseUriFunction, executorFactory, strategy);
    }
}
