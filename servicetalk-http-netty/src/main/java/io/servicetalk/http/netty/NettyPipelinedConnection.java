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
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ConcurrentUtils;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.SslConfig;
import io.servicetalk.transport.netty.internal.FlushStrategy;
import io.servicetalk.transport.netty.internal.NettyConnection;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;
import io.servicetalk.transport.netty.internal.WriteDemandEstimator;
import io.servicetalk.transport.netty.internal.WriteDemandEstimators;

import io.netty.channel.Channel;

import java.net.SocketAddress;
import java.net.SocketOption;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.releaseLock;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.tryAcquireLock;
import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverErrorFromSource;
import static io.servicetalk.utils.internal.PlatformDependent.newUnboundedMpscQueue;
import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

/**
 * Contract for using a {@link NettyConnection} to make pipelined requests, typically for a client.
 * <p>
 * Pipelining allows to have concurrent requests processed on the server but still deliver responses in order.
 * This eliminates the need for request-response correlation, at the cost of head-of-line blocking.
 * @param <Req> Type of requests sent on this connection.
 * @param <Resp> Type of responses read from this connection.
 */
final class NettyPipelinedConnection<Req, Resp> implements NettyConnectionContext {
    @SuppressWarnings("rawtypes")
    private static final AtomicIntegerFieldUpdater<NettyPipelinedConnection> writeQueueLockUpdater =
            newUpdater(NettyPipelinedConnection.class, "writeQueueLock");
    @SuppressWarnings("rawtypes")
    private static final AtomicIntegerFieldUpdater<NettyPipelinedConnection> readQueueLockUpdater =
            newUpdater(NettyPipelinedConnection.class, "readQueueLock");
    private static final int MAX_INIT_QUEUE_SIZE = 8;
    private final NettyConnection<Resp, Req> connection;
    private final Queue<WriteTask> writeQueue;
    private final Queue<Subscriber<? super Resp>> readQueue;
    @SuppressWarnings("unused")
    private volatile int writeQueueLock;
    @SuppressWarnings("unused")
    private volatile int readQueueLock;

