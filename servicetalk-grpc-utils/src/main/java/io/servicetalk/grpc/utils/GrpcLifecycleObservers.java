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
package io.servicetalk.grpc.utils;

import io.servicetalk.grpc.api.GrpcLifecycleObserver;
import io.servicetalk.logging.api.LogLevel;

/**
 * A factory to create different {@link GrpcLifecycleObserver}s.
 */
public final class GrpcLifecycleObservers {

    private GrpcLifecycleObservers() {
        // No instances
    }

    /**
     * Logging implementation of {@link GrpcLifecycleObserver}.
     *
     * @param loggerName The name of the logger to use
     * @param logLevel The level to log at
     * @return {@link GrpcLifecycleObserver} that logs events at the specified {@link LogLevel}
     */
    public static GrpcLifecycleObserver logging(final String loggerName, final LogLevel logLevel) {
        return new LoggingGrpcLifecycleObserver(loggerName, logLevel);
    }

    /**
     * Combines multiple {@link GrpcLifecycleObserver}s into a single {@link GrpcLifecycleObserver}.
     *
     * @param first {@link GrpcLifecycleObserver} to combine
     * @param second {@link GrpcLifecycleObserver} to combine
     * @return a {@link GrpcLifecycleObserver} that delegates all invocations to the provided
     * {@link GrpcLifecycleObserver}s
     */
    public static GrpcLifecycleObserver combine(final GrpcLifecycleObserver first, final GrpcLifecycleObserver second) {
        return new BiGrpcLifecycleObserver(first, second);
    }

    /**
     * Combines multiple {@link GrpcLifecycleObserver}s into a single {@link GrpcLifecycleObserver}.
     *
     * @param first {@link GrpcLifecycleObserver} to combine
     * @param second {@link GrpcLifecycleObserver} to combine
     * @param others {@link GrpcLifecycleObserver}s to combine
     * @return a {@link GrpcLifecycleObserver} that delegates all invocations to the provided
     * {@link GrpcLifecycleObserver}s
     */
    public static GrpcLifecycleObserver combine(final GrpcLifecycleObserver first, final GrpcLifecycleObserver second,
                                                final GrpcLifecycleObserver... others) {
        BiGrpcLifecycleObserver bi = new BiGrpcLifecycleObserver(first, second);
        if (others.length > 0) {
            for (GrpcLifecycleObserver observer : others) {
                bi = new BiGrpcLifecycleObserver(bi, observer);
            }
        }
        return bi;
    }
}
