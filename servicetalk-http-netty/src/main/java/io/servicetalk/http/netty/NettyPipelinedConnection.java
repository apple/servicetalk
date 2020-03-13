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
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.netty.internal.FlushStrategy;
import io.servicetalk.transport.netty.internal.NettyConnection;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;
import io.servicetalk.transport.netty.internal.WriteDemandEstimator;

import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.net.SocketOption;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

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
    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<NettyPipelinedConnection, Node>
            writeQueueTailUpdater = newUpdater(NettyPipelinedConnection.class, Node.class, "writeQueueTail");
    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<NettyPipelinedConnection, Node>
            readQueueTailUpdater = newUpdater(NettyPipelinedConnection.class, Node.class, "readQueueTail");
    @SuppressWarnings("unused")
    @Nullable
    private volatile Node writeQueueTail;
    @SuppressWarnings("unused")
    @Nullable
    private volatile Node readQueueTail;

    private final NettyConnection<Resp, Req> connection;

    /**
     * New instance.
     *
     * @param connection {@link NettyConnection} requests to which are to be pipelined.
     */
    NettyPipelinedConnection(NettyConnection<Resp, Req> connection) {
        this.connection = requireNonNull(connection);
    }

    /**
     * Do a write operation in a pipelined fashion.
     * @param requestPublisher {@link Publisher} producing the request(s) to write.
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
            DelayedSubscribePublisher<Resp> delayedResponsePublisher = new DelayedSubscribePublisher<>(
                    toSource(connection.read()));
            Node readNode = new Node(delayedResponsePublisher::processSubscribers);
            Publisher<Resp> composedResponsePublisher =
                    delayedResponsePublisher.liftSync(new ReadPopNextOperator(readNode));

            // Setup write side publisher and nodes
            DelayedSubscribeCompletable delayedRequestCompletable = new DelayedSubscribeCompletable(toSource(
                            connection.write(requestPublisher, flushStrategySupplier, writeDemandEstimatorSupplier)));
            Node writeNode = new Node(() -> {
                try {
                    queueOffer(readQueueTailUpdater, readNode);
                } finally {
                    delayedRequestCompletable.processSubscribers();
                }
            });

            queueOffer(writeQueueTailUpdater, writeNode);

            return delayedRequestCompletable.liftSync(new WritePopNextOperator(writeNode))
                    // If there is an error on the read/write side we propagate the errors between the two via merge.
                    .merge(composedResponsePublisher);
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

    private void queueOffer(
            final @SuppressWarnings("rawtypes") AtomicReferenceFieldUpdater<NettyPipelinedConnection, Node> tailUpdater,
            final Node node) {
        for (;;) {
            Node tail = tailUpdater.get(this);
            if (tail == null) {
                if (tailUpdater.compareAndSet(this, null, node)) {
                    // node has been inserted and is the only node, we initiate processing.
                    safeProcessSubscribers(node.delayedSource);
                    break;
                }
                // Another thread won the race to offer a node, loop around and try again.
            } else if (tail.append(node)) {
                // Make the newly appended node visible as the tail. This is a best effort CAS and may fail because:
                // 1. Another thread is also inserting, has a stale tail, followed its existing tail links, and updated
                // the tail reference via queueOfferPatchTail.
                // 2. The consumer thread has seen the link from the old tail to the new node, processed node,
                // popped node from the list (updated node's next to point to EMPTY_NODE), another producer thread
                // appends a new node, sees the tail is popped, and updates the tail reference via CAS.
                tailUpdater.compareAndSet(this, tail, node);
                break;
            } else if (tail.isPopped()) {
                // A previously appended node was processed, and popped before updating the tail after append. In that
                // case the tail maybe pointing to an invalid node and we clear it out.
                if (tailUpdater.compareAndSet(this, tail, node)) {
                    safeProcessSubscribers(node.delayedSource);
                    break;
                }
                // Best effort to clear the tail, and failure is OK because:
                // 1. Another thread is in offer and already patched up the tail pointer and we will read the new tail
                // on the next loop iteration.
            } else if (queueOfferPatchTail(tailUpdater, node, tail)) {
                break;
            }
        }
    }

    private boolean queueOfferPatchTail(
            final @SuppressWarnings("rawtypes") AtomicReferenceFieldUpdater<NettyPipelinedConnection, Node> tailUpdater,
            final Node node,
            final Node tail) {
        Node currentTail = tailUpdater.get(this);
        if (currentTail == tail) {
            // tail is stale so attempt to iterate through the linked list and update tail.
            currentTail = tail.iterateToTail();
            if (currentTail.isPopped()) {
                if (tailUpdater.compareAndSet(this, tail, node)) {
                    safeProcessSubscribers(node.delayedSource);
                    return true;
                }
            } else {
                tailUpdater.compareAndSet(this, tail, currentTail);
            }
            // Best effort to update/clear the tail, and failure is OK because:
            // 1. Another thread is in offer and already patched up the tail pointer and we will read the new
            // tail on the next loop iteration.
        }
        return false;
    }

    private void queuePop(
            final @SuppressWarnings("rawtypes") AtomicReferenceFieldUpdater<NettyPipelinedConnection, Node> tailUpdater,
            final Node head) {
        // This method maybe called multiple times on the same node, in which case next will be EMPTY_NODE and the run
        // method will be a noop.
        Node next = head.pop();
        if (next != null) {
            safeProcessSubscribers(next.delayedSource);
        } else {
            tailUpdater.compareAndSet(this, head, null);
            // Best effort to clear the tail, and failure is OK because:
            // 1. Another thread appended this head, but has not yet updated the tail. In this case the tail will be
            // stale (e.g. pointing to head node that has already been processed) and corrected by future inserts.
        }
    }

    private void safeProcessSubscribers(Runnable delayedSource) {
        try {
            delayedSource.run();
        } catch (Throwable cause) {
            connection.closeAsync().subscribe();
            LOGGER.warn("closing connection={} due to unexpected error on subscribe", connection, cause);
        }
    }

    /**
     * Logically equivalent to {@link Publisher#afterFinally(Runnable)} but relies upon internal queue CAS operations
     * to prevent multiple executions (e.g. reduces a CAS operation).
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
                    queuePop(readQueueTailUpdater, readNode);
                }
            };
        }
    }

    /**
     * Logically equivalent to {@link Completable#afterFinally(Runnable)} but relies upon internal queue CAS operations
     * to prevent multiple executions (e.g. reduces a CAS operation).
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
            queuePop(writeQueueTailUpdater, writeNode);
        }
    }

    private static final class Node {
        private static final Node EMPTY_NODE = new Node();
        private static final AtomicReferenceFieldUpdater<Node, Node> nextUpdater =
                newUpdater(Node.class, Node.class, "next");
        @Nullable
        private volatile Node next;
        final Runnable delayedSource;

        Node(Runnable delayedSource) {
            this.delayedSource = requireNonNull(delayedSource);
        }

        private Node() {
            this.next = this;
            this.delayedSource = () -> { };
        }

        boolean append(Node next) {
            return nextUpdater.compareAndSet(this, null, next);
        }

        @Nullable
        Node pop() {
            return nextUpdater.getAndSet(this, EMPTY_NODE);
        }

        boolean isPopped() {
            return next == EMPTY_NODE;
        }

        Node iterateToTail() {
            Node prev = this;
            Node next = prev.next;
            while (next != null) {
                prev = next;
                next = next.next;
            }
            return prev;
        }
    }

    private static final class DelayedSubscribeCompletable extends Completable {
        private static final AtomicReferenceFieldUpdater<DelayedSubscribeCompletable, Object> stateUpdater =
                newUpdater(DelayedSubscribeCompletable.class, Object.class, "state");
        private static final Object ALLOW_SUBSCRIBE = new Object();
        private static final Object DRAINING_SUBSCRIBERS = new Object();

        private final CompletableSource completable;
        /**
         * One of the following:
         * <li>
         *     <ul>{@code null} - initial state</ul>
         *     <ul>{@link #ALLOW_SUBSCRIBE} - {@link #handleSubscribe(CompletableSource.Subscriber)} methods will
         *     pass through to {@link #completable}</ul>
         *     <ul>{@link #DRAINING_SUBSCRIBERS} - set in {@link #processSubscribers()} while calling
         *     {@link CompletableSource#subscribe(CompletableSource.Subscriber)} on each {@link Completable}</ul>
         *     <ul>{@link CompletableSource.Subscriber} - if there is a single
         *     {@link #handleSubscribe(CompletableSource.Subscriber)} pending</ul>
         *     <ul>{@code Object[]} - if there are multiple {@link #handleSubscribe(CompletableSource.Subscriber)}
         *     calls pending</ul>
         * </li>
         */
        @Nullable
        private volatile Object state;

        private DelayedSubscribeCompletable(final CompletableSource completable) {
            this.completable = requireNonNull(completable);
        }

        void processSubscribers() {
            for (;;) {
                Object currentState = state;
                if (currentState == null) {
                    if (stateUpdater.compareAndSet(this, null, ALLOW_SUBSCRIBE)) {
                        break;
                    }
                } else if (currentState == ALLOW_SUBSCRIBE) {
                    break;
                } else if (currentState instanceof CompletableSource.Subscriber) {
                    CompletableSource.Subscriber currentSubscriber = (CompletableSource.Subscriber) currentState;
                    if (stateUpdater.compareAndSet(this, currentState, DRAINING_SUBSCRIBERS)) {
                        completable.subscribe(currentSubscriber);
                        if (stateUpdater.compareAndSet(this, DRAINING_SUBSCRIBERS, ALLOW_SUBSCRIBE)) {
                            break;
                        }
                    }
                } else if (stateUpdater.compareAndSet(this, currentState, DRAINING_SUBSCRIBERS)) {
                    assert currentState != DRAINING_SUBSCRIBERS;
                    CompletableSource.Subscriber[] queue = (CompletableSource.Subscriber[]) currentState;
                    for (CompletableSource.Subscriber next : queue) {
                        completable.subscribe(next);
                    }
                    if (stateUpdater.compareAndSet(this, DRAINING_SUBSCRIBERS, ALLOW_SUBSCRIBE)) {
                        break;
                    }
                }
            }
        }

        @Override
        protected void handleSubscribe(final CompletableSource.Subscriber subscriber) {
            for (;;) {
                Object currentState = state;
                if (currentState == null || currentState == DRAINING_SUBSCRIBERS) {
                    if (stateUpdater.compareAndSet(this, currentState, subscriber)) {
                        break;
                    }
                } else if (currentState == ALLOW_SUBSCRIBE) {
                    completable.subscribe(subscriber);
                    break;
                } else if (currentState instanceof CompletableSource.Subscriber) {
                    // Ideally we can propagate the onSubscribe ASAP to allow for cancellation but this completable is
                    // designed to defer the subscribe until some other condition occurs, so no work will actually be
                    // done until that later time.
                    CompletableSource.Subscriber currentSubscriber = (CompletableSource.Subscriber) currentState;
                    if (stateUpdater.compareAndSet(this, currentState,
                            new Object[] {currentSubscriber, subscriber})) {
                        break;
                    }
                } else {
                    Object[] array = (Object[]) currentState;
                    // Unmodifiable collection to avoid issues with concurrent adding/draining with processSubscribers.
                    // The expected cardinality of the array will be low, so copy/resize is "good enough" for now.
                    Object[] newArray = Arrays.copyOf(array, array.length + 1);
                    newArray[array.length] = subscriber;
                    if (stateUpdater.compareAndSet(this, currentState, newArray)) {
                        break;
                    }
                }
            }
        }
    }

    private static final class DelayedSubscribePublisher<T> extends Publisher<T> {
        @SuppressWarnings("rawtypes")
        private static final AtomicReferenceFieldUpdater<DelayedSubscribePublisher, Object> stateUpdater =
                newUpdater(DelayedSubscribePublisher.class, Object.class, "state");
        private static final Object ALLOW_SUBSCRIBE = new Object();
        private static final Object DRAINING_SUBSCRIBERS = new Object();

        private final PublisherSource<T> publisher;
        /**
         * One of the following:
         * <li>
         *     <ul>{@code null} - initial state</ul>
         *     <ul>{@link #ALLOW_SUBSCRIBE} - {@link #handleSubscribe(PublisherSource.Subscriber)} methods will
         *     pass through to {@link #publisher}</ul>
         *     <ul>{@link #DRAINING_SUBSCRIBERS} - set in {@link #processSubscribers()} while calling
         *     {@link PublisherSource#subscribe(PublisherSource.Subscriber)} on each {@link Publisher}</ul>
         *     <ul>{@link PublisherSource.Subscriber} - if there is a single
         *     {@link #handleSubscribe(PublisherSource.Subscriber)} pending</ul>
         *     <ul>{@code Object[]} - if there are multiple {@link #handleSubscribe(PublisherSource.Subscriber)}
         *     calls pending</ul>
         * </li>
         */
        @Nullable
        private volatile Object state;

        DelayedSubscribePublisher(final PublisherSource<T> publisher) {
            this.publisher = requireNonNull(publisher);
        }

        void processSubscribers() {
            for (;;) {
                Object currentState = state;
                if (currentState == null) {
                    if (stateUpdater.compareAndSet(this, null, ALLOW_SUBSCRIBE)) {
                        break;
                    }
                } else if (currentState == ALLOW_SUBSCRIBE) {
                    break;
                } else if (currentState instanceof PublisherSource.Subscriber) {
                    @SuppressWarnings("unchecked")
                    PublisherSource.Subscriber<? super T> currentSubscriber =
                            (PublisherSource.Subscriber<? super T>) currentState;
                    if (stateUpdater.compareAndSet(this, currentState, DRAINING_SUBSCRIBERS)) {
                        publisher.subscribe(currentSubscriber);
                        if (stateUpdater.compareAndSet(this, DRAINING_SUBSCRIBERS, ALLOW_SUBSCRIBE)) {
                            break;
                        }
                    }
                } else if (stateUpdater.compareAndSet(this, currentState, DRAINING_SUBSCRIBERS)) {
                    assert currentState != DRAINING_SUBSCRIBERS;
                    @SuppressWarnings("unchecked")
                    PublisherSource.Subscriber<? super T>[] queue =
                            (PublisherSource.Subscriber<? super T>[]) currentState;
                    for (PublisherSource.Subscriber<? super T> next : queue) {
                        publisher.subscribe(next);
                    }
                    if (stateUpdater.compareAndSet(this, DRAINING_SUBSCRIBERS, ALLOW_SUBSCRIBE)) {
                        break;
                    }
                }
            }
        }

        @Override
        protected void handleSubscribe(final PublisherSource.Subscriber<? super T> subscriber) {
            for (;;) {
                Object currentState = state;
                if (currentState == null || currentState == DRAINING_SUBSCRIBERS) {
                    if (stateUpdater.compareAndSet(this, currentState, subscriber)) {
                        break;
                    }
                } else if (currentState == ALLOW_SUBSCRIBE) {
                    publisher.subscribe(subscriber);
                    break;
                } else if (currentState instanceof PublisherSource.Subscriber) {
                    // Ideally we can propagate the onSubscribe ASAP to allow for cancellation but this publisher is
                    // designed to defer the subscribe until some other condition occurs, so no work will actually be
                    // done until that later time.
                    @SuppressWarnings("unchecked")
                    PublisherSource.Subscriber<? super T> currentSubscriber =
                            (PublisherSource.Subscriber<? super T>) currentState;
                    if (stateUpdater.compareAndSet(this, currentState,
                            new Object[] {currentSubscriber, subscriber})) {
                        break;
                    }
                } else {
                    Object[] array = (Object[]) currentState;
                    // Unmodifiable collection to avoid issues with concurrent adding/draining with processSubscribers.
                    // The expected cardinality of the array will be low, so copy/resize is "good enough" for now.
                    Object[] newArray = Arrays.copyOf(array, array.length + 1);
                    newArray[array.length] = subscriber;
                    if (stateUpdater.compareAndSet(this, currentState, newArray)) {
                        break;
                    }
                }
            }
        }
    }
}
