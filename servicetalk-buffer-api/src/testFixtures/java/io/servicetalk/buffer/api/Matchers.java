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

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import static io.servicetalk.buffer.api.CharSequences.contentEquals;
import static java.util.Objects.requireNonNull;

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
                if (item == null) {
                    return false;
                }
                return contentEquals(expected, item);
            }

            @Override
            public void describeTo(final Description description) {
                description.appendValue(expected);
            }
        };
    }
}
