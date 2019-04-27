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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.client.api.ConsumableEvent;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.SingleSource.Subscriber;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.SpScPublisherProcessor;
import io.servicetalk.concurrent.api.internal.SubscribableSingle;
import io.servicetalk.concurrent.internal.DelayedCancellable;
import io.servicetalk.concurrent.internal.SequentialCancellable;
import io.servicetalk.http.api.DefaultHttpExecutionContext;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpEventKey;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.netty.internal.ChannelInitializer;
import io.servicetalk.transport.netty.internal.DefaultNettyConnection;
import io.servicetalk.transport.netty.internal.FlushStrategy;
import io.servicetalk.transport.netty.internal.NettyChannelListenableAsyncCloseable;
import io.servicetalk.transport.netty.internal.NettyConnection.TerminalPredicate;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2GoAwayFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.DefaultHttp2PingFrame;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2GoAwayFrame;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2MultiplexCodec;
import io.netty.handler.codec.http2.Http2MultiplexCodecBuilder;
import io.netty.handler.codec.http2.Http2PingFrame;
import io.netty.handler.codec.http2.Http2SettingsAckFrame;
import io.netty.handler.codec.http2.Http2SettingsFrame;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap;
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static io.netty.handler.codec.http.HttpHeaderNames.EXPECT;
import static io.netty.handler.codec.http.HttpHeaderNames.TE;
import static io.netty.handler.codec.http.HttpHeaderValues.CONTINUE;
import static io.netty.handler.codec.http.HttpHeaderValues.TRAILERS;
import static io.netty.handler.codec.http2.Http2Error.NO_ERROR;
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.AUTHORITY;
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.METHOD;
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.PATH;
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.STATUS;
import static io.netty.handler.codec.http2.Http2MultiplexCodecBuilder.forClient;
import static io.netty.handler.logging.LogLevel.DEBUG;
import static io.netty.util.ReferenceCountUtil.release;
import static io.servicetalk.buffer.netty.BufferUtil.newBufferFrom;
import static io.servicetalk.buffer.netty.BufferUtil.toByteBufNoThrow;
import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.Processors.newCompletableProcessor;
import static io.servicetalk.concurrent.api.Processors.newSingleProcessor;
import static io.servicetalk.concurrent.api.Publisher.failed;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverTerminalFromSource;
import static io.servicetalk.concurrent.internal.ThrowableUtil.unknownStackTrace;
import static io.servicetalk.http.api.HttpEventKey.MAX_CONCURRENCY;
import static io.servicetalk.http.api.HttpHeaderNames.CONNECTION;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.COOKIE;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderNames.UPGRADE;
import static io.servicetalk.http.api.HttpHeaderValues.KEEP_ALIVE;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_2_0;
import static io.servicetalk.http.api.HttpRequestMethod.CONNECT;
import static io.servicetalk.http.api.HttpRequestMethod.Properties.NONE;
import static io.servicetalk.http.api.StreamingHttpRequests.newRequest;
import static io.servicetalk.http.api.StreamingHttpResponses.newResponse;
import static io.servicetalk.http.netty.HeaderUtils.indexOf;
import static io.servicetalk.http.netty.HeaderUtils.shouldAddZeroContentLength;
import static io.servicetalk.transport.netty.internal.ChannelSet.CHANNEL_CLOSABLE_KEY;
import static io.servicetalk.transport.netty.internal.CloseHandler.UNSUPPORTED_PROTOCOL_CLOSE_HANDLER;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.fromNettyEventLoop;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

