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
package io.servicetalk.transport.netty.internal;

import io.netty.channel.Channel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.servicetalk.transport.api.ConnectionContext;

/**
 * A {@link ChannelInitializer} that enables wire-logging for all channels.
 */
public class WireLogInitializer implements ChannelInitializer {

    public static final WireLogInitializer GLOBAL_WIRE_LOGGER = new WireLogInitializer("servicetalk-global-wire-logger", LogLevel.DEBUG);

    private final LoggingHandler loggingHandler;

    /**
     * New instance.
     *
     * @param loggerName name of the logger.
     * @param logLevel level to log at.
     */
    public WireLogInitializer(String loggerName, LogLevel logLevel) {
        loggingHandler = new LoggingHandler(loggerName, logLevel);
    }

    @Override
    public ConnectionContext init(Channel channel, ConnectionContext context) {
        channel.pipeline().addLast(loggingHandler);
        return context;
    }
}
