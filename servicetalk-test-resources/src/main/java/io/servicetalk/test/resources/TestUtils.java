/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.test.resources;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import java.util.Queue;

import static io.servicetalk.utils.internal.ThrowableUtils.addSuppressed;
import static java.lang.Integer.min;

/**
 * Test utility methods / helpers.
 */
public final class TestUtils {

    private TestUtils() {
        // No instances
    }

    /**
     * Helper method to check if a given {@link Queue} contains any errors,
     * usually produced through async sources. For ALL {@link Throwable}s found in the queue,
     * a suppressed error will be captured and surface through a single {@link AssertionError}.
     * @param errors The queue of captured errors.
     */
    public static void assertNoAsyncErrors(final Queue<? extends Throwable> errors) {
        assertNoAsyncErrors("Async errors occurred. See suppressed!", errors);
    }

    /**
     * Helper method to check if a given {@link Queue} contains any errors,
     * usually produced through async sources. For ALL {@link Throwable}s found in the queue,
     * a suppressed error will be captured and surface through a single {@link AssertionError}.
     * @param message message for the AssertionError.
     * @param errors The queue of captured errors.
     */
    public static void assertNoAsyncErrors(final String message, final Queue<? extends Throwable> errors) {
        if (errors.isEmpty()) {
            return;
        }

        final AssertionError error = null != message ? new AssertionError(message) : new AssertionError();
        Throwable t;
        while ((t = errors.poll()) != null) {
            addSuppressed(error, t);
        }

        throw error;
    }

    /**
     * Create a {@link Matcher} that matches the thread's name against and expected prefix value.
     * @param expectPrefix the expected prefix value to match.
     * @return a {@link Matcher} that matches the thread's name against and expected prefix value.
     */
    public static Matcher<Thread> matchThreadNamePrefix(String expectPrefix) {
        return new TypeSafeMatcher<Thread>() {
            final String matchPrefix = expectPrefix;

            @Override
            public void describeTo(final Description description) {
                description.appendText("a prefix of ")
                        .appendValue(matchPrefix);
            }

            @Override
            public void describeMismatchSafely(Thread item, Description mismatchDescription) {
                String threadName = item.getName();
                String mismatch = threadName.substring(0, min(threadName.length(), expectPrefix.length()));
                mismatchDescription
                        .appendText("was ")
                        .appendValue(mismatch);
            }

            @Override
            protected boolean matchesSafely(final Thread item) {
                return item.getName().startsWith(matchPrefix);
            }
        };
    }
}
