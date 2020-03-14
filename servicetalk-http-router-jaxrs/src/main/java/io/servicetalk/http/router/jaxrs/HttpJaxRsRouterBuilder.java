/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.router.jaxrs;

import io.servicetalk.http.api.BlockingHttpService;
import io.servicetalk.http.api.BlockingStreamingHttpService;
import io.servicetalk.http.api.HttpApiConversions;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.router.predicate.HttpPredicateRouterBuilder;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import javax.ws.rs.Path;
import javax.ws.rs.core.Application;

/**
 * Builds an {@link StreamingHttpService} which routes requests to JAX-RS annotated classes,
 * using internal routing engine, eg.
 * <pre>{@code
 * final StreamingHttpService router = new HttpJaxRsRouterBuilder()
 *     .buildStreaming(application);
 * }</pre>
 */
public final class HttpJaxRsRouterBuilder {

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

    StreamingHttpService from(final Class<? extends Application> applicationClass) {

        final Application application;
        try {
            application = applicationClass.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return from(application);
    }

    StreamingHttpService from(final Application application) {

        HttpPredicateRouterBuilder routerBuilder = new HttpPredicateRouterBuilder();

        for (Object resources : application.getSingletons()) {

            final Path rootPath = resources.getClass().getAnnotation(Path.class);

            if (rootPath == null) {
                continue;
            }

            for (Method method : resources.getClass().getDeclaredMethods()) {
                if (Modifier.isPublic(method.getModifiers())) {
                    final Path methodPath = method.getAnnotation(Path.class);

                    if (methodPath == null) {
                        continue;
                    }

                    final StreamingHttpService streamingService = new ReflectionStreamingService(method);
                    routerBuilder.whenPathMatches(rootPath.value() + methodPath.value())
                            .thenRouteTo(streamingService);
                }
            }
        }

        return routerBuilder.buildStreaming();
    }

    static HttpService toAggregated(StreamingHttpService router) {
        return HttpApiConversions.toHttpService(router);
    }

    static BlockingHttpService toBlocking(StreamingHttpService router) {
        return HttpApiConversions.toBlockingHttpService(router);
    }

    static BlockingStreamingHttpService toBlockingStreaming(StreamingHttpService router) {
        return HttpApiConversions.toBlockingStreamingHttpService(router);
    }
}
