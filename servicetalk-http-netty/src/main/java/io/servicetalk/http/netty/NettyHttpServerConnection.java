/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompletableProcessor;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.RejectedSubscribeError;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.netty.internal.CloseHandler;
import io.servicetalk.transport.netty.internal.DefaultNettyConnection;
import io.servicetalk.transport.netty.internal.FlushStrategy;
import io.servicetalk.transport.netty.internal.NettyConnection;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;
import io.servicetalk.transport.netty.internal.WriteEventsListenerAdapter;

import io.netty.channel.Channel;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.StreamingHttpRequests.newRequestWithTrailers;
import static io.servicetalk.http.netty.HeaderUtils.addResponseTransferEncodingIfNecessary;
import static io.servicetalk.http.netty.SpliceFlatStreamToMetaSingle.flatten;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

final class NettyHttpServerConnection extends HttpServiceContext implements NettyConnectionContext {

    private static final Logger LOGGER = LoggerFactory.getLogger(NettyHttpServerConnection.class);
    private final StreamingHttpService service;
    private final DefaultNettyConnection<Object, Object> connection;
    private final CompositeFlushStrategy compositeFlushStrategy;

    NettyHttpServerConnection(final DefaultNettyConnection<Object, Object> connection,
                              final StreamingHttpService service, final CompositeFlushStrategy compositeFlushStrategy,
                              final HttpHeadersFactory headersFactory, final BufferAllocator allocator) {
        super(new DefaultHttpResponseFactory(headersFactory, allocator),
                new DefaultStreamingHttpResponseFactory(headersFactory, allocator),
                new DefaultBlockingStreamingHttpResponseFactory(headersFactory, allocator));
        this.connection = connection;
        this.service = service;
        this.compositeFlushStrategy = compositeFlushStrategy;
    }

    Completable process() {
        final Publisher<Object> connRequestObjectPublisher = connection.read();

        final Single<StreamingHttpRequest> requestSingle =
                new SpliceFlatStreamToMetaSingle<>(connRequestObjectPublisher,
                        (HttpRequestMetaData hdr, Publisher<Object> pandt) ->
                        spliceRequest(NettyHttpServerConnection.this.connection.executionContext().bufferAllocator(),
                                hdr, pandt));
        return handleRequestAndWriteResponse(requestSingle);
    }

    DefaultNettyConnection<Object, Object> getConnection() {
        return connection;
    }

    @Override
    public Cancellable updateFlushStrategy(final UnaryOperator<FlushStrategy> strategyProvider) {
        return compositeFlushStrategy.updateFlushStrategy(strategyProvider);
    }

    @Override
    public Publisher<ConnectionEvent> connectionEvents() {
        return connection.connectionEvents();
    }

    static NettyHttpServerConnection newConnection(final Channel channel,
                                                   final Publisher<Object> requestObjectPublisher,
                                                   final NettyConnection.TerminalPredicate<Object> terminalPredicate,
                                                   final CloseHandler closeHandler, final ConnectionContext context,
                                                   final StreamingHttpService service,
                                                   final FlushStrategy flushStrategy,
                                                   final HttpHeadersFactory headersFactory) {
        CompositeFlushStrategy cfs = new CompositeFlushStrategy(flushStrategy);
        DefaultNettyConnection<Object, Object> conn = new DefaultNettyConnection<>(channel, context,
                requestObjectPublisher, terminalPredicate, closeHandler, cfs);
        return new NettyHttpServerConnection(conn, service, cfs, headersFactory,
                context.executionContext().bufferAllocator());
    }

    private static StreamingHttpRequest spliceRequest(final BufferAllocator alloc,
                                                      final HttpRequestMetaData hr,
                                                      final Publisher<Object> pub) {
        return newRequestWithTrailers(hr.method(), hr.requestTarget(), hr.version(), hr.headers(), alloc, pub);
    }

