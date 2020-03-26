/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompletableOperator;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.PublisherOperator;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.netty.MpmcSequentialRunQueue.Node;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.netty.internal.FlushStrategy;
import io.servicetalk.transport.netty.internal.NettyConnection;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;
import io.servicetalk.transport.netty.internal.WriteDemandEstimator;
import io.servicetalk.transport.netty.internal.WriteDemandEstimators;

import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.net.SocketOption;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static java.util.Objects.requireNonNull;

/**
 * Contract for using a {@link NettyConnection} to make pipelined requests, typically for a client.
 * <p>
 * Pipelining allows to have concurrent requests processed on the server but still deliver responses in order.
 * This eliminates the need for request-response correlation, at the cost of head-of-line blocking.
 * @param <Req> Type of requests sent on this connection.
 * @param <Resp> Type of responses read from this connection.
 */
final class NettyPipelinedConnection<Req, Resp> implements NettyConnectionContext {
    private static final Logger LOGGER = LoggerFactory.getLogger(NettyPipelinedConnection.class);

    private final NettyConnection<Resp, Req> connection;
    private final MpmcSequentialRunQueue writeQueue;
    private final MpmcSequentialRunQueue readQueue;

    /**
     * New instance.
     *
     * @param connection {@link NettyConnection} requests to which are to be pipelined.
     */
    NettyPipelinedConnection(NettyConnection<Resp, Req> connection) {
        this.connection = requireNonNull(connection);
        writeQueue = new MpmcSequentialRunQueue();
        readQueue = new MpmcSequentialRunQueue();
    }

    /**
     * Do a write operation in a pipelined fashion.
     * @param requestPublisher {@link Publisher} representing the stream of data for a single "request".
     * impacts how many elements are requested from the {@code requestPublisher} depending upon channel writability.
     * @return Response {@link Publisher} for this request.
     */
    Publisher<Resp> write(final Publisher<Req> requestPublisher) {
        return write(requestPublisher, connection::defaultFlushStrategy, WriteDemandEstimators::newDefaultEstimator);
    }

    /**
     * Do a write operation in a pipelined fashion.
     * @param requestPublisher {@link Publisher} representing the stream of data for a single "request".
     * @param flushStrategySupplier The {@link FlushStrategy} to use for this write operation.
     * @param writeDemandEstimatorSupplier A {@link Supplier} of {@link WriteDemandEstimator} for this request which
     * impacts how many elements are requested from the {@code requestPublisher} depending upon channel writability.
     * @return Response {@link Publisher} for this request.
     */
    Publisher<Resp> write(final Publisher<Req> requestPublisher,
                          final Supplier<FlushStrategy> flushStrategySupplier,
                          final Supplier<WriteDemandEstimator> writeDemandEstimatorSupplier) {
        return Publisher.defer(() -> {
            // Lazy modification of local state required (e.g. nodes, delayed subscriber, queue modifications)

            // Setup read side publisher and nodes
            DelayedSubscribePublisher<Resp> delayedReadPublisher = new DelayedSubscribePublisher<>(
                    toSource(connection.read()));
            Node readNode = new Node() {
                @Override
                public void run() {
                    try {
                        delayedReadPublisher.processSubscribers();
                    } catch (Throwable cause) {
                        connection.closeAsync().subscribe();
                        LOGGER.warn("closing connection={} due to unexpected error subscribing to read publisher={}",
                                connection, delayedReadPublisher, cause);
                    }
                }
            };
            Publisher<Resp> composedReadPublisher = delayedReadPublisher.liftSync(new ReadPopNextOperator(readNode));

            // Setup write side publisher and nodes
            DelayedSubscribeCompletable delayedWriteCompletable = new DelayedSubscribeCompletable(toSource(
                            connection.write(requestPublisher, flushStrategySupplier, writeDemandEstimatorSupplier)));
            Node writeNode = new Node() {
                @Override
                public void run() {
                    try {
                        try {
                            readQueue.offer(readNode);
                        } catch (Throwable cause) {
                            delayedWriteCompletable.failSubscribers(cause);
                            throw cause;
                        }

                        delayedWriteCompletable.processSubscribers();
                    } catch (Throwable cause) {
                        connection.closeAsync().subscribe();
                        LOGGER.warn(
                                "closing connection={} due to unexpected error offering to readQueue or subscribing " +
                                "to write publisher={}", connection, delayedWriteCompletable, cause);
                    }
                }
            };

            writeQueue.offer(writeNode);

            return delayedWriteCompletable.liftSync(new WritePopNextOperator(writeNode))
                    // If there is an error on the read/write side we propagate the errors between the two via merge.
                    .merge(composedReadPublisher);
        });
    }

