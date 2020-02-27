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
import io.servicetalk.client.api.internal.IgnoreConsumedEvent;
import io.servicetalk.concurrent.Cancellable;
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
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpEventKey;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategy;
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
import io.servicetalk.transport.netty.internal.NettyConnection.TerminalPredicate;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;
import io.servicetalk.transport.netty.internal.NettyPipelineSslUtils;

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

import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

import static io.netty.handler.codec.http2.Http2CodecUtil.SMALLEST_MAX_CONCURRENT_STREAMS;
import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.Publisher.failed;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverTerminalFromSource;
import static io.servicetalk.http.api.HttpEventKey.MAX_CONCURRENCY;
import static io.servicetalk.http.netty.HeaderUtils.LAST_CHUNK_PREDICATE;
import static io.servicetalk.http.netty.HttpDebugUtils.showPipeline;
import static io.servicetalk.transport.netty.internal.ChannelSet.CHANNEL_CLOSEABLE_KEY;
import static io.servicetalk.transport.netty.internal.CloseHandler.PROTOCOL_OUTBOUND_CLOSE_HANDLER;
import static java.util.Objects.requireNonNull;

final class H2ClientParentConnectionContext extends H2ParentConnectionContext implements NettyConnectionContext {
    private H2ClientParentConnectionContext(Channel channel, BufferAllocator allocator,
                                            Executor executor, FlushStrategy flushStrategy,
                                            HttpExecutionStrategy executionStrategy) {
        super(channel, allocator, executor, flushStrategy, executionStrategy);
    }

    interface H2ClientParentConnection extends FilterableStreamingHttpConnection, NettyConnectionContext {
    }

    static Single<H2ClientParentConnection> initChannel(Channel channel, BufferAllocator allocator,
                                                        Executor executor, H2ProtocolConfig config,
                                                        StreamingHttpRequestResponseFactory reqRespFactory,
                                                        FlushStrategy parentFlushStrategy,
                                                        HttpExecutionStrategy executionStrategy,
                                                        ChannelInitializer initializer) {
        return showPipeline(new SubscribableSingle<H2ClientParentConnection>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super H2ClientParentConnection> subscriber) {
                final DefaultH2ClientParentConnection parentChannelInitializer;
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
                    initializer.init(channel);
                    pipeline = channel.pipeline();
                    parentChannelInitializer = new DefaultH2ClientParentConnection(connection, subscriber,
                            delayedCancellable,
                            NettyPipelineSslUtils.isSslEnabled(pipeline), config.headersFactory(), reqRespFactory);
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
        }, "HTTP/2.0", channel);
    }

    private static final class DefaultH2ClientParentConnection extends AbstractH2ParentConnection implements
                                                                                             H2ClientParentConnection {

        private static final IgnoreConsumedEvent<Integer> DEFAULT_H2_MAX_CONCURRENCY_EVENT =
                new IgnoreConsumedEvent<>(SMALLEST_MAX_CONCURRENT_STREAMS);

        private final Http2StreamChannelBootstrap bs;
        private final HttpHeadersFactory headersFactory;
        private final StreamingHttpRequestResponseFactory reqRespFactory;
        private final SpScPublisherProcessor<ConsumableEvent<Integer>> maxConcurrencyPublisher;
        @Nullable
        private Subscriber<? super H2ClientParentConnection> subscriber;

        DefaultH2ClientParentConnection(H2ClientParentConnectionContext connection,
                                        Subscriber<? super H2ClientParentConnection> subscriber,
                                        DelayedCancellable delayedCancellable,
                                        boolean waitForSslHandshake,
                                        HttpHeadersFactory headersFactory,
                                        StreamingHttpRequestResponseFactory reqRespFactory) {
            super(connection, delayedCancellable, waitForSslHandshake);
            this.subscriber = requireNonNull(subscriber);
            this.headersFactory = requireNonNull(headersFactory);
            this.reqRespFactory = requireNonNull(reqRespFactory);
            maxConcurrencyPublisher = new SpScPublisherProcessor<>(16);
            // Set maxConcurrency to the initial value recommended by the HTTP/2 spec
            maxConcurrencyPublisher.sendOnNext(DEFAULT_H2_MAX_CONCURRENCY_EVENT);
            bs = new Http2StreamChannelBootstrap(connection.channel());
        }

        @Override
        void tryCompleteSubscriber() {
            if (subscriber != null) {
                Subscriber<? super H2ClientParentConnection> subscriberCopy = subscriber;
                subscriber = null;
                subscriberCopy.onSuccess(this);
            }
        }

        @Override
        void tryFailSubscriber(Throwable cause) {
            if (subscriber != null) {
                parentContext.nettyChannel().close();
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

            maxConcurrencyPublisher.sendOnNext(new MaxConcurrencyConsumableEvent(
                    maxConcurrentStreams.intValue(), ctx.channel()));
            return false;
        }

        @Override
        public ConnectionContext connectionContext() {
            return parentContext;
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
                        final EventExecutor e = parentContext.nettyChannel().eventLoop();
                        promise = e.newPromise();
                        bs.open(promise);
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
                    parentContext.trackActiveStream(streamChannel);
                    streamChannel.pipeline().addLast(new H2ToStH1ClientDuplexHandler(waitForSslHandshake,
                            parentContext.executionContext().bufferAllocator(), headersFactory,
                            PROTOCOL_OUTBOUND_CLOSE_HANDLER));
                    DefaultNettyConnection<Object, Object> nettyConnection =
                            DefaultNettyConnection.initChildChannel(streamChannel,
                                    parentContext.executionContext().bufferAllocator(),
                                    parentContext.executionContext().executor(),
                                    new TerminalPredicate<>(LAST_CHUNK_PREDICATE),
                                    // Http2StreamChannel is not of type SocketChannel. Also Netty will manage the half
                                    // closure based upon stream state.
                                    PROTOCOL_OUTBOUND_CLOSE_HANDLER,
                                    parentContext.flushStrategyHolder.currentStrategy(),
                                    parentContext.executionContext().executionStrategy(),
                                    parentContext.sslSession());

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
            return parentContext.localAddress();
        }

        @Override
        public SocketAddress remoteAddress() {
            return parentContext.remoteAddress();
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

        @Override
        public StreamingHttpResponseFactory httpResponseFactory() {
            return reqRespFactory;
        }

        @Override
        public Completable onClose() {
            return parentContext.onClose();
        }

        @Override
        public Completable closeAsync() {
            return parentContext.closeAsync();
        }

        @Override
        public Completable closeAsyncGracefully() {
            return parentContext.closeAsyncGracefully();
        }

        @Override
        public Channel nettyChannel() {
            return parentContext.nettyChannel();
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

        @Override
        public Completable onClosing() {
            return parentContext.onClosing();
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
}
