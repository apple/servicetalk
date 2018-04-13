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
package io.servicetalk.transport.netty.internal;

import io.servicetalk.buffer.BufferAllocator;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.QueueFullException;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.FlushStrategy;
import io.servicetalk.transport.api.IoExecutor;

import org.reactivestreams.Subscriber;

import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

/**
 * Implementation of {@link PipelinedConnection} using a {@link Connection}.
 *
 * @param <Req> Type of requests sent on this connection.
 * @param <Resp> Type of responses read from this connection.
 */
public final class DefaultPipelinedConnection<Req, Resp> implements PipelinedConnection<Req, Resp> {

    private static final AtomicIntegerFieldUpdater<DefaultPipelinedConnection> pendingRequestsCountUpdater =
            newUpdater(DefaultPipelinedConnection.class, "pendingRequestsCount");

    private final Connection<Resp, Req> connection;
    private final Connection.TerminalPredicate<Resp> terminalMsgPredicate;
    private final CompletableQueue writeQueue;
    private final CompletableQueue readQueue;
    private final int maxPendingRequests;

    @SuppressWarnings("unused")
    private volatile int pendingRequestsCount;

    /**
     * New instance.
     *
     * @param connection {@link Connection} requests to which are to be pipelined.
     * @param maxPendingRequests Max requests that can be pending waiting for a response.
     */
    public DefaultPipelinedConnection(Connection<Resp, Req> connection, int maxPendingRequests) {
        this(connection, maxPendingRequests, 2);
    }

    /**
     * New instance.
     *
     * @param connection {@link Connection} requests to which are to be pipelined.
     * @param maxPendingRequests Max requests that can be pending waiting for a response.
     * @param initialQueueSize Initial size for the write and read queues.
     */
    public DefaultPipelinedConnection(Connection<Resp, Req> connection, int maxPendingRequests, int initialQueueSize) {
        this.connection = requireNonNull(connection);
        this.terminalMsgPredicate = connection.getTerminalMsgPredicate();
        writeQueue = new CompletableQueue(initialQueueSize);
        readQueue = new CompletableQueue(initialQueueSize);
        this.maxPendingRequests = maxPendingRequests;
    }

    @Override
    public Publisher<Resp> request(Req request) {
        return writeOrQueue(connection.writeAndFlush(request), null);
    }

    @Override
    public Publisher<Resp> request(Writer writer) {
        return requestWithWriter(writer, null);
    }

    @Override
    public Publisher<Resp> request(Writer writer, Supplier<Predicate<Resp>> terminalMsgPredicateSupplier) {
        return requestWithWriter(writer, terminalMsgPredicateSupplier);
    }

    @Override
    public Publisher<Resp> request(Req request, Supplier<Predicate<Resp>> terminalMsgPredicateSupplier) {
        return writeOrQueue(connection.writeAndFlush(request), terminalMsgPredicateSupplier);
    }

    @Override
    public Publisher<Resp> request(Single<Req> request) {
        return writeOrQueue(connection.writeAndFlush(request), null);
    }

    @Override
    public Publisher<Resp> request(Single<Req> request, Supplier<Predicate<Resp>> terminalMsgPredicateSupplier) {
        return writeOrQueue(connection.writeAndFlush(request), terminalMsgPredicateSupplier);
    }

    @Override
    public Publisher<Resp> request(Publisher<Req> request, FlushStrategy flushStrategy) {
        return writeOrQueue(connection.write(request, flushStrategy), null);
    }

    @Override
    public Publisher<Resp> request(Publisher<Req> request, Supplier<Predicate<Resp>> terminalMsgPredicateSupplier, FlushStrategy flushStrategy) {
        return writeOrQueue(connection.write(request, flushStrategy), terminalMsgPredicateSupplier);
    }

    @Override
    public Publisher<Resp> request(Publisher<Req> request, FlushStrategy flushStrategy, Supplier<Connection.RequestNSupplier> requestNSupplierFactory) {
        return writeOrQueue(connection.write(request, flushStrategy, requestNSupplierFactory), null);
    }

    @Override
    public Publisher<Resp> request(Publisher<Req> request, FlushStrategy flushStrategy, Supplier<Connection.RequestNSupplier> requestNSupplierFactory,
                                   Supplier<Predicate<Resp>> terminalMsgPredicateSupplier) {
        return writeOrQueue(connection.write(request, flushStrategy, requestNSupplierFactory), terminalMsgPredicateSupplier);
    }

