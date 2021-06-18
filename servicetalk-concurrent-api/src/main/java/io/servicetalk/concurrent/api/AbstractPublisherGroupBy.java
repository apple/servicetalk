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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.GroupedPublisher.QueueSizeProvider;
import io.servicetalk.concurrent.api.MulticastUtils.IndividualMulticastSubscriber;
import io.servicetalk.concurrent.api.MulticastUtils.SpscQueue;
import io.servicetalk.concurrent.internal.ConcurrentSubscription;
import io.servicetalk.concurrent.internal.DuplicateSubscribeException;
import io.servicetalk.concurrent.internal.FlowControlUtils;
import io.servicetalk.concurrent.internal.QueueFullException;
import io.servicetalk.concurrent.internal.TerminalNotification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Consumer;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.MulticastUtils.drainToSubscriber;
import static io.servicetalk.concurrent.api.SubscriberApiUtils.SUBSCRIBER_STATE_IDLE;
import static io.servicetalk.concurrent.api.SubscriberApiUtils.SUBSCRIBER_STATE_ON_NEXT;
import static io.servicetalk.concurrent.api.SubscriberApiUtils.SUBSCRIBER_STATE_TERMINATED;
import static io.servicetalk.concurrent.internal.SubscriberUtils.checkDuplicateSubscription;
import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverErrorFromSource;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static java.util.Objects.requireNonNull;

