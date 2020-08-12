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
package io.servicetalk.transport.netty.internal;

import io.netty.handler.logging.LogLevel;
import io.netty.util.internal.logging.InternalLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.netty.handler.logging.LogLevel.DEBUG;
import static io.netty.handler.logging.LogLevel.ERROR;
import static io.netty.handler.logging.LogLevel.INFO;
import static io.netty.handler.logging.LogLevel.TRACE;
import static io.netty.handler.logging.LogLevel.WARN;

/**
 * Utility methods for {@link InternalLogger} related types.
 */
public final class NettyLoggerUtils {
    private NettyLoggerUtils() {
    }

    /**
     * Translate the log level as determined by SL4J for the {@code loggerName} to Netty's {@link LogLevel} type.
     * @param loggerName The logger name to lookup.
     * @return Netty's {@link LogLevel} corresponding to the SL4J log level for {@code loggerName}.
     */
    public static LogLevel getNettyLogLevel(String loggerName) {
        final Logger logger = LoggerFactory.getLogger(loggerName);
        if (logger.isTraceEnabled()) {
            return TRACE;
        } else if (logger.isDebugEnabled()) {
            return DEBUG;
        } else if (logger.isInfoEnabled()) {
            return INFO;
        } else if (logger.isWarnEnabled()) {
            return WARN;
        } else if (logger.isErrorEnabled()) {
            return ERROR;
        }
        throw new IllegalArgumentException("unknown log level: " + logger);
    }
}
