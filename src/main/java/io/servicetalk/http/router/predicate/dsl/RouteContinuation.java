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
     * See {@link RouteStarter#whenMethod(HttpRequestMethod)}.
     */
    RouteContinuation andMethod(HttpRequestMethod method);

    /**
     * See {@link RouteStarter#whenMethodIsOneOf(HttpRequestMethod...)}.
     */
    RouteContinuation andMethodIsOneOf(HttpRequestMethod... methods);

    /**
     * See {@link RouteStarter#whenPathEquals(String)}.
     */
    RouteContinuation andPathEquals(String path);

    /**
     * See {@link RouteStarter#whenPathIsOneOf(String...)}.
     */
    RouteContinuation andPathIsOneOf(String... paths);

    /**
     * See {@link RouteStarter#whenPathStartsWith(String)}.
     */
    RouteContinuation andPathStartsWith(String pathPrefix);

    /**
     * See {@link RouteStarter#whenPathMatches(String)}.
     */
    RouteContinuation andPathMatches(String pathRegex);

    /**
     * See {@link RouteStarter#whenPathMatches(Pattern)}.
     */
    RouteContinuation andPathMatches(Pattern pathRegex);

    /**
     * See {@link RouteStarter#whenQueryParam(String)}.
     */
    StringMultiValueMatcher andQueryParam(String name);

    /**
     * See {@link RouteStarter#whenHeader(CharSequence)}.
     */
    StringMultiValueMatcher andHeader(CharSequence name);

    /**
     * See {@link RouteStarter#whenCookie(String)}.
     */
    CookieMatcher andCookie(String name);

    /**
     * See {@link RouteStarter#whenCookieNameMatches(String)}.
     */
    CookieMatcher andCookieNameMatches(String regex);

    /**
     * See {@link RouteStarter#whenCookieNameMatches(Pattern)}.
     */
    CookieMatcher andCookieNameMatches(Pattern regex);

    /**
     * See {@link RouteStarter#whenIsSsl()}.
     */
    RouteContinuation andIsSsl();

    /**
     * See {@link RouteStarter#whenIsNotSsl()}.
     */
    RouteContinuation andIsNotSsl();

    /**
     * See {@link RouteStarter#when(Predicate)}.
     */
    RouteContinuation and(Predicate<HttpRequest<HttpPayloadChunk>> predicate);

    /**
     * See {@link RouteStarter#when(Predicate)}.
     */
    RouteContinuation and(BiPredicate<ConnectionContext, HttpRequest<HttpPayloadChunk>> predicate);

    /**
     * Specifies the {@link HttpService} to route requests to that match the previously specified criteria.
     * Each call to {@code thenRouteTo} resets the criteria, prior to building the next route.
     * @param service the {@link HttpService} to route requests to.
     * {@link RouteStarter} for building another route.
     */
    RouteStarter thenRouteTo(HttpService service);
}
