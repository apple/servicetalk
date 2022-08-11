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
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpConnectionContext;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.DelegatingConnectionContext;
import io.servicetalk.transport.netty.internal.FlushStrategy;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;

import io.netty.channel.Channel;

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

final class DefaultNettyHttpConnectionContext extends DelegatingConnectionContext
        implements HttpConnectionContext, NettyConnectionContext {

    private final NettyConnectionContext nettyConnectionContext;
    private final HttpExecutionContext executionContext;

    DefaultNettyHttpConnectionContext(final NettyConnectionContext delegate,
                                      final HttpExecutionContext executionContext) {
        super(delegate);
        this.nettyConnectionContext = delegate;
        this.executionContext = requireNonNull(executionContext);
    }

    @Override
    public HttpExecutionContext executionContext() {
        return executionContext;
    }

    @Override
    public HttpProtocolVersion protocol() {
        return (HttpProtocolVersion) nettyConnectionContext.protocol();
    }

    @Nullable
    @Override
    public ConnectionContext parent() {
        return nettyConnectionContext.parent();
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
    public Completable onClosing() {
        return nettyConnectionContext.onClosing();
    }

    @Override
    public Channel nettyChannel() {
        return nettyConnectionContext.nettyChannel();
    }
}