    @Override
    public SocketAddress localAddress() {
        return connection.localAddress();
    }

    @Override
    public SocketAddress remoteAddress() {
        return connection.remoteAddress();
    }

    @Override
    @Nullable
    public SSLSession sslSession() {
        return connection.sslSession();
    }

    @Override
    public ExecutionContext executionContext() {
        return connection.executionContext();
    }

    @Nullable
    @Override
    public <T> T socketOption(final SocketOption<T> option) {
        return connection.socketOption(option);
    }

    @Override
    public Protocol protocol() {
        return connection.protocol();
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
        return getClass().getName() + '(' + connection + ')';
    }

    @Override
    public Cancellable updateFlushStrategy(final NettyConnectionContext.FlushStrategyProvider strategyProvider) {
        return connection.updateFlushStrategy(strategyProvider);
    }

    @Override
    public FlushStrategy defaultFlushStrategy() {
        return connection.defaultFlushStrategy();
    }

    /**
     * Logically equivalent to {@link Publisher#afterFinally(Runnable)} but relies upon
     * {@link MpmcSequentialRunQueue#pop(Node)} CAS operations to prevent multiple executions (e.g. reduces a CAS
     * operation).
     */
    private final class ReadPopNextOperator implements PublisherOperator<Resp, Resp> {
        private final Node readNode;

        private ReadPopNextOperator(final Node readNode) {
            this.readNode = readNode;
        }

        @Override
        public PublisherSource.Subscriber<? super Resp> apply(PublisherSource.Subscriber<? super Resp> subscriber) {
            return new PublisherSource.Subscriber<Resp>() {
                @Override
                public void onSubscribe(final PublisherSource.Subscription subscription) {
                    subscriber.onSubscribe(new PublisherSource.Subscription() {
                        @Override
                        public void request(final long n) {
                            subscription.request(n);
                        }

                        @Override
                        public void cancel() {
                            try {
                                subscription.cancel();
                            } finally {
                                pollNext();
                            }
                        }
                    });
                }

                @Override
                public void onNext(@Nullable final Resp t) {
                    subscriber.onNext(t);
                }

                @Override
                public void onError(final Throwable t) {
                    try {
                        subscriber.onError(t);
                    } finally {
                        pollNext();
                    }
                }

                @Override
                public void onComplete() {
                    try {
                        subscriber.onComplete();
                    } finally {
                        pollNext();
                    }
                }

                private void pollNext() {
                    readQueue.pop(readNode);
                }
            };
        }
    }

    /**
     * Logically equivalent to {@link Completable#afterFinally(Runnable)} but relies upon
     * {@link MpmcSequentialRunQueue#pop(Node)} CAS operations to prevent multiple executions (e.g. reduces a CAS
     * operation).
     */
    private final class WritePopNextOperator implements CompletableOperator {
        private final Node writeNode;

        WritePopNextOperator(final Node writeNode) {
            this.writeNode = writeNode;
        }

        @Override
        public CompletableSource.Subscriber apply(final CompletableSource.Subscriber subscriber) {
            return new CompletableSource.Subscriber() {
                @Override
                public void onSubscribe(final Cancellable cancellable) {
                    subscriber.onSubscribe(() -> {
                        try {
                            cancellable.cancel();
                        } finally {
                            pollNext();
                        }
                    });
                }

                @Override
                public void onComplete() {
                    try {
                        subscriber.onComplete();
                    } finally {
                        pollNext();
                    }
                }

                @Override
                public void onError(final Throwable t) {
                    try {
                        subscriber.onError(t);
                    } finally {
                        pollNext();
                    }
                }
            };
        }

        private void pollNext() {
            writeQueue.pop(writeNode);
        }
    }
}
