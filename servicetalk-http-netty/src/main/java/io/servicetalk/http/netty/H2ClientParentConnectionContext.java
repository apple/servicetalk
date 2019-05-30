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
import io.servicetalk.http.api.HttpRequestMethod;
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
import io.servicetalk.transport.netty.internal.NettyPipelineSslUtils;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http2.DefaultHttp2GoAwayFrame;
import io.netty.handler.codec.http2.DefaultHttp2PingFrame;
import io.netty.handler.codec.http2.Http2GoAwayFrame;
import io.netty.handler.codec.http2.Http2MultiplexCodec;
import io.netty.handler.codec.http2.Http2PingFrame;
import io.netty.handler.codec.http2.Http2SettingsAckFrame;
import io.netty.handler.codec.http2.Http2SettingsFrame;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

import static io.netty.handler.codec.http2.Http2Error.NO_ERROR;
import static io.netty.util.ReferenceCountUtil.release;
import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.Processors.newCompletableProcessor;
import static io.servicetalk.concurrent.api.Processors.newSingleProcessor;
import static io.servicetalk.concurrent.api.Publisher.failed;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverTerminalFromSource;
import static io.servicetalk.concurrent.internal.ThrowableUtil.unknownStackTrace;
import static io.servicetalk.http.api.HttpEventKey.MAX_CONCURRENCY;
import static io.servicetalk.transport.netty.internal.ChannelSet.CHANNEL_CLOSEABLE_KEY;
import static io.servicetalk.transport.netty.internal.CloseHandler.UNSUPPORTED_PROTOCOL_CLOSE_HANDLER;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.fromNettyEventLoop;
import static io.servicetalk.transport.netty.internal.NettyPipelineSslUtils.extractSslSession;
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
    private static final ScheduledFuture<?> GRACEFUL_CLOSE_PING_PENDING = new NoopScheduledFuture();
    private static final ScheduledFuture<?> GRACEFUL_CLOSE_PING_ACK_RECV = new NoopScheduledFuture();
    private static final long GRACEFUL_CLOSE_PING_CONTENT = 34213531352L;
    private static final long GRACEFUL_CLOSE_PING_ACK_TIMEOUT_MS = 10000;
    private volatile FlushStrategy flushStrategy;
    private final HttpExecutionContext executionContext;
    private final SingleSource.Processor<Throwable, Throwable> transportError = newSingleProcessor();
    private final CompletableSource.Processor onClosing = newCompletableProcessor();
    private final FlushStrategy originalFlushStrategy;
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
        originalFlushStrategy = requireNonNull(flushStrategy);
        this.flushStrategy = originalFlushStrategy;
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
                    channel.attr(CHANNEL_CLOSEABLE_KEY).set(connection);
                    // We need the NettyToStChannelInboundHandler to be last in the pipeline. We accomplish that by
                    // calling the ChannelInitializer before we do addLast for the NettyToStChannelInboundHandler.
                    // This could mean if there are any synchronous events generated via ChannelInitializer handlers
                    // that NettyToStChannelInboundHandler will not see them. This is currently not an issue and would
                    // require some pipeline modifications if we wanted to insert NettyToStChannelInboundHandler first,
                    // but not allow any other handlers to be after it.
                    initializer.init(channel, connection);
                    pipeline = channel.pipeline();
                    parentChannelInitializer = new H2ParentClientConnection(connection, subscriber,
                            delayedCancellable,
                            NettyPipelineSslUtils.isSslEnabled(pipeline),
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
    public Cancellable updateFlushStrategy(final FlushStrategyProvider strategyProvider) {
        FlushStrategy old = flushStrategyUpdater.getAndUpdate(this, current ->
                strategyProvider.getNewStrategy(current, current == originalFlushStrategy));
        return () -> updateFlushStrategy((__, ___) -> old);
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
            gracefulClosePingAckTimeoutFuture = GRACEFUL_CLOSE_PING_PENDING;

            onClosing.onComplete();

            // The graceful close process is described in [1]. In general it involves sending 2 GOAWAY frames. The first
            // GOAWAY has last-stream-id=<maximum stream ID> to indicate no new streams can be created, wait for 2 RTT
            // time duration for inflight frames to land, and the second GOAWAY includes the maximum known stream ID.
            // To account for 2 RTTs we can send a PING and when the PING(ACK) comes back we can send the second GOAWAY.
            // https://tools.ietf.org/html/rfc7540#section-6.8
            DefaultHttp2GoAwayFrame goAwayFrame = new DefaultHttp2GoAwayFrame(NO_ERROR);
            goAwayFrame.setExtraStreamIds(Integer.MAX_VALUE);
            channel().write(goAwayFrame);
            channel().writeAndFlush(new DefaultHttp2PingFrame(GRACEFUL_CLOSE_PING_CONTENT)).addListener(future -> {
                        // If gracefulClosePingAckTimeoutFuture is not GRACEFUL_CLOSE_PING_PENDING that means we have
                        // already received the PING(ACK) and there is no need to apply the timeout.
                        if (gracefulClosePingAckTimeoutFuture == GRACEFUL_CLOSE_PING_PENDING) {
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
                    connection.sslSession = extractSslSession(ctx.pipeline(), (SslHandshakeCompletionEvent) evt,
                            this::tryFailSubscriber);
                    if (subscriber != null) {
                        assert waitForSslHandshake;
                        completeSubscriber();
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
                  connection.gracefulClosePingAckTimeoutFuture = GRACEFUL_CLOSE_PING_ACK_RECV;
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

                    // In h2 a stream is 1 to 1 with a request/response life cycle. This means there is no concept of
                    // pipelining on a stream so we can use the non-pipelined connection which is more light weight.
                    // https://tools.ietf.org/html/rfc7540#section-8.1
                    responseSingle = toSource(new NonPipelinedStreamingHttpConnection(nettyConnection,
                            executionContext(), reqRespFactory, headersFactory).request(strategy, request));
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
        public Cancellable updateFlushStrategy(final FlushStrategyProvider strategyProvider) {
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
        @Override
        public long getDelay(final TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(final Delayed o) {
            return o == this ? 0 : 1;
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
        public Object get() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object get(final long timeout, final TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int hashCode() {
            return 0;
        }

        @Override
        public boolean equals(Object o) {
            return o == this;
        }
    }
}
