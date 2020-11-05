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

import io.servicetalk.logging.api.FixedLevelLogger;
import io.servicetalk.logging.api.LogLevel;

import org.slf4j.Logger;

import static java.util.Objects.requireNonNull;

final class InfoLogger implements FixedLevelLogger {
    private final Logger logger;

    InfoLogger(final Logger logger) {
        this.logger = requireNonNull(logger);
    }

    @Override
    public String loggerName() {
        return logger.getName();
    }

    @Override
    public LogLevel logLevel() {
        return LogLevel.INFO;
    }

    @Override
    public boolean isEnabled() {
        return logger.isInfoEnabled();
    }

    @Override
    public void log(final String msg) {
        logger.info(msg);
    }

    @Override
    public void log(final String msg, final Throwable cause) {
        logger.info(msg, cause);
    }

    @Override
    public void log(final String format, final Object arg) {
        logger.info(format, arg);
    }

    @Override
    public void log(final String format, final Object arg1, final Object arg2) {
        logger.info(format, arg1, arg2);
    }

    @Override
    public void log(final String format, final Object... args) {
        logger.info(format, args);
    }
}
