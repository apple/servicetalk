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
package io.servicetalk.logging.slf4j.internal;

import io.servicetalk.logging.api.LogLevel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SLF4J implementations of {@link FixedLevelLogger}.
 */
public final class Slf4jFixedLevelLoggers {
    private Slf4jFixedLevelLoggers() {
    }

    /**
     * Create a {@link FixedLevelLogger}.
     * @param loggerName the name of the logger to use.
     * @param level the level to log at.
     * @return a {@link FixedLevelLogger}.
     */
    public static FixedLevelLogger newLogger(final String loggerName, final LogLevel level) {
        final Logger logger = LoggerFactory.getLogger(loggerName);
        switch (level) {
            case TRACE:
                return new TraceLogger(logger);
            case DEBUG:
                return new DebugLogger(logger);
            case INFO:
                return new InfoLogger(logger);
            case WARN:
                return new WarnLogger(logger);
            case ERROR:
                return new ErrorLogger(logger);
            default:
                throw new IllegalArgumentException("unsupported level: " + level);
        }
    }
}
