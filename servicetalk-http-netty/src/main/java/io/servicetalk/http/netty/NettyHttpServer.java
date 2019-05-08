/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.CompletableSource.Processor;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.RejectedSubscribeError;
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
import io.servicetalk.tcp.netty.internal.ReadOnlyTcpServerConfig;
import io.servicetalk.tcp.netty.internal.TcpServerBinder;
import io.servicetalk.tcp.netty.internal.TcpServerChannelInitializer;
import io.servicetalk.transport.api.ConnectionAcceptor;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.ChannelInitializer;
import io.servicetalk.transport.netty.internal.CloseHandler;
import io.servicetalk.transport.netty.internal.DefaultNettyConnection;
import io.servicetalk.transport.netty.internal.FlushStrategy;
import io.servicetalk.transport.netty.internal.NettyConnection;
import io.servicetalk.transport.netty.internal.NettyConnection.TerminalPredicate;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;
import io.servicetalk.transport.netty.internal.NoopWriteEventsListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.AsyncCloseables.toListenableAsyncCloseable;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.defer;
import static io.servicetalk.concurrent.api.Processors.newCompletableProcessor;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.StreamingHttpRequests.newRequestWithTrailers;
import static io.servicetalk.http.netty.HeaderUtils.addResponseTransferEncodingIfNecessary;
import static io.servicetalk.http.netty.HeaderUtils.canAddResponseContentLength;
import static io.servicetalk.http.netty.HeaderUtils.setResponseContentLength;
import static io.servicetalk.transport.netty.internal.CloseHandler.forPipelinedRequestResponse;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

