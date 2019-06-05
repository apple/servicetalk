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

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpStatusClass;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static java.util.Objects.requireNonNull;

class HttpConnectHandler extends ChannelDuplexHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpConnectHandler.class);
    private static final NoOpHandler NO_OP_HANDLER = new NoOpHandler();

    private final String connectAddress;
    private final HttpClientCodec httpClientCodec;
    private final List<Object> writes = new ArrayList<>();
    private final List<ChannelPromise> promises = new ArrayList<>();

    private boolean sentConnect;
    private boolean connected;
    private boolean doFlush;
    private boolean doRead;

    HttpConnectHandler(final ReadOnlyHttpClientConfig config) {
        this.connectAddress = requireNonNull(config.connectAddress()).toString();
        httpClientCodec = new HttpClientCodec(config.maxInitialLineLength(), config.maxHeaderSize(), 1024);
    }

    @Override
    public void read(final ChannelHandlerContext ctx) {
        doRead = true;
    }

    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) {
        writes.add(msg);
        promises.add(promise);
        if (!sentConnect) {
            sentConnect = true;
            ctx.pipeline().addFirst(httpClientCodec);

            // Move the LoggingHandler to the front of the pipeline so that the `CONNECT` request and it's response
            // are logged.
            if (ctx.pipeline().get(LoggingHandler.class) != null) {
                // Use NoOpHandler to "save" the place in the pipeline, for putting the LoggingHandler back later.
                final LoggingHandler loggingHandler = ctx.pipeline().replace(LoggingHandler.class, "noOpHandler",
                        NO_OP_HANDLER);
                ctx.pipeline().addFirst(loggingHandler);
            }

            final DefaultHttpRequest connectRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.CONNECT,
                    connectAddress);
            ctx.writeAndFlush(connectRequest);
            ctx.read();
        }
    }

    @Override
    public void flush(final ChannelHandlerContext ctx) {
        doFlush = true;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        if (msg instanceof HttpObject) {
            if (msg instanceof HttpResponse) {
                final HttpResponse response = (HttpResponse) msg;
                if (HttpStatusClass.SUCCESS.contains(response.status().code())) {
                    LOGGER.debug("Received 2xx response to CONNECT");
                    connected = true;
                } else {
                    for (final ChannelPromise channelPromise : promises) {
                        channelPromise.setFailure(new IOException("Received non-2xx response from proxy: " +
                                response.status()));
                    }
                    promises.clear();
                    writes.clear();
                    resetHandlers(ctx);
                    return;
                }
            }
            if (msg instanceof LastHttpContent && connected) {
                LOGGER.debug("Received LastHttpContent, removing handlers");
                resetHandlers(ctx);
                return;
            }
            if (!connected) {
                LOGGER.warn("Unexpected HttpObject received: {}", msg.getClass());
            }
        } else {
            LOGGER.warn("Unexpected object received: {}", msg.getClass());
            super.channelRead(ctx, msg);
        }
    }

    @Override
    public void channelReadComplete(final ChannelHandlerContext ctx) {
        // don't pass these down
    }

    private void resetHandlers(final ChannelHandlerContext ctx) throws Exception {
        ctx.pipeline().remove(httpClientCodec);
        ctx.pipeline().remove(this);

        if (ctx.pipeline().get(LoggingHandler.class) != null) {
            final LoggingHandler loggingHandler = ctx.pipeline().remove(LoggingHandler.class);
            ctx.pipeline().replace(NO_OP_HANDLER, "loggingHandler", loggingHandler);
        }

        if (doRead) {
            super.read(ctx);
        }
        final Iterator<Object> wi = writes.iterator();
        final Iterator<ChannelPromise> pi = promises.iterator();
        while (wi.hasNext()) {
            super.write(ctx, wi.next(), pi.next());
        }
        if (doFlush) {
            super.flush(ctx);
        }
    }

    private static final class NoOpHandler extends ChannelDuplexHandler {
    }
}
