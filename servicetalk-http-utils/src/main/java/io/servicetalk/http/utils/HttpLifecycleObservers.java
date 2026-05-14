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
package io.servicetalk.http.utils;

import io.servicetalk.http.api.HttpLifecycleObserver;
import io.servicetalk.logging.api.LogLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A factory to create different {@link HttpLifecycleObserver}s.
 */
public final class HttpLifecycleObservers {

    private HttpLifecycleObservers() {
        // No instances
    }

    /**
     * Logging implementation of {@link HttpLifecycleObserver}.
     *
     * @param loggerName The name of the logger to use
     * @param logLevel The level to log at
     * @return {@link HttpLifecycleObserver} that logs events at the specified {@link LogLevel}
     */
    public static HttpLifecycleObserver logging(final String loggerName, final LogLevel logLevel) {
        return new LoggingHttpLifecycleObserver(loggerName, logLevel);
    }

    /**
     * Combines multiple {@link HttpLifecycleObserver}s into a single {@link HttpLifecycleObserver}.
     *
     * @param first {@link HttpLifecycleObserver} to combine
     * @param second {@link HttpLifecycleObserver} to combine
     * @return a {@link HttpLifecycleObserver} that delegates all invocations to the provided
     * {@link HttpLifecycleObserver}s
     * @see #unpack(HttpLifecycleObserver)
     */
    public static HttpLifecycleObserver combine(final HttpLifecycleObserver first, final HttpLifecycleObserver second) {
        return new BiHttpLifecycleObserver(first, second);
    }

    /**
     * Combines multiple {@link HttpLifecycleObserver}s into a single {@link HttpLifecycleObserver}.
     *
     * @param first {@link HttpLifecycleObserver} to combine
     * @param second {@link HttpLifecycleObserver} to combine
     * @param others {@link HttpLifecycleObserver}s to combine
     * @return a {@link HttpLifecycleObserver} that delegates all invocations to the provided
     * {@link HttpLifecycleObserver}s
     * @see #unpack(HttpLifecycleObserver)
     */
    public static HttpLifecycleObserver combine(final HttpLifecycleObserver first, final HttpLifecycleObserver second,
                                                final HttpLifecycleObserver... others) {
        BiHttpLifecycleObserver bi = new BiHttpLifecycleObserver(first, second);
        if (others.length > 0) {
            for (HttpLifecycleObserver observer : others) {
                bi = new BiHttpLifecycleObserver(bi, observer);
            }
        }
        return bi;
    }

    /**
     * Unpacks a {@link HttpLifecycleObserver} into a list of its leaf observers.
     * <p>
     * If the provided {@code observer} was created using one of the {@link #combine(HttpLifecycleObserver,
     * HttpLifecycleObserver) combine} methods, this method recursively extracts all individual observers that were
     * combined. Otherwise, returns a singleton list containing the provided observer.
     *
     * @param observer {@link HttpLifecycleObserver} to unpack
     * @return a {@link List} of leaf {@link HttpLifecycleObserver}s
     * @see #combine(HttpLifecycleObserver, HttpLifecycleObserver)
     * @see #combine(HttpLifecycleObserver, HttpLifecycleObserver, HttpLifecycleObserver...)
     */
    public static List<HttpLifecycleObserver> unpack(final HttpLifecycleObserver observer) {
        if (observer instanceof BiHttpLifecycleObserver) {
            final List<HttpLifecycleObserver> result = new ArrayList<>();
            unpack(observer, result);
            return Collections.unmodifiableList(result);
        }
        return Collections.singletonList(observer);
    }

    private static void unpack(final HttpLifecycleObserver observer, final List<HttpLifecycleObserver> result) {
        if (observer instanceof BiHttpLifecycleObserver) {
            final BiHttpLifecycleObserver bi = (BiHttpLifecycleObserver) observer;
            unpack(bi.first(), result);
            unpack(bi.second(), result);
        } else {
            result.add(observer);
        }
    }
}