final class NettyHttpServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(NettyHttpServer.class);
    private static final Predicate<Object> LAST_HTTP_PAYLOAD_CHUNK_OBJECT_PREDICATE =
            p -> p instanceof HttpHeaders;

    private NettyHttpServer() {
        // No instances
    }

    static Single<ServerContext> bind(final HttpExecutionContext executionContext,
                                      final ReadOnlyHttpServerConfig config,
                                      final SocketAddress address,
                                      @Nullable final ConnectionAcceptor connectionAcceptor,
                                      StreamingHttpService service,
                                      final boolean drainRequestPayloadBody) {
        // This state is read only, so safe to keep a copy across Subscribers
        final ReadOnlyTcpServerConfig tcpServerConfig = config.tcpConfig();
        return TcpServerBinder.bind(address, tcpServerConfig, executionContext, connectionAcceptor,
                channel -> {
                    final CloseHandler closeHandler = forPipelinedRequestResponse(false, channel.config());
                    final NettyHttpServerConnection.CompositeFlushStrategy flushStrategy =
                            new NettyHttpServerConnection.CompositeFlushStrategy(tcpServerConfig.flushStrategy());
                    return DefaultNettyConnection.initChannel(
                            channel, executionContext.bufferAllocator(), executionContext.executor(),
                            new TerminalPredicate<>(LAST_HTTP_PAYLOAD_CHUNK_OBJECT_PREDICATE), closeHandler,
                            flushStrategy, new TcpServerChannelInitializer(tcpServerConfig)
                                    .andThen(getChannelInitializer(config, closeHandler)),
                            executionContext.executionStrategy())
                        .map(conn -> new NettyHttpServerConnection(conn, service, executionContext.executionStrategy(),
                                flushStrategy, config.headersFactory(), drainRequestPayloadBody));
                },
                serverConnection -> serverConnection.process().subscribe())
            .map(delegate -> {
                LOGGER.debug("Started HTTP server for address {}.", delegate.listenAddress());
                // The ServerContext returned by TcpServerBinder takes care of closing the connectionAcceptor.
                return new NettyHttpServerContext(delegate, service, executionContext.executionStrategy());
            });
    }

    private static ChannelInitializer getChannelInitializer(final ReadOnlyHttpServerConfig config,
                                                            final CloseHandler closeHandler) {
        return (channel, context) -> {
            Queue<HttpRequestMethod> methodQueue = new ArrayDeque<>(2);
            channel.pipeline().addLast(new HttpRequestDecoder(methodQueue, config.headersFactory(),
                    config.maxInitialLineLength(), config.maxHeaderSize(), closeHandler));
            channel.pipeline().addLast(new HttpResponseEncoder(methodQueue, config.headersEncodedSizeEstimate(),
                    config.trailersEncodedSizeEstimate(), closeHandler));
            return context;
        };
    }

    private static final class NettyHttpServerContext implements ServerContext {
        private final ServerContext delegate;
        private final HttpExecutionStrategy strategy;
        private final ListenableAsyncCloseable asyncCloseable;

        NettyHttpServerContext(final ServerContext delegate, final StreamingHttpService service,
                               final HttpExecutionStrategy strategy) {
            this.delegate = delegate;
            this.strategy = strategy;
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

    private static final class NettyHttpServerConnection extends HttpServiceContext implements NettyConnectionContext {
        private static final Logger LOGGER = LoggerFactory.getLogger(NettyHttpServerConnection.class);
        private final StreamingHttpService service;
        private final HttpExecutionStrategy strategy;
        private final NettyConnection<Object, Object> connection;
        private final HttpExecutionContext executionContext;
        private final CompositeFlushStrategy compositeFlushStrategy;
        private final boolean drainRequestPayloadBody;

        NettyHttpServerConnection(final NettyConnection<Object, Object> connection,
                                  final StreamingHttpService service,
                                  final HttpExecutionStrategy strategy,
                                  final CompositeFlushStrategy compositeFlushStrategy,
                                  final HttpHeadersFactory headersFactory,
                                  final boolean drainRequestPayloadBody) {
            super(headersFactory,
                    new DefaultHttpResponseFactory(headersFactory, connection.executionContext().bufferAllocator()),
                    new DefaultStreamingHttpResponseFactory(headersFactory,
                            connection.executionContext().bufferAllocator()),
                    new DefaultBlockingStreamingHttpResponseFactory(headersFactory,
                            connection.executionContext().bufferAllocator()));
            this.connection = connection;
            executionContext = new DefaultHttpExecutionContext(connection.executionContext().bufferAllocator(),
                    connection.executionContext().ioExecutor(), connection.executionContext().executor(),
                    strategy);
            this.service = service;
            this.strategy = strategy;
            this.compositeFlushStrategy = compositeFlushStrategy;
            this.drainRequestPayloadBody = drainRequestPayloadBody;
        }

        Completable process() {
            final Single<StreamingHttpRequest> requestSingle =
                    new SpliceFlatStreamToMetaSingle<>(connection.read(),
                            (HttpRequestMetaData meta, Publisher<Object> payload) ->
                                    newRequestWithTrailers(meta.method(), meta.requestTarget(), meta.version(),
                                            meta.headers(), executionContext().bufferAllocator(), payload));
            return handleRequestAndWriteResponse(requestSingle);
        }

        @Override
        public Cancellable updateFlushStrategy(final UnaryOperator<FlushStrategy> strategyProvider) {
            return compositeFlushStrategy.updateFlushStrategy(strategyProvider);
        }

        private Completable handleRequestAndWriteResponse(final Single<StreamingHttpRequest> requestSingle) {
            final Publisher<Object> responseObjectPublisher = requestSingle.flatMapPublisher(rawRequest -> {
                // We transform the request and delay the completion of the result flattened stream to avoid
                // resubscribing to the NettyChannelPublisher before the previous subscriber has terminated. Otherwise
                // we may attempt to do duplicate subscribe on NettyChannelPublisher, which will result in a connection
                // closure.
                final Processor requestCompletion = newCompletableProcessor();
                @Nullable
                final AtomicBoolean payloadSubscribed = drainRequestPayloadBody ? new AtomicBoolean() : null;
                final StreamingHttpRequest request = rawRequest.transformRawPayloadBody(
                        // Cancellation is assumed to close the connection, or be ignored if this Subscriber has already
                        // terminated. That means we don't need to trigger the processor as completed because we don't
                        // care about processing more requests.
                        payload -> payload.afterSubscriber(() -> new Subscriber<Object>() {
                            @Override
                            public void onSubscribe(final Subscription s) {
                                if (drainRequestPayloadBody) {
                                    payloadSubscribed.set(true);
                                }
                            }

                            @Override
                            public void onNext(final Object obj) {
                            }

                            @Override
                            public void onError(final Throwable t) {
                                // After the response payload has terminated, we may attempt to subscribe to the request
                                // payload and drain/discard the content (in case the user forgets to consume the
                                // stream). However this means we may introduce a duplicate subscribe and this doesn't
                                // mean the request content has not terminated.
                                if (!drainRequestPayloadBody || !(t instanceof RejectedSubscribeError)) {
                                    requestCompletion.onComplete();
                                }
                            }

                            @Override
                            public void onComplete() {
                                requestCompletion.onComplete();
                            }
                        }));

                final HttpRequestMethod requestMethod = request.method();
                final HttpKeepAlive keepAlive = HttpKeepAlive.responseKeepAlive(request);
                Publisher<Object> responsePublisher = strategy
                        .invokeService(executionContext().executor(), request,
                                req -> service.handle(NettyHttpServerConnection.this, req, streamingResponseFactory())
                                        .flatMap(response -> {
                                            keepAlive.addConnectionHeaderIfNecessary(response);
                                            if (!canAddResponseContentLength(response, requestMethod)) {
                                                addResponseTransferEncodingIfNecessary(response, requestMethod);
                                                return succeeded(response);
                                            }
                                            // Add the content-length if necessary, falling back to transfer-encoding
                                            // otherwise.
                                            return setResponseContentLength(response);
                                        }),
                                (cause, executor) -> newErrorResponse(cause, executor, request.version(), keepAlive));

                if (drainRequestPayloadBody) {
                    responsePublisher = responsePublisher.concat(defer(() -> payloadSubscribed.get() ?
                                    Completable.completed() : request.payloadBody().ignoreElements()
                            // Discarding the request payload body is an operation which should not impact the state of
                            // request/response processing. It's appropriate to recover from any error here.
                            // ST may introduce RejectedSubscribeError if user already consumed the request payload body
                            .onErrorResume(t -> completed())));
                }

                return responsePublisher.concat(fromSource(requestCompletion));
            });
            return connection.write(responseObjectPublisher.repeat(val -> true)
                    // We generate synthetic callbacks to WriteEventsListener as there is a single write per connection
                    // but FlushStrategy are implemented considering individual responses.
                    // Since this operator is present on the flattened single write stream, with or without pipelined
                    // requests processed in parallel, we can send WriteEventsListener callbacks per response.
                    .liftSync(subscriber -> new Subscriber<Object>() {
                        @Override
                        public void onSubscribe(final Subscription s) {
                            subscriber.onSubscribe(s);
                        }

                        @Override
                        public void onNext(final Object o) {
                            if (o instanceof HttpResponseMetaData) {
                                compositeFlushStrategy.beforeEmitMetadata();
                                // If beforeEmitMetadata() throws we will get terminated since we are inside onNext, in
                                // which case we do not need to call onNext to the subscriber.
                                subscriber.onNext(o);
                            } else if (o instanceof HttpHeaders) { // trailers
                                subscriber.onNext(o);
                                // If subscriber throws from onNext, connection should get closed and hence
                                // flushStrategy should get the terminal notification. Any further changes are
                                // insignificant, so we do not care to send the following callback.
                                compositeFlushStrategy.afterEmitTrailers();
                            } else {
                                subscriber.onNext(o);
                            }
                        }

                        @Override
                        public void onError(final Throwable t) {
                            subscriber.onError(t);
                        }

                        @Override
                        public void onComplete() {
                            subscriber.onComplete();
                        }
                    }));
        }

        private Single<StreamingHttpResponse> newErrorResponse(final Throwable cause, final Executor executor,
                                                               final HttpProtocolVersion version,
                                                               final HttpKeepAlive keepAlive) {
            final StreamingHttpResponse response;
            if (cause instanceof RejectedExecutionException) {
                LOGGER.error("Task rejected by Executor {} for service={}, connection={}", executor, service, this,
                        cause);
                response = streamingResponseFactory().serviceUnavailable();
            } else {
                LOGGER.error("Internal server error service={} connection={}", service, this, cause);
                response = streamingResponseFactory().internalServerError();
            }
            response.version(version)
                    .setHeader(CONTENT_LENGTH, ZERO);
            keepAlive.addConnectionHeaderIfNecessary(response);
            return succeeded(response);
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
        public String toString() {
            return connection.toString();
        }

        /**
         * We do a single write per connection, so this {@link FlushStrategy} manages any dynamic change to
         * {@link FlushStrategy} for the connection. We always register a single {@link FlushStrategy} for the
         * connection and intercept all changes to {@link FlushStrategy}. If a user provided {@link FlushStrategy} is
         * registered, this class manages the interaction with that {@link FlushStrategy}.
         */
        private static final class CompositeFlushStrategy implements FlushStrategy, FlushStrategy.WriteEventsListener {

            private static final WriteEventsListener INIT = new NoopWriteEventsListener() { };
            private static final WriteEventsListener CANCELLED = new NoopWriteEventsListener() { };
            private static final WriteEventsListener TERMINATED = new NoopWriteEventsListener() { };

            private static final AtomicReferenceFieldUpdater<CompositeFlushStrategy, FlushStrategy>
                    flushStrategyUpdater = newUpdater(CompositeFlushStrategy.class, FlushStrategy.class,
                    "flushStrategy");
            private static final AtomicReferenceFieldUpdater<CompositeFlushStrategy, WriteEventsListener>
                    currentListenerUpdater = newUpdater(CompositeFlushStrategy.class, WriteEventsListener.class,
                    "currentListener");

            private final FlushStrategy originalStrategy;
            private volatile FlushStrategy flushStrategy;
            private volatile WriteEventsListener currentListener = INIT;
            private FlushSender flushSender = () -> { };

            CompositeFlushStrategy(final FlushStrategy flushStrategy) {
                originalStrategy = flushStrategy;
                this.flushStrategy = flushStrategy;
            }

            Cancellable updateFlushStrategy(final UnaryOperator<FlushStrategy> strategyProvider) {
                flushStrategyUpdater.updateAndGet(this, strategyProvider);
                // We always revert to the original strategy specified for the connection. If a user wishes to create a
                // hierarchical strategy, they have to do it by themselves.
                return () -> {
                    final WriteEventsListener prev = currentListener;
                    updateFlushStrategy(__ -> originalStrategy);
                    // Since flushStrategy and currentListener can not be updated atomically, we only switch
                    // currentListener if it has not changed from what it was before updating flushStrategy.
                    // If the listener has changed, it could have changed before or after updating the flushStrategy but
                    // we do not have any way to find, so we let the listener terminate through the regular code path
                    // when the response terminates.
                    // Unconditionally updating the currentListener would mean that we may swap out the listener for the
                    // updated strategy.
                    if (currentListener == prev) {
                        WriteEventsListener listener = originalStrategy.apply(flushSender);
                        if (currentListenerUpdater.compareAndSet(CompositeFlushStrategy.this, prev, listener)) {
                            try {
                                prev.writeTerminated();
                            } finally {
                                listener.writeStarted();
                            }
                        }
                    }
                };
            }

            @Override
            public WriteEventsListener apply(final FlushSender sender) {
                flushSender = sender;
                // This method is called after the read-transform-write for Http server is completed on the
                // NettyConnection. This does not occur until after the ConnectionAcceptor is called which may invoke
                // updateFlushStrategy to swap the flush strategy. We defer calling apply on the real flushStrategy
                // until we write the first meta data, and flushing before this will have no impact (all writes are
                // done in the request-response lifecycle). If this changes in the future (e.g. control data) we should
                // reevaluate.
                return this;
            }

            @Override
            public void writeStarted() {
                // Noop. We eagerly send writeStarted to any new listener.
            }

            @Override
            public void itemWritten() {
                // In case this callback is received concurrently with updateFlushStrategy(), we do a best effort to
                // send the callback to the new listener.
                currentListener.itemWritten();
            }

            @Override
            public void writeTerminated() {
                currentListenerUpdater.getAndSet(this, TERMINATED).writeTerminated();
            }

            @Override
            public void writeCancelled() {
                currentListenerUpdater.getAndSet(this, CANCELLED).writeCancelled();
            }

            void beforeEmitMetadata() {
                // We do not need any extra visibility guarantees for flushSender as there is a happens-before
                // relationship between apply() (where flushSender is assigned) and beforeEmitMetadata() (where
                // flushSender is used).
                //
                // We know that apply() should happen-before we subscribe to the Publisher that is written to the
                // connection. Since read-transform-write for Http server, subscribe to write Publisher MUST
                // happen-before reading of the first request. This means that apply() should happen-before any metadata
                // is emitted.
                updateListener(flushStrategy.apply(flushSender));
            }

            void afterEmitTrailers() {
                updateListener(INIT);
            }

            private void updateListener(final WriteEventsListener newListener) {
                for (;;) {
                    final WriteEventsListener current = currentListener;
                    if (current == CANCELLED || current == TERMINATED) {
                        return;
                    } else if (currentListenerUpdater.compareAndSet(this, current, newListener)) {
                        try {
                            // Old listener will not be invoked any more, so send a terminal signal.
                            current.writeTerminated();
                        } finally {
                            newListener.writeStarted();
                        }
                        return;
                    }
                }
            }
        }
    }
}
