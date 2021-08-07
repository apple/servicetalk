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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.CompletableSource.Processor;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Processors;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.SubscribableCompletable;
import io.servicetalk.concurrent.internal.DuplicateSubscribeException;
import io.servicetalk.concurrent.internal.RejectedSubscribeError;
import io.servicetalk.concurrent.internal.TerminalNotification;
import io.servicetalk.http.api.DefaultHttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.serializer.api.SerializationException;
import io.servicetalk.tcp.netty.internal.ReadOnlyTcpServerConfig;
import io.servicetalk.tcp.netty.internal.TcpServerBinder;
import io.servicetalk.tcp.netty.internal.TcpServerChannelInitializer;
import io.servicetalk.transport.api.ConnectionAcceptor;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.ChannelInitializer;
import io.servicetalk.transport.netty.internal.CloseHandler;
import io.servicetalk.transport.netty.internal.CloseHandler.CloseEventObservedException;
import io.servicetalk.transport.netty.internal.CopyByteBufHandlerChannelInitializer;
import io.servicetalk.transport.netty.internal.DefaultNettyConnection;
import io.servicetalk.transport.netty.internal.FlushStrategy;
import io.servicetalk.transport.netty.internal.NettyConnection;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;
import io.servicetalk.transport.netty.internal.SplittingFlushStrategy;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.DecoderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.RejectedExecutionException;
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
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.StreamingHttpRequests.newTransportRequest;
import static io.servicetalk.http.netty.AbstractStreamingHttpConnection.determineFlushStrategyForApi;
import static io.servicetalk.http.netty.HeaderUtils.LAST_CHUNK_PREDICATE;
import static io.servicetalk.http.netty.HeaderUtils.addResponseTransferEncodingIfNecessary;
import static io.servicetalk.http.netty.HeaderUtils.canAddResponseContentLength;
import static io.servicetalk.http.netty.HeaderUtils.emptyMessageBody;
import static io.servicetalk.http.netty.HeaderUtils.flatEmptyMessage;
import static io.servicetalk.http.netty.HeaderUtils.setResponseContentLength;
import static io.servicetalk.http.netty.HttpDebugUtils.showPipeline;
import static io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent.CHANNEL_CLOSED_INBOUND;
import static io.servicetalk.transport.netty.internal.CloseHandler.forPipelinedRequestResponse;
import static io.servicetalk.transport.netty.internal.SplittingFlushStrategy.FlushBoundaryProvider.FlushBoundary.End;
import static io.servicetalk.transport.netty.internal.SplittingFlushStrategy.FlushBoundaryProvider.FlushBoundary.InProgress;
import static io.servicetalk.transport.netty.internal.SplittingFlushStrategy.FlushBoundaryProvider.FlushBoundary.Start;

