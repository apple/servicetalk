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

import io.servicetalk.http.api.HttpCookie;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.transport.api.ConnectionContext;

import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Methods for continuing a route.
 */
public interface RouteContinuation {
    /**
     * Extends the current route such that it matches requests where the {@link HttpRequestMethod} is {@code method}.
     *
     * @param method the method to match.
     * @return {@link RouteContinuation} for the next steps of building a route.
     */
    RouteContinuation andMethod(HttpRequestMethod method);

    /**
     * Extends the current route such that it matches requests where the {@link HttpRequestMethod} is one of the
     * {@code methods}.
     *
     * @param methods the methods to match.
     * @return {@link RouteContinuation} for the next steps of building a route.
     */
    RouteContinuation andMethodIsOneOf(HttpRequestMethod... methods);

    /**
     * Extends the current route such that it matches requests where the path is equal to {@code path}.
     *
     * @param path the path to match.
     * @return {@link RouteContinuation} for the next steps of building a route.
     */
    RouteContinuation andPathEquals(String path);

    /**
     * Extends the current route such that it matches requests where the path is equal to any of the specified
     * {@code path}s.
     *
     * @param paths the paths to match.
     * @return {@link RouteContinuation} for the next steps of building a route.
     */
    RouteContinuation andPathIsOneOf(String... paths);

    /**
     * Extends the current route such that it matches requests where the path starts with {@code pathPrefix}.
     *
     * @param pathPrefix the path prefix to match.
     * @return {@link RouteContinuation} for the next steps of building a route.
     */
    RouteContinuation andPathStartsWith(String pathPrefix);

    /**
     * Extends the current route such that it matches requests where the path matches the regex {@code pathRegex}.
     *
     * @param pathRegex the regex to match against the request path.
     * @return {@link RouteContinuation} for the next steps of building a route.
     */
    RouteContinuation andPathMatches(String pathRegex);

    /**
     * Extends the current route such that it matches requests where the path matches the regex {@code pathRegex}.
     *
     * @param pathRegex the regex to match against the request path.
     * @return {@link RouteContinuation} for the next steps of building a route.
     */
    RouteContinuation andPathMatches(Pattern pathRegex);

    /**
     * Extends the current route with a {@link StringMultiValueMatcher} that matches against the value(s) of the
     * request parameter {@code name}.
     *
     * @param name the request parameter name that must be present in the request in order to continue evaluation of
     * this route.
     * @return {@link StringMultiValueMatcher} for the next steps of building a route.
     */
    StringMultiValueMatcher andQueryParam(String name);

    /**
     * Extends the current route with a {@link StringMultiValueMatcher} that matches against the value(s) of the
     * {@code name} headers.
     *
     * @param name The header name that must be present in the request in order to continue evaluation of this route.
     * @return {@link StringMultiValueMatcher} for the next steps of building a route.
     */
    StringMultiValueMatcher andHeader(CharSequence name);

    /**
     * Extends the current route with a {@link CookieMatcher} that matches against {@link HttpCookie}s with the name
     * {@code name}.
     *
     * @param name the cookie name that must be present in the request in order to continue evaluation of this route.
     * @return {@link CookieMatcher} for the next steps of building a route.
     */
    CookieMatcher andCookie(String name);

    /**
     * Extends the current route with a {@link CookieMatcher} that matches against {@link HttpCookie}s with a name
     * matching the regex {@code regex}.
     *
     * @param regex the regex to match against the cookie name.
     * @return {@link CookieMatcher} for the next steps of building a route.
     */
    CookieMatcher andCookieNameMatches(String regex);

    /**
     * Extends the current route with a {@link CookieMatcher} that matches against {@link HttpCookie}s with a name
     * matching the regex {@code regex}.
     *
     * @param regex the regex to match against the cookie name.
     * @return {@link CookieMatcher} for the next steps of building a route.
     */
    CookieMatcher andCookieNameMatches(Pattern regex);

    /**
     * Extends the current route such that it matches requests that are over SSL/TLS.
     *
     * @return {@link RouteContinuation} for the next steps of building a route.
     */
    RouteContinuation andIsSsl();

    /**
     * Extends the current route such that it matches requests that are not over SSL/TLS.
     *
     * @return {@link RouteContinuation} for the next steps of building a route.
     */
    RouteContinuation andIsNotSsl();

    /**
     * Extends the current route such that it matches {@link HttpRequest}s with a user-specified {@code predicate}.
     *
     * @param predicate the predicate to evaluate against requests.
     * @return {@link RouteContinuation} for the next steps of building a route.
     */
    RouteContinuation and(Predicate<HttpRequest<HttpPayloadChunk>> predicate);

    /**
     * Extends the current route such that it matches {@link HttpRequest} and {@link ConnectionContext} with a
     * user-specified {@code predicate}.
     *
     * @param predicate the predicate to evaluate against the request and connection context.
     * @return {@link RouteContinuation} for the next steps of building a route.
     */
    RouteContinuation and(BiPredicate<ConnectionContext, HttpRequest<HttpPayloadChunk>> predicate);

    /**
     * Completes the route by specifying the {@link HttpService} to route requests to that match the previously
     * specified criteria. Each call to {@code thenRouteTo} resets the criteria, prior to building the next route.
     *
     * @param service the {@link HttpService} to route requests to.
     * @return {@link RouteStarter} for building another route.
     */
    RouteStarter thenRouteTo(HttpService service);
}
