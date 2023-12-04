/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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

import io.netty.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

/**
 * A utility class to handle close operations.
 */
public final class CloseUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(CloseUtils.class);

    private CloseUtils() {
        // No instances.
    }

    /**
     * Closes passed {@link AutoCloseable} and logs an exception if occurred.
     *
     * @param closeable {@link AutoCloseable} to close
     */
    public static void safeClose(@Nullable AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                LOGGER.error("Unexpected exception while closing", e);
            }
        }
    }

    /**
     * Calls {@link Future#sync()} and logs an exception if occurred.
     *
     * @param future {@link Future} to sync
     * @throws InterruptedException if "sync" operation was interrupted
     */
    public static void safeSync(Future<?> future) throws InterruptedException {
        try {
            future.sync();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (Exception e) {
            LOGGER.error("Unexpected exception while syncing {}", future, e);
        }
    }
}
