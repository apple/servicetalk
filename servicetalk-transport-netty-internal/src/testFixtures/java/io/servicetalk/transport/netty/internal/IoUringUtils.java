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
package io.servicetalk.transport.netty.internal;

import io.netty.incubator.channel.uring.IOUring;

/**
 * Utilities for {@link IOUring}.
 */
public final class IoUringUtils {

    private IoUringUtils() {
        // No instances.
    }

    /**
     * Sets ability to try {@link IOUring} transport.
     * <p>
     * Use of {@link IOUring} is concurrently protected by a system property which is initialized when
     * {@link NativeTransportUtils} class is loaded. This utility allows to override this value for testing purposes.
     *
     * @param tryIoUring {@code true} to try {@link IOUring}, {@code false} otherwise
     */
    public static void tryIoUring(final boolean tryIoUring) {
        NativeTransportUtils.tryIoUring(tryIoUring);
    }

    /**
     * Determine if {@link IOUring} is available.
     *
     * @return {@code true} if {@link IOUring} is available
     */
    public static boolean isAvailable() {
        return NativeTransportUtils.isIoUringAvailable();
    }
}
