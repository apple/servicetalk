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

import io.servicetalk.http.api.HttpCookie;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;

import java.util.Iterator;
import java.util.function.Predicate;

/**
 * Methods for route matching on cookies.
 *
 * @param <I> the type of the content in the {@link HttpRequest}s.
 * @param <O> the type of the content in the {@link HttpResponse}s.
 */
public interface CookieMatcher<I, O> {
    /**
     * Matches requests where the specified cookie is present.
     * @return {@link RouteContinuation} for the next steps of building a route.
     */
    RouteContinuation<I, O> isPresent();

    /**
     * Matches requests where one of the cookies with the specified name matches {@code predicate}.
     * @param predicate the {@link Predicate} to match against the values.
     * @return {@link RouteContinuation} for the next steps of building a route.
     */
    RouteContinuation<I, O> value(Predicate<HttpCookie> predicate);

    /**
     * Matches requests where the list of cookies for the specified name matches the predicate.
     * @param predicate the {@link Predicate} to match against the list of cookies.
     * @return {@link RouteContinuation} for the next steps of building a route.
     */
    RouteContinuation<I, O> values(Predicate<Iterator<? extends HttpCookie>> predicate);
}