final class NettyHttpServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(NettyHttpServer.class);

    private NettyHttpServer() {
        // No instances
    }

    static Single<ServerContext> bind(final HttpExecutionContext executionContext,
                                      final ReadOnlyHttpServerConfig config,
                                      final SocketAddress address,
                                      @Nullable final ConnectionAcceptor connectionAcceptor,
                                      final StreamingHttpService service,
                                      final boolean drainRequestPayloadBody) {
        if (config.h1Config() == null) {
            return failed(newH1ConfigException());
        }
        // This state is read only, so safe to keep a copy across Subscribers
        final ReadOnlyTcpServerConfig tcpServerConfig = config.tcpConfig();
        // We disable auto read so we can handle stuff in the ConnectionFilter before we accept any content.
        return TcpServerBinder.bind(address, tcpServerConfig, false, executionContext, connectionAcceptor,
                (channel, connectionObserver) -> initChannel(channel, executionContext, config,
                        new TcpServerChannelInitializer(tcpServerConfig, connectionObserver), service,
                        drainRequestPayloadBody, connectionObserver),
                serverConnection -> serverConnection.process(true))
                .map(delegate -> {
                    LOGGER.debug("Started HTTP/1.1 server for address {}.", delegate.listenAddress());
                    // The ServerContext returned by TcpServerBinder takes care of closing the connectionAcceptor.
                    return new NettyHttpServerContext(delegate, service);
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
        return showPipeline(DefaultNettyConnection.initChannel(channel,
                httpExecutionContext.bufferAllocator(), httpExecutionContext.executor(), LAST_CHUNK_PREDICATE,
                closeHandler, config.tcpConfig().flushStrategy(), config.tcpConfig().idleTimeoutMs(),
                initializer.andThen(getChannelInitializer(getByteBufAllocator(httpExecutionContext.bufferAllocator()),
                        h1Config, closeHandler)), httpExecutionContext.executionStrategy(), HTTP_1_1, observer, false)
                .map(conn -> new NettyHttpServerConnection(conn, service, httpExecutionContext.executionStrategy(),
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
            pipeline.addLast(new HttpRequestDecoder(methodQueue, alloc, config.headersFactory(),
                    config.maxStartLineLength(), config.maxHeaderFieldLength(),
                    config.specExceptions().allowPrematureClosureBeforePayloadBody(),
                    config.specExceptions().allowLFWithoutCR(), closeHandler));
            pipeline.addLast(new HttpResponseEncoder(methodQueue, config.headersEncodedSizeEstimate(),
                    config.trailersEncodedSizeEstimate(), closeHandler));
        });
    }

    static final class NettyHttpServerContext implements ServerContext {
        private final ServerContext delegate;
        private final ListenableAsyncCloseable asyncCloseable;

        NettyHttpServerContext(final ServerContext delegate, final StreamingHttpService service) {
            this.delegate = delegate;
            asyncCloseable = toListenableAsyncCloseable(newCompositeCloseable().appendAll(service, delegate));
        }

        @Override
        public SocketAddress listenAddress() {
            return delegate.listenAddress();
        }

        @Override
        public ExecutionContext executionContext() {
            return delegate.executionContext();
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
        private static final Logger LOGGER = LoggerFactory.getLogger(NettyHttpServerConnection.class);
        private final StreamingHttpService service;
        private final HttpExecutionStrategy strategy;
        private final NettyConnection<Object, Object> connection;
        private final HttpHeadersFactory headersFactory;
        private final HttpExecutionContext executionContext;
        private final SplittingFlushStrategy splittingFlushStrategy;
        private final boolean drainRequestPayloadBody;
        private final boolean requireTrailerHeader;

        NettyHttpServerConnection(final NettyConnection<Object, Object> connection,
                                  final StreamingHttpService service,
                                  final HttpExecutionStrategy strategy,
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
                    strategy);
            this.service = service;
            this.strategy = strategy;
            this.splittingFlushStrategy = new SplittingFlushStrategy(connection.defaultFlushStrategy(),
                    itemWritten -> {
                        if (itemWritten instanceof HttpResponseMetaData) {
                            final HttpResponseMetaData metadata = (HttpResponseMetaData) itemWritten;
                            return protocol().major() > 1 && emptyMessageBody(metadata) ? End : Start;
                        }
                        if (itemWritten instanceof HttpHeaders) {
                            return End;
                        }
                        return InProgress;
                    });
            connection.updateFlushStrategy((current, isCurrentOriginal) -> splittingFlushStrategy);
            this.drainRequestPayloadBody = drainRequestPayloadBody;
            this.requireTrailerHeader = requireTrailerHeader;
        }

        void process(final boolean handleMultipleRequests) {
            final Single<StreamingHttpRequest> requestSingle =
                    connection.read().liftSyncToSingle(new SpliceFlatStreamToMetaSingle<>(
                            (HttpRequestMetaData meta, Publisher<Object> payload) ->
                                    newTransportRequest(meta.method(), meta.requestTarget(), meta.version(),
                                            meta.headers(), executionContext().bufferAllocator(), payload,
                                            requireTrailerHeader, headersFactory)));
            toSource(handleRequestAndWriteResponse(requestSingle, handleMultipleRequests))
                    .subscribe(new ErrorLoggingHttpSubscriber());
        }

        @Override
        public Cancellable updateFlushStrategy(final FlushStrategyProvider strategyProvider) {
            return splittingFlushStrategy.updateFlushStrategy(strategyProvider);
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
                final StreamingHttpRequest request = rawRequest.transformMessageBody(
                        // Cancellation is assumed to close the connection, or be ignored if this Subscriber has already
                        // terminated. That means we don't need to trigger the processor as completed because we don't
                        // care about processing more requests.
                        payload -> payload.afterSubscriber(() -> {
                            if (drainRequestPayloadBody) {
                                payloadSubscribed.set(true);
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
                final HttpKeepAlive keepAlive = HttpKeepAlive.responseKeepAlive(request);
                Publisher<Object> responsePublisher = strategy
                        .invokeService(executionContext().executor(), request,
                                req -> service.handle(this, req, streamingResponseFactory())
                                        .onErrorReturn(cause -> newErrorResponse(cause, executionContext.executor(),
                                                        req.version(), keepAlive))
                                        .flatMapPublisher(response -> {
                                            keepAlive.addConnectionHeaderIfNecessary(response);

                                            final FlushStrategy flushStrategy = determineFlushStrategyForApi(response);
                                            if (flushStrategy != null) {
                                                splittingFlushStrategy.updateFlushStrategy(
                                                        (prev, isOriginal) -> isOriginal ? flushStrategy : prev, 1);
                                            }
                                            return handleResponse(protocol(), requestMethod, response);
                                        }),
                                (cause, executor) -> {
                                    final StreamingHttpResponse errorResponse = newErrorResponse(cause, executor,
                                            request.version(), keepAlive);
                                    return flatEmptyMessage(protocol(), errorResponse, errorResponse.messageBody());
                                });

                if (drainRequestPayloadBody) {
                    responsePublisher = responsePublisher.concat(defer(() -> payloadSubscribed.get() ?
                                    completed() : request.messageBody().ignoreElements()
                            // Discarding the request payload body is an operation which should not impact the state of
                            // request/response processing. It's appropriate to recover from any error here.
                            // ST may introduce RejectedSubscribeError if user already consumed the request payload body
                            .onErrorComplete()));
                }

                return responsePublisher.concat(requestCompletion);
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
                final Publisher<Object> flatResponse = emptyMessageBody(response, response.messageBody()) ?
                        flatEmptyMessage(protocolVersion, response, response.messageBody()) :
                        // Not necessary to defer subscribe to the messageBody because server does not retry responses
                        Single.<Object>succeeded(response).concat(response.messageBody())
                                .scanWith(HeaderUtils::insertTrailersMapper);
                addResponseTransferEncodingIfNecessary(response, requestMethod);
                return flatResponse;
            }
        }

        private StreamingHttpResponse newErrorResponse(final Throwable cause, final Executor executor,
                                                       final HttpProtocolVersion version,
                                                       final HttpKeepAlive keepAlive) {
            final StreamingHttpResponse response;
            if (cause instanceof RejectedExecutionException) {
                LOGGER.error("Task rejected by Executor {} for service={}, connection={}", executor, service, this,
                        cause);
                response = streamingResponseFactory().serviceUnavailable();
            } else if (cause instanceof SerializationException) {
                // It is assumed that a failure occurred when attempting to deserialize the request.
                response = streamingResponseFactory().unsupportedMediaType();
            } else {
                LOGGER.error("Internal server error service={} connection={}", service, this, cause);
                response = streamingResponseFactory().internalServerError();
            }
            response.version(version)
                    .setHeader(CONTENT_LENGTH, ZERO);
            keepAlive.addConnectionHeaderIfNecessary(response);
            return response;
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
        public String toString() {
            return getClass().getSimpleName() + '(' + connection + ')';
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
                    LOGGER.trace("Client closed the connection without sending 'Connection: close' header", t);
                    return;
                }
                if (t.getCause() instanceof DecoderException) {
                    LOGGER.warn("Can not decode HTTP message, no more requests will be received on this connection.",
                            t);
                    return;
                }
            }
            LOGGER.debug("Unexpected error received while processing connection, {}",
                    "no more requests will be received on this connection.", t);
        }
    }
}
