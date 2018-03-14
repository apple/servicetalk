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

import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.transport.api.ConnectionContext;

import java.util.Iterator;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.regex.Pattern.compile;
import static java.util.stream.Collectors.toList;

final class Predicates {

    private Predicates() {
        // static helper class
    }

    static <I> BiPredicate<ConnectionContext, HttpRequest<I>> method(final HttpRequestMethod method) {
        requireNonNull(method);
        return (ctx, req) -> req.getMethod().equals(method);
    }

    static <I> BiPredicate<ConnectionContext, HttpRequest<I>> methodIsOneOf(final HttpRequestMethod... methods) {
        return orBiPredicates(Stream.of(methods).map(Predicates::<I>method).collect(toList()));
    }

    static <I> BiPredicate<ConnectionContext, HttpRequest<I>> pathEquals(final String path) {
        requireNonNull(path);
        return (ctx, req) -> req.getPath().equals(path);
    }

    static <I> BiPredicate<ConnectionContext, HttpRequest<I>> pathIsOneOf(final String... paths) {
        return orBiPredicates(Stream.of(paths).map(Predicates::<I>pathEquals).collect(toList()));
    }

    static <I> BiPredicate<ConnectionContext, HttpRequest<I>> pathStartsWith(final String pathPrefix) {
        requireNonNull(pathPrefix);
        return (ctx, req) -> req.getPath().startsWith(pathPrefix);
    }

    static <I> BiPredicate<ConnectionContext, HttpRequest<I>> pathRegex(final String regex) {
        final Predicate<CharSequence> regexPredicate = regex(regex);
        return (ctx, req) -> regexPredicate.test(req.getPath());
    }

    static <I> BiPredicate<ConnectionContext, HttpRequest<I>> pathRegex(final Pattern regex) {
        final Predicate<CharSequence> regexPredicate = regex(regex);
        return (ctx, req) -> regexPredicate.test(req.getPath());
    }

    static Predicate<CharSequence> regex(final String regex) {
        return regex(compile(regex));
    }

    static Predicate<CharSequence> regex(final Pattern pattern) {
        return (value) -> pattern.matcher(value).matches();
    }

    static <T, U> BiPredicate<T, U> orBiPredicates(final Iterable<BiPredicate<T, U>> predicates) {
        final Iterator<BiPredicate<T, U>> iterator = predicates.iterator();
        if (!iterator.hasNext()) {
            return (ctx, req) -> true;
        }
        BiPredicate<T, U> predicate = iterator.next();
        while (iterator.hasNext()) {
            predicate = predicate.or(iterator.next());
        }
        return predicate;
    }
}
