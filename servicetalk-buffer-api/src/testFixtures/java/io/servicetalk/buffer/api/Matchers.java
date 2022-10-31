/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.buffer.api;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.CharSequences.contentEquals;
import static io.servicetalk.buffer.api.CharSequences.contentEqualsIgnoreCase;
import static java.util.Objects.requireNonNull;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;

/**
 * Custom {@link Matcher}s specific to http-api.
 */
public final class Matchers {

    private Matchers() {
        // No instances
    }

    /**
     * {@link Matcher} representation for {@link CharSequences#contentEquals(CharSequence, CharSequence)}.
     *
     * @param expected expected {@link CharSequence} value
     * @return a {@link Matcher} to verify content equality of two {@link CharSequence}s
     */
    public static Matcher<CharSequence> contentEqualTo(final CharSequence expected) {
        requireNonNull(expected);
        return new TypeSafeMatcher<CharSequence>() {
            @Override
            protected boolean matchesSafely(final CharSequence item) {
                return contentEquals(expected, item);
            }

            @Override
            public void describeTo(final Description description) {
                description.appendValue(expected);
            }
        };
    }

    /**
     * Matcher which compares in order while ignoring case.
     * @param items The expected items.
     * @param <E> {@link CharSequence} type to match.
     * @return Matcher which compares in order while ignoring case.
     */
    @SafeVarargs
    public static <E extends CharSequence> Matcher<Iterable<? extends E>> containsIgnoreCase(E... items) {
        final List<Matcher<? super E>> matchers = new ArrayList<>(items.length);
        for (E item : items) {
            matchers.add(new CharsEqualsIgnoreCase(item));
        }
        return contains(matchers);
    }

    private static final class CharsEqualsIgnoreCase extends BaseMatcher<CharSequence> {
        @Nullable
        private final CharSequence expected;

        private CharsEqualsIgnoreCase(final CharSequence expected) {
            this.expected = expected;
        }

        @Override
        public boolean matches(final Object actual) {
            return actual instanceof CharSequence && contentEqualsIgnoreCase(expected, (CharSequence) actual);
        }

        @Override
        public void describeTo(final Description description) {
            description.appendValue(expected);
        }
    }
}
