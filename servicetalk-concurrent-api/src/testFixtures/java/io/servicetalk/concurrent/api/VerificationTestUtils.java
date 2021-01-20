/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

import javax.annotation.Nullable;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
        assertTrue("couldn't find suppressed cause " + expectedSuppressedCause, found);
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
}
