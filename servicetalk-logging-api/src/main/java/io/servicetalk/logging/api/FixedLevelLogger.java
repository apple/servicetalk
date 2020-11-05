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
package io.servicetalk.logging.api;

/**
 * A logger that logs at a fixed {@link LogLevel}.
 */
public interface FixedLevelLogger {
    /**
     * Get the logger name.
     * @return the logger name.
     */
    String loggerName();

    /**
     * Get the level of this logger.
     * @return the level of this logger.
     */
    LogLevel logLevel();

    /**
     * Determine if the level is enabled.
     * @return {@code true} if the level is enabled.
     */
    boolean isEnabled();

    /**
     * Log a {@link String} corresponding level.
     * @param msg the message to log.
     */
    void log(String msg);

    /**
     * Log an {@link Throwable Exception} at the corresponding level with an
     * accompanying message.
     * @param msg the message accompanying the exception.
     * @param cause   the exception (throwable) to log.
     */
    void log(String msg, Throwable cause);

    /**
     * Log a message at the corresponding level according to the specified format
     * and argument.
     * <p>
     * This form avoids superfluous object creation when the logger
     * is disabled for the corresponding level
     * @param format the format string.
     * @param arg    the argument.
     */
    void log(String format, Object arg);

    /**
     * Log a message at the corresponding level according to the specified format
     * and arguments.
     * <p>
     * This form avoids superfluous object creation when the logger
     * is disabled for the corresponding level.
     * @param format the format string.
     * @param arg1   the first argument.
     * @param arg2   the second argument.
     */
    void log(String format, Object arg1, Object arg2);

    /**
     * Log a message at the corresponding level according to the specified format
     * and arguments.
     * <p>
     * This form avoids superfluous string concatenation when the logger
     * is disabled for the corresponding level. However, this variant incurs the hidden
     * (and relatively small) cost of creating an {@code Object[]} before invoking the method,
     * even if this logger is disabled for the corresponding level. The variants taking
     * {@link #log(String, Object) one} and {@link #log(String, Object, Object) two}
     * arguments exist solely in order to avoid this hidden cost.
     * @param format the format string.
     * @param args a list of 3 or more arguments.
     */
    void log(String format, Object... args);
}
