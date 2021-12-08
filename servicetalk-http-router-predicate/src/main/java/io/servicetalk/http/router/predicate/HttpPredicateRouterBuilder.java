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
package io.servicetalk.http.router.predicate;

import io.servicetalk.http.api.BlockingHttpService;
import io.servicetalk.http.api.BlockingStreamingHttpService;
import io.servicetalk.http.api.HttpApiConversions.ServiceAdapterHolder;
import io.servicetalk.http.api.HttpCookiePair;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.router.predicate.dsl.CookieMatcher;
import io.servicetalk.http.router.predicate.dsl.RouteContinuation;
import io.servicetalk.http.router.predicate.dsl.RouteStarter;
import io.servicetalk.http.router.predicate.dsl.StringMultiValueMatcher;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionStrategyInfluencer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpApiConversions.toStreamingHttpService;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.router.predicate.Predicates.method;
import static io.servicetalk.http.router.predicate.Predicates.methodIsOneOf;
import static io.servicetalk.http.router.predicate.Predicates.pathEquals;
import static io.servicetalk.http.router.predicate.Predicates.pathIsOneOf;
import static io.servicetalk.http.router.predicate.Predicates.pathRegex;
import static io.servicetalk.http.router.predicate.Predicates.pathStartsWith;
import static io.servicetalk.http.router.predicate.Predicates.regex;
import static java.util.Objects.requireNonNull;

/**
 * Builds a {@link StreamingHttpService} which routes requests to a number of other {@link StreamingHttpService}s based
 * on user specified criteria.
 * <p>
 * eg.
 * <pre>{@code
 * final StreamingHttpService<HttpChunk, HttpChunk> router = new HttpPredicateRouterBuilder<HttpChunk, HttpChunk>()
 *     .whenMethod(GET).andPathStartsWith("/a/").thenRouteTo(serviceA)
 *     .whenMethod(GET).andPathStartsWith("/b/").thenRouteTo(serviceB)
 *     .whenMethod(POST).thenRouteTo(serviceC)
 *     .buildStreaming();
 * }</pre>
 * <p>
 * If no routes match, a default service is used, which returns a 404 response.
 *
 */
public final class HttpPredicateRouterBuilder implements RouteStarter {
    private final List<Route> routes = new ArrayList<>();
    private final RouteContinuationImpl continuation = new RouteContinuationImpl();
    @Nullable
    private BiPredicate<ConnectionContext, StreamingHttpRequest> predicate;

    @Override
    public RouteContinuation whenMethod(final HttpRequestMethod method) {
        andPredicate(method(method));
        return continuation;
    }

    @Override
    public RouteContinuation whenMethodIsOneOf(final HttpRequestMethod... methods) {
        andPredicate(methodIsOneOf(methods));
        return continuation;
    }

    @Override
    public RouteContinuation whenPathEquals(final String path) {
        andPredicate(pathEquals(path));
        return continuation;
    }

    @Override
    public RouteContinuation whenPathIsOneOf(final String... paths) {
        andPredicate(pathIsOneOf(paths));
        return continuation;
    }

    @Override
    public RouteContinuation whenPathStartsWith(final String pathPrefix) {
        andPredicate(pathStartsWith(pathPrefix));
        return continuation;
    }

    @Override
    public RouteContinuation whenPathMatches(final String pathRegex) {
        andPredicate(pathRegex(pathRegex));
        return continuation;
    }

    @Override
    public RouteContinuation whenPathMatches(final Pattern pathRegex) {
        andPredicate(pathRegex(pathRegex));
        return continuation;
    }

    @Override
    public StringMultiValueMatcher whenQueryParam(final String name) {
        requireNonNull(name);
        return new StringMultiValueMatcherImpl(req -> req.queryParametersIterator(name));
    }

    @Override
    public StringMultiValueMatcher whenHeader(final CharSequence name) {
        requireNonNull(name);
        return new StringMultiValueMatcherImpl(req -> req.headers().valuesIterator(name));
    }

    @Override
    public CookieMatcher whenCookie(final String name) {
        requireNonNull(name);
        return new CookieMatcherImpl(req -> req.headers().getCookiesIterator(name));
    }

    @Override
    public RouteContinuation whenIsSsl() {
        andPredicate((ctx, req) -> ctx.sslSession() != null);
        return continuation;
    }

    @Override
    public RouteContinuation whenIsNotSsl() {
        andPredicate((ctx, req) -> ctx.sslSession() == null);
        return continuation;
    }

    @Override
    public RouteContinuation when(final Predicate<StreamingHttpRequest> predicate) {
        requireNonNull(predicate);
        andPredicate((ctx, req) -> predicate.test(req));
        return continuation;
    }

    @Override
    public RouteContinuation when(final BiPredicate<ConnectionContext, StreamingHttpRequest> predicate) {
        andPredicate(requireNonNull(predicate));
        return continuation;
    }

    @Override
    public StreamingHttpService buildStreaming() {
        return new InOrderRouter(DefaultFallbackServiceStreaming.instance(), routes);
    }

    private void andPredicate(final BiPredicate<ConnectionContext, StreamingHttpRequest> newPredicate) {
        if (predicate == null) {
            predicate = newPredicate;
        } else {
            predicate = predicate.and(newPredicate);
        }
    }

    private class RouteContinuationImpl implements RouteContinuation {

        @Nullable
        private HttpExecutionStrategy strategy;

        @Override
        public RouteContinuation andMethod(final HttpRequestMethod method) {
            return whenMethod(method);
        }

