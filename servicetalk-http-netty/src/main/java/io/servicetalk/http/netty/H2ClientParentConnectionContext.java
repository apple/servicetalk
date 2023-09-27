/*
 * Copyright © 2019-2022 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.ConsumableEvent;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.PublisherSource.Processor;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.SingleSource.Subscriber;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.SubscribableSingle;
import io.servicetalk.concurrent.internal.DelayedCancellable;
import io.servicetalk.concurrent.internal.SequentialCancellable;
import io.servicetalk.concurrent.internal.ThrowableUtils;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpConnectionContext;
import io.servicetalk.http.api.HttpEventKey;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.netty.LoadBalancedStreamingHttpClient.OnStreamClosedRunnable;
import io.servicetalk.http.netty.ReservableRequestConcurrencyControllers.IgnoreConsumedEvent;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.ConnectionObserver.MultiplexedObserver;
import io.servicetalk.transport.api.ConnectionObserver.StreamObserver;
import io.servicetalk.transport.api.IoThreadFactory;
import io.servicetalk.transport.api.SslConfig;
import io.servicetalk.transport.netty.internal.ChannelInitializer;
import io.servicetalk.transport.netty.internal.CloseHandler;
import io.servicetalk.transport.netty.internal.DefaultNettyConnection;
import io.servicetalk.transport.netty.internal.FlushStrategy;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;
import io.servicetalk.transport.netty.internal.NoopTransportObserver.NoopMultiplexedObserver;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http2.Http2SettingsAckFrame;
import io.netty.handler.codec.http2.Http2SettingsFrame;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.net.SocketOption;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

import static io.netty.handler.codec.http2.Http2CodecUtil.SMALLEST_MAX_CONCURRENT_STREAMS;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Processors.newPublisherProcessorDropHeadOnOverflow;
import static io.servicetalk.concurrent.api.Publisher.failed;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverErrorFromSource;
import static io.servicetalk.concurrent.internal.SubscriberUtils.handleExceptionFromOnSubscribe;
import static io.servicetalk.http.api.HttpEventKey.MAX_CONCURRENCY;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_2_0;
import static io.servicetalk.http.netty.AbstractStreamingHttpConnection.MAX_CONCURRENCY_NO_OFFLOADING;
import static io.servicetalk.http.netty.AbstractStreamingHttpConnection.ZERO_MAX_CONCURRENCY_EVENT;
import static io.servicetalk.http.netty.HeaderUtils.OBJ_EXPECT_CONTINUE;
import static io.servicetalk.http.netty.HttpDebugUtils.showPipeline;
import static io.servicetalk.transport.netty.internal.ChannelCloseUtils.close;
import static io.servicetalk.transport.netty.internal.ChannelSet.CHANNEL_CLOSEABLE_KEY;
import static io.servicetalk.transport.netty.internal.CloseHandler.forNonPipelined;
import static io.servicetalk.transport.netty.internal.NettyPipelineSslUtils.extractSslSession;
import static io.servicetalk.utils.internal.ThrowableUtils.addSuppressed;
import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;

final class H2ClientParentConnectionContext extends H2ParentConnectionContext {

    static final ConsumableEvent<Integer> DEFAULT_H2_MAX_CONCURRENCY_EVENT =
            new IgnoreConsumedEvent<>(SMALLEST_MAX_CONCURRENT_STREAMS);

    private H2ClientParentConnectionContext(Channel channel, HttpExecutionContext executionContext,
                                            FlushStrategy flushStrategy, long idleTimeoutMs,
                                            @Nullable final SslConfig sslConfig, @Nullable final SSLSession sslSession,
                                            final KeepAliveManager keepAliveManager) {
        super(channel, executionContext, flushStrategy, idleTimeoutMs, sslConfig, sslSession, keepAliveManager);
    }

    interface H2ClientParentConnection extends FilterableStreamingHttpConnection, NettyConnectionContext {
    }

    static Single<H2ClientParentConnection> initChannel(Channel channel, HttpExecutionContext executionContext,
                                                        H2ProtocolConfig config,
                                                        StreamingHttpRequestResponseFactory reqRespFactory,
                                                        FlushStrategy parentFlushStrategy,
                                                        long idleTimeoutMs,
                                                        @Nullable SslConfig sslConfig,
                                                        ChannelInitializer initializer,
                                                        ConnectionObserver observer,
                                                        boolean allowDropTrailersReadFromTransport) {
        return showPipeline(new SubscribableSingle<H2ClientParentConnection>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super H2ClientParentConnection> subscriber) {
                final DefaultH2ClientParentConnection parentChannelInitializer;
                final DelayedCancellable delayedCancellable;
                final ChannelPipeline pipeline;
                try {
                    // We need the NettyToStChannelInboundHandler to be last in the pipeline. We accomplish that by
                    // calling the ChannelInitializer before we do addLast for the NettyToStChannelInboundHandler.
                    // This could mean if there are any synchronous events generated via ChannelInitializer handlers
                    // that NettyToStChannelInboundHandler will not see them. This is currently not an issue and would
                    // require some pipeline modifications if we wanted to insert NettyToStChannelInboundHandler first,
                    // but not allow any other handlers to be after it.
                    initializer.init(channel);

                    pipeline = channel.pipeline();
                    @Nullable
                    final SSLSession sslSession = extractSslSession(sslConfig, pipeline);
                    H2ClientParentConnectionContext connection = new H2ClientParentConnectionContext(channel,
                            executionContext, parentFlushStrategy, idleTimeoutMs, sslConfig, sslSession,
                            new KeepAliveManager(channel, config.keepAlivePolicy()));
                    channel.attr(CHANNEL_CLOSEABLE_KEY).set(connection);
                    delayedCancellable = new DelayedCancellable();
                    parentChannelInitializer = new DefaultH2ClientParentConnection(connection, subscriber,
                            delayedCancellable, shouldWaitForSslHandshake(sslSession, sslConfig),
                            allowDropTrailersReadFromTransport, config.headersFactory(), reqRespFactory, observer);
                } catch (Throwable cause) {
                    close(channel, cause);
                    deliverErrorFromSource(subscriber, cause);
                    return;
                }
                subscriber.onSubscribe(delayedCancellable);
                // We have to add to the pipeline AFTER we call onSubscribe, because adding to the pipeline may invoke
                // callbacks that interact with the subscriber.
                pipeline.addLast(parentChannelInitializer);
            }
        }, HTTP_2_0, channel);
    }

    private static final class DefaultH2ClientParentConnection extends AbstractH2ParentConnection implements
                                                                                             H2ClientParentConnection {

        private static final Logger LOGGER = LoggerFactory.getLogger(DefaultH2ClientParentConnection.class);

        private final Http2StreamChannelBootstrap bs;
        private final HttpHeadersFactory headersFactory;
        private final StreamingHttpRequestResponseFactory reqRespFactory;
        private final Processor<ConsumableEvent<Integer>, ConsumableEvent<Integer>> maxConcurrencyProcessor =
                newPublisherProcessorDropHeadOnOverflow(16);
        private final Publisher<ConsumableEvent<Integer>> maxConcurrencyPublisher;
        private final boolean allowDropTrailersReadFromTransport;
        @Nullable
        private Subscriber<? super H2ClientParentConnection> subscriber;
        private MultiplexedObserver multiplexedObserver = NoopMultiplexedObserver.INSTANCE;

        DefaultH2ClientParentConnection(H2ClientParentConnectionContext connection,
                                        Subscriber<? super H2ClientParentConnection> subscriber,
                                        DelayedCancellable delayedCancellable,
                                        boolean waitForSslHandshake,
                                        boolean allowDropTrailersReadFromTransport,
                                        HttpHeadersFactory headersFactory,
                                        StreamingHttpRequestResponseFactory reqRespFactory,
                                        ConnectionObserver observer) {
            super(connection, delayedCancellable, waitForSslHandshake, observer);
            this.subscriber = requireNonNull(subscriber);
            this.headersFactory = requireNonNull(headersFactory);
            this.reqRespFactory = requireNonNull(reqRespFactory);
            this.allowDropTrailersReadFromTransport = allowDropTrailersReadFromTransport;
            // Set maxConcurrency to the initial value recommended by the HTTP/2 spec
            maxConcurrencyProcessor.onNext(DEFAULT_H2_MAX_CONCURRENCY_EVENT);
            bs = new Http2StreamChannelBootstrap(connection.channel());
            maxConcurrencyPublisher = fromSource(maxConcurrencyProcessor)
                    .multicast(1); // Allows multiple Subscribers to consume the event stream.
        }

        @Override
        boolean hasSubscriber() {
            return subscriber != null;
        }

        @Override
        void tryCompleteSubscriber() {
            if (subscriber != null) {
                Subscriber<? super H2ClientParentConnection> subscriberCopy = subscriber;
                subscriber = null;
                multiplexedObserver = observer.multiplexedConnectionEstablished(this);
                subscriberCopy.onSuccess(this);
            }
        }

        @Override
        void tryFailSubscriber(Throwable cause) {
            if (subscriber != null) {
                close(parentContext.nettyChannel(), cause);
                Subscriber<? super H2ClientParentConnection> subscriberCopy = subscriber;
                subscriber = null;
                subscriberCopy.onError(cause);
            }
        }

        @Override
        boolean ackSettings(final ChannelHandlerContext ctx, final Http2SettingsFrame settingsFrame) {
            final Long maxConcurrentStreams = settingsFrame.settings().maxConcurrentStreams();
            if (maxConcurrentStreams == null) {
                return true;
            }

            maxConcurrencyProcessor.onNext(new MaxConcurrencyConsumableEvent(
                    (int) min(maxConcurrentStreams, Integer.MAX_VALUE), ctx.channel()));
            return false;
        }

        @Override
        public HttpConnectionContext connectionContext() {
            return parentContext;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> Publisher<? extends T> transportEventStream(final HttpEventKey<T> eventKey) {
            if (eventKey == MAX_CONCURRENCY_NO_OFFLOADING) {
                return (Publisher<? extends T>) maxConcurrencyPublisher;
            } else if (eventKey == MAX_CONCURRENCY) {
                return (Publisher<? extends T>) maxConcurrencyPublisher
                        .publishOn(executionContext().executionStrategy().isEventOffloaded() ?
                                        executionContext().executor() : immediate(),
                                IoThreadFactory.IoThread::currentThreadIsIoThread);
            } else {
                return failed(new IllegalArgumentException("Unknown key: " + eventKey));
            }
        }

        @Override
        public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
            return new SubscribableSingle<StreamingHttpResponse>() {
                @Override
                protected void handleSubscribe(final Subscriber<? super StreamingHttpResponse> subscriber) {
                    final StreamObserver observer = multiplexedObserver.onNewStream();
                    final Promise<Http2StreamChannel> promise;
                    final SequentialCancellable sequentialCancellable;
                    OnStreamClosedRunnable ownedRunnable = null;
                    try {
                        final EventExecutor e = parentContext.nettyChannel().eventLoop();
                        promise = e.newPromise();
                        // Take ownership of the Runnable associated with the request (if any) before we start opening
                        // a new stream. If we move this action to childChannelActive, the
                        // LoadBalancedStreamingHttpClient may prematurely mark the request as finished before netty
                        // marks the stream as inactive. This code is responsible for running this Runnable in case of
                        // any errors or stream closure.
                        // See LoadBalancedStreamingHttpClient and AbstractStreamingHttpConnection.
                        final OnStreamClosedRunnable runnable = request.context().get(OnStreamClosedRunnable.KEY);
                        if (runnable != null) {
                            if (runnable.own()) {
                                ownedRunnable = runnable;
                            } else {
                                // The request is already cancelled and the cancel signal will eventually propagate
                                // here. No need to try to open a stream, we can just fail fast:
                                final Throwable cause = StacklessCancellationException.newInstance(
                                        "The request was cancelled", this.getClass(), "handleSubscribe");
                                observer.streamClosed(cause);
                                deliverErrorFromSource(subscriber, cause);
                                return;
                            }
                        }
                        // OnStreamClosedRunnable is null in the following cases:
                        // 1. This is a reserved connection, meaning that the concurrency control is not applied. There
                        //    is nothing to do in this case.
                        // 2. User could wipe/modify the request context after LoadBalancedStreamingHttpClient in a
                        //    processing chain. The LoadBalancedStreamingHttpClient always double-checks ownership of
                        //    the runnable to mitigate this use-case. Users risk to see "Maximum concurrent streams
                        //    violated for this endpoint" from Netty bcz the request will be marked as "finished" before
                        //    Netty actually closes the stream and may race with new requests on the same connection.
                        //    This is an acceptable thread-off taking into account users intentionally modified the
                        //    request.context().
                        bs.open(promise);
                        sequentialCancellable = new SequentialCancellable(() -> promise.cancel(true));
                    } catch (Throwable cause) {
                        cleanupWhenError(cause, observer, ownedRunnable);
                        deliverErrorFromSource(subscriber, cause);
                        return;
                    }

                    try {
                        subscriber.onSubscribe(sequentialCancellable);
                    } catch (Throwable cause) {
                        cleanupErrorBeforeOpen(cause, promise, observer, ownedRunnable);
                        handleExceptionFromOnSubscribe(subscriber, cause);
                        return;
                    }

                    final Runnable onCloseRunnable = ownedRunnable;
                    if (promise.isDone()) {
                        childChannelActive(promise, subscriber, sequentialCancellable, request, observer,
                                allowDropTrailersReadFromTransport, onCloseRunnable);
                    } else {
                        promise.addListener((FutureListener<Http2StreamChannel>) future -> childChannelActive(
                                future, subscriber, sequentialCancellable, request, observer,
                                allowDropTrailersReadFromTransport, onCloseRunnable));
                    }
                }
            };
        }

        private static void cleanupErrorBeforeOpen(final Throwable cause,
                                                   final Promise<Http2StreamChannel> promise,
                                                   final StreamObserver observer,
                                                   @Nullable final Runnable ownedRunnable) {
            promise.addListener((FutureListener<Http2StreamChannel>) future -> {
                if (future.cause() == null) {   // if succeeded, close the stream then clean up
                    future.getNow().close().addListener(__ -> cleanupWhenError(cause, observer, ownedRunnable));
                } else {
                    cleanupWhenError(cause, observer, ownedRunnable);
                }
            });
        }

        private static void cleanupWhenError(final Throwable cause, final StreamObserver observer,
                                             @Nullable final Runnable ownedRunnable) {
            observer.streamClosed(cause);
            if (ownedRunnable != null) {
                ownedRunnable.run();
            }
        }

        private void childChannelActive(Future<Http2StreamChannel> future,
                                        Subscriber<? super StreamingHttpResponse> subscriber,
                                        SequentialCancellable sequentialCancellable,
                                        StreamingHttpRequest request,
                                        StreamObserver streamObserver,
                                        boolean allowDropTrailersReadFromTransport,
                                        @Nullable Runnable onCloseRunnable) {
            final SingleSource<StreamingHttpResponse> responseSingle;
            Throwable futureCause = future.cause(); // assume this doesn't throw
            if (futureCause == null) {
                Http2StreamChannel streamChannel = null;
                try {
                    streamChannel = future.getNow();
                    if (onCloseRunnable != null) {
                        streamChannel.closeFuture().addListener(f -> onCloseRunnable.run());
                    }
                    parentContext.trackActiveStream(streamChannel);

                    final CloseHandler closeHandler = forNonPipelined(true, streamChannel.config());
                    streamChannel.pipeline().addLast(new H2ToStH1ClientDuplexHandler(parentContext.sslConfig() != null,
                            parentContext.executionContext().bufferAllocator(), headersFactory,
                            closeHandler, streamObserver));
                    DefaultNettyConnection<Object, Object> nettyConnection =
                            DefaultNettyConnection.initChildChannel(streamChannel,
                                    parentContext,
                                    closeHandler,
                                    parentContext.defaultFlushStrategy(),
                                    parentContext.idleTimeoutMs,
                                    HTTP_2_0,
                                    parentContext.nettyChannel().config(),
                                    streamObserver,
                                    true, OBJ_EXPECT_CONTINUE,
                                    NettyHttp2ExceptionUtils::wrapIfNecessary);

                    // In h2 a stream is 1 to 1 with a request/response life cycle. This means there is no concept of
                    // pipelining on a stream so we can use the non-pipelined connection which is more light weight.
                    // https://tools.ietf.org/html/rfc7540#section-8.1
                    responseSingle = toSource(new NonPipelinedStreamingHttpConnection(nettyConnection,
                            reqRespFactory, headersFactory, allowDropTrailersReadFromTransport).request(request));
                } catch (Throwable cause) {
                    if (streamChannel != null) {
                        try {
                            close(streamChannel, cause);
                        } catch (Throwable unexpected) {
                            addSuppressed(unexpected, cause);
                            LOGGER.warn("Unexpected exception while handling the original cause", unexpected);
                        }
                    } else {
                        cleanupWhenError(cause, streamObserver, onCloseRunnable);
                    }
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
                cleanupWhenError(futureCause, streamObserver, onCloseRunnable);
                subscriber.onError(futureCause);
            }
        }

        @Override
        public SocketAddress localAddress() {
            return parentContext.localAddress();
        }

        @Override
        public SocketAddress remoteAddress() {
            return parentContext.remoteAddress();
        }

        @Nullable
        @Override
        public SslConfig sslConfig() {
            return parentContext.sslConfig();
        }

        @Nullable
        @Override
        public SSLSession sslSession() {
            return parentContext.sslSession();
        }

        @Override
        public HttpExecutionContext executionContext() {
            return parentContext.executionContext();
        }

        @Nullable
        @Override
        public <T> T socketOption(final SocketOption<T> option) {
            return parentContext.socketOption(option);
        }

        @Override
        public HttpProtocolVersion protocol() {
            return parentContext.protocol();
        }

        @Nullable
        @Override
        public ConnectionContext parent() {
            return parentContext.parent();
        }

        @Override
        public StreamingHttpResponseFactory httpResponseFactory() {
            return reqRespFactory;
        }

        @Override
        public Completable onClose() {
            return parentContext.onClose();
        }

        @Override
        public Completable onClosing() {
            return parentContext.onClosing();
        }

        @Override
        public Completable closeAsync() {
            return Completable.defer(() -> {
                maxConcurrencyProcessor.onNext(ZERO_MAX_CONCURRENCY_EVENT);
                maxConcurrencyProcessor.onComplete();
                return parentContext.closeAsync().shareContextOnSubscribe();
            });
        }

        @Override
        public Completable closeAsyncGracefully() {
            return Completable.defer(() -> {
                maxConcurrencyProcessor.onNext(ZERO_MAX_CONCURRENCY_EVENT);
                maxConcurrencyProcessor.onComplete();
                return parentContext.closeAsyncGracefully().shareContextOnSubscribe();
            });
        }

        @Override
        public Channel nettyChannel() {
            return parentContext.nettyChannel();
        }

        @Override
        public String toString() {
            return parentContext.toString();
        }

        @Override
        public StreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
            return reqRespFactory.newRequest(method, requestTarget);
        }

        @Override
        public Cancellable updateFlushStrategy(final FlushStrategyProvider strategyProvider) {
            return parentContext.updateFlushStrategy(strategyProvider);
        }

        @Override
        public FlushStrategy defaultFlushStrategy() {
            return parentContext.defaultFlushStrategy();
        }

        @Override
        public Single<Throwable> transportError() {
            return parentContext.transportError();
        }
    }

    private static final class MaxConcurrencyConsumableEvent implements ConsumableEvent<Integer> {
        private static final AtomicIntegerFieldUpdater<MaxConcurrencyConsumableEvent> completedUpdater =
                AtomicIntegerFieldUpdater.newUpdater(MaxConcurrencyConsumableEvent.class, "completed");
        private volatile int completed;
        private final int maxConcurrentStreams;
        private final Channel channel;

        MaxConcurrencyConsumableEvent(final int maxConcurrentStreams, final Channel channel) {
            assert maxConcurrentStreams >= 0;
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

        @Override
        public String toString() {
            return getClass().getSimpleName() +
                    "{maxConcurrentStreams=" + maxConcurrentStreams +
                    ", completed=" + completed +
                    '}';
        }
    }

    static final class StacklessCancellationException extends CancellationException {
        private static final long serialVersionUID = 3235852873427231209L;

        private StacklessCancellationException(String message) {
            super(message);
        }

        // Override fillInStackTrace() so we not populate the backtrace via a native call and so leak the
        // Classloader.
        @Override
        public Throwable fillInStackTrace() {
            return this;
        }

        static StacklessCancellationException newInstance(String message, Class<?> clazz, String method) {
            return ThrowableUtils.unknownStackTrace(new StacklessCancellationException(message), clazz, method);
        }
    }
}
