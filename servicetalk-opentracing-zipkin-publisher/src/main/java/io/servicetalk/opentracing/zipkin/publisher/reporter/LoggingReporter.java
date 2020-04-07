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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin2.Span;
import zipkin2.reporter.Reporter;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;

/**
 * A Simple {@link Reporter} that logs the span at INFO level.
 */
public final class LoggingReporter implements Reporter<Span> {
    private final Logger logger;
    private final LogLevel logLevel;

    private LoggingReporter(Builder builder) {
        this.logger = LoggerFactory.getLogger(builder.loggerName);
        this.logLevel = builder.logLevel;
    }

    /**
     * A builder to create a new {@link LoggingReporter}.
     */
    public static final class Builder {
        private String loggerName;
        private LogLevel logLevel = LogLevel.INFO;

        public Builder(String loggerName) {
            this.loggerName = requireNonNull(loggerName);
        }

        /**
         * Sets the log level.
         * <p>
         * Logging will defaulit to {@link LogLevel#INFO} if not set.
         *
         * @param logLevel the {@link LogLevel} to use
         * @return {@code this}
         */
        public Builder logLevel(LogLevel logLevel) {
            this.logLevel = requireNonNull(logLevel);
            return this;
        }

        /**
         * Builds a new {@link LoggingReporter} instance with this builder's options.
         *
         * @return a new {@link LoggingReporter}
         */
        public LoggingReporter build() {
            return new LoggingReporter(this);
        }
    }

    /**
     * Different log levels and how to log them.
     */
    public enum LogLevel {
        TRACE(Logger::isTraceEnabled, Logger::trace),
        DEBUG(Logger::isDebugEnabled, Logger::debug),
        INFO(Logger::isInfoEnabled, Logger::info),
        WARN(Logger::isWarnEnabled, Logger::warn),
        ERROR(Logger::isErrorEnabled, Logger::error);

        private final Predicate<Logger> isEnabled;
        private final BiConsumer<Logger, String> log;

        LogLevel(Predicate<Logger> isEnabled, BiConsumer<Logger, String> log) {
            this.isEnabled = isEnabled;
            this.log = log;
        }
    }

    /**
     * Logs a {@link Span} to the configured logger.
     *
     * @param span {@link Span} to log
     */
    @Override
    public void report(Span span) {
        if (logLevel.isEnabled.test(logger)) {
            logLevel.log.accept(logger, requireNonNull(span).toString());
        }
    }
}
