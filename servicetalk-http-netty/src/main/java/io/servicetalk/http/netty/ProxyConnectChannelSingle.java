/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.ProxyConnectException;
import io.servicetalk.http.api.ProxyConnectResponseException;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.ConnectionObserver.ProxyConnectObserver;
import io.servicetalk.transport.api.RetryableException;
import io.servicetalk.transport.netty.internal.ChannelInitializer;
import io.servicetalk.transport.netty.internal.CloseHandler.InboundDataEndEvent;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMetaDataFactory.newRequestMetaData;
import static io.servicetalk.http.api.HttpRequestMethod.CONNECT;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.SERVER_ERROR_5XX;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.SUCCESSFUL_2XX;
import static io.servicetalk.transport.netty.internal.ChannelCloseUtils.assignConnectionError;

/**
 * A {@link Single} that adds a {@link ChannelHandler} into {@link ChannelPipeline} to perform HTTP/1.1 CONNECT
 * exchange.
 */
final class ProxyConnectChannelSingle extends ChannelInitSingle<Channel> {

    private final ConnectionObserver observer;
    private final HttpHeadersFactory headersFactory;
    private final String connectAddress;

    ProxyConnectChannelSingle(final Channel channel,
                              final ChannelInitializer channelInitializer,
                              final ConnectionObserver observer,
                              final HttpHeadersFactory headersFactory,
                              final String connectAddress) {
        super(channel, channelInitializer);
        this.observer = observer;
        this.headersFactory = headersFactory;
        this.connectAddress = connectAddress;
        assert !channel.config().isAutoRead();
    }

    @Override
    protected ChannelHandler newChannelHandler(final Subscriber<? super Channel> subscriber) {
        return new ProxyConnectHandler(observer, headersFactory, connectAddress, subscriber);
    }

    private static final class ProxyConnectHandler extends ChannelDuplexHandler {

        private static final Logger LOGGER = LoggerFactory.getLogger(ProxyConnectHandler.class);

        private final ConnectionObserver observer;
        private final HttpHeadersFactory headersFactory;
        private final String connectAddress;
        @Nullable
        private Subscriber<? super Channel> subscriber;
        @Nullable
        private ProxyConnectObserver connectObserver;
        @Nullable
        private HttpResponseMetaData response;

        private ProxyConnectHandler(final ConnectionObserver observer,
                                    final HttpHeadersFactory headersFactory,
                                    final String connectAddress,
                                    final Subscriber<? super Channel> subscriber) {
            this.observer = observer;
            this.headersFactory = headersFactory;
            this.connectAddress = connectAddress;
            this.subscriber = subscriber;
        }

        @Override
        public void handlerAdded(final ChannelHandlerContext ctx) {
            if (ctx.channel().isActive()) {
                sendConnectRequest(ctx);
            }
        }

        @Override
        public void channelActive(final ChannelHandlerContext ctx) {
            sendConnectRequest(ctx);
            ctx.fireChannelActive();
        }

        private void sendConnectRequest(final ChannelHandlerContext ctx) {
            final HttpRequestMetaData request = newRequestMetaData(HTTP_1_1, CONNECT, connectAddress,
                    headersFactory.newHeaders()).addHeader(HOST, connectAddress);
            connectObserver = observer.onProxyConnect(request);
            ctx.writeAndFlush(request).addListener(f -> {
                if (f.isSuccess()) {
                    ctx.read();
                } else {
                    failSubscriber(ctx, new RetryableProxyConnectException(ctx.channel() +
                            " Failed to write CONNECT request", f.cause()));
                }
            });
        }

        @Override
        public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
            if (msg instanceof HttpResponseMetaData) {
                if (response != null) {
                    failSubscriber(ctx, new RetryableProxyConnectException(ctx.channel() +
                            " Received two responses for a single CONNECT request"));
                    return;
                }
                response = (HttpResponseMetaData) msg;
                if (response.status().statusClass() != SUCCESSFUL_2XX) {
                    failSubscriber(ctx, unsuccessfulResponse(ctx.channel(), response, connectAddress));
                }
                // We do not complete subscriber here because we need to wait for the HttpResponseDecoder state machine
                // to complete. Completion will be signalled by InboundDataEndEvent. Any other messages before that are
                // unexpected, see https://datatracker.ietf.org/doc/html/rfc9110#section-9.3.6
                // It also helps to make sure we do not propagate InboundDataEndEvent after the next handlers are added
                // to the pipeline, potentially causing changes in their state machine.
            } else {
                failSubscriber(ctx, new RetryableProxyConnectException(ctx.channel() +
                        " Received unexpected message in the pipeline of type: " + msg.getClass().getName()));
            }
        }

        private static ProxyConnectResponseException unsuccessfulResponse(final Channel channel,
                                                                          final HttpResponseMetaData response,
                                                                          final String connectAddress) {
            final String message = channel + " Non-successful response '" + response.status() +
                    "' from proxy on CONNECT " + connectAddress;
            return response.status().statusClass() == SERVER_ERROR_5XX ?
                    new RetryableProxyConnectResponseException(message, response) :
                    new ProxyConnectResponseException(message, response);
        }

        @Override
        public void channelReadComplete(final ChannelHandlerContext ctx) throws Exception {
            if (subscriber != null) {
                ctx.read(); // Keep requesting until finished
            }
            ctx.fireChannelReadComplete();
        }

        @Override
        public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) throws Exception {
            if (evt != InboundDataEndEvent.INSTANCE || subscriber == null) {
                ctx.fireUserEventTriggered(evt);
                return;
            }
            assert response != null;
            assert connectObserver != null;
            connectObserver.proxyConnectComplete(response);
            ctx.pipeline().remove(this);
            final Channel channel = ctx.channel();
            LOGGER.debug("{} Received successful response from proxy on CONNECT {}", channel, connectAddress);
            final Subscriber<? super Channel> subscriberCopy = subscriber;
            subscriber = null;
            subscriberCopy.onSuccess(channel);
        }

        @Override
        public void channelInactive(final ChannelHandlerContext ctx) {
            if (subscriber != null) {
                failSubscriber(ctx, new RetryableProxyConnectException(ctx.channel() +
                        " Connection closed before proxy CONNECT finished"));
                return;
            }
            ctx.fireChannelInactive();
        }

        @Override
        public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) throws Exception {
            if (subscriber != null) {
                failSubscriber(ctx, new ProxyConnectException(ctx.channel() +
                        " Unexpected exception before proxy CONNECT finished", cause));
                return;
            }
            ctx.fireExceptionCaught(cause);
        }

        private void failSubscriber(final ChannelHandlerContext ctx, final Throwable cause) {
            assignConnectionError(ctx.channel(), cause);
            if (subscriber != null) {
                if (connectObserver != null) {
                    connectObserver.proxyConnectFailed(cause);
                }
                final SingleSource.Subscriber<? super Channel> subscriberCopy = subscriber;
                subscriber = null;
                subscriberCopy.onError(cause);
            }
            ctx.close();
        }
    }

    static final class RetryableProxyConnectException extends ProxyConnectException
            implements RetryableException {

        private static final long serialVersionUID = 5118637083568536242L;

        RetryableProxyConnectException(final String message) {
            super(message);
        }

        RetryableProxyConnectException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }

    private static final class RetryableProxyConnectResponseException extends ProxyResponseException
            implements RetryableException {

        private static final long serialVersionUID = -4572727779387205399L;

        RetryableProxyConnectResponseException(final String message, final HttpResponseMetaData response) {
            super(message, response);
        }
    }
}
