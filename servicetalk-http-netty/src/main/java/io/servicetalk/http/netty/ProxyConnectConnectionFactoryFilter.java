/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.DelegatingConnectionFactory;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.transport.netty.internal.DeferSslHandler;
import io.servicetalk.transport.netty.internal.NettyPipelinedConnection;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;

import static io.servicetalk.concurrent.api.Processors.newSingleProcessor;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.SUCCESSFUL_2XX;

/**
 * A connection factory filter that sends a `CONNECT` request for https proxying.
 *
 * @param <ResolvedAddress> The type of a resolved address that can be used for connecting.
 * @param <C> The type of connections created by this factory.
 */
final class ProxyConnectConnectionFactoryFilter<ResolvedAddress, C
        extends ListenableAsyncCloseable & FilterableStreamingHttpConnection>
        implements ConnectionFactoryFilter<ResolvedAddress, C>,
                   HttpExecutionStrategyInfluencer {

    private final StreamingHttpRequestResponseFactory reqRespFactory;
    private final String connectAddress;

    ProxyConnectConnectionFactoryFilter(final CharSequence connectAddress,
                                        final StreamingHttpRequestResponseFactory reqRespFactory) {
        this.reqRespFactory = reqRespFactory;
        this.connectAddress = connectAddress.toString();
    }

    @Override
    public ConnectionFactory<ResolvedAddress, C> create(final ConnectionFactory<ResolvedAddress, C> original) {
        return new ProxyFilter(original);
    }

    private final class ProxyFilter extends DelegatingConnectionFactory<ResolvedAddress, C> {

        private ProxyFilter(final ConnectionFactory<ResolvedAddress, C> delegate) {
            super(delegate);
        }

        @Override
        public Single<C> newConnection(final ResolvedAddress resolvedAddress) {
            return delegate().newConnection(resolvedAddress).flatMap(c ->
                // We currently only have access to a StreamingHttpRequester, which means we are forced to provide an
                // HttpExecutionStrategy. Because we can't be sure if there is any blocking code in the connection
                // filters we use the default strategy which should offload everything to be safe.
                c.request(defaultStrategy(),
                            reqRespFactory.connect(connectAddress).addHeader(CONTENT_LENGTH, ZERO))
                 .flatMap(response -> {
                if (SUCCESSFUL_2XX.contains(response.status())) {
                    final Channel channel = ((NettyPipelinedConnection) c.connectionContext()).nettyChannel();
                    final SingleSource.Processor<C, C> processor = newSingleProcessor();

                    channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) {
                            if (evt instanceof SslHandshakeCompletionEvent) {
                                SslHandshakeCompletionEvent event = (SslHandshakeCompletionEvent) evt;
                                if (event.isSuccess()) {
                                    processor.onSuccess(c);
                                } else {
                                    processor.onError(event.cause());
                                }
                            }
                            ctx.fireUserEventTriggered(evt);
                        }
                    });

                    DeferSslHandler deferSslHandler = channel.pipeline().get(DeferSslHandler.class);
                    if (deferSslHandler == null) {
                        return response.payloadBody().ignoreElements().concat(failed(
                                new IllegalStateException("Failed to find a handler of type " +
                                        DeferSslHandler.class + " in channel pipeline.")));
                    }

                    deferSslHandler.ready();

                    // There is no need to apply offloading explicitly (despite completing `processor` on the
                    // EventLoop) because `payloadBody()` will be offloaded according to the strategy for the
                    // request.
                    return response.payloadBody().ignoreElements().concat(fromSource(processor));
                } else {
                    return response.payloadBody().ignoreElements().concat(
                            failed(new ProxyResponseException("Bad response from proxy CONNECT " + connectAddress,
                                    response.status())));
                }
            }));
        }
    }

    @Override
    public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
        // No influence since we do not block.
        return strategy;
    }
}