final class H2ClientParentConnectionContext extends NettyChannelListenableAsyncCloseable implements
                                                                                         NettyConnectionContext {
    private static final Predicate<Object> LAST_CHUNK_PREDICATE = p -> p instanceof HttpHeaders;
    private static final ClosedChannelException CLOSED_CHANNEL_INACTIVE = unknownStackTrace(
            new ClosedChannelException(), H2ClientParentConnectionContext.class, "channelInactive(..)");
    private static final ClosedChannelException CLOSED_HANDLER_REMOVED =
            unknownStackTrace(new ClosedChannelException(), H2ClientParentConnectionContext.class,
                    "handlerRemoved(..)");
    private static final Logger LOGGER = LoggerFactory.getLogger(H2ClientParentConnectionContext.class);
    private static final AtomicReferenceFieldUpdater<H2ClientParentConnectionContext, FlushStrategy>
            flushStrategyUpdater = newUpdater(H2ClientParentConnectionContext.class, FlushStrategy.class,
            "flushStrategy");
    private static final long GRACEFUL_CLOSE_PING_CONTENT = 34213531352L;
    private static final long GRACEFUL_CLOSE_PING_ACK_TIMEOUT_MS = 10000;
    private volatile FlushStrategy flushStrategy;
    private final HttpExecutionContext executionContext;
    private final SingleSource.Processor<Throwable, Throwable> transportError = newSingleProcessor();
    private final CompletableSource.Processor onClosing = newCompletableProcessor();
    @Nullable
    private SSLSession sslSession;
    @Nullable
    private ScheduledFuture<?> gracefulClosePingAckTimeoutFuture;

    private H2ClientParentConnectionContext(Channel channel, BufferAllocator allocator,
                                            Executor executor, FlushStrategy flushStrategy,
                                            HttpExecutionStrategy executionStrategy) {
        super(channel, executor);
        this.executionContext = new DefaultHttpExecutionContext(allocator, fromNettyEventLoop(channel.eventLoop()),
                executor, executionStrategy);
        this.flushStrategy = flushStrategy;
        // Just in case the channel abruptly closes, we should complete the onClosing Completable.
        onClose().subscribe(onClosing::onComplete);
    }

    interface Http2ParentConnection extends FilterableStreamingHttpConnection, NettyConnectionContext {
    }

    static Single<Http2ParentConnection> initChannel(Channel channel, BufferAllocator allocator,
                                                     Executor executor, ReadOnlyH2ClientConfig config,
                                                     StreamingHttpRequestResponseFactory reqRespFactory,
                                                     FlushStrategy parentFlushStrategy,
                                                     HttpExecutionStrategy executionStrategy,
                                                     ChannelInitializer initializer) {
        return new SubscribableSingle<Http2ParentConnection>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super Http2ParentConnection> subscriber) {
                final H2ParentClientConnection parentChannelInitializer;
                final DelayedCancellable delayedCancellable;
                final ChannelPipeline pipeline;
                try {
                    delayedCancellable = new DelayedCancellable();
                    H2ClientParentConnectionContext connection = new H2ClientParentConnectionContext(channel,
                            allocator, executor, parentFlushStrategy, executionStrategy);
                    channel.attr(CHANNEL_CLOSABLE_KEY).set(connection);
                    // We need the NettyToStChannelInboundHandler to be last in the pipeline. We accomplish that by
                    // calling the ChannelInitializer before we do addLast for the NettyToStChannelInboundHandler.
                    // This could mean if there are any synchronous events generated via ChannelInitializer handlers
                    // that NettyToStChannelInboundHandler will not see them. This is currently not an issue and would
                    // require some pipeline modifications if we wanted to insert NettyToStChannelInboundHandler first,
                    // but not allow any other handlers to be after it.
                    initializer.init(channel, connection);
                    pipeline = channel.pipeline();
                    Http2MultiplexCodecBuilder multiplexCodecBuilder = forClient(H2PushStreamHandler.INSTANCE)
                            // Due to offloading this may be processed asynchronously, so we manually control the
                            // SETTINGS ACK frames.
                            .autoAckSettingsFrame(false)
                            // We don't want to rely upon Netty to manage the graceful close timeout, because we expect
                            // the user to apply their own timeout at the call site.
                            .gracefulShutdownTimeoutMillis(-1);
                    String h2FrameLogger = config.h2FrameLogger();
                    if (h2FrameLogger != null) {
                        multiplexCodecBuilder.frameLogger(new Http2FrameLogger(DEBUG, h2FrameLogger));
                    }

                    // TODO(scott): more configuration. header validation, settings stream, etc...

                    pipeline.addLast(multiplexCodecBuilder.build());
                    parentChannelInitializer = new H2ParentClientConnection(connection, subscriber,
                            delayedCancellable,
                            pipeline.get(SslHandler.class) != null || pipeline.get(SniHandler.class) != null,
                            pipeline.lastContext(), config.h2HeadersFactory(), reqRespFactory);
                } catch (Throwable cause) {
                    channel.close();
                    subscriber.onSubscribe(IGNORE_CANCEL);
                    subscriber.onError(cause);
                    return;
                }
                subscriber.onSubscribe(delayedCancellable);
                // We have to add to the pipeline AFTER we call onSubscribe, because adding to the pipeline may invoke
                // callbacks that interact with the subscriber.
                pipeline.addLast(parentChannelInitializer);
            }
        };
    }

    @Override
    public Cancellable updateFlushStrategy(final UnaryOperator<FlushStrategy> strategyProvider) {
        FlushStrategy old = flushStrategyUpdater.getAndUpdate(this, strategyProvider);
        return () -> updateFlushStrategy(__ -> old);
    }

    @Override
    public Single<Throwable> transportError() {
        return fromSource(transportError).publishOn(executionContext().executor());
    }

    @Override
    public Completable onClosing() {
        return fromSource(onClosing).publishOn(executionContext().executor());
    }

    @Override
    public SocketAddress localAddress() {
        return channel().localAddress();
    }

    @Override
    public SocketAddress remoteAddress() {
        return channel().remoteAddress();
    }

    @Nullable
    @Override
    public SSLSession sslSession() {
        return sslSession;
    }

    @Override
    public HttpExecutionContext executionContext() {
        return executionContext;
    }

    @Override
    protected void doCloseAsyncGracefully() {
        // TODO(scott): invoked from multiple threads?
        EventLoop eventLoop = channel().eventLoop();
        if (eventLoop.inEventLoop()) {
            doCloseAsyncGracefully0();
        } else {
            try {
                eventLoop.execute(this::doCloseAsyncGracefully0);
            } catch (Throwable cause) {
                channel().close();
                LOGGER.warn("channel={} EventLoop rejected a task for graceful shutdown, force closing connection",
                        channel(), cause);
            }
        }
    }

    private void doCloseAsyncGracefully0() {
        if (gracefulClosePingAckTimeoutFuture == null) {
            // Set the gracefulClosePingAckTimeoutFuture before doing the write, because we will reference the state
            // when we receive the PING(ACK) to determine if action is necessary, and it is conceivable that the
            // write future may not be executed which sets the timer.
            gracefulClosePingAckTimeoutFuture = NoopScheduledFuture.INSTANCE;

            onClosing.onComplete();

            // The graceful close process is described in [1]. In general it involves sending 2 GOAWAY frames. The first
            // GOAWAY has last-stream-id=<maximum stream ID> to indicate no new streams can be created, wait for 2 RTT time
            // duration for inflight frames to land, and the second GOAWAY includes the maximum known stream ID.
            // To account for 2 RTTs we can send a PING and when the PING(ACK) comes back we can send the second GOAWAY.
            // https://tools.ietf.org/html/rfc7540#section-6.8
            DefaultHttp2GoAwayFrame goAwayFrame = new DefaultHttp2GoAwayFrame(NO_ERROR);
            goAwayFrame.setExtraStreamIds(Integer.MAX_VALUE);
            channel().write(goAwayFrame);
            channel().writeAndFlush(new DefaultHttp2PingFrame(GRACEFUL_CLOSE_PING_CONTENT)).addListener(
                    future -> {
                        // If gracefulClosePingAckTimeoutFuture is not null that means we have already received the
                        // PING(ACK) and there is no need to apply the timeout.
                        if (gracefulClosePingAckTimeoutFuture == null) {
                            gracefulClosePingAckTimeoutFuture = channel().eventLoop().schedule(() -> {
                                // If the PING(ACK) times out we may have under estimated the 2RTT time so we
                                // optimistically keep the connection open and rely upon higher level timeouts to tear
                                // down the connection.
                                channel().writeAndFlush(new DefaultHttp2GoAwayFrame(NO_ERROR));
                                LOGGER.debug("channel={} timeout {}ms waiting for PING(ACK) during graceful close",
                                        channel(), GRACEFUL_CLOSE_PING_ACK_TIMEOUT_MS);
                            }, GRACEFUL_CLOSE_PING_ACK_TIMEOUT_MS, MILLISECONDS);
                        }
                    });
        }
    }

    private static final class H2ParentClientConnection extends ChannelInboundHandlerAdapter implements
                                                                                             Http2ParentConnection {
        private final Http2StreamChannelBootstrap bs;
        private final ChannelHandlerContext http2MultiplexCodecContext;
        private final boolean waitForSslHandshake;
        private final H2ClientParentConnectionContext connection;
        private final DelayedCancellable delayedCancellable;
        private final HttpHeadersFactory headersFactory;
        private final StreamingHttpRequestResponseFactory reqRespFactory;
        private final SpScPublisherProcessor<ConsumableEvent<Integer>> maxConcurrencyPublisher;
        @Nullable
        private Subscriber<? super Http2ParentConnection> subscriber;

        H2ParentClientConnection(H2ClientParentConnectionContext connection,
                                 Subscriber<? super Http2ParentConnection> subscriber,
                                 DelayedCancellable delayedCancellable,
                                 boolean waitForSslHandshake,
                                 ChannelHandlerContext http2MultiplexCodecContext,
                                 HttpHeadersFactory headersFactory,
                                 StreamingHttpRequestResponseFactory reqRespFactory) {
            assert http2MultiplexCodecContext.handler() instanceof Http2MultiplexCodec;
            this.connection = connection;
            this.subscriber = requireNonNull(subscriber);
            this.delayedCancellable = delayedCancellable;
            this.waitForSslHandshake = waitForSslHandshake;
            this.http2MultiplexCodecContext = requireNonNull(http2MultiplexCodecContext);
            this.headersFactory = requireNonNull(headersFactory);
            this.reqRespFactory = requireNonNull(reqRespFactory);
            bs = new Http2StreamChannelBootstrap(connection.channel());
            maxConcurrencyPublisher = new SpScPublisherProcessor<>(16);
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            delayedCancellable.delayedCancellable(ctx.channel()::close);
            // Double check In the event of a late handler (or test utility like EmbeddedChannel) check activeness.
            if (ctx.channel().isActive()) {
                doChannelActive(ctx);
            }
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            doChannelActive(ctx);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            tryFailSubscriber(CLOSED_CHANNEL_INACTIVE);
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            tryFailSubscriber(CLOSED_HANDLER_REMOVED);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            connection.transportError.onSuccess(cause);
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            try {
                if (evt instanceof SslHandshakeCompletionEvent) {
                    SslHandshakeCompletionEvent sslEvent = ((SslHandshakeCompletionEvent) evt);
                    if (sslEvent.isSuccess()) {
                        final SslHandler sslHandler = ctx.pipeline().get(SslHandler.class);
                        if (sslHandler != null) {
                            connection.sslSession = sslHandler.engine().getSession();
                            if (subscriber != null) {
                                assert waitForSslHandshake;
                                completeSubscriber();
                            }
                        } else {
                            final String errMsg = "Unable to find " + SslHandler.class.getName() + " in the pipeline.";
                            tryFailSubscriber(new IllegalStateException(errMsg));
                            LOGGER.error(errMsg);
                        }
                    } else {
                        tryFailSubscriber(sslEvent.cause());
                    }
                }
            } finally {
                release(evt);
            }
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof Http2SettingsFrame) {
                Http2SettingsFrame settingsFrame = (Http2SettingsFrame) msg;
                Long maxConcurrentStreams = settingsFrame.settings().maxConcurrentStreams();
                if (maxConcurrentStreams == null) {
                    ctx.writeAndFlush(Http2SettingsAckFrame.INSTANCE);
                } else {
                    maxConcurrencyPublisher.sendOnNext(new MaxConcurrencyConsumableEvent(
                            maxConcurrentStreams.intValue(), ctx.channel()));
                }
            } else if (msg instanceof Http2GoAwayFrame) {
                Http2GoAwayFrame goAwayFrame = (Http2GoAwayFrame) msg;
                goAwayFrame.release();
                connection.onClosing.onComplete();

                // We trigger the graceful close process here (with no timeout) to make sure the socket is closed once
                // the existing streams are closed. The MultiplexCodec may simulate a GOAWAY when the stream IDs are
                // exhausted so we shouldn't rely upon our peer to close the transport.
                connection.doCloseAsyncGracefully0();
            } else if (msg instanceof Http2PingFrame) {
              Http2PingFrame pingFrame = (Http2PingFrame) msg;
              if (pingFrame.ack() && pingFrame.content() == GRACEFUL_CLOSE_PING_CONTENT &&
                      connection.gracefulClosePingAckTimeoutFuture != null) {
                  connection.gracefulClosePingAckTimeoutFuture.cancel(true);
                  connection.gracefulClosePingAckTimeoutFuture = NoopScheduledFuture.INSTANCE;
                  ctx.writeAndFlush(new DefaultHttp2GoAwayFrame(NO_ERROR));
              }
            } else {
                ctx.fireChannelRead(msg);
            }
        }

        private void doChannelActive(ChannelHandlerContext ctx) {
            if (waitForSslHandshake) {
                // Force a read to get the SSL handshake started, any application data that makes it past the SslHandler
                // will be queued in the NettyChannelPublisher.
                ctx.read();
            } else if (subscriber != null) {
                completeSubscriber();
            }
        }

        private void completeSubscriber() {
            assert subscriber != null;
            Subscriber<? super Http2ParentConnection> subscriberCopy = subscriber;
            subscriber = null;
            subscriberCopy.onSuccess(this);
        }

        private void tryFailSubscriber(Throwable cause) {
            if (subscriber != null) {
                connection.channel().close();
                Subscriber<? super Http2ParentConnection> subscriberCopy = subscriber;
                subscriber = null;
                subscriberCopy.onError(cause);
            }
        }

        @Override
        public ConnectionContext connectionContext() {
            return connection;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> Publisher<? extends T> transportEventStream(final HttpEventKey<T> eventKey) {
            return eventKey == MAX_CONCURRENCY ? (Publisher<T>) maxConcurrencyPublisher :
                    failed(new IllegalArgumentException("Unknown key: " + eventKey));
        }

        @Override
        public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                     final StreamingHttpRequest request) {
            return new SubscribableSingle<StreamingHttpResponse>() {
                @Override
                protected void handleSubscribe(final Subscriber<? super StreamingHttpResponse> subscriber) {
                    final Promise<Http2StreamChannel> promise;
                    final SequentialCancellable sequentialCancellable;
                    try {
                        final EventExecutor e = connection.channel().eventLoop();
                        promise = e.newPromise();
                        if (e.inEventLoop()) {
                            bs.open0(http2MultiplexCodecContext, promise);
                        } else {
                            e.execute(() -> bs.open0(http2MultiplexCodecContext, promise));
                        }
                        sequentialCancellable = new SequentialCancellable(() -> promise.cancel(true));
                    } catch (Throwable cause) {
                        deliverTerminalFromSource(subscriber, cause);
                        return;
                    }
                    subscriber.onSubscribe(sequentialCancellable);
                    if (promise.isDone()) {
                        childChannelActive(promise, subscriber, sequentialCancellable, strategy, request);
                    } else {
                        promise.addListener((FutureListener<Http2StreamChannel>) future ->
                                childChannelActive(future, subscriber, sequentialCancellable, strategy, request));
                    }
                }
            };
        }

        private void childChannelActive(Future<Http2StreamChannel> future,
                                        Subscriber<? super StreamingHttpResponse> subscriber,
                                        SequentialCancellable sequentialCancellable,
                                        HttpExecutionStrategy strategy,
                                        StreamingHttpRequest request) {
            final SingleSource<StreamingHttpResponse> responseSingle;
            Throwable futureCause = future.cause(); // assume this doesn't throw
            if (futureCause == null) {
                try {
                    Http2StreamChannel streamChannel = future.getNow();
                    streamChannel.pipeline().addLast(new H2ToStH1ClientDuplexHandler(waitForSslHandshake,
                            connection.executionContext().bufferAllocator(), headersFactory));
                    DefaultNettyConnection<Object, Object> nettyConnection =
                            DefaultNettyConnection.initChildChannel(streamChannel,
                                    connection.executionContext.bufferAllocator(),
                                    connection.executionContext.executor(),
                                    new TerminalPredicate<>(LAST_CHUNK_PREDICATE),
                                    // Http2StreamChannel is not of type SocketChannel. Also Netty will manage the half
                                    // closure based upon stream state.
                                    UNSUPPORTED_PROTOCOL_CLOSE_HANDLER,
                                    connection.flushStrategy,
                                    connection.executionContext.executionStrategy());
                    responseSingle = toSource(new NonPipelinedStreamingHttpConnection(nettyConnection,
                            executionContext(), reqRespFactory).request(strategy, request));
                } catch (Throwable cause) {
                    subscriber.onError(cause);
                    return;
                }
                responseSingle.subscribe(new Subscriber<StreamingHttpResponse>() {
                    @Override
                    public void onSubscribe(final Cancellable cancellable) {
                        sequentialCancellable.nextCancellable(cancellable);
                    }

                    @Override
                    public void onSuccess(@Nullable final StreamingHttpResponse result) {
                        subscriber.onSuccess(result);
                    }

                    @Override
                    public void onError(final Throwable t) {
                        subscriber.onError(t);
                    }
                });
            } else {
                subscriber.onError(futureCause);
            }
        }

        @Override
        public SocketAddress localAddress() {
            return connection.localAddress();
        }

        @Override
        public SocketAddress remoteAddress() {
            return connection.remoteAddress();
        }

        @Nullable
        @Override
        public SSLSession sslSession() {
            return connection.sslSession();
        }

        @Override
        public HttpExecutionContext executionContext() {
            return connection.executionContext();
        }

        @Override
        public StreamingHttpResponseFactory httpResponseFactory() {
            return reqRespFactory;
        }

        @Override
        public void close() throws Exception {
            // TODO(scott): should we have a utility method to make sure this is done consistently?
            connection.closeAsync().toFuture().get();
        }

        @Override
        public Completable onClose() {
            return connection.onClose();
        }

        @Override
        public Completable closeAsync() {
            return connection.closeAsync();
        }

        @Override
        public Completable closeAsyncGracefully() {
            return connection.closeAsyncGracefully();
        }

        @Override
        public StreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
            return reqRespFactory.newRequest(method, requestTarget);
        }

        @Override
        public Cancellable updateFlushStrategy(final UnaryOperator<FlushStrategy> strategyProvider) {
            return connection.updateFlushStrategy(strategyProvider);
        }

        @Override
        public Single<Throwable> transportError() {
            return connection.transportError();
        }

        @Override
        public Completable onClosing() {
            return connection.onClosing();
        }
    }

    @ChannelHandler.Sharable
    private static final class H2PushStreamHandler extends ChannelInboundHandlerAdapter {
        static final ChannelInboundHandlerAdapter INSTANCE = new H2PushStreamHandler();

        private H2PushStreamHandler() {
            // singleton
        }

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) {
            ctx.close(); // push streams are not supported
        }
    }

    private static final class H2ToStH1ClientDuplexHandler extends ChannelDuplexHandler {
        private boolean readHeaders;
        private final HttpScheme scheme;
        private final HttpHeadersFactory headersFactory;
        private final BufferAllocator allocator;
        @Nullable
        private HttpRequestMethod method;

        H2ToStH1ClientDuplexHandler(boolean sslEnabled, BufferAllocator allocator, HttpHeadersFactory headersFactory) {
            this.scheme = sslEnabled ? HttpScheme.HTTPS : HttpScheme.HTTP;
            this.allocator = allocator;
            this.headersFactory = headersFactory;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            if (msg instanceof HttpRequestMetaData) {
                HttpRequestMetaData metaData = (HttpRequestMetaData) msg;
                HttpHeaders h1Headers = metaData.headers();
                CharSequence host = h1Headers.getAndRemove(HOST);
                Http2Headers h2Headers = h1HeadersToH2Headers(h1Headers);
                if (host == null) {
                    host = metaData.effectiveHost();
                    if (host != null) {
                        h2Headers.authority(host);
                    }
                } else {
                    h2Headers.authority(host);
                }
                method = metaData.method();
                h2Headers.method(method.name());
                if (!CONNECT.equals(method)) {
                    // The ":scheme" and ":path" pseudo-header fields MUST be omitted.
                    // https://tools.ietf.org/html/rfc7540#section-8.3
                    h2Headers.scheme(scheme.name());
                    h2Headers.path(metaData.path());
                }
                ctx.write(new DefaultHttp2HeadersFrame(h2Headers, false), promise);
            } else if (msg instanceof Buffer) {
                ByteBuf byteBuf = toByteBufNoThrow((Buffer) msg);
                if (byteBuf == null) {
                    ctx.close();
                    promise.setFailure(new IllegalArgumentException("unsupported Buffer type:" + msg));
                } else {
                    ctx.write(new DefaultHttp2DataFrame(byteBuf.retain(), false), promise);
                }
            } else if (msg instanceof HttpHeaders) {
                HttpHeaders h1Headers = (HttpHeaders) msg;
                Http2Headers h2Headers = h1HeadersToH2Headers(h1Headers);
                if (h2Headers.isEmpty()) {
                    ctx.write(new DefaultHttp2DataFrame(EMPTY_BUFFER, true), promise);
                } else {
                    ctx.write(new DefaultHttp2HeadersFrame(h2Headers, true), promise);
                }
            } else {
                ctx.write(msg, promise);
            }
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof Http2HeadersFrame) {
                Http2HeadersFrame headersFrame = (Http2HeadersFrame) msg;
                Http2Headers h2Headers = headersFrame.headers();
                final CharSequence status;
                if (!readHeaders) {
                    status = h2Headers.getAndRemove(STATUS.value());
                    if (status == null) {
                        throw new IllegalArgumentException("a response must have " + STATUS + " header");
                    }
                    // If this is a 100-continue request we will see multiple initial headers frames
                    readHeaders = !io.netty.handler.codec.http.HttpResponseStatus.CONTINUE.codeAsText()
                            .contentEqualsIgnoreCase(status);
                } else {
                    status = null;
                }

                if (headersFrame.isEndStream()) {
                    if (status != null) {
                        fireFullResponse(ctx, h2Headers, status);
                    } else {
                        ctx.fireChannelRead(h2HeadersToH1HeadersClient(h2Headers));
                    }
                } else if (status == null) {
                    throw new IllegalArgumentException("a response must have " + STATUS + " header");
                } else {
                    if (!h2Headers.contains(HttpHeaderNames.CONTENT_LENGTH)) {
                        h2Headers.add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
                    }
                    StreamingHttpResponse response = newResponse(HttpResponseStatus.of(status), HTTP_2_0,
                            h2HeadersToH1HeadersClient(h2Headers), headersFactory.newEmptyTrailers(), allocator);
                    ctx.fireChannelRead(response);
                }
            } else if (msg instanceof Http2DataFrame) {
                Http2DataFrame dataFrame = (Http2DataFrame) msg;
                if (dataFrame.content().isReadable()) {
                    ctx.fireChannelRead(newBufferFrom(dataFrame.content()));
                }
                if (dataFrame.isEndStream()) {
                    ctx.fireChannelRead(headersFactory.newEmptyTrailers());
                }
            } else {
                ctx.fireChannelRead(msg);
            }
        }

        private void fireFullResponse(ChannelHandlerContext ctx, final Http2Headers h2Headers, CharSequence status) {
            StreamingHttpResponse response = newResponse(HttpResponseStatus.of(status), HTTP_2_0,
                    h2HeadersToH1HeadersClient(h2Headers), headersFactory.newEmptyTrailers(), allocator);
            assert method != null;
            if (shouldAddZeroContentLength(response, method)) {
                h2Headers.add(CONTENT_LENGTH, ZERO);
            }
            ctx.fireChannelRead(response);
            ctx.fireChannelRead(headersFactory.newEmptyTrailers());
        }

        private NettyH2HeadersToHttpHeaders h2HeadersToH1HeadersClient(Http2Headers h2Headers) {
            h2HeadersSanitizeForH1(h2Headers);
            return new NettyH2HeadersToHttpHeaders(h2Headers, headersFactory.validateCookies());
        }
    }

    private static final class H2ToStH1ServerDuplexHandler extends ChannelDuplexHandler {
        private boolean readHeaders;
        private final HttpScheme scheme;
        private final HttpHeadersFactory headersFactory;
        private final BufferAllocator allocator;

        H2ToStH1ServerDuplexHandler(boolean sslEnabled, BufferAllocator allocator, HttpHeadersFactory headersFactory) {
            this.scheme = sslEnabled ? HttpScheme.HTTPS : HttpScheme.HTTP;
            this.allocator = allocator;
            this.headersFactory = headersFactory;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            if (msg instanceof HttpResponseMetaData) {
                HttpResponseMetaData metaData = (HttpResponseMetaData) msg;
                HttpHeaders h1Headers = metaData.headers();
                Http2Headers h2Headers = h1HeadersToH2Headers(h1Headers);
                h2Headers.status(metaData.status().toString());
                h2Headers.scheme(scheme.name());
                ctx.write(new DefaultHttp2HeadersFrame(h2Headers, false), promise);
            } else if (msg instanceof Buffer) {
                ByteBuf byteBuf = toByteBufNoThrow((Buffer) msg);
                if (byteBuf == null) {
                    ctx.close();
                    promise.setFailure(new IllegalArgumentException("unsupported Buffer type:" + msg));
                } else {
                    ctx.write(new DefaultHttp2DataFrame(byteBuf.retain(), false), promise);
                }
            } else if (msg instanceof HttpHeaders) {
                HttpHeaders h1Headers = (HttpHeaders) msg;
                Http2Headers h2Headers = h1HeadersToH2Headers(h1Headers);
                if (h2Headers.isEmpty()) {
                    ctx.write(new DefaultHttp2DataFrame(EMPTY_BUFFER, true), promise);
                } else {
                    ctx.write(new DefaultHttp2HeadersFrame(h2Headers, true), promise);
                }
            } else {
                ctx.write(msg, promise);
            }
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof Http2HeadersFrame) {
                Http2HeadersFrame headersFrame = (Http2HeadersFrame) msg;
                Http2Headers h2Headers = headersFrame.headers();
                final CharSequence method;
                final CharSequence path;
                if (!readHeaders) {
                    method = h2Headers.getAndRemove(METHOD.value());
                    path = h2Headers.getAndRemove(PATH.value());
                    if (path == null || method == null) {
                        throw new IllegalArgumentException("a request must have " + METHOD + " and " +
                                PATH + " headers");
                    }
                    // If this is a 100-continue request we will see multiple initial headers frames
                    readHeaders = !h2Headers.contains(EXPECT, CONTINUE);
                } else {
                    method = null;
                    path = null;
                }

                if (headersFrame.isEndStream()) {
                    if (method != null) {
                        fireFullRequest(ctx, h2Headers, method, path);
                    } else {
                        ctx.fireChannelRead(h2TrailersToH1TrailersServer(h2Headers));
                    }
                } else if (method == null) {
                    throw new IllegalArgumentException("a request must have " + METHOD + " and " +
                            PATH + " headers");
                } else {
                    if (!h2Headers.contains(HttpHeaderNames.CONTENT_LENGTH)) {
                        h2Headers.add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
                    }
                    StreamingHttpRequest request = newRequest(
                            sequenceToHttpRequestMethod(method), path.toString(), HTTP_2_0,
                            h2HeadersToH1HeadersServer(h2Headers), headersFactory.newEmptyTrailers(), allocator);
                    ctx.fireChannelRead(request);
                }
            } else if (msg instanceof Http2DataFrame) {
                Http2DataFrame dataFrame = (Http2DataFrame) msg;
                if (dataFrame.content().isReadable()) {
                    ctx.fireChannelRead(newBufferFrom(dataFrame.content()));
                }
                if (dataFrame.isEndStream()) {
                    ctx.fireChannelRead(headersFactory.newEmptyTrailers());
                }
            } else {
                ctx.fireChannelRead(msg);
            }
        }

        private void fireFullRequest(ChannelHandlerContext ctx, final Http2Headers h2Headers,
                                     CharSequence method, CharSequence path) {
            StreamingHttpRequest request = newRequest(
                    sequenceToHttpRequestMethod(method), path.toString(), HTTP_2_0,
                    h2HeadersToH1HeadersServer(h2Headers), headersFactory.newEmptyTrailers(), allocator);
            if (shouldAddZeroContentLength(request)) {
                h2Headers.add(CONTENT_LENGTH, ZERO);
            }
            ctx.fireChannelRead(request);
            ctx.fireChannelRead(headersFactory.newEmptyTrailers());
        }

        private NettyH2HeadersToHttpHeaders h2HeadersToH1HeadersServer(Http2Headers h2Headers) {
            CharSequence value = h2Headers.getAndRemove(AUTHORITY.value());
            if (value != null) {
                h2Headers.set(HOST, value);
            }
            h2Headers.remove(PseudoHeaderName.SCHEME.value());
            h2HeadersSanitizeForH1(h2Headers);
            return new NettyH2HeadersToHttpHeaders(h2Headers, headersFactory.validateCookies());
        }

        private NettyH2HeadersToHttpHeaders h2TrailersToH1TrailersServer(Http2Headers h2Headers) {
            return new NettyH2HeadersToHttpHeaders(h2Headers, headersFactory.validateCookies());
        }

        private static HttpRequestMethod sequenceToHttpRequestMethod(CharSequence sequence) {
            String strMethod = sequence.toString();
            HttpRequestMethod reqMethod = HttpRequestMethod.of(strMethod);
            return reqMethod != null ? reqMethod : HttpRequestMethod.of(strMethod, NONE);
        }
    }

    private static void h2HeadersSanitizeForH1(Http2Headers h2Headers) {
        h2Headers.remove(HttpHeaderNames.TRANSFER_ENCODING);
        h2Headers.remove(HttpHeaderNames.TRAILER);
        h2HeadersCompressCookieCrumbs(h2Headers);
    }

    /**
     * Combine the cookie values into 1 header entry as required by
     * <a href="https://tools.ietf.org/html/rfc7540#section-8.1.2.5">RFC 7540, 8.1.2.5</a>.
     *
     * @param h2Headers The headers which may contain cookies.
     */
    private static void h2HeadersCompressCookieCrumbs(Http2Headers h2Headers) {
        // Netty's value iterator doesn't return elements in insertion order, this is not strictly compliant with the
        // RFC and may result in reversed order cookies.
        Iterator<? extends CharSequence> cookieItr = h2Headers.valueIterator(HttpHeaderNames.COOKIE);
        if (cookieItr.hasNext()) {
            CharSequence prevCookItr = cookieItr.next();
            if (cookieItr.hasNext()) {
                CharSequence nextCookItr = cookieItr.next();
                // *2 gives some space for an extra cookie.
                StringBuilder sb = new StringBuilder(prevCookItr.length() * 2 + nextCookItr.length() + 2);
                sb.append(prevCookItr).append("; ").append(nextCookItr);
                while (cookieItr.hasNext()) {
                    nextCookItr = cookieItr.next();
                    sb.append("; ").append(nextCookItr);
                }
                h2Headers.set(HttpHeaderNames.COOKIE, sb.toString());
            }
        }
    }

    /**
     * Split up cookies to allow for better compression as described in
     * <a href="https://tools.ietf.org/html/rfc7540#section-8.1.2.5">RFC 7540, 8.1.2.5</a>.
     *
     * @param h1Headers The headers which may contain cookies.
     */
    private static void h1HeadersSplitCookieCrumbs(HttpHeaders h1Headers) {
        Iterator<? extends CharSequence> cookieItr = h1Headers.values(COOKIE);
        // We want to avoid "concurrent modifications" of the headers while we are iterating. So we insert crumbs
        // into an intermediate collection and insert them after the split process concludes.
        List<CharSequence> cookiesToAdd = null;
        while (cookieItr.hasNext()) {
            CharSequence nextCookie = cookieItr.next();
            int i = indexOf(nextCookie, ';', 0);
            if (i > 0) {
                if (cookiesToAdd == null) {
                    cookiesToAdd = new ArrayList<>(4);
                }
                int start = 0;
                do {
                    cookiesToAdd.add(nextCookie.subSequence(start, i));
                    // skip 2 characters "; " (see https://tools.ietf.org/html/rfc6265#section-4.2.1)
                    start = i + 2;
                } while (start < nextCookie.length() &&
                         (i = indexOf(nextCookie, ';', start)) >= 0);
                cookiesToAdd.add(nextCookie.subSequence(start, nextCookie.length()));
                cookieItr.remove();
            }
        }
        if (cookiesToAdd != null) {
            for (CharSequence crumb : cookiesToAdd) {
                h1Headers.add(COOKIE, crumb);
            }
        }
    }

    private static Http2Headers h1HeadersToH2Headers(HttpHeaders h1Headers) {
        // H2 doesn't support connection headers, so remove each one, and the headers corresponding to the
        // connection value.
        // https://tools.ietf.org/html/rfc7540#section-8.1.2.2
        Iterator<? extends CharSequence> connectionItr = h1Headers.values(CONNECTION);
        if (connectionItr.hasNext()) {
            do {
                String connectionHeader = connectionItr.next().toString();
                connectionItr.remove();
                int i = connectionHeader.indexOf(',');
                if (i != -1) {
                    int start = 0;
                    do {
                        h1Headers.remove(connectionHeader.substring(start, i));
                        start = i + 1;
                    } while (start < connectionHeader.length() && (i = connectionHeader.indexOf(',', start)) != -1);
                    h1Headers.remove(connectionHeader.substring(start));
                } else {
                    h1Headers.remove(connectionHeader);
                }
            } while (connectionItr.hasNext());
        }

        // remove other illegal headers
        h1Headers.remove(KEEP_ALIVE);
        h1Headers.remove(TRANSFER_ENCODING);
        h1Headers.remove(UPGRADE);

        // TE header is treated specially https://tools.ietf.org/html/rfc7540#section-8.1.2.2
        // (only value of "trailers" is allowed).
        Iterator<? extends CharSequence> teItr = h1Headers.values(TE);
        boolean addTrailers = false;
        while (teItr.hasNext()) {
            String teValue = teItr.next().toString();
            int i = teValue.indexOf(',');
            if (i != -1) {
                int start = 0;
                do {
                    if (teValue.substring(start, i).compareToIgnoreCase(TRAILERS.toString()) == 0) {
                        addTrailers = true;
                        break;
                    }
                } while (start < teValue.length() && (i = teValue.indexOf(',', start)) != -1);
                teItr.remove();
            } else if (teValue.compareToIgnoreCase(TRAILERS.toString()) != 0) {
                teItr.remove();
            }
        }
        if (addTrailers) { // add after iteration to avoid concurrent modification.
            h1Headers.add(TE, TRAILERS);
        }

        h1HeadersSplitCookieCrumbs(h1Headers);

        if (h1Headers instanceof NettyH2HeadersToHttpHeaders) {
            // Assume header field names are already lowercase if they reside in the Http2Headers. We may want to be
            // more strict in the future, but that would require iteration.
            return ((NettyH2HeadersToHttpHeaders) h1Headers).nettyHeaders();
        }

        DefaultHttp2Headers http2Headers = new DefaultHttp2Headers(false);
        for (Entry<CharSequence, CharSequence> h1Entry : h1Headers) {
            // header field names MUST be converted to lowercase prior to their encoding in HTTP/2
            // https://tools.ietf.org/html/rfc7540#section-8.1.2
            http2Headers.add(h1Entry.getKey().toString().toLowerCase(), h1Entry.getValue());
        }
        return http2Headers;
    }

    private static final class MaxConcurrencyConsumableEvent implements ConsumableEvent<Integer> {
        private static final AtomicIntegerFieldUpdater<MaxConcurrencyConsumableEvent> completedUpdater =
                AtomicIntegerFieldUpdater.newUpdater(MaxConcurrencyConsumableEvent.class, "completed");
        private volatile int completed;
        private final int maxConcurrentStreams;
        private final Channel channel;

        MaxConcurrencyConsumableEvent(final int maxConcurrentStreams, final Channel channel) {
            this.maxConcurrentStreams = maxConcurrentStreams;
            this.channel = channel;
        }

        @Override
        public Integer event() {
            return maxConcurrentStreams;
        }

        @Override
        public void eventConsumed() {
            if (completedUpdater.compareAndSet(this, 0, 1)) {
                channel.writeAndFlush(Http2SettingsAckFrame.INSTANCE);
            }
        }
    }

    private static final class NoopScheduledFuture implements ScheduledFuture<Object> {
        static final ScheduledFuture<?> INSTANCE = new NoopScheduledFuture();

        private NoopScheduledFuture() {
            // singleton
        }

        @Override
        public long getDelay(final TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(final Delayed o) {
            return 0;
        }

        @Override
        public boolean cancel(final boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public Object get() throws InterruptedException, ExecutionException {
            return null;
        }

        @Override
        public Object get(final long timeout, final TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            return null;
        }
    }
}
