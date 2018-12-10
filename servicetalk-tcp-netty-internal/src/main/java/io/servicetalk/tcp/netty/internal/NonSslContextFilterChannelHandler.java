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

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.transport.api.ConnectionAcceptor;
import io.servicetalk.transport.api.ConnectionContext;

import io.netty.channel.ChannelHandlerContext;

final class NonSslContextFilterChannelHandler extends AbstractContextFilterChannelHandler {

    NonSslContextFilterChannelHandler(final ConnectionContext context, final ConnectionAcceptor connectionAcceptor,
                                      final Executor executor) {
        super(context, connectionAcceptor, executor);
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        executeContextFilter(ctx);
    }

    @Override
    void onContextFilterSuccessful(final ChannelHandlerContext ctx) {
        ctx.fireChannelActive();
    }
}
