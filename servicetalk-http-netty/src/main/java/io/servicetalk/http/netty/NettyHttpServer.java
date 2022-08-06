/*
 * Copyright © 2018-2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.CompletableSource.Processor;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Processors;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.SpliceFlatStreamToSingleResult;
import io.servicetalk.concurrent.api.internal.SubscribableCompletable;
import io.servicetalk.concurrent.internal.DuplicateSubscribeException;
import io.servicetalk.concurrent.internal.RejectedSubscribeError;
import io.servicetalk.concurrent.internal.TerminalNotification;
import io.servicetalk.http.api.DefaultHttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpServerContext;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.tcp.netty.internal.ReadOnlyTcpServerConfig;
import io.servicetalk.tcp.netty.internal.TcpServerBinder;
import io.servicetalk.tcp.netty.internal.TcpServerChannelInitializer;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.SslConfig;
import io.servicetalk.transport.netty.internal.ChannelInitializer;
import io.servicetalk.transport.netty.internal.CloseHandler;
import io.servicetalk.transport.netty.internal.CloseHandler.CloseEventObservedException;
import io.servicetalk.transport.netty.internal.CopyByteBufHandlerChannelInitializer;
import io.servicetalk.transport.netty.internal.DefaultNettyConnection;
import io.servicetalk.transport.netty.internal.FlushStrategy;
import io.servicetalk.transport.netty.internal.InfluencerConnectionAcceptor;
import io.servicetalk.transport.netty.internal.NettyConnection;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;
import io.servicetalk.transport.netty.internal.SplittingFlushStrategy;
import io.servicetalk.transport.netty.internal.SplittingFlushStrategy.FlushBoundaryProvider;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.DecoderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

import static io.servicetalk.buffer.netty.BufferUtils.getByteBufAllocator;
import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.AsyncCloseables.toListenableAsyncCloseable;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.defer;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.http.api.HeaderUtils.isTransferEncodingChunked;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_2_0;
import static io.servicetalk.http.api.HttpRequestMethod.HEAD;
import static io.servicetalk.http.api.StreamingHttpRequests.newTransportRequest;
import static io.servicetalk.http.netty.AbstractStreamingHttpConnection.isSafeToAggregateOrEmpty;
import static io.servicetalk.http.netty.HeaderUtils.REQ_EXPECT_CONTINUE;
import static io.servicetalk.http.netty.HeaderUtils.addResponseTransferEncodingIfNecessary;
import static io.servicetalk.http.netty.HeaderUtils.canAddResponseContentLength;
import static io.servicetalk.http.netty.HeaderUtils.emptyMessageBody;
import static io.servicetalk.http.netty.HeaderUtils.flatEmptyMessage;
import static io.servicetalk.http.netty.HeaderUtils.setResponseContentLength;
import static io.servicetalk.http.netty.HeaderUtils.shouldAppendTrailers;
import static io.servicetalk.http.netty.HttpDebugUtils.showPipeline;
import static io.servicetalk.http.netty.HttpObjectDecoder.getContentLength;
import static io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent.CHANNEL_CLOSED_INBOUND;
import static io.servicetalk.transport.netty.internal.CloseHandler.forPipelinedRequestResponse;
import static io.servicetalk.transport.netty.internal.FlushStrategies.flushOnEach;
import static io.servicetalk.transport.netty.internal.FlushStrategies.flushOnEnd;
import static io.servicetalk.transport.netty.internal.SplittingFlushStrategy.FlushBoundaryProvider.FlushBoundary.End;
import static io.servicetalk.transport.netty.internal.SplittingFlushStrategy.FlushBoundaryProvider.FlushBoundary.InProgress;
import static io.servicetalk.transport.netty.internal.SplittingFlushStrategy.FlushBoundaryProvider.FlushBoundary.Start;

final class NettyHttpServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(NettyHttpServer.class);

    private NettyHttpServer() {
        // No instances
    }

    static Single<HttpServerContext> bind(final HttpExecutionContext executionContext,
                                          final ReadOnlyHttpServerConfig config,
                                          final SocketAddress address,
                                          @Nullable final InfluencerConnectionAcceptor connectionAcceptor,
                                          final StreamingHttpService service,
                                          final boolean drainRequestPayloadBody) {
        if (config.h1Config() == null) {
            return failed(newH1ConfigException());
        }
        // This state is read only, so safe to keep a copy across Subscribers
        final ReadOnlyTcpServerConfig tcpServerConfig = config.tcpConfig();
        // We disable auto read so that we can handle stuff in the ConnectionFilter before we accept any content.
        return TcpServerBinder.bind(address, tcpServerConfig, false, executionContext, connectionAcceptor,
                (channel, connectionObserver) -> initChannel(channel, executionContext, config,
                        new TcpServerChannelInitializer(tcpServerConfig, connectionObserver), service,
                        drainRequestPayloadBody, connectionObserver),
                serverConnection -> serverConnection.process(true))
                .map(delegate -> {
                    LOGGER.debug("Started HTTP/1.1 server for address {}.", delegate.listenAddress());
                    // The ServerContext returned by TcpServerBinder takes care of closing the connectionAcceptor.
                    return new NettyHttpServerContext(delegate, service, executionContext);
                });
    }

    private static Throwable newH1ConfigException() {
        return new IllegalStateException(
                "HTTP/1.x channel initialization failure due to missing HTTP/1.x configuration");
    }

    static Single<NettyHttpServerConnection> initChannel(final Channel channel,
                                                         final HttpExecutionContext httpExecutionContext,
                                                         final ReadOnlyHttpServerConfig config,
                                                         final ChannelInitializer initializer,
                                                         final StreamingHttpService service,
                                                         final boolean drainRequestPayloadBody,
                                                         final ConnectionObserver observer) {
        return initChannel(channel, httpExecutionContext, config, initializer, service, drainRequestPayloadBody,
                observer, forPipelinedRequestResponse(false, channel.config()));
    }

    private static Single<NettyHttpServerConnection> initChannel(final Channel channel,
                                                                 final HttpExecutionContext httpExecutionContext,
                                                                 final ReadOnlyHttpServerConfig config,
                                                                 final ChannelInitializer initializer,
                                                                 final StreamingHttpService service,
                                                                 final boolean drainRequestPayloadBody,
                                                                 final ConnectionObserver observer,
                                                                 final CloseHandler closeHandler) {
        final H1ProtocolConfig h1Config = config.h1Config();
        if (h1Config == null) {
            return failed(newH1ConfigException());
        }
        final ReadOnlyTcpServerConfig tcpConfig = config.tcpConfig();
        return showPipeline(DefaultNettyConnection.initChannel(channel,
                httpExecutionContext.bufferAllocator(), httpExecutionContext.executor(),
                httpExecutionContext.ioExecutor(), closeHandler, tcpConfig.flushStrategy(), tcpConfig.idleTimeoutMs(),
                tcpConfig.sslConfig(),
                initializer.andThen(getChannelInitializer(getByteBufAllocator(httpExecutionContext.bufferAllocator()),
                        h1Config, closeHandler)), httpExecutionContext.executionStrategy(), HTTP_1_1, observer, false,
                        __ -> false)
                .map(conn -> new NettyHttpServerConnection(conn, service,
                        HTTP_1_1, h1Config.headersFactory(), drainRequestPayloadBody,
                        config.allowDropTrailersReadFromTransport())), HTTP_1_1, channel);
    }

    private static ChannelInitializer getChannelInitializer(final ByteBufAllocator alloc, final H1ProtocolConfig config,
                                                            final CloseHandler closeHandler) {
        // H1 slices passed memory chunks into headers and payload body without copying and will emit them to the
        // user-code. Therefore, ByteBufs must be copied to unpooled memory before HttpObjectDecoder.
        return new CopyByteBufHandlerChannelInitializer(alloc).andThen(channel -> {
            Queue<HttpRequestMethod> methodQueue = new ArrayDeque<>(2);
            final ChannelPipeline pipeline = channel.pipeline();
            final HttpRequestDecoder decoder = new HttpRequestDecoder(methodQueue, alloc, config.headersFactory(),
                    config.maxStartLineLength(), config.maxHeaderFieldLength(),
                    config.specExceptions().allowPrematureClosureBeforePayloadBody(),
                    config.specExceptions().allowLFWithoutCR(), closeHandler);
            pipeline.addLast(decoder);
            pipeline.addLast(new HttpResponseEncoder(methodQueue, config.headersEncodedSizeEstimate(),
                    config.trailersEncodedSizeEstimate(), closeHandler, decoder));
        });
    }

    static final class NettyHttpServerContext implements HttpServerContext {
        private final ServerContext delegate;
        private final ListenableAsyncCloseable asyncCloseable;
        private final HttpExecutionContext executionContext;

        NettyHttpServerContext(final ServerContext delegate, final StreamingHttpService service,
                               final HttpExecutionContext executionContext) {
            this.delegate = delegate;
            asyncCloseable = toListenableAsyncCloseable(newCompositeCloseable().appendAll(service, delegate));
            this.executionContext = executionContext;
        }

        @Override
        public SocketAddress listenAddress() {
            return delegate.listenAddress();
        }

        @Override
        public void acceptConnections(final boolean accept) {
            delegate.acceptConnections(accept);
        }

        @Override
        public HttpExecutionContext executionContext() {
            return executionContext;
        }

        @Override
        public Completable closeAsync() {
            return asyncCloseable.closeAsync()
                    .whenFinally(() -> LOGGER.debug("Stopped HTTP server for address {}.", listenAddress()));
        }

        @Override
        public Completable closeAsyncGracefully() {
            return asyncCloseable.closeAsyncGracefully();
        }

        @Override
        public Completable onClose() {
            return asyncCloseable.onClose();
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }

    static final class NettyHttpServerConnection extends HttpServiceContext implements NettyConnectionContext {
        private final StreamingHttpService service;
        private final NettyConnection<Object, Object> connection;
        private final HttpHeadersFactory headersFactory;
        private final HttpExecutionContext executionContext;
        private final SplittingFlushStrategy flushStrategy;
        private final boolean drainRequestPayloadBody;
        private final boolean requireTrailerHeader;

        NettyHttpServerConnection(final NettyConnection<Object, Object> connection,
                                  final StreamingHttpService service,
                                  final HttpProtocolVersion version,
                                  final HttpHeadersFactory headersFactory,
                                  final boolean drainRequestPayloadBody,
                                  final boolean requireTrailerHeader) {
            super(headersFactory,
                    new DefaultHttpResponseFactory(headersFactory, connection.executionContext().bufferAllocator(),
                            version),
                    new DefaultStreamingHttpResponseFactory(headersFactory,
                            connection.executionContext().bufferAllocator(), version),
                    new DefaultBlockingStreamingHttpResponseFactory(headersFactory,
                            connection.executionContext().bufferAllocator(), version));
            this.connection = connection;
            this.headersFactory = headersFactory;
            executionContext = new DefaultHttpExecutionContext(connection.executionContext().bufferAllocator(),
                    connection.executionContext().ioExecutor(), connection.executionContext().executor(),
                    HttpExecutionStrategies.offloadNone());
            this.service = service;
            flushStrategy = new SplittingFlushStrategy(connection.defaultFlushStrategy(),
                    // h2 may return a single HttpResponseMetaData for an empty response in some scenarios,
                    // otherwise a trailers object will be included to indicate the end because content-length isn't
                    // mutually exclusive from trailers in h2.
                    protocol().major() > 1 ?
                            itemWritten -> (itemWritten instanceof HttpResponseMetaData &&
                                        emptyMessageBody((HttpResponseMetaData) itemWritten)) ||
                                    itemWritten instanceof HttpHeaders ? End : InProgress :
                    new FlushBoundaryProvider() {
                        private long contentLength;
                        @Override
                        public FlushBoundary detectBoundary(@Nullable final Object itemWritten) {
                            if (itemWritten instanceof HttpResponseMetaData) {
                                final HttpResponseMetaData metadata = (HttpResponseMetaData) itemWritten;
                                contentLength = getContentLength(metadata);
                                // The content length maybe unknown at this point (e.g. 204 response) but then later
                                // determined to be 0. In that case we should conservatively use End and rely upon
                                // adjustForMissingBoundaries to accommodate if more data comes. Otherwise the
                                // FlushStrategy may not trigger a flush (e.g. flushOnEnd) and if so the response
                                // won't actually be written.
                                return contentLength > 0 || (contentLength < 0 &&
                                        (!emptyMessageBody(metadata) &&
                                                isTransferEncodingChunked(metadata.headers()))) ? Start : End;
                            }
                            if (itemWritten instanceof Buffer) {
                                return contentLength > 0 &&
                                        (contentLength -= ((Buffer) itemWritten).readableBytes()) <= 0 ?
                                        End : InProgress;
                            }
                            return itemWritten instanceof HttpHeaders ? End : InProgress;
                        }
                    });
            connection.updateFlushStrategy((current, isCurrentOriginal) -> flushStrategy);
            this.drainRequestPayloadBody = drainRequestPayloadBody;
            this.requireTrailerHeader = requireTrailerHeader;
        }

        void process(final boolean handleMultipleRequests) {
            final Single<StreamingHttpRequest> requestSingle =
                    connection.read().liftSyncToSingle(new SpliceFlatStreamToSingleResult<>(
                            (HttpRequestMetaData meta, Publisher<Object> payload) ->
                                    newTransportRequest(meta.method(), meta.requestTarget(), meta.version(),
                                            meta.headers(), executionContext().bufferAllocator(), payload,
                                            requireTrailerHeader, headersFactory)));
            toSource(handleRequestAndWriteResponse(requestSingle, handleMultipleRequests))
                    .subscribe(new ErrorLoggingHttpSubscriber(connection));
        }

        @Override
        public Cancellable updateFlushStrategy(final FlushStrategyProvider strategyProvider) {
            return flushStrategy.updateFlushStrategy(strategyProvider);
        }

        @Override
        public FlushStrategy defaultFlushStrategy() {
            return connection.defaultFlushStrategy();
        }

        private Completable handleRequestAndWriteResponse(final Single<StreamingHttpRequest> requestSingle,
                                                          final boolean handleMultipleRequests) {
            final Publisher<Object> responseObjectPublisher = requestSingle.flatMapPublisher(rawRequest -> {
                // We transform the request and delay the completion of the result flattened stream to avoid
                // resubscribing to the NettyChannelPublisher before the previous subscriber has terminated. Otherwise
                // we may attempt to do duplicate subscribe on NettyChannelPublisher, which will result in a connection
                // closure.
                final SingleSubscriberProcessor requestCompletion = new SingleSubscriberProcessor();
                final AtomicBoolean payloadSubscribed = drainRequestPayloadBody ? new AtomicBoolean() : null;
                final AtomicBoolean responseSent = REQ_EXPECT_CONTINUE.test(rawRequest) ? new AtomicBoolean() : null;
                final StreamingHttpRequest request = rawRequest.transformMessageBody(
                        // Cancellation is assumed to close the connection, or be ignored if this Subscriber has already
                        // terminated. That means we don't need to trigger the processor as completed because we don't
                        // care about processing more requests.
                        payload -> payload.afterSubscriber(() -> {
                            if (drainRequestPayloadBody) {
                                payloadSubscribed.set(true);
                            }
                            if (responseSent != null && !responseSent.get()) {
                                // After users subscribe to the request payload body, generate 100 (Continue) response
                                // if the final response wasn't sent already for this request. Concurrency between
                                // 100 (Continue) and the final response is handled by Netty outbound encoders.
                                // Use Netty Channel directly to avoid adjustments for SplittingFlushStrategy,
                                // WriteStreamSubscriber, and CloseHandler state machines.
                                final Channel channel = nettyChannel();
                                if (channel.eventLoop().inEventLoop()) {
                                    channel.write(streamingResponseFactory().continueResponse());
                                } else {
                                    channel.eventLoop().execute(() ->
                                            channel.write(streamingResponseFactory().continueResponse()));
                                }
                            }
                            return new Subscriber<Object>() {
                                @Override
                                public void onSubscribe(final Subscription s) {
                                }

                                @Override
                                public void onNext(final Object obj) {
                                }

                                @Override
                                public void onError(final Throwable t) {
                                    // After the response payload has terminated, we may attempt to subscribe to the
                                    // request payload and drain/discard the content (in case the user forgets to
                                    // consume the stream). However this means we may introduce a duplicate subscribe
                                    // and this doesn't mean the request content has not terminated.
                                    if (!drainRequestPayloadBody || !(t instanceof RejectedSubscribeError)) {
                                        requestCompletion.onComplete();
                                    }
                                }

                                @Override
                                public void onComplete() {
                                    requestCompletion.onComplete();
                                }
                            };
                        }));

                final HttpRequestMethod requestMethod = request.method();
                final boolean isHeadRequest = HEAD.equals(request.method());
                // Don't expect any exceptions from service because it's already wrapped with
                // ExceptionMapperServiceFilter
                Publisher<Object> respPublisher = service.handle(this, request, streamingResponseFactory())
                        .flatMapPublisher(response -> {
                            if (responseSent != null) {
                                // While concurrency between 100 (Continue) and the final response is handled in Netty
                                // encoders, it's necessary to prevent generating 100 (Continue) response after the full
                                // final response is sent. Otherwise, there is a risk of sending 100 (Continue) after
                                // the final response, which may trigger continuation for the next request in pipeline.
                                responseSent.set(true);
                            }
                            // SplittingFlushStrategy needs to be aware of protocols constraints in order to determine
                            // boundaries between responses. However it isn't aware of request data and content-length
                            // for HEAD requests won't actually be followed by payload. It also has a method
                            // adjustForMissingBoundaries to accommodate for missing End boundaries, so just flush on
                            // each. SplittingFlushStrategy should be removed when NettyHttpServer writes per request
                            // instead of a single stream with repeat() operator, and this code can also be removed.
                            Cancellable c = null;
                            if (isHeadRequest) {
                                flushStrategy.updateFlushStrategy(
                                        (prev, isOriginal) -> isOriginal ? flushOnEach() : prev, 1);
                            } else {
                                final FlushStrategy flushStrategy = determineFlushStrategyForApi(response);
                                if (flushStrategy != null) {
                                    c = updateFlushStrategy((prev, isOriginal) -> isOriginal ? flushStrategy : prev);
                                }
                            }

                            Publisher<Object> pub = handleResponse(protocol(), requestMethod, response);
                            return c == null ? pub : pub.beforeFinally(c::cancel);
                        });

                if (drainRequestPayloadBody) {
                    respPublisher = respPublisher.concat(defer(() -> payloadSubscribed.get() ?
                                    completed() : request.messageBody().ignoreElements()
                            // Discarding the request payload body is an operation which should not impact the state of
                            // request/response processing. It's appropriate to recover from any error here.
                            // ST may introduce RejectedSubscribeError if user already consumed the request payload body
                            .onErrorComplete()));
                }

                return respPublisher.concat(requestCompletion);
            });
            return connection.write(handleMultipleRequests ? responseObjectPublisher.repeat(val -> true) :
                    responseObjectPublisher);
        }

        @Nonnull
        private static Publisher<Object> handleResponse(final HttpProtocolVersion protocolVersion,
                                                        final HttpRequestMethod requestMethod,
                                                        final StreamingHttpResponse response) {
            // Add the content-length if necessary, falling back to transfer-encoding otherwise.
            if (canAddResponseContentLength(response, requestMethod)) {
                return setResponseContentLength(protocolVersion, response);
            } else {
                Publisher<Object> flatResponse;
                if (emptyMessageBody(response, response.messageBody())) {
                    flatResponse = flatEmptyMessage(protocolVersion, response, response.messageBody());
                } else {
                    // Not necessary to defer subscribe to the messageBody because server does not retry responses
                    flatResponse = Single.<Object>succeeded(response).concat(response.messageBody());
                    if (shouldAppendTrailers(protocolVersion, response)) {
                        flatResponse = flatResponse.scanWith(HeaderUtils::appendTrailersMapper);
                    }
                }
                addResponseTransferEncodingIfNecessary(response, requestMethod);
                return flatResponse;
            }
        }

        @Nullable
        private static FlushStrategy determineFlushStrategyForApi(final HttpResponseMetaData response) {
            // For non-aggregated, don't change the flush strategy, keep the default.
            return isSafeToAggregateOrEmpty(response) ? flushOnEnd() : null;
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
        public SslConfig sslConfig() {
            return connection.sslConfig();
        }

        @Nullable
        @Override
        public SSLSession sslSession() {
            return connection.sslSession();
        }

        @Override
        public HttpExecutionContext executionContext() {
            return executionContext;
        }

        @Nullable
        @Override
        public <T> T socketOption(final SocketOption<T> option) {
            return connection.socketOption(option);
        }

        @Override
        public HttpProtocolVersion protocol() {
            return (HttpProtocolVersion) connection.protocol();
        }

        @Override
        public Single<Throwable> transportError() {
            return connection.transportError();
        }

        @Override
        public Completable onClosing() {
            return connection.onClosing();
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
        public Channel nettyChannel() {
            return connection.nettyChannel();
        }

        @Override
        public void acceptConnections(final boolean accept) {
            assert connection.nettyChannel().parent() != null;
            connection.nettyChannel().parent().config().setAutoRead(accept);
        }

        @Override
        public String toString() {
            return connection.toString();
        }
    }

    /**
     * Equivalent of {@link Processors#newCompletableProcessor()} that doesn't handle multiple
     * {@link Subscriber#subscribe(Subscriber) subscribes}.
     */
    private static final class SingleSubscriberProcessor extends SubscribableCompletable implements Processor,
                                                                                                    Cancellable {
        private static final Object CANCELLED = new Object();

        private static final AtomicReferenceFieldUpdater<SingleSubscriberProcessor, Object> stateUpdater =
                AtomicReferenceFieldUpdater.newUpdater(SingleSubscriberProcessor.class, Object.class, "state");

        @Nullable
        private volatile Object state;

        @Override
        protected void handleSubscribe(final Subscriber subscriber) {
            subscriber.onSubscribe(this);
            for (;;) {
                final Object cState = state;
                if (cState instanceof TerminalNotification) {
                    TerminalNotification terminalNotification = (TerminalNotification) cState;
                    terminalNotification.terminate(subscriber);
                    break;
                } else if (cState instanceof Subscriber) {
                    subscriber.onError(new DuplicateSubscribeException(cState, subscriber));
                    break;
                } else if (cState == CANCELLED ||
                        cState == null && stateUpdater.compareAndSet(this, null, subscriber)) {
                    break;
                }
            }
        }

        @Override
        public void onSubscribe(final Cancellable cancellable) {
            // no op, we never cancel as Subscribers and subscribes are decoupled.
        }

        @Override
        public void onComplete() {
            final Object oldState = stateUpdater.getAndSet(this, TerminalNotification.complete());
            if (oldState instanceof Subscriber) {
                ((Subscriber) oldState).onComplete();
            }
        }

        @Override
        public void onError(final Throwable t) {
            final Object oldState = stateUpdater.getAndSet(this, TerminalNotification.error(t));
            if (oldState instanceof Subscriber) {
                ((Subscriber) oldState).onError(t);
            }
        }

        @Override
        public void cancel() {
            state = CANCELLED;
        }
    }

    private static final class ErrorLoggingHttpSubscriber implements CompletableSource.Subscriber {

        private static final Logger LOGGER = LoggerFactory.getLogger(ErrorLoggingHttpSubscriber.class);

        private final NettyConnection<Object, Object> connection;

        ErrorLoggingHttpSubscriber(final NettyConnection<Object, Object> connection) {
            this.connection = connection;
        }

        @Override
        public void onSubscribe(final Cancellable cancellable) {
            // We never cancel from this Subscriber
        }

        @Override
        public void onComplete() {
            // NOOP
        }

        @Override
        public void onError(final Throwable t) {
            if (t instanceof CloseEventObservedException) {
                final CloseEventObservedException ceoe = (CloseEventObservedException) t;
                if (ceoe.event() == CHANNEL_CLOSED_INBOUND && t.getCause() instanceof ClosedChannelException) {
                    LOGGER.trace("{} Client closed the {} connection without sending {}.",
                            connection, connection.protocol(),
                            HTTP_2_0.equals(connection.protocol()) ? "GO_AWAY" : "'Connection: close' header", t);
                } else if (t.getCause() instanceof DecoderException) {
                    logDecoderException((DecoderException) t.getCause(), connection);
                } else {
                    logUnexpectedException(t.getCause() instanceof IOException ? t.getCause() : t, connection);
                }
            } else if (t instanceof DecoderException) {
                logDecoderException((DecoderException) t, connection);
            } else {
                logUnexpectedException(t, connection);
            }
        }

        private static void logDecoderException(final DecoderException e,
                                                final NettyConnection<Object, Object> connection) {
            LOGGER.warn("{} Can not decode a message, no more requests will be received on this {} {}.", connection,
                    connection.protocol(), HTTP_2_0.equals(connection.protocol()) ? "stream" : "connection", e);
        }

        private static void logUnexpectedException(final Throwable t, NettyConnection<Object, Object> connection) {
            LOGGER.debug("{} Unexpected error received, closing {} {} due to:", connection, connection.protocol(),
                    HTTP_2_0.equals(connection.protocol()) ? "stream" : "connection", t);
        }
    }
}
