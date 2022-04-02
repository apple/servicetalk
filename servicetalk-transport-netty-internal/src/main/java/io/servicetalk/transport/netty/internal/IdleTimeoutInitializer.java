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
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Initializes the channel with idle timeout handling.
 */
public class IdleTimeoutInitializer implements ChannelInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdleTimeoutInitializer.class);

    private final long timeoutMs;

    /**
     * New instance.
     *
     * @param idleTimeout timeout duration.
     * @deprecated Use {@link #IdleTimeoutInitializer(long)}
     */
    @Deprecated
    public IdleTimeoutInitializer(Duration idleTimeout) {   // FIXME: 0.43 - remove deprecated constructor (not used)
        this(idleTimeout.toMillis());
    }

    /**
     * New instance.
     *
     * @param idleTimeoutMillis timeout in milliseconds.
     */
    public IdleTimeoutInitializer(long idleTimeoutMillis) {
        timeoutMs = idleTimeoutMillis;
    }

    @Override
    public void init(Channel channel) {
        LOGGER.debug("Channel idle timeout is {}ms.", timeoutMs);
        channel.pipeline().addLast(new IdleStateHandler(0, 0, timeoutMs, TimeUnit.MILLISECONDS) {
            @Override
            protected void channelIdle(ChannelHandlerContext ctx, IdleStateEvent evt) {
                if (evt.state() == IdleState.ALL_IDLE) {
                    // Some protocols may have their own grace period to shutdown the channel
                    // and we don't want the idle timeout to fire again during this process.
                    ctx.pipeline().remove(this);
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Closing channel {} after {}ms of inactivity.", ctx.channel(), timeoutMs);
                    }
                    // Fire the event through the pipeline so protocols can prepare for the close event.
                    ctx.fireUserEventTriggered(evt);
                    ctx.close();
                }
            }
        });
    }
}