    /**
     * New instance.
     *
     * @param connection {@link NettyConnection} requests to which are to be pipelined.
     * @param maxPipelinedRequests The maximum number of pipelined requests.
     */
    NettyPipelinedConnection(NettyConnection<Resp, Req> connection,
                             int maxPipelinedRequests) {
        this.connection = requireNonNull(connection);
        writeQueue = newUnboundedMpscQueue(min(maxPipelinedRequests, MAX_INIT_QUEUE_SIZE));
        readQueue = newUnboundedMpscQueue(min(maxPipelinedRequests, MAX_INIT_QUEUE_SIZE));
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
        // Lazy modification of local state required (e.g. nodes, delayed subscriber, queue modifications)
        return new Publisher<Resp>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super Resp> subscriber) {
                final WriteTask nextWriteTask;
                try {
                    nextWriteTask = addAndTryPoll(writeQueue, writeQueueLockUpdater,
                            new WriteTask(subscriber, requestPublisher, flushStrategySupplier,
                                    writeDemandEstimatorSupplier));
                } catch (Throwable cause) {
                    closeConnection(subscriber, cause);
                    return;
                }

                if (nextWriteTask != null) {
                    nextWriteTask.run();
                }
            }
        };
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

    @Override
    @Nullable
    public SSLSession sslSession() {
        return connection.sslSession();
    }

    @Override
    public ExecutionContext<?> executionContext() {
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

    @Nullable
    @Override
    public ConnectionContext parent() {
        return connection.parent();
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
        return connection.toString();
    }

    @Override
    public Cancellable updateFlushStrategy(final NettyConnectionContext.FlushStrategyProvider strategyProvider) {
        return connection.updateFlushStrategy(strategyProvider);
    }

    @Override
    public FlushStrategy defaultFlushStrategy() {
        return connection.defaultFlushStrategy();
    }

    private void closeConnection(final Subscriber<? super Resp> subscriber, final Throwable cause) {
        toSource(connection.closeAsync().concat(Publisher.<Resp>failed(cause))).subscribe(subscriber);
    }

    private final class WriteTask {
        private final Subscriber<? super Resp> subscriber;
        private final Publisher<Req> requestPublisher;
        private final Supplier<FlushStrategy> flushStrategySupplier;
        private final Supplier<WriteDemandEstimator> writeDemandEstimatorSupplier;

        private WriteTask(final Subscriber<? super Resp> subscriber,
                          final Publisher<Req> requestPublisher,
                          final Supplier<FlushStrategy> flushStrategySupplier,
                          final Supplier<WriteDemandEstimator> writeDemandEstimatorSupplier) {
            this.subscriber = subscriber;
            this.requestPublisher = requestPublisher;
            this.flushStrategySupplier = flushStrategySupplier;
            this.writeDemandEstimatorSupplier = writeDemandEstimatorSupplier;
        }

        void run() {
            final PublisherSource<Resp> src;
            try {
                src = toSource(connection.write(requestPublisher, flushStrategySupplier,
                        writeDemandEstimatorSupplier)
                        .afterFinally(() -> {
                            WriteTask nextWriteTask = pollWithLockAcquired(writeQueue, writeQueueLockUpdater);
                            if (nextWriteTask != null) {
                                nextWriteTask.run();
                            }
                        })
                        // The write and read operation are coupled via a merge operator. This is because if an error
                        // occurs on write or read we want to propagate the error back to the user. On the client side
                        // the most straightforward way to propagate an error through the APIs is through the read async
                        // source. This has a side effect that the read async source isn't strictly full-duplex (data
                        // will be full-duplex, but completion will be delayed until the write completes).
                        .mergeDelayError(new Publisher<Resp>() {
                            @Override
                            protected void handleSubscribe(final Subscriber<? super Resp> rSubscriber) {
                                final Subscriber<? super Resp> nextReadSubscriber;
                                try {
                                    nextReadSubscriber =
                                            addAndTryPoll(readQueue, readQueueLockUpdater, rSubscriber);
                                } catch (Throwable cause) {
                                    closeConnection(rSubscriber, cause);
                                    return;
                                }

                                tryStartRead(nextReadSubscriber);
                            }
                        }));
            } catch (Throwable cause) {
                handleWriteSetupError(subscriber, cause);
                return;
            }
            src.subscribe(subscriber);
        }

        private void tryStartRead(@Nullable Subscriber<? super Resp> subscriber) {
            if (subscriber == null) {
                return;
            }
            final PublisherSource<Resp> src;
            try {
                src = toSource(connection.read().afterFinally(() ->
                        tryStartRead(pollWithLockAcquired(readQueue, readQueueLockUpdater)))
                );
            } catch (Throwable cause) {
                handleReadSetupError(subscriber, cause);
                return;
            }
            src.subscribe(subscriber);
        }
    }

    private void handleWriteSetupError(Subscriber<? super Resp> subscriber, Throwable cause) {
        closeConnection(subscriber, cause);

        // the lock has been acquired!
        do {
            WriteTask nextWriteTask;
            while ((nextWriteTask = writeQueue.poll()) != null) {
                deliverErrorFromSource(nextWriteTask.subscriber, cause);
            }
        } while (!releaseLock(writeQueueLockUpdater, this) && tryAcquireLock(writeQueueLockUpdater, this));
    }

    private void handleReadSetupError(Subscriber<? super Resp> subscriber, Throwable cause) {
        closeConnection(subscriber, cause);

        // the lock has been acquired!
        do {
            Subscriber<? super Resp> nextSubscriber;
            while ((nextSubscriber = readQueue.poll()) != null) {
                deliverErrorFromSource(nextSubscriber, cause);
            }
        } while (!releaseLock(readQueueLockUpdater, this) && tryAcquireLock(readQueueLockUpdater, this));
    }

    /**
     * Offer {@code item} to the queue, try to acquire the processing lock, and if successful return an item for
     * single-consumer style processing. If non-{@code null} is returned the caller is responsible for releasing
     * the lock!
     * @param queue The {@link Queue#offer(Object)} and {@link Queue#poll()} (assuming lock was acquired).
     * @param lockUpdater Used to acquire the lock via
     * {@link ConcurrentUtils#tryAcquireLock(AtomicIntegerFieldUpdater, Object)}.
     * @param item The item to {@link Queue#offer(Object)}.
     * @param <T> The type of item in the {@link Queue}.
     * @return {@code null} if the queue was empty, or the lock couldn't be acquired. otherwise the lock has been
     * acquired and it is the caller's responsibility to release!
     */
    @Nullable
    private <T> T addAndTryPoll(final Queue<T> queue,
        @SuppressWarnings("rawtypes") final AtomicIntegerFieldUpdater<NettyPipelinedConnection> lockUpdater, T item) {
        queue.add(item);
        while (tryAcquireLock(lockUpdater, this)) {
            // exceptions are not expected from poll, and if they occur we can't reliably recover which would involve
            // draining the queue. just throw with the lock poisoned, callers will propagate the exception to related
            // subscriber and close the connection.
            final T next = queue.poll();
            if (next != null) {
                return next; // lock must be released when the returned task completes!
            } else if (releaseLock(lockUpdater, this)) {
                return null;
            }
        }
        return null;
    }

    /**
     * Poll the {@code queue} and attempt to process an item. The lock must be acquired on entry into this method and
     * if this method return non-{@code null} the lock will not be released (caller's responsibility to later release)
     * to continue the single-consumer style processing.
     * @param queue The queue to {@link Queue#poll()}.
     * @param lockUpdater Used to release via
     * {@link ConcurrentUtils#releaseLock(AtomicIntegerFieldUpdater, Object)} if the queue is empty
     * @param <T> The type of item in the {@link Queue}.
     * @return {@code null} if the queue was empty. otherwise the lock remains acquired and it is the caller's
     * responsibility to release (via subsequent calls to this method).
     */
    @Nullable
    private <T> T pollWithLockAcquired(final Queue<T> queue,
               @SuppressWarnings("rawtypes") final AtomicIntegerFieldUpdater<NettyPipelinedConnection> lockUpdater) {
        // the lock has been acquired!
        try {
            do {
                final T next = queue.poll();
                if (next != null) {
                    return next; // lock must be released when the returned task completes!
                } else if (releaseLock(lockUpdater, this)) {
                    return null;
                }
            } while (tryAcquireLock(lockUpdater, this));

            return null;
        } catch (Throwable cause) {
            // exceptions are not expected from poll, and if they occur we can't reliably recover which would involve
            // draining the queue. just throw with the lock poisoned and close the connection.
            connection.closeAsync().subscribe();
            throw cause;
        }
    }
}
