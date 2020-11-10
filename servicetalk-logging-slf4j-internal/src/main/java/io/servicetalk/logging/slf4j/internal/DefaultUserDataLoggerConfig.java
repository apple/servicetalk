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
import io.servicetalk.logging.api.LoggerConfig;
import io.servicetalk.logging.api.UserDataLoggerConfig;

import java.util.Objects;
import java.util.function.BooleanSupplier;

import static java.util.Objects.requireNonNull;

/**
 * Default implementation of {@link LoggerConfig}.
 */
public final class DefaultUserDataLoggerConfig implements UserDataLoggerConfig {
    private final String loggerName;
    private final LogLevel logLevel;
    private final BooleanSupplier logUserData;

    /**
     * Create a new instance.
     * @param loggerName the name of the logger to use.
     * @param logLevel the level to log at.
     * @param logUserData if user data (e.g. data, headers, etc.) should be included in logs.
     */
    public DefaultUserDataLoggerConfig(final String loggerName, final LogLevel logLevel,
                                       final BooleanSupplier logUserData) {
        this.loggerName = requireNonNull(loggerName);
        this.logLevel = requireNonNull(logLevel);
        this.logUserData = requireNonNull(logUserData);
    }

    @Override
    public String loggerName() {
        return loggerName;
    }

    @Override
    public LogLevel logLevel() {
        return logLevel;
    }

    @Override
    public BooleanSupplier logUserData() {
        return logUserData;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultUserDataLoggerConfig that = (DefaultUserDataLoggerConfig) o;
        return loggerName.equals(that.loggerName) &&
                logLevel == that.logLevel &&
                logUserData.equals(that.logUserData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(loggerName, logLevel, logUserData);
    }

    @Override
    public String toString() {
        return "DefaultUserDataLoggerConfig{" +
                "loggerName='" + loggerName + '\'' +
                ", logLevel=" + logLevel +
                ", logUserData=" + logUserData +
                '}';
    }
}
