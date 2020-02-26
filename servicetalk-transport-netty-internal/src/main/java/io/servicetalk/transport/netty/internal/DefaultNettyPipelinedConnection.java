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
package io.servicetalk.transport.netty.internal;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.SubscribableCompletable;
import io.servicetalk.concurrent.internal.QueueFullAndRejectedSubscribeException;
import io.servicetalk.concurrent.internal.QueueFullException;
import io.servicetalk.concurrent.internal.SequentialCancellable;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.netty.internal.NettyConnection.RequestNSupplier;

import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.net.SocketOption;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

/**
 * Implementation of {@link NettyPipelinedConnection} using a {@link NettyConnection}.
 *
 * @param <Req> Type of requests sent on this connection.
 * @param <Resp> Type of responses read from this connection.
 */
public final class DefaultNettyPipelinedConnection<Req, Resp> implements NettyPipelinedConnection<Req, Resp> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultNettyPipelinedConnection.class);

    private final NettyConnection<Resp, Req> connection;
    private final NettyConnection.TerminalPredicate<Resp> terminalMsgPredicate;
    private final WriteQueue<Resp> writeQueue;

    /**
     * New instance.
     *
     * @param connection {@link NettyConnection} requests to which are to be pipelined.
     * @param initialQueueSize Initial size for the write and read queues.
     */
    public DefaultNettyPipelinedConnection(NettyConnection<Resp, Req> connection, int initialQueueSize) {
        this.connection = requireNonNull(connection);
        this.terminalMsgPredicate = connection.terminalMsgPredicate();
        writeQueue = new WriteQueue<>(terminalMsgPredicate, initialQueueSize);
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
    public Publisher<Resp> request(Publisher<Req> request) {
        return writeOrQueue(connection.write(request), null);
    }

    @Override
    public Publisher<Resp> request(Supplier<Predicate<Resp>> terminalMsgPredicateSupplier, Publisher<Req> request) {
        return writeOrQueue(connection.write(request), terminalMsgPredicateSupplier);
    }

    @Override
    public Publisher<Resp> request(Publisher<Req> request, Supplier<RequestNSupplier> requestNSupplierFactory) {
        return writeOrQueue(connection.write(request, requestNSupplierFactory), null);
    }

    @Override
    public Publisher<Resp> request(Publisher<Req> request, Supplier<RequestNSupplier> requestNSupplierFactory,
                                   Supplier<Predicate<Resp>> terminalMsgPredicateSupplier) {
        return writeOrQueue(connection.write(request, requestNSupplierFactory), terminalMsgPredicateSupplier);
    }

    private Publisher<Resp> requestWithWriter(Writer writer,
                                              @Nullable Supplier<Predicate<Resp>> terminalMsgPredicateSupplier) {
        return writeOrQueue(Completable.defer(() -> writer.write().subscribeShareContext()),
                terminalMsgPredicateSupplier);
    }

    private Publisher<Resp> writeOrQueue(Completable completable,
                                         @Nullable Supplier<Predicate<Resp>> terminalMsgPredicateSupplier) {
        return Publisher.defer(() -> writeOrQueueRequest(completable, terminalMsgPredicateSupplier == null ?
                null : terminalMsgPredicateSupplier.get()).subscribeShareContext());
    }

    private Publisher<Resp> writeOrQueueRequest(Completable completable,
                                                @Nullable Predicate<Resp> terminalMsgPredicate) {
        return new SubscribableCompletable() {
            @Override
            protected void handleSubscribe(Subscriber subscriber) {
                Task<Resp> task = new Task<>(completable, subscriber, terminalMsgPredicate);
                subscriber.onSubscribe(task);
                if (!writeQueue.offerAndTryExecute(task)) {
                    task.cancel();
                    subscriber.onError(new QueueFullAndRejectedSubscribeException("pending requests"));
                }
            }
        }.concat(
                connection.read()
                        // Below is related to read stream: terminal predicate and responseQueue
                        // We should only trigger this on the read stream signals. Attaching them to the write+read
                        // stream will eagerly start the subsequent read (writeQueue.responseQueue::postTaskTermination)
                        // on cancellation.
                        .beforeFinally(() -> {
                            if (terminalMsgPredicate != null) {
                                this.terminalMsgPredicate.discardIfCurrent(terminalMsgPredicate);
                            }
                        })
                        .afterFinally(writeQueue.responseQueue::postTaskTermination)
        );
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
    public Cancellable updateFlushStrategy(final FlushStrategyProvider strategyProvider) {
        return connection.updateFlushStrategy(strategyProvider);
    }

    @Override
    public FlushStrategy defaultFlushStrategy() {
        return connection.defaultFlushStrategy();
    }

    private static final class WriteQueue<Resp> extends SequentialTaskQueue<Task<Resp>> {

        private final ResponseQueue<Resp> responseQueue;

        WriteQueue(NettyConnection.TerminalPredicate<Resp> terminalMsgPredicate, int initialQueueSize) {
            // Queues are unbounded since max capacity has to be enforced across these two queues
            // i.e. requests queued for write + responses not completed must not exceed maxPendingRequests.
            super(initialQueueSize, UNBOUNDED);
            responseQueue = new ResponseQueue<>(terminalMsgPredicate, initialQueueSize);
        }

        @Override
        protected void execute(Task<Resp> requestTask) {
            requestTask.write.subscribe(new WriteSourceSubscriber<>(requestTask, this));
        }
    }

    private static final class ResponseQueue<Resp> extends SequentialTaskQueue<Task<Resp>> {

        private final NettyConnection.TerminalPredicate<Resp> terminalMsgPredicate;

        ResponseQueue(NettyConnection.TerminalPredicate<Resp> terminalMsgPredicate, int initialQueueSize) {
            // Queues are unbounded since max capacity has to be enforced across these two queues
            // i.e. requests queued for write + responses not completed must not exceed maxPendingRequests.
            super(initialQueueSize, UNBOUNDED);
            this.terminalMsgPredicate = terminalMsgPredicate;
        }

        @Override
        protected void execute(Task<Resp> toExecute) {
            final Predicate<Resp> predicate = toExecute.terminalMsgPredicate;
            if (predicate != null) {
                terminalMsgPredicate.replaceCurrent(predicate);
            }
            // Trigger subscription to the read Publisher. postTaskTermination will be called when response stream
            // completes.
            toExecute.readReadyListener.onComplete();
        }
    }

    private static final class Task<Resp> extends SequentialCancellable {

        final CompletableSource write;
        final CompletableSource.Subscriber readReadyListener;
        @Nullable
        final Predicate<Resp> terminalMsgPredicate;

        Task(Completable write, CompletableSource.Subscriber readReadyListener,
             @Nullable Predicate<Resp> terminalMsgPredicate) {
            this.write = toSource(write);
            this.readReadyListener = requireNonNull(readReadyListener);
            this.terminalMsgPredicate = terminalMsgPredicate;
        }
    }

    private static final class WriteSourceSubscriber<Resp> implements CompletableSource.Subscriber {

        private static final AtomicIntegerFieldUpdater<WriteSourceSubscriber> postTaskTerminationCalledUpdater =
                newUpdater(WriteSourceSubscriber.class, "postTaskTerminationCalled");
        private final Task<Resp> requestTask;
        private final WriteQueue<Resp> writeQueue;

        @SuppressWarnings("unused")
        private volatile int postTaskTerminationCalled;

        WriteSourceSubscriber(Task<Resp> requestTask, WriteQueue<Resp> writeQueue) {
            this.requestTask = requestTask;
            this.writeQueue = writeQueue;
        }

        @Override
        public void onSubscribe(Cancellable cancellable) {
            requestTask.nextCancellable(() -> {
                cancellable.cancel();
                safePostTaskTermination();
            });
        }

        @Override
        public void onComplete() {
            // Write completed successfully, enqueue response listener and execute response before
            // writing further requests.
            final boolean offered;
            try {
                offered = writeQueue.responseQueue.offerAndTryExecute(requestTask);
            } catch (Throwable cause) {
                requestTask.readReadyListener.onError(cause);
                throw cause;
            } finally {
                safePostTaskTermination();
            }

            if (!offered) {
                onError0(new QueueFullException("response listener"));
            }
        }

        @Override
        public void onError(Throwable t) {
            onError0(t);
        }

        private void onError0(Throwable t) {
            try {
                requestTask.readReadyListener.onError(t);
            } finally {
                safePostTaskTermination();
            }
        }

        private void safePostTaskTermination() {
            if (!postTaskTerminationCalledUpdater.compareAndSet(this, 0, 1)) {
                return;
            }
            try {
                // Since this method may throw if a task is executed synchronously and it fails,
                // putting this in finally may hide the exception.
                writeQueue.postTaskTermination();
            } catch (Throwable t) {
                LOGGER.error("Unexpected failure cleaning up task, post termination.", t);
            }
        }
    }
}
