/*
 * Copyright © 2025 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Executors;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.SourceAdapters;
import io.servicetalk.concurrent.internal.CancelImmediatelySubscriber;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

final class WatchdogLeakDetector {

    private static final AtomicIntegerFieldUpdater<WatchdogLeakDetector> STATE_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(WatchdogLeakDetector.class, "state");

    private static final Logger LOGGER = LoggerFactory.getLogger(WatchdogLeakDetector.class);

    private static final WatchdogLeakDetector INSTANCE = new WatchdogLeakDetector(Executors.global());

    private static final String PROPERTY_NAME = "io.servicetalk.http.netty.leakdetection";

    private static final String STRICT_MODE = "strict";

    private static final boolean STRICT_DETECTION;

    static {
        String prop = System.getProperty(PROPERTY_NAME);
        STRICT_DETECTION = prop != null && prop.equalsIgnoreCase(STRICT_MODE);
    }

    private final Executor executor;
    private final ReferenceQueue<Object> refQueue = new ReferenceQueue<>();
    private final Map<Reference<?>, CleanupState> allRefs = new ConcurrentHashMap<>();
    private volatile int state;

    private WatchdogLeakDetector(Executor executor) {
        this.executor = executor;
    }

    static <T> Publisher<T> gcLeakDetection(Publisher<T> publisher, Runnable onLeak) {
        return INSTANCE.gcLeakDetection0(publisher, onLeak);
    }

    static boolean strictDetection() {
        return STRICT_DETECTION;
    }

    @SuppressWarnings("unchecked")
    static <T> Class<T> generifyAtomicReference() {
        return (Class<T>) AtomicReference.class;
    }

    private <T> Publisher<T> gcLeakDetection0(Publisher<T> publisher, Runnable onLeak) {
        maybeCleanRefs();
        CleanupState cleanupState = new CleanupState(publisher, onLeak);
        Publisher<T> result = publisher.liftSync(subscriber -> new InstrumentedSubscriber<>(subscriber, cleanupState));
        Reference<?> ref = new WeakReference<>(result, refQueue);
        allRefs.put(ref, cleanupState);
        return result;
    }

    private void maybeCleanRefs() {
        final Reference<?> testRef = refQueue.poll();
        if (testRef != null && STATE_UPDATER.compareAndSet(this, 0, 1)) {
            // There are references to be cleaned but don't do it on this thread.
            // TODO: what executor should we really use?
            executor.submit(() -> {
                Reference<?> ref = testRef;
                try {
                    do {
                        ref.clear();
                        CleanupState cleanupState = allRefs.remove(ref);
                        if (cleanupState != null) {
                            cleanupState.check();
                        }
                    } while ((ref = refQueue.poll()) != null);
                } finally {
                    STATE_UPDATER.set(this, 0);
                }
            });
        }
    }

    private static final class InstrumentedSubscriber<T> implements Subscriber<T> {

        private final Subscriber<T> delegate;
        private final CleanupState cleanupToken;

        InstrumentedSubscriber(Subscriber<T> delegate, CleanupState cleanupToken) {
            this.delegate = delegate;
            this.cleanupToken = cleanupToken;
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            cleanupToken.subscribed(subscription);
            Subscription nextSubscription = new Subscription() {
                @Override
                public void request(long n) {
                    subscription.request(n);
                }

                @Override
                public void cancel() {
                    cleanupToken.doComplete();
                    subscription.cancel();
                }
            };
            delegate.onSubscribe(nextSubscription);
        }

        @Override
        public void onNext(@Nullable T t) {
            delegate.onNext(t);
        }

        @Override
        public void onError(Throwable t) {
            cleanupToken.doComplete();
            delegate.onError(t);
        }

        @Override
        public void onComplete() {
            cleanupToken.doComplete();
            delegate.onComplete();
        }
    }

    private static final class CleanupState {

        private static final AtomicReferenceFieldUpdater<CleanupState, Object> UPDATER =
                AtomicReferenceFieldUpdater.newUpdater(CleanupState.class, Object.class, "state");
        private static final String COMPLETE = "complete";

        private final Runnable onLeak;
        volatile Object state;

        CleanupState(Publisher<?> parent, Runnable onLeak) {
            this.onLeak = onLeak;
            this.state = parent;
        }

        void doComplete() {
            UPDATER.set(this, COMPLETE);
        }

        private boolean checkComplete() {
            Object previous = UPDATER.getAndSet(this, COMPLETE);
            if (previous != COMPLETE) {
                // This means something leaked.
                if (previous instanceof Publisher) {
                    // never subscribed to.
                    SourceAdapters.toSource((Publisher<?>) previous).subscribe(CancelImmediatelySubscriber.INSTANCE);
                } else {
                    assert previous instanceof Cancellable;
                    Cancellable cancellable = (Cancellable) previous;
                    cancellable.cancel();
                }
                return true;
            } else {
                return false;
            }
        }

        void subscribed(Subscription subscription) {
            while (true) {
                Object old = UPDATER.get(this);
                if (old == COMPLETE || old instanceof Subscription) {
                    // TODO: What to do here?
                    LOGGER.debug("Publisher subscribed to multiple times.");
                    return;
                } else if (UPDATER.compareAndSet(this, old, subscription)) {
                    return;
                }
            }
        }

        void check() {
            if (checkComplete()) {
                onLeak.run();
            }
        }
    }
}