    private Completable handleRequestAndWriteResponse(final Single<StreamingHttpRequest> requestSingle) {
        final Publisher<Object> responseObjectPublisher = requestSingle.flatMapPublisher(request -> {
            final HttpRequestMethod requestMethod = request.method();
            final HttpKeepAlive keepAlive = HttpKeepAlive.getResponseKeepAlive(request);
            // We transform the request and delay the completion of the result flattened stream to avoid resubscribing
            // to the NettyChannelPublisher before the previous subscriber has terminated. Otherwise we may attempt
            // to do duplicate subscribe on NettyChannelPublisher, which will result in a connection closure.
            CompletableProcessor processor = new CompletableProcessor();
            StreamingHttpRequest request2 = request.transformPayloadBody(
                    // Cancellation is assumed to close the connection, or be ignored if this Subscriber has already
                    // terminated. That means we don't need to trigger the processor as completed because we don't care
                    // about processing more requests.
                    payload -> payload.doAfterSubscriber(() -> new Subscriber<Buffer>() {
                        @Override
                        public void onSubscribe(final Subscription s) {
                        }

                        @Override
                        public void onNext(final Buffer buffer) {
                        }

                        @Override
                        public void onError(final Throwable t) {
                            // After the response payload has terminated, we attempt to subscribe to the request payload
                            // and drain/discard the content (in case the user forgets to consume the stream). However
                            // this means we may introduce a duplicate subscribe and this doesn't mean the request
                            // content has not terminated.
                            if (!(t instanceof RejectedSubscribeError)) {
                                processor.onComplete();
                            }
                        }

                        @Override
                        public void onComplete() {
                            processor.onComplete();
                        }
                    }));
            final Completable drainRequestPayloadBody = request2.payloadBody().ignoreElements()
                    // ignore error about duplicate subscriptions, we are forcing a subscription here and the user
                    // may also subscribe, so it is OK if we fail here.
                    .onErrorResume(t -> completed());
            return handleRequest(request2)
                    .map(response -> processResponse(requestMethod, keepAlive, drainRequestPayloadBody, response))
                    .flatMapPublisher(resp -> flatten(resp, StreamingHttpResponse::payloadBodyAndTrailers))
                    .concatWith(processor);
            // We are writing to the connection which may request more data from the EventLoop. So offload control
            // signals which may have blocking code.
        }).subscribeOn(executionContext().executor());
        return connection.write(responseObjectPublisher.repeat(val -> true)
                // We generate synthetic callbacks to WriteEventsListener as there is a single write per connection but
                // FlushStrategy are implemented considering individual responses.
                // Since this operator is present on the flattened single write stream, with or without pipelined
                // requests processed in parallel, we can send WriteEventsListener callbacks per response.
                .liftSynchronous(subscriber -> new Subscriber<Object>() {
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
                            // If subscriber throws from onNext, connection should get closed and hence flushStrategy
                            // should get the terminal notification. Any further changes are insignificant, so we do
                            // not care to send the following callback.
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

    private Single<StreamingHttpResponse> handleRequest(final StreamingHttpRequest request) {
        return new Single<StreamingHttpResponse>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super StreamingHttpResponse> subscriber) {
                // Since we do not offload data path for the request single, this method will be invoked from the
                // EventLoop. So, we offload the call to StreamingHttpService.
                Executor exec = executionContext().executor();
                exec.execute(() -> {
                    Single<StreamingHttpResponse> source;
                    try {
                        source = service.handle(NettyHttpServerConnection.this,
                                request.transformPayloadBody(bdy -> bdy.publishOn(exec)), streamingResponseFactory())
                                .onErrorResume(cause -> newErrorResponse(cause, request));
                    } catch (final Throwable cause) {
                        source = newErrorResponse(cause, request);
                    }
                    source.subscribe(subscriber);
                });
            }
        };
    }

    private static StreamingHttpResponse processResponse(final HttpRequestMethod requestMethod,
                                                         final HttpKeepAlive keepAlive,
                                                         final Completable drainRequestPayloadBody,
                                                         final StreamingHttpResponse response) {
        addResponseTransferEncodingIfNecessary(response, requestMethod);
        keepAlive.addConnectionHeaderIfNecessary(response);

        // When the response payload publisher completes, read any of the request payload that hasn't already
        // been read. This is necessary for using a persistent connection to send multiple requests.
        return response.transformPayloadBody(responsePayload -> responsePayload.concatWith(drainRequestPayloadBody));
    }

    private Single<StreamingHttpResponse> newErrorResponse(final Throwable cause,
                                                           final StreamingHttpRequest request) {
        LOGGER.error("internal server error service={} connection={}", service, this, cause);
        StreamingHttpResponse resp = streamingResponseFactory().internalServerError().version(request.version());
        resp.headers().set(CONTENT_LENGTH, ZERO);
        return success(resp);
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
    public ExecutionContext executionContext() {
        return connection.executionContext();
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

    /**
     * We do a single write per connection, so this {@link FlushStrategy} manages any dynamic change to
     * {@link FlushStrategy} for the connection. We always register a single {@link FlushStrategy} for the connection
     * and intercept all changes to {@link FlushStrategy}. If a user provided {@link FlushStrategy} is registered, this
     * class manages the interaction with that {@link FlushStrategy}.
     */
    private static final class CompositeFlushStrategy implements FlushStrategy, FlushStrategy.WriteEventsListener {

        private static final WriteEventsListener INIT = new WriteEventsListenerAdapter() { };
        private static final WriteEventsListener CANCELLED = new WriteEventsListenerAdapter() { };
        private static final WriteEventsListener TERMINATED = new WriteEventsListenerAdapter() { };

        private static final AtomicReferenceFieldUpdater<CompositeFlushStrategy, FlushStrategy> flushStrategyUpdater =
                newUpdater(CompositeFlushStrategy.class, FlushStrategy.class, "flushStrategy");
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
                // Since flushStrategy and currentListener can not be updated atomically, we only switch currentListener
                // if it has not changed from what it was before updating flushStrategy.
                // If the listener has changed, it could have changed before or after updating the flushStrategy but
                // we do not have any way to find, so we let the listener terminate through the regular code path when
                // the response terminates.
                // Unconditionally updating the currentListener would mean that we may swap out the listener for the
                // updated strategy.
                if (currentListener == prev) {
                    WriteEventsListener listener = originalStrategy.apply(flushSender);
                    listener.writeStarted();
                    if (currentListenerUpdater.compareAndSet(CompositeFlushStrategy.this, prev, listener)) {
                        prev.writeTerminated();
                    } else {
                        listener.writeTerminated();
                    }
                }
            };
        }

        @Override
        public WriteEventsListener apply(final FlushSender sender) {
            flushSender = sender;
            // FlushStrategy#apply() MUST be called before any item is written on the connection. Since, we
            // read-transform-write for Http server, it means this method MUST be called before we start reading data.
            // Since, there is no way to update FlushStrategy for the connection before we read the first request, this
            // method MUST be called before FlushStrategy is updated. So, we simply use the originalStrategy here.
            updateListener(originalStrategy.apply(sender));
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
            // We do not need any extra visibility guarantees for flushSender as there is a happens-before relationship
            // between apply() (where flushSender is assigned) and beforeEmitMetadata() (where flushSender is used).
            //
            // We know that apply() should happen-before we subscribe to the Publisher that is written to the
            // connection. Since read-transform-write for Http server, subscribe to write Publisher MUST happen-before
            // reading of the first request. This means that apply() should happen-before any metadata is emitted.
            updateListener(flushStrategy.apply(flushSender));
        }

        void afterEmitTrailers() {
            updateListener(INIT);
        }

        private void updateListener(final WriteEventsListener newListener) {
            newListener.writeStarted();
            for (;;) {
                final WriteEventsListener current = currentListener;
                if (current == CANCELLED) {
                    newListener.writeCancelled();
                    return;
                } else if (current == TERMINATED) {
                    newListener.writeTerminated();
                    return;
                } else if (currentListenerUpdater.compareAndSet(this, current, newListener)) {
                    // Old listener will not be invoked any more, so send a terminal signal.
                    current.writeTerminated();
                    return;
                }
            }
        }
    }
}
