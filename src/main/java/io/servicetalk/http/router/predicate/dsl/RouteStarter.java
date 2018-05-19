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
 * Methods for starting a route.
 */
public interface RouteStarter {
    /**
     * Matches requests where the method is {@code method}.
     * @param method the method to match.
     * @return {@link RouteContinuation} for the next steps of building a route.
     */
    RouteContinuation whenMethod(HttpRequestMethod method);

    /**
     * Matches requests where the method is one of the {@code methods}.
     * @param methods the methods to match.
     * @return {@link RouteContinuation} for the next steps of building a route.
     */
    RouteContinuation whenMethodIsOneOf(HttpRequestMethod... methods);

    /**
     * Matches requests where the path is equal to {@code path}.
     * @param path the path to match.
     * @return {@link RouteContinuation} for the next steps of building a route.
     */
    RouteContinuation whenPathEquals(String path);

    /**
     * Matches requests where the path is equal to any of the specified {@code path}s.
     * @param paths the paths to match.
     * @return {@link RouteContinuation} for the next steps of building a route.
     */
    RouteContinuation whenPathIsOneOf(String... paths);

    /**
     * Matches requests where the path starts with {@code pathPrefix}.
     * @param pathPrefix the path prefix to match.
     * @return {@link RouteContinuation} for the next steps of building a route.
     */
    RouteContinuation whenPathStartsWith(String pathPrefix);

    /**
     * Matches requests where the path matches the regex {@code pathRegex}.
     * @param pathRegex the regex to match against the request path.
     * @return {@link RouteContinuation} for the next steps of building a route.
     */
    RouteContinuation whenPathMatches(String pathRegex);

    /**
     * Matches requests where the path matches the regex {@code pathRegex}.
     * @param pathRegex the regex to match against the request path.
     * @return {@link RouteContinuation} for the next steps of building a route.
     */
    RouteContinuation whenPathMatches(Pattern pathRegex);

    /**
     * Begins a builder that matches against the value(s) of the request parameter {@code name}.
     * @param name the request parameter to match.
     * @return {@link StringMultiValueMatcher} for the next steps of building a route.
     */
    StringMultiValueMatcher whenQueryParam(String name);

    /**
     * Begins a builder that matches against the value(s) of the {@code name} headers.
     * @param name the header name to match.
     * @return {@link StringMultiValueMatcher} for the next steps of building a route.
     */
    StringMultiValueMatcher whenHeader(CharSequence name);

    /**
     * Begins a builder that matches against cookies with the name {@code name}.
     * @param name the cookie name to match.
     * @return {@link CookieMatcher} for the next steps of building a route.
     */
    CookieMatcher whenCookie(String name);

    /**
     * Begins a builder that matches against cookies with a name matching the regex {@code regex}.
     * @param regex the regex to match against the cookie name.
     * @return {@link CookieMatcher} for the next steps of building a route.
     */
    CookieMatcher whenCookieNameMatches(String regex);

    /**
     * Begins a builder that matches against cookies with a name matching the regex {@code regex}.
     * @param regex the regex to match against the cookie name.
     * @return {@link CookieMatcher} for the next steps of building a route.
     */
    CookieMatcher whenCookieNameMatches(Pattern regex);

    /**
     * Matches requests that are over SSL/TLS.
     * @return {@link RouteContinuation} for the next steps of building a route.
     */
    RouteContinuation whenIsSsl();

    /**
     * Matches requests that are not over SSL/TLS.
     * @return {@link RouteContinuation} for the next steps of building a route.
     */
    RouteContinuation whenIsNotSsl();

    /**
     * Matches requests with a user-specified {@code predicate}.
     * @param predicate the predicate to evaluate against requests.
     * @return {@link RouteContinuation} for the next steps of building a route.
     */
    RouteContinuation when(Predicate<HttpRequest<HttpPayloadChunk>> predicate);

    /**
     * Matches request and connection context with a user-specified {@code predicate}.
     * @param predicate the predicate to evaluate against the request and connection context.
     */
    RouteContinuation when(BiPredicate<ConnectionContext, HttpRequest<HttpPayloadChunk>> predicate);

    /**
     * Builds the {@link HttpService} that performs the configured routing.
     * @return the router {@link HttpService}.
     */
    HttpService build();
}
