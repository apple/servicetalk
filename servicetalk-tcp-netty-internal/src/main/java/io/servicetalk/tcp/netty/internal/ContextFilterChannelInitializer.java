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
package io.servicetalk.tcp.netty.internal;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.Single.Subscriber;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ContextFilter;
import io.servicetalk.transport.netty.internal.ChannelInitializer;

import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

final class ContextFilterChannelInitializer implements ChannelInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContextFilterChannelInitializer.class);

    private final ContextFilter contextFilter;
    private final ChannelInitializer channelInitializer;

    ContextFilterChannelInitializer(final ContextFilter contextFilter, final ChannelInitializer channelInitializer) {
        this.contextFilter = contextFilter;
        this.channelInitializer = channelInitializer;
    }

    @Override
    public ConnectionContext init(final Channel channel, final ConnectionContext context) {
        contextFilter.filter(context).subscribe(new Subscriber<Boolean>() {
            @Override
            public void onSubscribe(final Cancellable cancellable) {
                // Don't need to do anything.
            }

            @Override
            public void onSuccess(@Nullable final Boolean result) {
                if (result != null && result) {
                    // Getting the remote-address may involve volatile reads and potentially a syscall, so guard it.
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Accepted connection from {}", context.getRemoteAddress());
                    }
                    try {
                        channelInitializer.init(channel, context);
                    } catch (Throwable t) {
                        LOGGER.error("Channel initializer for context {} threw exception", context, t);
                        context.closeAsync().subscribe();
                    }
                } else {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Rejected connection from {}", context.getRemoteAddress());
                    }
                    context.closeAsync().subscribe();
                }
            }

            @Override
            public void onError(final Throwable t) {
                LOGGER.warn("Exception from context filter {} for context {}.", contextFilter, context, t);
                context.closeAsync().subscribe();
            }
        });
        return context;
    }
}
