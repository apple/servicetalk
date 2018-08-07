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
package io.servicetalk.concurrent.api;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

final class VerificationTestUtils {
    private VerificationTestUtils() {
    }

    static void verifyOriginalAndSuppressedCauses(Throwable actualCause, Throwable expectedOriginalCause, Throwable expectedSuppressedCause) {
        assertNotNull(actualCause);
        assertSame(expectedOriginalCause, actualCause.getCause());
        verifySuppressed(actualCause, expectedSuppressedCause);
    }

    static void verifySuppressed(Throwable holder, Throwable expectedSuppressedCause) {
        boolean found = false;
        for (Throwable actualSuppressed : holder.getSuppressed()) {
            if (actualSuppressed == expectedSuppressedCause) {
                found = true;
                break;
            }
        }
        assertTrue("couldn't find suppressed cause " + expectedSuppressedCause, found);
    }
}
