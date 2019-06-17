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
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.netty.internal.DefaultNettyPipelinedConnection;
import io.servicetalk.transport.netty.internal.DeferHandler;

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
        implements ConnectionFactoryFilter<ResolvedAddress, C> {

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
        final AbstractStreamingHttpConnection ashc = (AbstractStreamingHttpConnection) c;
        final DefaultNettyPipelinedConnection dnpc = (DefaultNettyPipelinedConnection) ashc.connectionContext();
        return dnpc.nettyChannel();
    }

    private final class ProxyFilter implements ConnectionFactory<ResolvedAddress, C> {

        private final ConnectionFactory<ResolvedAddress, C> original;

        private ProxyFilter(final ConnectionFactory<ResolvedAddress, C> original) {
            this.original = original;
        }

        @Override
        public Single<C> newConnection(final ResolvedAddress resolvedAddress) {
            final Single<C> cSingle = original.newConnection(resolvedAddress);
            return cSingle.flatMap(c -> {
                final StreamingHttpRequest connectRequest = reqRespFactory.connect(connectAddress)
                        .addHeader(CONTENT_LENGTH, ZERO);
                final Single<StreamingHttpResponse> responseSingle = c.request(defaultStrategy(), connectRequest);
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

                        channel.pipeline().get(DeferHandler.class).ready();

                        return response.payloadBody().ignoreElements().concat(fromSource(processor));
                    } else {
                        return response.payloadBody().ignoreElements().concat(
                                failed(new ProxyResponseException("Bad response from proxy", response.status())));
                    }
                });
            });
        }

        @Override
        public Completable onClose() {
            return original.onClose();
        }

        @Override
        public Completable closeAsync() {
            return original.closeAsync();
        }
    }
}
