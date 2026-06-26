/*
 * Copyright © 2022, 2026 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.utils.internal;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;

import static java.lang.Boolean.getBoolean;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.condition.OS.LINUX;
import static org.junit.jupiter.api.condition.OS.MAC;

class PlatformDependentTest {

    @Test
    void hasUnsafe() {
        assumeFalse(getBoolean("io.servicetalk.noUnsafe"));
        assertDoesNotThrow(PlatformDependent::hasUnsafe);
    }

    @Test
    void memoryAllocation() {
        assumeTrue(PlatformDependent.hasUnsafe(), "Unsafe absent or disabled");
        long allocated = PlatformDependent.allocateMemory(1);
        assertThat(allocated, is(Matchers.not(0)));
        PlatformDependent.freeMemory(allocated);
    }

    @Test
    void linuxAndOsxAreMutuallyExclusive() {
        assertFalse(PlatformDependent.isLinux() && PlatformDependent.isOsx());
    }

    @Test
    void normalizedOsAgreesWithGetters() {
        final String os = PlatformDependent.normalizedOs();
        assertEquals("linux".equals(os), PlatformDependent.isLinux());
        assertEquals("osx".equals(os), PlatformDependent.isOsx());
    }

    @Test
    @EnabledOnOs(LINUX)
    void linuxHostReportsLinux() {
        assertTrue(PlatformDependent.isLinux());
        assertFalse(PlatformDependent.isOsx());
        assertEquals("linux", PlatformDependent.normalizedOs());
    }

    @Test
    @EnabledOnOs(MAC)
    void osxHostReportsOsx() {
        assertTrue(PlatformDependent.isOsx());
        assertFalse(PlatformDependent.isLinux());
        assertEquals("osx", PlatformDependent.normalizedOs());
    }

    @Test
    void normalizeOsCanonicalMapping() {
        // Linux
        assertNormalizesTo("linux", "Linux", "linux", "LINUX", "Linux 5.15");
        // macOS — the real os.name on macOS hosts is "Mac OS X"; "Darwin"/"OSX"/"macosx" also map to "osx".
        assertNormalizesTo("osx", "Mac OS X", "Darwin", "macosx", "OSX");
        // Bare "Mac OS" (without trailing X) normalizes to "macos" which doesn't start with any recognized
        // prefix and therefore returns "unknown" — matches Netty 4.1.x behavior. Locked in to catch any
        // future predicate change that would silently start matching it.
        assertNormalizesTo("unknown", "Mac OS");
        // Windows: still mapped by normalizeOs, even though no isWindows() getter is exposed.
        assertNormalizesTo("windows", "Windows 11", "Windows Server 2022");
        // BSDs
        assertNormalizesTo("freebsd", "FreeBSD 13");
        assertNormalizesTo("openbsd", "OpenBSD");
        assertNormalizesTo("netbsd", "NetBSD");
        // Solaris / SunOS
        assertNormalizesTo("sunos", "SunOS", "Solaris 11");
        // Mainframes and legacy
        assertNormalizesTo("aix", "AIX 7.2");
        assertNormalizesTo("hpux", "HP-UX");
        assertNormalizesTo("os400", "OS400");
        // Unknown
        assertNormalizesTo("unknown", "BeOS", "Plan9");
    }

    @Test
    void normalizeOsEmptyInputIsUnknown() {
        // readOsName() returns "" if reading the system property is denied; verify it normalizes safely.
        assertEquals("unknown", PlatformDependent.normalizeOs(""));
    }

    @Test
    void normalizeOsOs400DigitBoundary() {
        // The 6th character must not be a digit, otherwise we'd accidentally bucket "os4000" as "os400".
        assertEquals("os400", PlatformDependent.normalizeOs("OS400"));
        assertEquals("unknown", PlatformDependent.normalizeOs("OS4000"));
    }

    @Test
    void normalizeStripsAndLowercases() {
        assertEquals("macosx", PlatformDependent.normalize("Mac OS X"));
        assertEquals("hpux", PlatformDependent.normalize("HP-UX"));
        assertEquals("windows10", PlatformDependent.normalize("Windows 10"));
        assertEquals("", PlatformDependent.normalize(""));
        assertEquals("freebsd13", PlatformDependent.normalize("Free_BSD/13"));
    }

    private static void assertNormalizesTo(final String expected, final String... inputs) {
        for (final String input : inputs) {
            assertEquals(expected, PlatformDependent.normalizeOs(input),
                    () -> "normalizeOs(\"" + input + "\")");
        }
    }
}
