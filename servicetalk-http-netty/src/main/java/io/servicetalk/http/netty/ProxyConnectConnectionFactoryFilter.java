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
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.netty.internal.DefaultNettyPipelinedConnection;
import io.servicetalk.transport.netty.internal.DeferSslHandler;

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

    private static Channel getChannel(final FilterableStreamingHttpConnection c) {
        final DefaultNettyPipelinedConnection dnpc = (DefaultNettyPipelinedConnection) c.connectionContext();
        return dnpc.nettyChannel();
    }

    private final class ProxyFilter extends DelegatingConnectionFactory<ResolvedAddress, C> {

        private ProxyFilter(final ConnectionFactory<ResolvedAddress, C> delegate) {
            super(delegate);
        }

        @Override
        public Single<C> newConnection(final ResolvedAddress resolvedAddress) {
            return delegate().newConnection(resolvedAddress).flatMap(c -> {
                final Single<StreamingHttpResponse> responseSingle = c.request(defaultStrategy(),
                        reqRespFactory.connect(connectAddress).addHeader(CONTENT_LENGTH, ZERO));
                return responseSingle.flatMap(response -> {
                    if (HttpResponseStatus.StatusClass.SUCCESSFUL_2XX.contains(response.status())) {
                        final Channel channel = getChannel(c);
                        final SingleSource.Processor<C, C> processor = newSingleProcessor();

                        channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt)
                                    throws Exception {
                                if (evt instanceof SslHandshakeCompletionEvent) {
                                    SslHandshakeCompletionEvent event = (SslHandshakeCompletionEvent) evt;
                                    if (event.isSuccess()) {
                                        processor.onSuccess(c);
                                    } else {
                                        processor.onError(event.cause());
                                    }
                                }
                                super.userEventTriggered(ctx, evt);
                            }
                        });

                        if (channel.pipeline().get(DeferSslHandler.class) == null) {
                            return response.payloadBody().ignoreElements().concat(failed(
                                    new IllegalStateException("No DeferSslHandler found in channel pipeline")));
                        }

                        channel.pipeline().get(DeferSslHandler.class).ready();

                        // There is no need to apply offloading explicitly (despite completing `processor` on the
                        // EventLoop) because `payloadBody()` will be offloaded according to the strategy for the
                        // request.
                        return response.payloadBody().ignoreElements().concat(fromSource(processor));
                    } else {
                        return response.payloadBody().ignoreElements().concat(
                                failed(new ProxyResponseException("Bad response from proxy CONNECT " + connectAddress,
                                        response.status())));
                    }
                });
            });
        }
    }

    @Override
    public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
        // No influence since we do not block.
        return strategy;
    }
}
