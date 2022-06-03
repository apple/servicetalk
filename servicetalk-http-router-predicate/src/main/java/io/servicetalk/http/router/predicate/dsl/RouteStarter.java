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
package io.servicetalk.http.router.predicate.dsl;

import io.servicetalk.http.api.HttpCookiePair;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.transport.api.ConnectionContext;

import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Methods for starting a route.
 */
public interface RouteStarter {
    /**
     * Begin a route that matches requests where the {@link HttpRequestMethod} is {@code method}.
     *
     * @param method the method to match.
     * @return {@link RouteContinuation} for the next steps of building a route.
     */
    RouteContinuation whenMethod(HttpRequestMethod method);

    /**
     * Begin a route that matches requests where the {@link HttpRequestMethod} is one of the {@code methods}.
     *
     * @param methods the methods to match.
     * @return {@link RouteContinuation} for the next steps of building a route.
     */
    RouteContinuation whenMethodIsOneOf(HttpRequestMethod... methods);

    /**
     * Begin a route that matches requests where the path is equal to {@code path}.
     *
     * @param path the path to match.
     * @return {@link RouteContinuation} for the next steps of building a route.
     */
    RouteContinuation whenPathEquals(String path);

    /**
     * Begin a route that matches requests where the path is equal to any of the specified {@code path}s.
     *
     * @param paths the paths to match.
     * @return {@link RouteContinuation} for the next steps of building a route.
     */
    RouteContinuation whenPathIsOneOf(String... paths);

    /**
     * Begin a route that matches requests where the path starts with {@code pathPrefix}.
     *
     * @param pathPrefix the path prefix to match.
     * @return {@link RouteContinuation} for the next steps of building a route.
     */
    RouteContinuation whenPathStartsWith(String pathPrefix);

    /**
     * Begin a route that matches requests where the path matches the regex {@code pathRegex}.
     *
     * @param pathRegex the regex to match against the request path.
     * @return {@link RouteContinuation} for the next steps of building a route.
     */
    RouteContinuation whenPathMatches(String pathRegex);

    /**
     * Begin a route that matches requests where the path matches the regex {@code pathRegex}.
     *
     * @param pathRegex the regex to match against the request path.
     * @return {@link RouteContinuation} for the next steps of building a route.
     */
    RouteContinuation whenPathMatches(Pattern pathRegex);

    /**
     * Begin a route with a {@link StringMultiValueMatcher} that matches against the value(s) of the request
     * parameter {@code name}.
     *
     * @param name the request parameter name that must be present in the request in order to continue evaluation of
     * this route.
     * @return {@link StringMultiValueMatcher} for the next steps of building a route.
     */
    StringMultiValueMatcher whenQueryParam(String name);

    /**
     * Begin a route with a {@link StringMultiValueMatcher} that matches against the value(s) of the
     * {@code name} headers.
     *
     * @param name The header name that must be present in the request in order to continue evaluation of this route.
     * @return {@link StringMultiValueMatcher} for the next steps of building a route.
     */
    StringMultiValueMatcher whenHeader(CharSequence name);

    /**
     * Begin a route with a {@link CookieMatcher} that matches against {@link HttpCookiePair}s with the name
     * {@code name}.
     *
     * @param name the cookie name that must be present in the request in order to continue evaluation of this route.
     * @return {@link CookieMatcher} for the next steps of building a route.
     */
    CookieMatcher whenCookie(String name);

    /**
     * Begin a route that matches requests that are over SSL/TLS.
     *
     * @return {@link RouteContinuation} for the next steps of building a route.
     */
    RouteContinuation whenIsSsl();

    /**
     * Begin a route that matches requests that are not over SSL/TLS.
     *
     * @return {@link RouteContinuation} for the next steps of building a route.
     */
    RouteContinuation whenIsNotSsl();

    /**
     * Begin a route that matches {@link StreamingHttpRequest}s with a user-specified {@code predicate}.
     *
     * @param predicate the predicate to evaluate against requests. This predicate must not block.
     * @return {@link RouteContinuation} for the next steps of building a route.
     */
    RouteContinuation when(Predicate<StreamingHttpRequest> predicate);

    /**
     * Begin a route that matches {@link StreamingHttpRequest} and {@link ConnectionContext} with a user-specified
     * {@code predicate}.
     *
     * @param predicate the predicate to evaluate against the request and connection context.
     *                  This predicate must not block
     * @return {@link RouteContinuation} for the next steps of building a route.
     */
    RouteContinuation when(BiPredicate<ConnectionContext, StreamingHttpRequest> predicate);

    /**
     * Builds the {@link StreamingHttpService} that performs the configured routing.
     *
     * @return the router {@link StreamingHttpService}.
     */
    StreamingHttpService buildStreaming();
}