        @Override
        public RouteContinuation andMethodIsOneOf(final HttpRequestMethod... methods) {
            return whenMethodIsOneOf(methods);
        }

        @Override
        public RouteContinuation andPathEquals(final String path) {
            return whenPathEquals(path);
        }

        @Override
        public RouteContinuation andPathIsOneOf(final String... paths) {
            return whenPathIsOneOf(paths);
        }

        @Override
        public RouteContinuation andPathStartsWith(final String pathPrefix) {
            return whenPathStartsWith(pathPrefix);
        }

        @Override
        public RouteContinuation andPathMatches(final String pathRegex) {
            return whenPathMatches(pathRegex);
        }

        @Override
        public RouteContinuation andPathMatches(final Pattern pathRegex) {
            return whenPathMatches(pathRegex);
        }

        @Override
        public StringMultiValueMatcher andQueryParam(final String name) {
            return whenQueryParam(name);
        }

        @Override
        public StringMultiValueMatcher andHeader(final CharSequence name) {
            return whenHeader(name);
        }

        @Override
        public CookieMatcher andCookie(final String name) {
            return whenCookie(name);
        }

        @Override
        public RouteContinuation andIsSsl() {
            return whenIsSsl();
        }

        @Override
        public RouteContinuation andIsNotSsl() {
            return whenIsNotSsl();
        }

        @Override
        public RouteContinuation and(final Predicate<StreamingHttpRequest> predicate) {
            return when(predicate);
        }

        @Override
        public RouteContinuation and(final BiPredicate<ConnectionContext, StreamingHttpRequest> predicate) {
            return when(predicate);
        }

        @Override
        public RouteContinuation executionStrategy(final HttpExecutionStrategy routeStrategy) {
            strategy = Objects.requireNonNull(routeStrategy);
            return this;
        }

        @Override
        public RouteStarter thenRouteTo(final StreamingHttpService service) {
            return thenRouteTo0(service, serviceOffloads(service));
        }

        @Override
        public RouteStarter thenRouteTo(final HttpService service) {
            final ServiceAdapterHolder adapterHolder = toStreamingHttpService(service, serviceOffloads(service));
            return thenRouteTo0(adapterHolder.adaptor(), adapterHolder.serviceInvocationStrategy());
        }

        @Override
        public RouteStarter thenRouteTo(final BlockingHttpService service) {
            final ServiceAdapterHolder adapterHolder = toStreamingHttpService(service, serviceOffloads(service));
            return thenRouteTo0(adapterHolder.adaptor(), adapterHolder.serviceInvocationStrategy());
        }

        @Override
        public RouteStarter thenRouteTo(final BlockingStreamingHttpService service) {
            final ServiceAdapterHolder adapterHolder = toStreamingHttpService(service, serviceOffloads(service));
            return thenRouteTo0(adapterHolder.adaptor(), adapterHolder.serviceInvocationStrategy());
        }

        private HttpExecutionStrategy serviceOffloads(final Object service) {
            return null != strategy ? strategy :
                    service instanceof ExecutionStrategyInfluencer ?
                            HttpExecutionStrategy.from(((ExecutionStrategyInfluencer) service).requiredOffloads()) :
                            defaultStrategy();
        }

        private RouteStarter thenRouteTo0(final StreamingHttpService route,
                                          @Nullable final HttpExecutionStrategy routeStrategy) {
            assert predicate != null;
            routes.add(new Route(predicate, route, null == strategy ? null : routeStrategy));
            // Reset shared state since we have finished current route construction
            predicate = null;
            strategy = null;
            return HttpPredicateRouterBuilder.this;
        }
    }

    private class CookieMatcherImpl implements CookieMatcher {
        private final Function<StreamingHttpRequest, Iterator<? extends HttpCookiePair>> itemsSource;

        CookieMatcherImpl(final Function<StreamingHttpRequest, Iterator<? extends HttpCookiePair>> itemsSource) {
            this.itemsSource = itemsSource;
        }

        @Override
        public RouteContinuation isPresent() {
            return value(v -> true);
        }

        @Override
        public RouteContinuation value(final Predicate<HttpCookiePair> predicate) {
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
        public RouteContinuation values(final Predicate<Iterator<? extends HttpCookiePair>> predicate) {
            andPredicate((ctx, req) -> predicate.test(itemsSource.apply(req)));
            return continuation;
        }
    }

    private class StringMultiValueMatcherImpl implements StringMultiValueMatcher {
        private final Function<StreamingHttpRequest, Iterator<? extends CharSequence>> itemsSource;

        StringMultiValueMatcherImpl(final Function<StreamingHttpRequest,
                Iterator<? extends CharSequence>> itemsSource) {
            this.itemsSource = itemsSource;
        }

        @Override
        public RouteContinuation isPresent() {
            return firstValue(value -> true);
        }

        @Override
        public RouteContinuation firstValue(final CharSequence value) {
            return firstValue(value::equals);
        }

        @Override
        public RouteContinuation firstValue(final Predicate<CharSequence> predicate) {
            requireNonNull(predicate);
            return values(iterator -> iterator.hasNext() && predicate.test(iterator.next()));
        }

        @Override
        public RouteContinuation values(final Predicate<Iterator<? extends CharSequence>> predicate) {
            andPredicate((ctx, req) -> predicate.test(itemsSource.apply(req)));
            return continuation;
        }

        @Override
        public RouteContinuation firstValueMatches(final String regex) {
            return firstValue(regex(regex));
        }

        @Override
        public RouteContinuation firstValueMatches(final Pattern regex) {
            return firstValue(regex(regex));
        }
    }
}