    private Publisher<Resp> requestWithWriter(Writer writer, @Nullable Supplier<Predicate<Resp>> terminalMsgPredicateSupplier) {
        return writeOrQueue(new Completable() {
            @Override
            protected void handleSubscribe(Subscriber subscriber) {
                writer.write().subscribe(subscriber);
            }
        }, terminalMsgPredicateSupplier);
    }

    private Publisher<Resp> writeOrQueue(Completable completable, @Nullable Supplier<Predicate<Resp>> terminalMsgPredicateSupplier) {
        return new Publisher<Resp>() {
            @Override
            protected void handleSubscribe(Subscriber<? super Resp> subscriber) {
                writeOrQueueRequest(completable, terminalMsgPredicateSupplier == null ? null : terminalMsgPredicateSupplier.get())
                        .subscribe(subscriber);
            }
        };
    }

    private Publisher<Resp> writeOrQueueRequest(Completable completable, @Nullable Predicate<Resp> terminalMsgPredicate) {

        Completable ensureMaxRequests = new Completable() {
            @Override
            protected void handleSubscribe(Subscriber subscriber) {
                subscriber.onSubscribe(IGNORE_CANCEL);
                for (;;) {
                    final int current = pendingRequestsCount;
                    if (current == maxPendingRequests) {
                        subscriber.onError(new QueueFullException("pipelined-requests", maxPendingRequests));
                        return;
                    }
                    if (pendingRequestsCountUpdater.compareAndSet(
                            DefaultPipelinedConnection.this, current, current + 1)) {
                        break;
                    }
                }
                subscriber.onComplete();
            }
        };

        if (terminalMsgPredicate == null) {
            return ensureMaxRequests.andThen(
                    queueWriteAndRead(completable)
                            // Cleanup needs to happen before firing terminal events
                            .doBeforeFinally(this::decrementRequestCount));
        }

        Runnable requestFinished = () -> {
            pendingRequestsCountUpdater.decrementAndGet(this);
            this.terminalMsgPredicate.discardIfCurrent(terminalMsgPredicate);
        };
        return ensureMaxRequests.andThen(
                queueWriteAndRead(completable)
                        // Cleanup needs to happen before firing terminal events
                        .doBeforeFinally(requestFinished));
    }

    private void decrementRequestCount() {
        pendingRequestsCountUpdater.decrementAndGet(this);
    }

    private Publisher<Resp> queueWriteAndRead(Completable write) {
        // doAfterFinally() x2 used here to satisfy the current unit tests, but in practice doBeforeFinally() works too.
        // Further investigation is needed to define the strategy when a read is canceled. Currently the pipeline code
        // here assumes the underlying NettyChannelPublisher to drop te connection on cancel. This should be expressed
        // in a tighter contract between Connection and PipelinedConnection. Alternative strategies may include
        // retaining the connection and simply draining and discarding the remaining elements in the stream before
        // subscribing the reader of the next request.
        return writeQueue.offerAndExecuteCompletable()
                .andThen(write.doAfterFinally(writeQueue::postTaskTermination))
                .merge(readQueue.offerAndExecuteCompletable()
                        .andThen(connection.read().doAfterFinally(readQueue::postTaskTermination)));
    }

    private static final class CompletableQueue extends SequentialTaskQueue<Completable.Subscriber> {

        private CompletableQueue(int initialCapacity) {
            // Queues are unbounded since max capacity has to be enforced across these two queues
            // i.e. requests queued for write + responses not completed must not exceed maxPendingRequests.
            super(initialCapacity, UNBOUNDED);
        }

        private Completable offerAndExecuteCompletable() {
            return new Completable() {
                @Override
                protected void handleSubscribe(Subscriber subscriber) {
                    subscriber.onSubscribe(Cancellable.IGNORE_CANCEL);
                    if (!offerAndTryExecute(subscriber)) {
                        subscriber.onError(new IllegalStateException("Failed to enqueue in unbounded Task Queue"));
                    }
                }
            };
        }

        @Override
        protected void execute(Completable.Subscriber subscriber) {
            subscriber.onComplete();
        }
    }

    /**
     * Visible for Unit testing.
     * @return number of pending requests
     */
    int getPendingRequestsCount() {
        return pendingRequestsCount;
    }

    @Override
    public SocketAddress getLocalAddress() {
        return connection.getLocalAddress();
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return connection.getRemoteAddress();
    }

    @Override
    public BufferAllocator getAllocator() {
        return connection.getAllocator();
    }

    @Override
    @Nullable
    public SSLSession getSslSession() {
        return connection.getSslSession();
    }

    @Override
    public IoExecutor getIoExecutor() {
        return connection.getIoExecutor();
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
    public String toString() {
        return DefaultPipelinedConnection.class.getSimpleName() + "(" + connection + ")";
    }
}
