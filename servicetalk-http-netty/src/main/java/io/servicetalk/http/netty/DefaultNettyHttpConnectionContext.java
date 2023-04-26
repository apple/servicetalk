/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.netty;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpConnectionContext;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.transport.api.DelegatingConnectionContext;
import io.servicetalk.transport.netty.internal.FlushStrategy;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;

import io.netty.channel.Channel;

final class DefaultNettyHttpConnectionContext extends DelegatingConnectionContext
        implements HttpConnectionContext, NettyConnectionContext {

    private final NettyConnectionContext nettyConnectionContext;

    DefaultNettyHttpConnectionContext(final NettyConnectionContext delegate) {
        super(delegate);
        this.nettyConnectionContext = delegate;
        assert delegate.executionContext() instanceof HttpExecutionContext;
        assert delegate.protocol() instanceof HttpProtocolVersion;
    }

    @Override
    public HttpExecutionContext executionContext() {
        return (HttpExecutionContext) nettyConnectionContext.executionContext();
    }

    @Override
    public HttpProtocolVersion protocol() {
        return (HttpProtocolVersion) nettyConnectionContext.protocol();
    }

    @Override
    public Cancellable updateFlushStrategy(final FlushStrategyProvider strategyProvider) {
        return nettyConnectionContext.updateFlushStrategy(strategyProvider);
    }

    @Override
    public FlushStrategy defaultFlushStrategy() {
        return nettyConnectionContext.defaultFlushStrategy();
    }

    @Override
    public Single<Throwable> transportError() {
        return nettyConnectionContext.transportError();
    }

    @Override
    public Channel nettyChannel() {
        return nettyConnectionContext.nettyChannel();
    }
}
