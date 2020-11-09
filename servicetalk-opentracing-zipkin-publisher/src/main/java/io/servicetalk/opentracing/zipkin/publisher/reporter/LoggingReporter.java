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
package io.servicetalk.opentracing.zipkin.publisher.reporter;

import io.servicetalk.logging.api.LogLevel;
import io.servicetalk.logging.slf4j.internal.FixedLevelLogger;

import zipkin2.Span;
import zipkin2.reporter.Reporter;

import static io.servicetalk.logging.slf4j.internal.Slf4jFixedLevelLoggers.newLogger;

/**
 * A Simple {@link Reporter} that logs the span at INFO level.
 */
public final class LoggingReporter implements Reporter<Span> {
    private final FixedLevelLogger logger;

    /**
     * Create a new instance.
     * @param loggerName The name of the logger to use.
     */
    public LoggingReporter(String loggerName) {
        this(loggerName, LogLevel.INFO);
    }

    /**
     * Create a new instance.
     * @param loggerName The name of the logger to use.
     * @param logLevel The level to log at.
     */
    public LoggingReporter(String loggerName, LogLevel logLevel) {
        logger = newLogger(loggerName, logLevel);
    }

    /**
     * Logs a {@link Span} to the configured logger.
     *
     * @param span {@link Span} to log
     */
    @Override
    public void report(Span span) {
        if (logger.isEnabled()) {
            logger.log(span.toString());
        }
    }
}
