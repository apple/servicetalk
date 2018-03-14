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
package io.servicetalk.http.router.predicate;

import io.servicetalk.http.api.HttpCookie;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.router.predicate.dsl.CookieMatcher;
import io.servicetalk.http.router.predicate.dsl.RouteContinuation;
import io.servicetalk.http.router.predicate.dsl.RouteStarter;
import io.servicetalk.http.router.predicate.dsl.StringMultiValueMatcher;
import io.servicetalk.transport.api.ConnectionContext;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static io.servicetalk.http.router.predicate.Predicates.method;
import static io.servicetalk.http.router.predicate.Predicates.methodIsOneOf;
import static io.servicetalk.http.router.predicate.Predicates.pathEquals;
import static io.servicetalk.http.router.predicate.Predicates.pathIsOneOf;
import static io.servicetalk.http.router.predicate.Predicates.pathRegex;
import static io.servicetalk.http.router.predicate.Predicates.pathStartsWith;
import static io.servicetalk.http.router.predicate.Predicates.regex;
import static java.util.Objects.requireNonNull;
import static java.util.stream.StreamSupport.stream;

/**
 * Builds an {@link HttpService} which routes requests to a number of other {@link HttpService}s based on user
 * specified criteria.
 *
 * eg.
 * <pre>{@code
 * final HttpService<HttpChunk, HttpChunk> router = new HttpPredicateRouterBuilder<HttpChunk, HttpChunk>()
 *     .whenMethod(GET).andPathStartsWith("/a/").thenRouteTo(serviceA)
 *     .whenMethod(GET).andPathStartsWith("/b/").thenRouteTo(serviceB)
 *     .whenMethod(POST).thenRouteTo(serviceC)
 *     .build();
 * }</pre>
 * <p>
 * If no routes match, a default service is used, which returns a 404 response.
 */
public final class HttpPredicateRouterBuilder<I, O> implements RouteStarter<I, O> {

    private final List<PredicateServicePair<I, O>> predicateServicePairs = new ArrayList<>();
    @Nullable
    private BiPredicate<ConnectionContext, HttpRequest<I>> predicate;
    private final RouteContinuationImpl continuation = new RouteContinuationImpl();

    @Override
    public RouteContinuation<I, O> whenMethod(final HttpRequestMethod method) {
        andPredicate(method(method));
        return continuation;
    }

    @Override
    public RouteContinuation<I, O> whenMethodIsOneOf(final HttpRequestMethod... methods) {
        andPredicate(methodIsOneOf(methods));
        return continuation;
    }

    @Override
    public RouteContinuation<I, O> whenPathEquals(final String path) {
        andPredicate(pathEquals(path));
        return continuation;
    }

    @Override
    public RouteContinuation<I, O> whenPathIsOneOf(final String... paths) {
        andPredicate(pathIsOneOf(paths));
        return continuation;
    }

    @Override
    public RouteContinuation<I, O> whenPathStartsWith(final String pathPrefix) {
        andPredicate(pathStartsWith(pathPrefix));
        return continuation;
    }

    @Override
    public RouteContinuation<I, O> whenPathMatches(final String pathRegex) {
        andPredicate(pathRegex(pathRegex));
        return continuation;
    }

    @Override
    public RouteContinuation<I, O> whenPathMatches(final Pattern pathRegex) {
        andPredicate(pathRegex(pathRegex));
        return continuation;
    }

    @Override
    public StringMultiValueMatcher<I, O> whenQueryParam(final String name) {
        requireNonNull(name);
        return new StringMultiValueMatcherImpl(req -> req.getQueryValues(name));
    }

    @Override
    public StringMultiValueMatcher<I, O> whenHeader(final CharSequence name) {
        requireNonNull(name);
        return new StringMultiValueMatcherImpl(req -> req.getHeaders().getAll(name));
    }

    @Override
    public CookieMatcher<I, O> whenCookie(final String name) {
        requireNonNull(name);
        return new CookieMatcherImpl(req -> req.getHeaders().parseCookies().getCookies(name));
    }

    @Override
    public CookieMatcher<I, O> whenCookieNameMatches(final String regex) {
        return new CookieMatcherImpl(getCookiesWithNameFunction(regex(regex)));
    }

    @Override
    public CookieMatcher<I, O> whenCookieNameMatches(final Pattern regex) {
        return new CookieMatcherImpl(getCookiesWithNameFunction(regex(regex)));
    }

    @Override
    public RouteContinuation<I, O> whenIsSsl() {
        andPredicate((ctx, req) -> ctx.getSslSession() != null);
        return continuation;
    }

    @Override
    public RouteContinuation<I, O> whenIsNotSsl() {
        andPredicate((ctx, req) -> ctx.getSslSession() == null);
        return continuation;
    }

    @Override
    public RouteContinuation<I, O> when(final Predicate<HttpRequest<I>> predicate) {
        requireNonNull(predicate);
        andPredicate((ctx, req) -> predicate.test(req));
        return continuation;
    }

    @Override
    public RouteContinuation<I, O> when(final BiPredicate<ConnectionContext, HttpRequest<I>> predicate) {
        andPredicate(requireNonNull(predicate));
        return continuation;
    }

    @Override
    public HttpService<I, O> build() {
        return new InOrderRouter<>(DefaultFallbackService.instance(), predicateServicePairs);
    }

