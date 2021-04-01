/*
 * Copyright © 2018-2019, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api;

import org.hamcrest.Matcher;
import org.junit.Assert;
import org.junit.function.ThrowingRunnable;

import javax.annotation.Nullable;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public final class VerificationTestUtils {
    private VerificationTestUtils() {
    }

    public static void verifySuppressed(@Nullable Throwable holder, Throwable expectedSuppressedCause) {
        assertNotNull(holder);
        boolean found = false;
        for (Throwable actualSuppressed : holder.getSuppressed()) {
            if (actualSuppressed == expectedSuppressedCause) {
                found = true;
                break;
            }
        }
        assertTrue(found, () -> "couldn't find suppressed cause " + expectedSuppressedCause);
    }

    public static void expectThrowable(final Runnable runnable, final Class<? extends Throwable> expected) {
        expectThrowable(runnable, instanceOf(expected));
    }

    public static void expectThrowable(final Runnable runnable, final Matcher<Throwable> matcher) {
        try {
            runnable.run();
        } catch (Throwable t) {
            assertThat(t, matcher);
            return;
        }
        fail("Expected AssertionError");
    }

    @SuppressWarnings("unchecked")
    public static <T1 extends Throwable, T2 extends Throwable> T1 assertThrows(
            Class<T1> expectedClass, @Nullable Class<T2> optionalWrapperClass, ThrowingRunnable runnable) {
        if (optionalWrapperClass == null) {
            return Assert.assertThrows(expectedClass, runnable);
        }
        try {
            runnable.run();
        } catch (Throwable cause) {
            if (expectedClass.isInstance(cause)) {
                return (T1) cause;
            } else if (optionalWrapperClass.isInstance(cause) && expectedClass.isInstance(cause.getCause())) {
                return (T1) cause.getCause();
            } else {
                throw new AssertionError("expected " + className(expectedClass) + " optionally caused by " +
                        className(optionalWrapperClass) + " but got " + className(cause) + " caused by " +
                        className(cause.getCause()));
            }
        }
        throw new AssertionError("expected " + className(expectedClass) + " optionally caused by " +
                className(optionalWrapperClass) + " but nothing was thrown");
    }

    private static String className(Object o) {
        return o.getClass().getCanonicalName();
    }
}
