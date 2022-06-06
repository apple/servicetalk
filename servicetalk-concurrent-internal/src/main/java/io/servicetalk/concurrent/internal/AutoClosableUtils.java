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
package io.servicetalk.concurrent.internal;

import java.io.IOException;
import java.io.UncheckedIOException;

import static io.servicetalk.utils.internal.PlatformDependent.throwException;

/**
 * Utilities for {@link AutoCloseable}.
 */
public final class AutoClosableUtils {
    private AutoClosableUtils() {
        // no instances
    }

    /**
     * Call {@link AutoCloseable#close()} and re-throw any exceptions as an unchecked exception.
     * @param closeable The object to close.
     * @deprecated Use {@link #closeAndReThrow(AutoCloseable)}.
     */
    @Deprecated
    public static void closeAndReThrowUnchecked(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Call {@link AutoCloseable#close()} and re-throw any exception.
     * @param closeable The object to close.
     */
    public static void closeAndReThrow(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception e) {
            throwException(e);
        }
    }

    /**
     * Call {@link AutoCloseable#close()} and re-throw any exceptions as a {@link IOException}.
     * @param closeable The object to close.
     * @throws IOException if an exception occurs during {@link AutoCloseable#close()}.
     */
    public static void closeAndReThrowIoException(AutoCloseable closeable) throws IOException {
        try {
            closeable.close();
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
}
