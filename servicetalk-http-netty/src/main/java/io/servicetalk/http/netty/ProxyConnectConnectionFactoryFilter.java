/*
 * Copyright © 2019-2020, 2022 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.DefaultContextMap;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.DeferSslHandler;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Processors.newSingleProcessor;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.http.api.HttpContextKeys.HTTP_TARGET_ADDRESS_BEHIND_PROXY;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.SUCCESSFUL_2XX;

/**
 * A connection factory filter that sends a `CONNECT` request for https proxying.
 *
 * @param <ResolvedAddress> The type of resolved addresses that can be used for connecting.
 * @param <C> The type of connections created by this factory.
 */
final class ProxyConnectConnectionFactoryFilter<ResolvedAddress, C extends FilterableStreamingHttpConnection>
        implements ConnectionFactoryFilter<ResolvedAddress, C> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyConnectConnectionFactoryFilter.class);

    private final String connectAddress;

    ProxyConnectConnectionFactoryFilter(final CharSequence connectAddress) {
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
        public Single<C> newConnection(final ResolvedAddress resolvedAddress,
                                       @Nullable ContextMap context,
                                       @Nullable final TransportObserver observer) {
            return Single.defer(() -> {
                final ContextMap contextMap = context != null ? context : new DefaultContextMap();
                logUnexpectedAddress(contextMap.put(HTTP_TARGET_ADDRESS_BEHIND_PROXY, connectAddress),
                        connectAddress, LOGGER);
                return delegate().newConnection(resolvedAddress, contextMap, observer).flatMap(c -> {
                    try {
                        return c.request(c.connect(connectAddress).addHeader(CONTENT_LENGTH, ZERO))
                                .flatMap(response -> handleConnectResponse(c, response))
                                // Close recently created connection in case of any error while it connects to the
                                // proxy:
                                .onErrorResume(t -> c.closeAsync().concat(failed(t)));
                        // We do not apply shareContextOnSubscribe() here to isolate a context for `CONNECT` request.
                    } catch (Throwable t) {
                        return c.closeAsync().concat(failed(t));
                    }
                }).shareContextOnSubscribe();
            });
        }

        private Single<C> handleConnectResponse(final C connection, final StreamingHttpResponse response) {
            if (response.status().statusClass() != SUCCESSFUL_2XX) {
                return failed(new ProxyResponseException("Non-successful response from proxy CONNECT " +
                        connectAddress, response.status()));
            }

            final Channel channel = ((NettyConnectionContext) connection.connectionContext()).nettyChannel();
            final SingleSource.Processor<C, C> processor = newSingleProcessor();
            channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                @Override
                public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) {
                    if (evt instanceof SslHandshakeCompletionEvent) {
                        SslHandshakeCompletionEvent event = (SslHandshakeCompletionEvent) evt;
                        if (event.isSuccess()) {
                            processor.onSuccess(connection);
                        } else {
                            processor.onError(event.cause());
                        }
                    }
                    ctx.fireUserEventTriggered(evt);
                }
            });

            final DeferSslHandler deferSslHandler = channel.pipeline().get(DeferSslHandler.class);
            if (deferSslHandler == null) {
                return failed(new IllegalStateException("Failed to find a handler of type " +
                        DeferSslHandler.class + " in channel pipeline."));
            }
            deferSslHandler.ready();

            // There is no need to apply offloading explicitly (despite completing `processor` on the EventLoop)
            // because `payloadBody()` will be offloaded according to the strategy for the request.
            return response.messageBody().ignoreElements().concat(fromSource(processor));
        }
    }

    static void logUnexpectedAddress(@Nullable final Object current, final Object expected, final Logger logger) {
        if (current != null && !expected.equals(current)) {
            logger.info("Observed unexpected value for {}: {}, overridden with: {}",
                    HTTP_TARGET_ADDRESS_BEHIND_PROXY, current, expected);
        }
    }

    @Override
    public HttpExecutionStrategy requiredOffloads() {
        // No influence since we do not block.
        return HttpExecutionStrategies.offloadNone();
    }
}
