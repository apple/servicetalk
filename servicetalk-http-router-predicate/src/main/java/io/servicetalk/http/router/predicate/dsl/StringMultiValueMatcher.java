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

import java.util.Iterator;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Extends {@link CookieMatcher} for things where the value is a string, like headers and query parameters.
 */
public interface StringMultiValueMatcher {

    /**
     * Matches requests where the list of values is not empty.
     * @return {@link RouteContinuation} for the next steps of building a route.
     */
    RouteContinuation isPresent();

    /**
     * Matches requests where the first value in the request is equal to {@code value}.
     * @param value the value to match against.
     * @return {@link RouteContinuation} for the next steps of building a route.
     */
    RouteContinuation firstValue(CharSequence value);

    /**
     * Matches requests where the first value in the request matches {@code predicate}.
     * @param predicate the {@link Predicate} to match against the values.
     * @return {@link RouteContinuation} for the next steps of building a route.
     */
    RouteContinuation firstValue(Predicate<CharSequence> predicate);

    /**
     * Matches requests where the list of values matches the predicate.
     * @param predicate the {@link Predicate} to match against the list of values.
     * @return {@link RouteContinuation} for the next steps of building a route.
     */
    RouteContinuation values(Predicate<Iterator<? extends CharSequence>> predicate);

    /**
     * Matches requests where the first value in the request matches the regex {@code regex}.
     * @param regex the regex to match against the values.
     * @return {@link RouteContinuation} for the next steps of building a route.
     */
    RouteContinuation firstValueMatches(String regex);

    /**
     * Matches requests where the first value in the request matches the regex {@code regex}.
     * @param regex the regex to match against the values.
     * @return {@link RouteContinuation} for the next steps of building a route.
     */
    RouteContinuation firstValueMatches(Pattern regex);
}
