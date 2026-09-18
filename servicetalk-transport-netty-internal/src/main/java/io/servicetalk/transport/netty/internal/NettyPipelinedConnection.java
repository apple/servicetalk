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
package io.servicetalk.transport.netty.internal;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TerminalSignalConsumer;
import io.servicetalk.concurrent.api.internal.SubscribablePublisher;
import io.servicetalk.concurrent.internal.ConcurrentUtils;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.SslConfig;

import io.netty.channel.Channel;

import java.net.SocketAddress;
import java.net.SocketOption;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Processors.newCompletableProcessor;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
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
 * <p>
 * The write lock is a token rather than a synchronous critical section: it is claimed when a request is dequeued and
 * released only once that request's write terminates, so it spans threads and outlives the call that claimed it.
 * <p>
 * Each queued request is started from the completion of the one before it, so exchanges that complete synchronously
 * recurse rather than iterate and can exhaust the stack. Depth is whatever a caller keeps in flight, which this class
 * does not limit: {@code maxPipelinedRequests} only sizes the queue.
 * @param <Req> Type of requests sent on this connection.
 * @param <Resp> Type of responses read from this connection.
 */
public final class NettyPipelinedConnection<Req, Resp> implements NettyConnectionContext {
    @SuppressWarnings("rawtypes")
    private static final AtomicIntegerFieldUpdater<NettyPipelinedConnection> writeQueueLockUpdater =
            newUpdater(NettyPipelinedConnection.class, "writeQueueLock");
    private static final int MAX_INIT_QUEUE_SIZE = 8;
    private final NettyConnection<Resp, Req> connection;
    private final Queue<WriteTask> writeQueue;
    /** One instance serves every request: {@link Publisher#defer(Supplier)} re-invokes the supplier per subscribe. */
    private final Publisher<Resp> deferredRead;
    @SuppressWarnings("unused")
    private volatile int writeQueueLock;
    /**
     * Completes when the previously queued request's response terminated, which is what orders reads. Written and read
     * only from {@link WriteTask#run()}, which {@code writeQueueLock} serializes. The ordering that makes the value
     * visible across a hand-off comes from the transport publishing the subscribe rather than from anything here, so
     * this is {@code volatile} instead. A link must only ever be completed, never failed:
     * {@link Completable#concat(Publisher)} skips its {@link Publisher} on error, leaving that request's response
     * unread.
     */
    private volatile Completable previousResponseTerminated = completed();

    /**
     * New instance.
     *
     * @param connection {@link NettyConnection} requests to which are to be pipelined.
     * @param maxPipelinedRequests initial size hint for the pipelining queue; not enforced.
     */
    public NettyPipelinedConnection(final NettyConnection<Resp, Req> connection, int maxPipelinedRequests) {
        this.connection = requireNonNull(connection);
        writeQueue = newUnboundedMpscQueue(min(maxPipelinedRequests, MAX_INIT_QUEUE_SIZE));
        // Deferred so that a read which fails to set up cannot leave the composed request Publisher unsubscribed.
        deferredRead = Publisher.defer(() -> {
            try {
                return connection.read();
            } catch (Throwable cause) {
                return connection.closeAsync().concat(Publisher.<Resp>failed(cause));
            }
        });
    }

    /**
     * Do a write operation in a pipelined fashion.
     * @param requestPublisher {@link Publisher} representing the stream of data for a single "request".
     * impacts how many elements are requested from the {@code requestPublisher} depending upon channel writability.
     * @return Response {@link Publisher} for this request.
     */
    public Publisher<Resp> write(final Publisher<Req> requestPublisher) {
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
    public Publisher<Resp> write(final Publisher<Req> requestPublisher,
                          final Supplier<FlushStrategy> flushStrategySupplier,
                          final Supplier<WriteDemandEstimator> writeDemandEstimatorSupplier) {
        // Lazy modification of local state required (e.g. nodes, delayed subscriber, queue modifications)
        return new SubscribablePublisher<Resp>() {
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
    public String connectionId() {
        return connection.connectionId();
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
    public Cancellable updateFlushStrategy(final FlushStrategyProvider strategyProvider) {
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
            // Chain this response behind the previous one here, where writeQueueLock still serializes write tasks.
            // Ordering must not depend on when the merge below subscribes each read: a write that completes
            // synchronously re-enters run() for the next request first.
            final Completable responseTurn = previousResponseTerminated;
            final CompletableSource.Processor responseTerminated = newCompletableProcessor();
            previousResponseTerminated = fromSource(responseTerminated);

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
                        // The merge is only for error propagation; response ordering comes from responseTurn.
                        .mergeDelayError(responseTurn.concat(deferredRead)
                                .afterFinally(new ResponseTerminated(responseTurn, responseTerminated))));
            } catch (Throwable cause) {
                // Nothing will subscribe to the read above, so release the successor here, but only once this
                // exchange's turn arrives or it would read over a response that is still reading. Release before
                // failing: handleWriteSetupError subscribes the caller's Subscriber, and a throw from its onSubscribe
                // would skip the drain below.
                responseTurn.afterFinally(responseTerminated::onComplete).subscribe();
                handleWriteSetupError(subscriber, cause);
                return;
            }
            src.subscribe(subscriber);
        }
    }

    /**
     * Releases the next queued response once this one is done with the connection. Cancellation is not a completion:
     * the response may never have been read, so the connection is closed and the successor waits for that close.
     */
    private final class ResponseTerminated implements TerminalSignalConsumer {
        private final Completable responseTurn;
        private final CompletableSource.Processor responseTerminated;

        private ResponseTerminated(final Completable responseTurn,
                                   final CompletableSource.Processor responseTerminated) {
            this.responseTurn = responseTurn;
            this.responseTerminated = responseTerminated;
        }

        @Override
        public void onComplete() {
            responseTerminated.onComplete();
        }

        @Override
        public void onError(final Throwable throwable) {
            responseTerminated.onComplete();
        }

        @Override
        public void cancel() {
            // Wait for this turn before closing, so an earlier response that is still reading finishes rather than
            // being torn down, then close before releasing the successor, or it would read the bytes this exchange
            // abandoned.
            responseTurn.concat(connection.closeAsync())
                    .afterFinally(responseTerminated::onComplete)
                    .subscribe();
        }
    }

    // Must own the write lock, which here holds because the write was never subscribed, so its afterFinally cannot
    // be competing for it.
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