abstract class AbstractPublisherGroupBy<Key, T>
        extends AbstractSynchronousPublisherOperator<T, GroupedPublisher<Key, T>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractPublisherGroupBy.class);

    final int initialCapacityForGroups;
    final int groupQueueSize;

    AbstractPublisherGroupBy(Publisher<T> original, int groupQueueSize) {
        this(original, groupQueueSize, 4);
    }

    AbstractPublisherGroupBy(Publisher<T> original, int groupQueueSize, int expectedGroupCountHint) {
        super(original);
        if (expectedGroupCountHint <= 0) {
            throw new IllegalArgumentException("expectedGroupCountHint " + expectedGroupCountHint + " (expected >0)");
        }
        this.initialCapacityForGroups = expectedGroupCountHint;
        if (groupQueueSize <= 0) {
            throw new IllegalArgumentException("groupQueueSize " + groupQueueSize + " (expected >0)");
        }
        this.groupQueueSize = groupQueueSize;
    }

    abstract static class AbstractSourceSubscriber<Key, T> implements Subscriber<T>, Subscription {
        private static final AtomicLongFieldUpdater<AbstractSourceSubscriber> groupRequestedUpdater =
                AtomicLongFieldUpdater.newUpdater(AbstractSourceSubscriber.class, "groupRequested");
        private static final AtomicIntegerFieldUpdater<AbstractSourceSubscriber> subscriberStateUpdater =
                AtomicIntegerFieldUpdater.newUpdater(AbstractSourceSubscriber.class, "subscriberState");
        private static final AtomicReferenceFieldUpdater<AbstractSourceSubscriber, Throwable> cancelCauseUpdater =
                AtomicReferenceFieldUpdater.newUpdater(AbstractSourceSubscriber.class, Throwable.class, "cancelCause");

        private boolean terminatedPrematurely;
        @SuppressWarnings("unused")
        @Nullable
        private volatile Throwable cancelCause;
        @SuppressWarnings("unused")
        private volatile long groupRequested;
        @SuppressWarnings("unused")
        private volatile int subscriberState;
        @Nullable
        private volatile Subscription subscription;
        @SuppressWarnings("unused")
        @Nullable
        private volatile SpscQueue<GroupedPublisher<Key, T>> groupQueue;
        private final Executor executor;
        private final Subscriber<? super GroupedPublisher<Key, T>> target;
        private final Map<Key, GroupSink<Key, T>> groups;

        AbstractSourceSubscriber(Executor executor, int initialCapacityForGroups,
                                 Subscriber<? super GroupedPublisher<Key, T>> target) {
            this.executor = executor;
            this.target = target;
            // loadFactor: 1 to have table size as expected groups.
            groups = new ConcurrentHashMap<>(initialCapacityForGroups, 1, 1);
        }

        @Override
        public final void onSubscribe(Subscription s) {
            if (!checkDuplicateSubscription(subscription, s)) {
                return;
            }
            subscription = ConcurrentSubscription.wrap(s);
            target.onSubscribe(this);
        }

        /**
         * Process incoming data. It is expected {@link #onNextGroup(Object, Object)} will be used after the keys are
         * extracted.
         *
         * @param t The incoming data.
         */
        abstract void onNext0(@Nullable T t);

        /**
         * Get the maximum size of the queue for the group level {@link Subscriber}.
         *
         * @return the maximum size of the queue for the group level {@link Subscriber}.
         */
        abstract int groupQueueSize();

        final void onNextGroup(Key key, @Nullable T t) {
            GroupSink<Key, T> groupSink = groups.get(key);
            // No concurrent additions to groups map as we only put from onNext which can not be concurrent.
            if (groupSink == null) {
                int groupSinkQueueSize = groupQueueSize();
                if (key instanceof QueueSizeProvider) {
                    try {
                        groupSinkQueueSize = ((QueueSizeProvider) key).calculateMaxQueueSize(groupQueueSize());
                        if (groupSinkQueueSize < 0) {
                            throw new IllegalStateException("groupSinkQueueSize: " + groupSinkQueueSize +
                                    " (expected >=0)");
                        }
                    } catch (Throwable cause) {
                        cancelSourceFromSource(false, cause);
                        return;
                    }
                }
                groupSink = new GroupSink<>(executor, key, groupSinkQueueSize, this);
                final GroupSink<Key, T> oldVal = groups.put(key, groupSink);
                assert oldVal == null;
                SpscQueue<GroupedPublisher<Key, T>> groupQueue = this.groupQueue;
                if (groupQueue != null && !groupQueue.isEmpty()) {
                    // The queue is not empty. We have to go through the queue to ensure ordering is preserved.
                    if (!groupQueue.offerNext(groupSink.groupedPublisher)) {
                        cancelSourceFromSource(false, new QueueFullException("global", groupQueueSize()), groupQueue);
                    }
                    drainPendingGroupsFromSource(groupQueue);
                } else if (subscriberStateUpdater.compareAndSet(this, SUBSCRIBER_STATE_IDLE,
                        SUBSCRIBER_STATE_ON_NEXT)) {
                    // The queue is empty, and we acquired the lock so we can try to directly deliver to target
                    // (assuming there is request(n) demand).
                    try {
                        for (;;) {
                            final long groupRequested = this.groupRequested;
                            if (groupRequested == 0) {
                                if (groupQueue == null) {
                                    // There will only ever be a single thread generating data. It is safe to
                                    // lazy-create the groupQueue without using atomic operations.
                                    this.groupQueue = groupQueue = new SpscQueue<>(groupQueueSize());
                                }
                                if (!groupQueue.offerNext(groupSink.groupedPublisher)) {
                                    cancelSourceFromSource(true, new QueueFullException("global", groupQueueSize()),
                                            groupQueue);
                                }
                                break;
                            }
                            if (groupRequestedUpdater.compareAndSet(this, groupRequested, groupRequested - 1)) {
                                try {
                                    target.onNext(groupSink.groupedPublisher);
                                } catch (Throwable cause) {
                                    cancelSourceFromSource(true, new IllegalStateException(
                                            "Unexpected exception thrown from onNext", cause), groupQueue);
                                }
                                break;
                            }
                        }
                    } finally {
                        Throwable cause = cancelCause;
                        if (cause == null) {
                            this.subscriberState = SUBSCRIBER_STATE_IDLE;
                            cause = cancelCause;
                            if (cause != null && subscriberStateUpdater.compareAndSet(this, SUBSCRIBER_STATE_IDLE,
                                    SUBSCRIBER_STATE_ON_NEXT)) {
                                sendErrorToAllGroups(cause);
                                this.subscriberState = SUBSCRIBER_STATE_IDLE;
                            }
                        } else {
                            sendErrorToAllGroups(cause);
                            this.subscriberState = SUBSCRIBER_STATE_IDLE;
                        }
                    }
                    // In the event of re-entry it is possible that the queue was initially null, but then later created
                    // so we should re-read the volatile variable just in case.
                    if (groupQueue == null) {
                        groupQueue = this.groupQueue;
                    }
                    if (groupQueue != null && !groupQueue.isEmpty()) {
                        // After we release the lock we have to try to drain from the queue in case there was any
                        // additional request(n) calls, or additional data was added in a re-entry fashion.
                        drainPendingGroupsFromSource(groupQueue);
                    }
                } else {
                    // If we failed to acquired the lock there is concurrency with request(n) and we have to go through
                    // the queue.
                    if (groupQueue == null) {
                        // There will only ever be a single thread generating data. It is safe to lazy-create the
                        // groupQueue without using atomic operations.
                        this.groupQueue = groupQueue = new SpscQueue<>(groupQueueSize());
                    }
                    if (!groupQueue.offerNext(groupSink.groupedPublisher)) {
                        cancelSourceFromSource(false, new QueueFullException("global", groupQueueSize()), groupQueue);
                    }
                    drainPendingGroupsFromSource(groupQueue);
                }
            }

            groupSink.onNext(t);
        }

        @Override
        public final void onNext(T t) {
            if (terminatedPrematurely) {
                return;
            }

            onNext0(t);
        }

        @Override
        public final void onError(Throwable t) {
            if (terminatedPrematurely) {
                return;
            }
            SpscQueue<GroupedPublisher<Key, T>> q = groupQueue;
            if (q == null || q.isEmpty() && subscriberStateUpdater.compareAndSet(this, SUBSCRIBER_STATE_IDLE,
                    SUBSCRIBER_STATE_TERMINATED)) {
                // If there is no queue, there is no concurrency for emission to Subscriber, so we can safely emit.
                try {
                    target.onError(t);
                } finally {
                    sendErrorToAllGroups(t);
                }
                // No need to unlock because we have terminated!
            } else {
                q.addTerminal(TerminalNotification.error(t));
                drainPendingGroupsFromSource(q);
            }
        }

        @Override
        public final void onComplete() {
            SpscQueue<GroupedPublisher<Key, T>> q = groupQueue;
            if (q == null || q.isEmpty() && subscriberStateUpdater.compareAndSet(this, SUBSCRIBER_STATE_IDLE,
                    SUBSCRIBER_STATE_TERMINATED)) {
                // If there is no queue, there is no concurrency for emission to Subscriber, so we can safely emit.
                try {
                    target.onComplete();
                } finally {
                    sendCompleteToAllGroups();
                }
                // No need to unlock because we have terminated!
            } else {
                q.addTerminal(complete());
                drainPendingGroupsFromSource(q);
            }
        }

        @Override
        public final void request(long n) {
            Subscription s = subscription;
            assert s != null : "Subscription can not be null";
            if (!isRequestNValid(n)) {
                s.request(n);
                return;
            }

            groupRequestedUpdater.accumulateAndGet(this, n, FlowControlUtils::addWithOverflowProtection);
            SpscQueue<GroupedPublisher<Key, T>> q = groupQueue;
            if (q == null) {
                s.request(n);
            } else {
                long delivered = drainPendingGroupsFromSubscription(q);
                if (delivered >= 0 && delivered < n) {
                    s.request(n - delivered);
                }
            }
        }

        final void requestFromGroup(long n) {
            Subscription s = subscription;
            assert s != null : "Subscription can not be null";
            s.request(n);
        }

        @Override
        public final void cancel() {
            cancelSourceFromExternal(new CancellationException("Group subscriber cancelled its subscription"));
        }

        final void cancelSourceFromExternal(Throwable cause) {
            Subscription s = subscription;
            assert s != null : "Subscription can not be null";

            s.cancel();
            if (cancelCauseUpdater.compareAndSet(this, null, cause) &&
                    subscriberStateUpdater.compareAndSet(this, SUBSCRIBER_STATE_IDLE, SUBSCRIBER_STATE_ON_NEXT)) {
                sendErrorToAllGroups(cause);
                this.subscriberState = SUBSCRIBER_STATE_IDLE;
                SpscQueue<GroupedPublisher<Key, T>> groupQueue = this.groupQueue;
                if (groupQueue != null) {
                    drainPendingGroupsFromSubscription(groupQueue);
                }
            }
        }

        final void removeGroup(GroupSink<Key, T> groupSink) {
            groups.remove(groupSink.groupedPublisher.key(), groupSink);
        }

        final void cancelSourceFromSource(Throwable throwable) {
            cancelSourceFromSource(true, throwable);
        }

        final void cancelSourceFromSource(boolean subscriberLockAcquired, Throwable throwable) {
            cancelSourceFromSource(subscriberLockAcquired, throwable, groupQueue);
        }

        private void cancelSourceFromSource(boolean subscriberLockAcquired, Throwable throwable,
                                            @Nullable SpscQueue<GroupedPublisher<Key, T>> pendingGroupsQ) {
            Subscription s = subscription;
            assert s != null : "Subscription can not be null in cancel()";
            terminatedPrematurely = true;

            s.cancel(); // Cancel subscription but not mark state as cancelled since we still need to drain the groups.
            if (pendingGroupsQ != null && !pendingGroupsQ.isEmpty()) {
                pendingGroupsQ.addTerminal(TerminalNotification.error(throwable));
                drainPendingGroupsFromSource(pendingGroupsQ);
            } else if (subscriberLockAcquired || subscriberStateUpdater.compareAndSet(this, SUBSCRIBER_STATE_IDLE,
                    SUBSCRIBER_STATE_ON_NEXT)) {
                try {
                    target.onError(throwable);
                } catch (Throwable onErrorError) {
                    LOGGER.error("Subscriber {} threw from onError for exception {}", target, throwable, onErrorError);
                } finally {
                    sendErrorToAllGroups(throwable);
                    if (!subscriberLockAcquired) {
                        this.subscriberState = SUBSCRIBER_STATE_IDLE;
                        // No need to drain the queue because we are in the Subscriber's thread and nothing else will be
                        // queued because the Subscriber is the only producer for the queue.
                    }
                }
            } else {
                if (pendingGroupsQ == null) {
                    // There will only ever be a single thread generating data. It is safe to lazy-create the groupQueue
                    // without using atomic operations.
                    this.groupQueue = pendingGroupsQ = new SpscQueue<>(groupQueueSize());
                }
                pendingGroupsQ.addTerminal(TerminalNotification.error(throwable));
                drainPendingGroupsFromSource(pendingGroupsQ);
            }
        }

        private void cancelSourceFromSubscription(Throwable cause) {
            Subscription s = subscription;
            assert s != null : "Subscription can not be null in cancel()";

            s.cancel();
            // The lock is always acquired because this is only called from on drain
            assert subscriberState != SUBSCRIBER_STATE_IDLE;
            sendErrorToAllGroups(cause);

            // To simplify concurrency and state management in this case we just log an error instead of trying to
            // synchronize state and deliver an error to the Subscriber. ReactiveStreams specification allows for this
            // in https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.1/README.md#2.13
            LOGGER.error("Unexpected exception thrown from subscriber", cause);
        }

        private void drainPendingGroupsFromSource(SpscQueue<GroupedPublisher<Key, T>> q) {
            drainPendingGroups(q, this::cancelSourceFromSource);
        }

        private long drainPendingGroupsFromSubscription(SpscQueue<GroupedPublisher<Key, T>> q) {
            return drainPendingGroups(q, this::cancelSourceFromSubscription);
        }

        private long drainPendingGroups(SpscQueue<GroupedPublisher<Key, T>> q,
                                        Consumer<Throwable> nonTerminalErrorConsumer) {
            return drainToSubscriber(q, target, subscriberStateUpdater, () -> groupRequestedUpdater.get(this),
                    terminalNotification -> {
                        Throwable cause = terminalNotification.cause();
                        if (cause == null) {
                            sendCompleteToAllGroups();
                        } else {
                            sendErrorToAllGroups(cause);
                        }
                    },
                    nonTerminalErrorConsumer,
                    this::drainPendingGroupsDecrementRequestN,
                    this);
        }

        private void drainPendingGroupsDecrementRequestN(int onNextCount) {
            assert onNextCount > 0;
            groupRequestedUpdater.addAndGet(this, -onNextCount);
        }

        private void sendErrorToAllGroups(Throwable cause) {
            for (Iterator<GroupSink<Key, T>> iterator = groups.values().iterator(); iterator.hasNext();) {
                GroupSink<Key, T> sink = iterator.next();
                iterator.remove();
                sink.onError(cause);
            }
        }

        private void sendCompleteToAllGroups() {
            for (Iterator<GroupSink<Key, T>> iterator = groups.values().iterator(); iterator.hasNext();) {
                GroupSink<Key, T> sink = iterator.next();
                iterator.remove();
                sink.onComplete();
            }
        }
    }

    private static final class GroupSink<Key, T> extends IndividualMulticastSubscriber<T> {
        final GroupedPublisher<Key, T> groupedPublisher;
        private final AbstractSourceSubscriber<Key, T> sourceSubscriber;

        GroupSink(Executor executor, Key key, int maxQueueSize, AbstractSourceSubscriber<Key, T> sourceSubscriber) {
            super(maxQueueSize);
            this.sourceSubscriber = sourceSubscriber;
            groupedPublisher = new GroupedPublisherSource<Key, T>(executor, key) {
                @Override
                protected void handleSubscribe(Subscriber<? super T> subscriber) {
                    requireNonNull(subscriber);
                    final Subscriber<? super T> target = GroupSink.this.target;
                    if (target != null) {
                        deliverErrorFromSource(subscriber, new DuplicateSubscribeException(target, subscriber));
                        return;
                    }
                    // We have to call onSubscribe before we set groupSinkTarget, because otherwise we may deliver data
                    // to the Subscriber before we call onSubscribe.
                    subscriber.onSubscribe(GroupSink.this);
                    GroupSink.this.target = subscriber;
                    // We must read this after we set the subscriber and call onSubscribe.
                    SpscQueue<T> subscriberQueue = subscriberQueue();
                    if (subscriberQueue != null) {
                        drainPendingFromExternal(subscriberQueue, subscriber);
                    }
                }
            };
        }

        @Override
        String queueIdentifier() {
            return groupedPublisher.key().toString();
        }

        @Override
        void requestFromSource(int requestN) {
            sourceSubscriber.requestFromGroup(requestN);
        }

        @Override
        void handleInvalidRequestN(long n) {
            sourceSubscriber.requestFromGroup(n);
        }

        @Override
        void cancelSourceFromExternal(Throwable cause) {
            sourceSubscriber.cancelSourceFromExternal(cause);

            // To simplify concurrency and state management in this case we just log an error instead of trying to
            // synchronize state and deliver an error to the Subscriber. ReactiveStreams specification allows for this
            // in [1].
            // [1] https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.1/README.md#2.13
            LOGGER.error("Unexpected exception thrown from group {} subscriber", groupedPublisher.key(), cause);
        }

        @Override
        void cancelSourceFromSource(boolean subscriberLockAcquired, Throwable cause) {
            sourceSubscriber.removeGroup(this);
            sourceSubscriber.cancelSourceFromSource(subscriberLockAcquired, cause);
        }

        @Override
        public void cancel() {
            sourceSubscriber.removeGroup(this);
        }
    }

    private abstract static class GroupedPublisherSource<Key, T> extends GroupedPublisher<Key, T>
            implements PublisherSource<T> {

        GroupedPublisherSource(final Executor executor, final Key key) {
            super(key);
        }

        @Override
        public final void subscribe(final Subscriber<? super T> subscriber) {
            subscribeInternal(subscriber);
        }
    }
}
