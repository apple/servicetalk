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

import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ContextFilter;
import io.servicetalk.transport.netty.internal.ChannelInitializer;

import io.netty.channel.Channel;

import static io.servicetalk.tcp.netty.internal.AcceptAllContextFilterChannelHandler.ACCEPT_ALL_HANDLER;
import static io.servicetalk.transport.api.ContextFilter.ACCEPT_ALL;

class ContextFilterChannelInitializer implements ChannelInitializer {

    private final ContextFilter contextFilter;
    private final boolean sslEnabled;

    ContextFilterChannelInitializer(final ContextFilter contextFilter, final boolean sslEnabled) {
        this.contextFilter = contextFilter;
        this.sslEnabled = sslEnabled;
    }

    @Override
    public ConnectionContext init(final Channel channel, final ConnectionContext context) {
        if (contextFilter == ACCEPT_ALL) {
            channel.pipeline().addLast(ACCEPT_ALL_HANDLER);
        } else {
            if (sslEnabled) {
                channel.pipeline().addLast(new SslContextFilterChannelHandler(context, contextFilter,
                        context.executionContext().executor()));
            } else {
                channel.pipeline().addLast(new NonSslContextFilterChannelHandler(context, contextFilter,
                        context.executionContext().executor()));
            }
        }
        return context;
    }
}
