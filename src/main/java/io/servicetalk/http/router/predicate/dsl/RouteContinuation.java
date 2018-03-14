/**
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

import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.transport.api.ConnectionContext;

import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Methods for continuing a route.
 *
 * @param <I> the type of the content in the {@link HttpRequest}s.
 * @param <O> the type of the content in the {@link HttpResponse}s.
 */
public interface RouteContinuation<I, O> {
    /**
     * See {@link RouteStarter#whenMethod(HttpRequestMethod)}.
     */
    RouteContinuation<I, O> andMethod(HttpRequestMethod method);

    /**
     * See {@link RouteStarter#whenMethodIsOneOf(HttpRequestMethod...)}.
     */
    RouteContinuation<I, O> andMethodIsOneOf(HttpRequestMethod... methods);

    /**
     * See {@link RouteStarter#whenPathEquals(String)}.
     */
    RouteContinuation<I, O> andPathEquals(String path);

    /**
     * See {@link RouteStarter#whenPathIsOneOf(String...)}.
     */
    RouteContinuation<I, O> andPathIsOneOf(String... paths);

    /**
     * See {@link RouteStarter#whenPathStartsWith(String)}.
     */
    RouteContinuation<I, O> andPathStartsWith(String pathPrefix);

    /**
     * See {@link RouteStarter#whenPathMatches(String)}.
     */
    RouteContinuation<I, O> andPathMatches(String pathRegex);

    /**
     * See {@link RouteStarter#whenPathMatches(Pattern)}.
     */
    RouteContinuation<I, O> andPathMatches(Pattern pathRegex);

    /**
     * See {@link RouteStarter#whenQueryParam(String)}.
     */
    StringMultiValueMatcher<I, O> andQueryParam(String name);

    /**
     * See {@link RouteStarter#whenHeader(CharSequence)}.
     */
    StringMultiValueMatcher<I, O> andHeader(CharSequence name);

    /**
     * See {@link RouteStarter#whenCookie(String)}.
     */
    CookieMatcher<I, O> andCookie(String name);

    /**
     * See {@link RouteStarter#whenCookieNameMatches(String)}.
     */
    CookieMatcher<I, O> andCookieNameMatches(String regex);

    /**
     * See {@link RouteStarter#whenCookieNameMatches(Pattern)}.
     */
    CookieMatcher<I, O> andCookieNameMatches(Pattern regex);

    /**
     * See {@link RouteStarter#whenIsSsl()}.
     */
    RouteContinuation<I, O> andIsSsl();

    /**
     * See {@link RouteStarter#whenIsNotSsl()}.
     */
    RouteContinuation<I, O> andIsNotSsl();

    /**
     * See {@link RouteStarter#when(Predicate)}.
     */
    RouteContinuation<I, O> and(Predicate<HttpRequest<I>> predicate);

    /**
     * See {@link RouteStarter#when(BiPredicate)}.
     */
    RouteContinuation<I, O> and(BiPredicate<ConnectionContext, HttpRequest<I>> predicate);

    /**
     * Specifies the {@link HttpService} to route requests to that match the previously specified criteria.
     * Each call to {@code thenRouteTo} resets the criteria, prior to building the next route.
     * @param service the {@link HttpService} to route requests to.
     * {@link RouteStarter} for building another route.
     */
    RouteStarter<I, O> thenRouteTo(HttpService<I, O> service);
}
