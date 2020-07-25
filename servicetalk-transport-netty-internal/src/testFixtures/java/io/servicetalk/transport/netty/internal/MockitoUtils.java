/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

import org.mockito.Mockito;
import org.mockito.verification.VerificationWithTimeout;

/**
 * Utilities for {@link Mockito}.
 */
public final class MockitoUtils {

    private MockitoUtils() {
        // No instances.
    }

    /**
     * Alias for {@link Mockito#timeout(long)} that awaits indefinitely.
     * <p>
     * Because the client is just a trigger for server-side events sometimes we need to await for invocations to verify
     * them.
     */
    public static VerificationWithTimeout await() {
        return Mockito.timeout(Long.MAX_VALUE);
    }
}
