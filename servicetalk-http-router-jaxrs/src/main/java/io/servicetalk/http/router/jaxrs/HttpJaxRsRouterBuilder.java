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
import io.servicetalk.http.api.CharSequences;
import io.servicetalk.http.api.HttpApiConversions;
import io.servicetalk.http.api.HttpHeaderNames;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.router.predicate.HttpPredicateRouterBuilder;
import io.servicetalk.http.router.predicate.dsl.RouteContinuation;
import io.servicetalk.transport.api.ConnectionContext;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import javax.annotation.Nonnull;
import javax.ws.rs.Consumes;
import javax.ws.rs.CookieParam;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.WILDCARD;

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
     * Build the {@link StreamingHttpService} for the specified JAX-RS {@link Application}.
     *
     * @param application the {@link Application} to route requests to.
     * @return the {@link StreamingHttpService}.
     */
    public StreamingHttpService buildStreaming(final Application application) {
        return from(application);
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
     * Build the {@link BlockingStreamingHttpService} for the specified JAX-RS {@link Application}.
     *
     * @param application the {@link Application} to route requests to.
     * @return the {@link BlockingStreamingHttpService}.
     */
    public BlockingStreamingHttpService buildBlockingStreaming(final Application application) {
        return toBlockingStreaming(from(application));
    }

    public StreamingHttpService from(final Application application) {

        final HttpPredicateRouterBuilder routerBuilder = new HttpPredicateRouterBuilder();

        if (!application.getClasses().isEmpty()) {
            throw new UnsupportedOperationException("Method application.getClasses() is not supported. " +
                    "Use application.getSingletons() instead");
        }

        for (Object resource : application.getSingletons()) {

            final Path rootPath = resource.getClass().getAnnotation(Path.class);

            if (rootPath == null) {
                continue;
            }

            Consumes resourceConsumes = resource.getClass().getAnnotation(Consumes.class);

            for (Method method : resource.getClass().getDeclaredMethods()) {
                final Path methodPath = method.getAnnotation(Path.class);

                if (methodPath == null) {
                    continue;
                }

                final PathTemplateDelegate pathTemplate =
                        new PathTemplateDelegate(rootPath.value() + methodPath.value());

                final Consumes methodConsumes = method.getAnnotation(Consumes.class);

                final String contentType;
                if (methodConsumes == null) {
                    contentType = resourceConsumes == null ? WILDCARD : resourceConsumes.value()[0];
                } else {
                    contentType = methodConsumes.value()[0];
                }

                final HttpRequestMethod httpMethod = buildHttpRequestMethod(method);

                final List<Parameter> parameters = buildParameters(method, contentType, pathTemplate);

                final StreamingHttpService streamingService =
                        new ReflectionStreamingService(resource, method, parameters);

                final RouteContinuation routeContinuation = routerBuilder.whenPathMatches(pathTemplate.getRegex())
                        .andMethod(httpMethod);

                if (!WILDCARD.equals(contentType)) {
                    routeContinuation
                            .and(addHeader(HttpHeaderNames.CONTENT_TYPE, contentType));
                }

                routeContinuation.thenRouteTo(streamingService);
            }
        }

        return routerBuilder.buildStreaming();
    }

    @Nonnull
    private static BiPredicate<ConnectionContext, StreamingHttpRequest> addHeader(final CharSequence name,
                                                                                  final CharSequence value) {
        return (ctx, req) -> {
            for (CharSequence header : req.headers().values(name)) {
                if (CharSequences.contentEqualsIgnoreCase(header, value)) {
                    return true;
                }
            }
            return false;
        };
    }

    private static HttpRequestMethod buildHttpRequestMethod(final Method method) {
        final HttpRequestMethod httpMethod;

        if (method.isAnnotationPresent(POST.class)) {
            httpMethod = HttpRequestMethod.POST;
        } else if (method.isAnnotationPresent(PUT.class)) {
            httpMethod = HttpRequestMethod.PUT;
        } else if (method.isAnnotationPresent(DELETE.class)) {
            httpMethod = HttpRequestMethod.DELETE;
        } else {
            httpMethod = HttpRequestMethod.GET;
        }
        return httpMethod;
    }

    private static List<Parameter> buildParameters(final Method method, final String contentType,
                                                   final PathTemplateDelegate pathTemplate) {
        final List<Parameter> parameters = new ArrayList<>(method.getParameterCount());
        for (int i = 0; i < method.getParameterCount(); i++) {
            java.lang.reflect.Parameter parameter = method.getParameters()[i];

            final QueryParam queryParamAnnotation = parameter.getAnnotation(QueryParam.class);
            if (queryParamAnnotation != null) {
                final DefaultValue defaultValueAnnotation = parameter.getAnnotation(DefaultValue.class);
                String defaultValue = defaultValueAnnotation == null ? null : defaultValueAnnotation.value();
                parameters.add(i, new QueryParameter(queryParamAnnotation.value(),
                        parameter.getType(), defaultValue));
            }

            final PathParam pathParamAnnotation = parameter.getAnnotation(PathParam.class);
            if (pathParamAnnotation != null) {
                parameters.add(i, new PathParameter(pathParamAnnotation.value(),
                        parameter.getType(), pathTemplate));
            }

            final FormParam formParamAnnotation = parameter.getAnnotation(FormParam.class);
            if (formParamAnnotation != null) {
                parameters.add(i, new FormParameter(formParamAnnotation.value()));
            }

            final HeaderParam headerParamAnnotation = parameter.getAnnotation(HeaderParam.class);
            if (headerParamAnnotation != null) {
                parameters.add(i, new HeaderParameter(headerParamAnnotation.value()));
            }

            final CookieParam cookieParamAnnotation = parameter.getAnnotation(CookieParam.class);
            if (cookieParamAnnotation != null) {
                parameters.add(i, new CookieParameter(cookieParamAnnotation.value()));
            }

            final Annotation contextAnnotation = parameter.getAnnotation(Context.class);
            if (contextAnnotation != null) {
                parameters.add(i, new ContextParameter(parameter.getType()));
            }

            if (pathParamAnnotation == null &&
                    queryParamAnnotation == null &&
                    formParamAnnotation == null &&
                    contextAnnotation == null &&
                    headerParamAnnotation == null &&
                    cookieParamAnnotation == null
            ) {

                if (!APPLICATION_JSON.equals(contentType)) {
                    throw new IllegalStateException("Wrong content type. Expecting " +
                            APPLICATION_JSON + " for method " + method.getName());
                }

                parameters.add(i, new JsonBodyParameter(parameter.getParameterizedType()));
            }
        }
        return parameters;
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
