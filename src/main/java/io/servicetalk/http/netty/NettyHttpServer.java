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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompletableProcessor;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.api.LastHttpPayloadChunk;
import io.servicetalk.tcp.netty.internal.TcpServerChannelInitializer;
import io.servicetalk.tcp.netty.internal.TcpServerInitializer;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ContextFilter;
import io.servicetalk.transport.api.FlushStrategy;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.AbstractChannelReadHandler;
import io.servicetalk.transport.netty.internal.ChannelInitializer;
import io.servicetalk.transport.netty.internal.Connection;
import io.servicetalk.transport.netty.internal.Connection.TerminalPredicate;
import io.servicetalk.transport.netty.internal.NettyConnection;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.http.api.EmptyHttpHeaders.INSTANCE;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.HttpPayloadChunks.newLastPayloadChunk;
import static io.servicetalk.http.api.HttpRequests.newRequest;
import static io.servicetalk.http.api.HttpResponseStatuses.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponses.newResponse;
import static io.servicetalk.http.netty.HeaderUtils.addResponseTransferEncodingIfNecessary;

final class NettyHttpServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(NettyHttpServer.class);
    private static final Predicate<Object> LAST_HTTP_PAYLOAD_CHUNK_PREDICATE = p -> p instanceof LastHttpPayloadChunk;

    private NettyHttpServer() {
        // No instances
    }

    static Single<ServerContext> bind(final ReadOnlyHttpServerConfig config, final SocketAddress address, final ContextFilter contextFilter, final Executor executor, final HttpService<HttpPayloadChunk, HttpPayloadChunk> service) {
        final TcpServerInitializer initializer = new TcpServerInitializer(config.getTcpConfig());

        final ChannelInitializer channelInitializer = new TcpServerChannelInitializer(config.getTcpConfig()).andThen((channel, context) -> {

            // TODO: Context filtering should be moved somewhere central. Maybe TcpServerInitializer.start?
            final Single<Boolean> filterResultSingle = contextFilter.filter(context);
            filterResultSingle.subscribe(new io.servicetalk.concurrent.Single.Subscriber<Boolean>() {
                @Override
                public void onSubscribe(final Cancellable cancellable) {
                    // Don't need to do anything.
                }

                @Override
                public void onSuccess(@Nullable final Boolean result) {
                    if (result != null && result) {
                        // Getting the remote-address may involve volatile reads and potentially a syscall, so guard it.
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("Accepted connection from {}", context.getRemoteAddress());
                        }
                        handleAcceptedConnection(config, executor, service, channel, context);
                    } else {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("Rejected connection from {}", context.getRemoteAddress());
                        }
                        handleRejectedConnection(context);
                    }
                }

                @Override
                public void onError(final Throwable t) {
                    LOGGER.warn("Context filter threw exception.", t);
                    handleRejectedConnection(context);
                }
            });

            return context;
        });

        // The ServerContext returned by TcpServerInitializer takes care of closing the contextFilter.
        return initializer.start(address, contextFilter, channelInitializer, false).map(
                (ServerContext delegate) -> new NettyHttpServerContext(delegate, service, executor));
    }

    private static void handleRejectedConnection(final ConnectionContext context) {
        context.closeAsync().subscribe();
    }

    private static void handleAcceptedConnection(final ReadOnlyHttpServerConfig config, final Executor executor, final HttpService<HttpPayloadChunk, HttpPayloadChunk> service, final Channel channel, final ConnectionContext context) {
        channel.pipeline().addLast(new HttpRequestDecoder(config.getHeadersFactory(),
                config.getMaxInitialLineLength(), config.getMaxHeaderSize(), config.getMaxChunkSize(), true));
        channel.pipeline().addLast(new HttpResponseEncoder(config.getHeadersEncodedSizeEstimate(),
                config.getTrailersEncodedSizeEstimate()));
        channel.pipeline().addLast(new AbstractChannelReadHandler<Object>(LAST_HTTP_PAYLOAD_CHUNK_PREDICATE, executor) {
            @Override
            protected void onPublisherCreation(final ChannelHandlerContext channelHandlerContext,
                                               final Publisher<Object> requestObjectPublisher) {
                final Connection<Object, Object> conn = new NettyConnection<>(
                        channelHandlerContext.channel(),
                        context,
                        requestObjectPublisher,
                        new TerminalPredicate<>(LAST_HTTP_PAYLOAD_CHUNK_PREDICATE));
                final Publisher<Object> connRequestObjectPublisher = conn.read();
                final Single<HttpRequest<HttpPayloadChunk>> requestSingle = groupRequest(executor, connRequestObjectPublisher);
                final Single<HttpResponse<HttpPayloadChunk>> responseSingle = handleRequest(service, requestSingle, context);
                final Publisher<Object> responseObjectPublisher = ungroupResponse(executor, responseSingle);
                writeResponse(conn, responseObjectPublisher);
            }
        });
    }

    private static Single<HttpRequest<HttpPayloadChunk>> groupRequest(final Executor executor, final Publisher<Object> requestObjectPublisher) {
        return new SpliceFlatStreamToMetaSingle<HttpRequest<HttpPayloadChunk>, HttpRequestMetaData, HttpPayloadChunk>(executor, requestObjectPublisher,
                (hr, pub) -> newRequest(hr.getVersion(), hr.getMethod(), hr.getRequestTarget(), pub, hr.getHeaders()));
    }

    private static Publisher<Object> ungroupResponse(final Executor executor, final Single<HttpResponse<HttpPayloadChunk>> responseSingle) {
        final Publisher<Object> responseObjectPublisher = responseSingle.flatMapPublisher(resp -> SpliceFlatStreamToMetaSingle.flatten(executor, resp, HttpResponse::getPayloadBody));
        return responseObjectPublisher.liftSynchronous(new EnsureLastItemBeforeCompleteOperator<>(
                LAST_HTTP_PAYLOAD_CHUNK_PREDICATE,
                () -> EmptyLastHttpPayloadChunk.INSTANCE)).repeat(val -> true);
    }

    private static Single<HttpResponse<HttpPayloadChunk>> handleRequest(final HttpService<HttpPayloadChunk, HttpPayloadChunk> service, final Single<HttpRequest<HttpPayloadChunk>> requestSingle, final ConnectionContext context) {
        return requestSingle.flatMap(request -> {
            final HttpRequestMethod requestMethod = request.getMethod();
            try {
                return service.handle(context, request).map(response -> {
                    addResponseTransferEncodingIfNecessary(response, requestMethod);

                    // When the response payload publisher completes, read any of the request payload that hasn't already
                    // been read. This is necessary for using a persistent connection to send multiple requests.
                    return response.transformPayloadBody(responsePayload -> responsePayload.concatWith(request.getPayloadBody().ignoreElements().onErrorResume(
                            t -> Completable.completed()
                            /* ignore error from SpliceFlatStreamToMetaSingle about duplicate subscriptions. */)));
                });
            } catch (final Throwable cause) {
                LOGGER.error("internal server error service={} connection={}", service, context, cause);
                final HttpResponse<HttpPayloadChunk> response = newResponse(request.getVersion(), INTERNAL_SERVER_ERROR,
                        // Using immediate is OK here because the user will never touch this response and it will
                        // only be consumed by ServiceTalk at this point.
                        just(newLastPayloadChunk(EMPTY_BUFFER, INSTANCE), immediate()));
                response.getHeaders().set(CONTENT_LENGTH, ZERO);
                return success(response);
            }
        });
    }

    private static void writeResponse(final Connection<Object, Object> conn, final Publisher<Object> responseObjectPublisher) {
        conn.write(responseObjectPublisher, FlushStrategy.flushOnEach())
                .subscribe();
    }

    private static final class NettyHttpServerContext implements ServerContext {

        // Flag to avoid multiple subscribes to closeAsync
        private static final AtomicIntegerFieldUpdater<NettyHttpServerContext> closedUpdater =
                AtomicIntegerFieldUpdater.newUpdater(NettyHttpServerContext.class, "closed");

        @SuppressWarnings("unused")
        private volatile int closed;

        private final ServerContext delegate;
        private final CompletableProcessor onClose = new CompletableProcessor();
        private final Completable closeAsync;

        NettyHttpServerContext(final ServerContext delegate, final HttpService service, final Executor executor) {
            this.delegate = delegate;
            // TODO: To ensure ordering when closing, we should use Completable.concatDelayError here, once it exists.
            closeAsync = completed().mergeDelayError(
                    service.closeAsync(),
                    delegate.closeAsync(),
                    executor.closeAsync());
        }

        @Override
        public SocketAddress getListenAddress() {
            return delegate.getListenAddress();
        }

        @Override
        public Completable closeAsync() {
            return new Completable() {
                protected void handleSubscribe(final Subscriber subscriber) {
                    if (closedUpdater.compareAndSet(NettyHttpServerContext.this, 0, 1)) {
                        closeAsync.subscribe(onClose);
                    }
                    onClose.subscribe(subscriber);
                }
            };
        }

        @Override
        public Completable onClose() {
            return onClose;
        }
    }
}
