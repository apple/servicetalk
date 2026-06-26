/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.transport.netty.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.condition.OS.LINUX;
import static org.junit.jupiter.api.condition.OS.MAC;
import static org.junit.jupiter.api.condition.OS.WINDOWS;

/**
 * Ensures {@link io.servicetalk.utils.internal.PlatformDependent}'s OS detection stays in sync with Netty's
 * normalization, so future Netty bumps that add or rename operating systems are caught.
 * <p>
 * Netty's {@code normalizeOs(String)} is private, so we cannot drive it with synthetic inputs without
 * reflection. The strongest no-reflection coverage we can achieve is to verify, on the host the test runs on:
 * <ul>
 *   <li>Both implementations agree on {@code normalizedOs()} and {@code isOsx()} for the host's actual
 *       {@code os.name}, so any divergence introduced by a Netty bump fails immediately.</li>
 *   <li>Each detectable OS produces the canonical string we expect (e.g. Linux → {@code "linux"},
 *       macOS → {@code "osx"}). This guards against the rarer case where both implementations agree but
 *       are simultaneously wrong (e.g. a coordinated rename to {@code "macos"}).</li>
 * </ul>
 * The exhaustive input-by-input mapping table for inputs the host doesn't expose is locked down separately
 * by {@code PlatformDependentTest#normalizeOsCanonicalMapping} in {@code servicetalk-utils-internal}.
 * <p>
 * Lives in this module because {@code servicetalk-utils-internal} intentionally does not depend on Netty.
 */
class PlatformDependentNettyParityTest {

    @Test
    void normalizedOsMatchesNetty() {
        assertEquals(io.netty.util.internal.PlatformDependent.normalizedOs(),
                io.servicetalk.utils.internal.PlatformDependent.normalizedOs());
    }

    @Test
    void isOsxMatchesNetty() {
        assertEquals(io.netty.util.internal.PlatformDependent.isOsx(),
                io.servicetalk.utils.internal.PlatformDependent.isOsx());
    }

    @Test
    void bsdDetectionMatchesNetty() {
        // ServiceTalk has no isBsd() getter; callers (e.g. NativeTransportUtils) inspect
        // normalizedOs().contains("bsd"). Verify that pattern still agrees with Netty for the host's actual os.name.
        final boolean nettyBsd = io.netty.util.internal.PlatformDependent.normalizedOs().contains("bsd");
        final boolean stBsd = io.servicetalk.utils.internal.PlatformDependent.normalizedOs().contains("bsd");
        assertEquals(nettyBsd, stBsd);
    }

    @Test
    @EnabledOnOs(LINUX)
    void linuxHostExpectedCanonicalValues() {
        assertEquals("linux", io.netty.util.internal.PlatformDependent.normalizedOs());
        assertEquals("linux", io.servicetalk.utils.internal.PlatformDependent.normalizedOs());
        assertTrue(io.servicetalk.utils.internal.PlatformDependent.isLinux());
        assertFalse(io.servicetalk.utils.internal.PlatformDependent.isOsx());
        assertFalse(io.netty.util.internal.PlatformDependent.isOsx());
    }

    @Test
    @EnabledOnOs(MAC)
    void macHostExpectedCanonicalValues() {
        assertEquals("osx", io.netty.util.internal.PlatformDependent.normalizedOs());
        assertEquals("osx", io.servicetalk.utils.internal.PlatformDependent.normalizedOs());
        assertTrue(io.servicetalk.utils.internal.PlatformDependent.isOsx());
        assertTrue(io.netty.util.internal.PlatformDependent.isOsx());
        assertFalse(io.servicetalk.utils.internal.PlatformDependent.isLinux());
    }

    @Test
    @EnabledOnOs(WINDOWS)
    void windowsHostExpectedCanonicalValues() {
        assertEquals("windows", io.netty.util.internal.PlatformDependent.normalizedOs());
        assertEquals("windows", io.servicetalk.utils.internal.PlatformDependent.normalizedOs());
        assertFalse(io.servicetalk.utils.internal.PlatformDependent.isLinux());
        assertFalse(io.servicetalk.utils.internal.PlatformDependent.isOsx());
    }
}
