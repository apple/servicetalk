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
import io.servicetalk.concurrent.internal.ConcurrentSubscription;
import io.servicetalk.concurrent.internal.DuplicateSubscribeException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.SubscriberUtils.checkDuplicateSubscription;
import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverErrorFromSource;
import static io.servicetalk.concurrent.internal.ThrowableUtils.catchUnexpected;

abstract class AbstractPublisherGroupBy<Key, T> extends AbstractNoHandleSubscribePublisher<GroupedPublisher<Key, T>> {
    final Publisher<T> original;
    final int initialCapacityForGroups;
    final int queueLimit;

    AbstractPublisherGroupBy(Publisher<T> original, int queueLimit) {
        this(original, queueLimit, 4);
    }

    AbstractPublisherGroupBy(Publisher<T> original, int queueLimit, int expectedGroupCountHint) {
        if (expectedGroupCountHint <= 0) {
            throw new IllegalArgumentException("expectedGroupCountHint " + expectedGroupCountHint + " (expected >0)");
        }
        this.initialCapacityForGroups = expectedGroupCountHint;
        if (queueLimit <= 0) {
            throw new IllegalArgumentException("queueLimit " + queueLimit + " (expected >0)");
        }
        this.queueLimit = queueLimit;
        this.original = original;
    }

    abstract static class AbstractGroupBySubscriber<Key, T> implements Subscriber<T> {
        private boolean rootCancelled;
        private final int queueLimit;
        private final AsyncContextMap contextMap;
        private final AsyncContextProvider contextProvider;
        private final Map<Key, GroupMulticastSubscriber<Key, T>> groups;
        private final GroupMulticastSubscriber<String, GroupedPublisher<Key, T>> target;
        @Nullable
        private Subscription subscription;

        AbstractGroupBySubscriber(final Subscriber<? super GroupedPublisher<Key, T>> target, final int queueLimit,
                                  final int initialCapacityForGroups, final AsyncContextMap contextMap,
                                  final AsyncContextProvider contextProvider) {
            this.queueLimit = queueLimit;
            this.contextMap = contextMap;
            this.contextProvider = contextProvider;
            this.target = new GroupMulticastSubscriber<>(this, "root");
            this.target.subscriber(target, false, contextMap, contextProvider);
            groups = new ConcurrentHashMap<>(initialCapacityForGroups);
        }

        @Override
        public final void onSubscribe(final Subscription subscription) {
            if (checkDuplicateSubscription(this.subscription, subscription)) {
                this.subscription = ConcurrentSubscription.wrap(subscription);
                target.triggerOnSubscribe();
            }
        }

        @Override
        public final void onError(final Throwable t) {
            Throwable delayedCause = onTerminal(t, GroupMulticastSubscriber::onError);
            target.onError(delayedCause == null ? t : delayedCause);
        }

        @Override
        public final void onComplete() {
            Throwable delayedCause = onTerminal(null, (groupSink, t) -> groupSink.onComplete());
            if (delayedCause == null) {
                target.onComplete();
            } else {
                target.onError(delayedCause);
            }
        }

        final void onNext(Key key, @Nullable T t) {
            GroupMulticastSubscriber<Key, T> groupSub = groups.get(key);
            if (groupSub != null) {
                groupSub.onNext(t);
            } else {
                groupSub = new GroupMulticastSubscriber<>(this, key);
                GroupedPublisher<Key, T> groupedPublisher = new DefaultGroupedPublisher<>(key, groupSub,
                        contextMap, contextProvider);
                final GroupMulticastSubscriber<Key, T> oldVal = groups.put(key, groupSub);
                assert oldVal == null; // concurrent onNext not allowed, collision not expected.
                groupSub.onNext(t); // deliver to group first to avoid re-entry creating ordering issues.
                target.onNext(groupedPublisher);
            }
        }

        private void requestUpstream(long n) {
            assert subscription != null;
            subscription.request(n);
        }

