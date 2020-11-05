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

import io.servicetalk.logging.api.FixedLevelLogger;

import io.netty.channel.Channel;

/**
 * A {@link ChannelInitializer} that enables wire-logging for all channels.
 * All wire events will be logged at trace level.
 */
public class WireLoggingInitializer implements ChannelInitializer {
    private final ServiceTalkWireLogger loggingHandler;

    /**
     * Create an instance.
     *
     * @param logger The logger to use for log wire events.
     * @param logUserData {@code true} to log user data. {@code false} to not log user data.
     */
    public WireLoggingInitializer(final FixedLevelLogger logger,
                                  final boolean logUserData) {
        loggingHandler = new ServiceTalkWireLogger(logger, logUserData);
    }

    @Override
    public void init(Channel channel) {
        channel.pipeline().addLast(loggingHandler);
    }
}