    private void andPredicate(final BiPredicate<ConnectionContext, HttpRequest<I>> newPredicate) {
        if (predicate == null) {
            predicate = newPredicate;
        } else {
            predicate = predicate.and(newPredicate);
        }
    }

    @Nonnull
    private Function<HttpRequest<I>, Iterator<? extends HttpCookie>> getCookiesWithNameFunction(final Predicate<CharSequence> cookieNamePredicate) {
        return req -> stream(req.getHeaders().parseCookies().spliterator(), false)
                .filter(cookie -> cookieNamePredicate.test(cookie.getName())).iterator();
    }

    private class RouteContinuationImpl implements RouteContinuation<I, O> {

        @Override
        public RouteContinuation<I, O> andMethod(final HttpRequestMethod method) {
            return whenMethod(method);
        }

        @Override
        public RouteContinuation<I, O> andMethodIsOneOf(final HttpRequestMethod... methods) {
            return whenMethodIsOneOf(methods);
        }

        @Override
        public RouteContinuation<I, O> andPathEquals(final String path) {
            return whenPathEquals(path);
        }

        @Override
        public RouteContinuation<I, O> andPathIsOneOf(final String... paths) {
            return whenPathIsOneOf(paths);
        }

        @Override
        public RouteContinuation<I, O> andPathStartsWith(final String pathPrefix) {
            return whenPathStartsWith(pathPrefix);
        }

        @Override
        public RouteContinuation<I, O> andPathMatches(final String pathRegex) {
            return whenPathMatches(pathRegex);
        }

        @Override
        public RouteContinuation<I, O> andPathMatches(final Pattern pathRegex) {
            return whenPathMatches(pathRegex);
        }

        @Override
        public StringMultiValueMatcher<I, O> andQueryParam(final String name) {
            return whenQueryParam(name);
        }

        @Override
        public StringMultiValueMatcher<I, O> andHeader(final CharSequence name) {
            return whenHeader(name);
        }

        @Override
        public CookieMatcher<I, O> andCookie(final String name) {
            return whenCookie(name);
        }

        @Override
        public CookieMatcher<I, O> andCookieNameMatches(final String regex) {
            return whenCookieNameMatches(regex);
        }

        @Override
        public CookieMatcher<I, O> andCookieNameMatches(final Pattern regex) {
            return whenCookieNameMatches(regex);
        }

        @Override
        public RouteContinuation<I, O> andIsSsl() {
            return whenIsSsl();
        }

        @Override
        public RouteContinuation<I, O> andIsNotSsl() {
            return whenIsNotSsl();
        }

        @Override
        public RouteContinuation<I, O> and(final Predicate<HttpRequest<I>> predicate) {
            return when(predicate);
        }

        @Override
        public RouteContinuation<I, O> and(final BiPredicate<ConnectionContext, HttpRequest<I>> predicate) {
            return when(predicate);
        }

        @Override
        public RouteStarter<I, O> thenRouteTo(final HttpService<I, O> service) {
            assert predicate != null;
            predicateServicePairs.add(new PredicateServicePair<>(predicate, service));
            predicate = null;
            return HttpPredicateRouterBuilder.this;
        }
    }

    private class CookieMatcherImpl implements CookieMatcher<I, O> {
        private final Function<HttpRequest<I>, Iterator<? extends HttpCookie>> itemsSource;

        CookieMatcherImpl(final Function<HttpRequest<I>, Iterator<? extends HttpCookie>> itemsSource) {
            this.itemsSource = itemsSource;
        }

        @Override
        public RouteContinuation<I, O> isPresent() {
            return value(v -> true);
        }

        @Override
        public RouteContinuation<I, O> value(final Predicate<HttpCookie> predicate) {
            return values((cookies) -> {
                while (cookies.hasNext()) {
                    if (predicate.test(cookies.next())) {
                        return true;
                    }
                }
                return false;
            });
        }

        @Override
        public RouteContinuation<I, O> values(final Predicate<Iterator<? extends HttpCookie>> predicate) {
            andPredicate((ctx, req) -> predicate.test(itemsSource.apply(req)));
            return continuation;
        }
    }

    private class StringMultiValueMatcherImpl implements StringMultiValueMatcher<I, O> {
        private final Function<HttpRequest<I>, Iterator<? extends CharSequence>> itemsSource;

        StringMultiValueMatcherImpl(final Function<HttpRequest<I>, Iterator<? extends CharSequence>> itemsSource) {
            this.itemsSource = itemsSource;
        }

        @Override
        public RouteContinuation<I, O> isPresent() {
            return firstValue(value -> true);
        }

        @Override
        public RouteContinuation<I, O> firstValue(final CharSequence value) {
            return firstValue(value::equals);
        }

        @Override
        public RouteContinuation<I, O> firstValue(final Predicate<CharSequence> predicate) {
            requireNonNull(predicate);
            return values(iterator -> iterator.hasNext() && predicate.test(iterator.next()));
        }

        @Override
        public RouteContinuation<I, O> values(final Predicate<Iterator<? extends CharSequence>> predicate) {
            andPredicate((ctx, req) -> predicate.test(itemsSource.apply(req)));
            return continuation;
        }

        @Override
        public RouteContinuation<I, O> firstValueMatches(final String regex) {
            return firstValue(regex(regex));
        }

        @Override
        public RouteContinuation<I, O> firstValueMatches(final Pattern regex) {
            return firstValue(regex(regex));
        }
    }
}