        private void removeSubscriber(final GroupMulticastSubscriber<?, ?> subscriber) {
            assert subscription != null;
            if (subscriber == target) {
                rootCancelled = true;
                if (groups.isEmpty()) {
                    subscription.cancel();
                }
            } else {
                @SuppressWarnings("unchecked")
                GroupMulticastSubscriber<Key, T> sub = (GroupMulticastSubscriber<Key, T>) subscriber;
                if (groups.remove(sub.key, sub) && rootCancelled && groups.isEmpty()) {
                    subscription.cancel();
                }
            }
        }

        @Nullable
        private Throwable onTerminal(@Nullable Throwable t,
                                     BiConsumer<GroupMulticastSubscriber<Key, T>, Throwable> terminator) {
            Throwable delayedCause = null;
            for (GroupMulticastSubscriber<Key, T> groupSink : groups.values()) {
                try {
                    terminator.accept(groupSink, t);
                } catch (Throwable cause) {
                    delayedCause = catchUnexpected(delayedCause, cause);
                }
            }
            return delayedCause;
        }
    }

    private static final class GroupMulticastSubscriber<Key, T> extends MulticastLeafSubscriber<T> {
        @SuppressWarnings("rawtypes")
        private static final AtomicIntegerFieldUpdater<GroupMulticastSubscriber> subscriberStateUpdater =
                AtomicIntegerFieldUpdater.newUpdater(GroupMulticastSubscriber.class, "subscriberState");
        private final AbstractGroupBySubscriber<?, ?> root;
        private final Key key;
        private volatile int subscriberState;
        @Nullable
        private Subscriber<? super T> subscriber;
        @Nullable
        private Subscriber<? super T> ctxSubscriber;

        GroupMulticastSubscriber(final AbstractGroupBySubscriber<?, ?> root, final Key key) {
            this.root = root;
            this.key = key;
        }

        @Override
        public String toString() {
            return key.toString();
        }

        void subscriber(final Subscriber<? super T> subscriber, final boolean triggerOnSubscribe,
                        final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
            // The root Subscriber's downstream subscriber is set internally, so no need for atomic operation to filter
            // duplicates.
            if (!triggerOnSubscribe) {
                assert this.subscriber == null && ctxSubscriber == null;
                this.subscriber = subscriber;
                ctxSubscriber = contextProvider.wrapPublisherSubscriber(subscriber, contextMap);
            } else if (subscriberStateUpdater.compareAndSet(this, 0, 1)) {
                this.subscriber = subscriber;
                ctxSubscriber = contextProvider.wrapPublisherSubscriber(subscriber, contextMap);
                triggerOnSubscribe();
            } else {
                // this.subscriber may be null (we set the subscriber variable after subscriberStateUpdater),
                // but we provide best effort visibility for the exception to avoid additional atomic ops.
                deliverErrorFromSource(subscriber,
                        new DuplicateSubscribeException(this.subscriber, subscriber));
            }
        }

        @Nullable
        @Override
        Subscriber<? super T> subscriber() {
            return subscriber;
        }

        @Nullable
        @Override
        Subscriber<? super T> subscriberOnSubscriptionThread() {
            return ctxSubscriber;
        }

        @Override
        void requestUpstream(final long n) {
            root.requestUpstream(n);
        }

        @Override
        void cancelUpstream() {
            root.removeSubscriber(this);
        }

        @Override
        int outstandingDemandLimit() {
            return root.queueLimit;
        }
    }

    private static final class DefaultGroupedPublisher<Key, T> extends GroupedPublisher<Key, T>
            implements PublisherSource<T> {
        private final GroupMulticastSubscriber<Key, T> groupSink;
        private final AsyncContextMap contextMap;
        private final AsyncContextProvider contextProvider;

        DefaultGroupedPublisher(final Key key, final GroupMulticastSubscriber<Key, T> groupSink,
                                final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
            super(key);
            this.groupSink = groupSink;
            this.contextMap = contextMap;
            this.contextProvider = contextProvider;
        }

        @Override
        public void subscribe(final Subscriber<? super T> subscriber) {
            subscribeInternal(subscriber);
        }

        @Override
        protected void handleSubscribe(Subscriber<? super T> sub) {
            groupSink.subscriber(sub, true, contextMap, contextProvider);
        }
    }
}
