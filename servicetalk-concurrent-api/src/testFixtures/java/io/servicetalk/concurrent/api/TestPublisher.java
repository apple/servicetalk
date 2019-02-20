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

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.internal.FlowControlUtil;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestPublisher<T> extends Publisher<T> implements Subscriber<T> {

    private final AtomicReference<Subscriber<? super T>> subscriber = new AtomicReference<>();
    private final AtomicReference<Subscription> usedSubscription = new AtomicReference<>();
    private final AtomicLong requested = new AtomicLong();
    private final AtomicLong sent = new AtomicLong();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final Subscription subscription = new Subscription() {
        @Override
        public void request(long n) {
            requested.accumulateAndGet(n, FlowControlUtil::addWithOverflowProtection);
        }

        @Override
        public void cancel() {
            if (!preserveSubscriber) {
                subscriber.set(null);
            }
            cancelled.set(true);
        }
    };
    private final boolean preserveSubscriber;
    private final boolean ignoreMultipleSubscriptions;

    public TestPublisher() {
        this(false);
    }

    public TestPublisher(boolean preserveSubscriber) {
        this(preserveSubscriber, false);
    }

    public TestPublisher(boolean preserveSubscriber, boolean ignoreMultipleSubscriptions) {
        this(immediate(), preserveSubscriber, ignoreMultipleSubscriptions);
    }

    public TestPublisher(Executor executor, boolean preserveSubscriber, boolean ignoreMultipleSubscriptions) {
        super(executor);
        this.preserveSubscriber = preserveSubscriber;
        this.ignoreMultipleSubscriptions = ignoreMultipleSubscriptions;
    }

    @Override
    public void handleSubscribe(Subscriber<? super T> s) {
        if (preserveSubscriber) {
            subscriber.set(s);
        } else if (!subscriber.compareAndSet(null, s)) {
            if (ignoreMultipleSubscriptions) {
                return;
            }
            throw new IllegalStateException("Only one active subscriber allowed.");
        }
        requested.set(0);
        sent.set(0);
        Subscription subscription = this.usedSubscription.get();
        if (subscription != null) {
            s.onSubscribe(subscription);
        }
    }

    public TestPublisher<T> sendOnSubscribe() {
        onSubscribe0(subscription);
        return this;
    }

    @SafeVarargs
    public final TestPublisher<T> sendItems(T... items) {
        Subscriber<? super T> subscriber = verifySubscriberAndStart();
        long req = getOutstandingRequested();
        if (req < items.length) {
            throw new IllegalStateException("Subscriber has not requested enough. Requested: " + req + ", Expected: " + items.length);
        }
        for (T item : items) {
            sent.incrementAndGet();
            subscriber.onNext(item);
        }
        return this;
    }

    @SafeVarargs
    public final TestPublisher<T> sendItemsNoDemandCheck(T... items) {
        Subscriber<? super T> subscriber = verifySubscriberAndStart();
        for (T item : items) {
            sent.incrementAndGet();
            subscriber.onNext(item);
        }
        return this;
    }

    public TestPublisher<T> fail() {
        fail(DELIBERATE_EXCEPTION);
        return this;
    }

    public TestPublisher<T> fail(Throwable cause) {
        onError(cause);
        return this;
    }

    public TestPublisher<T> failIfSubscriberActive() {
        Subscriber<? super T> subscriber = this.subscriber.get();
        if (subscriber != null) {
            subscriber.onError(DELIBERATE_EXCEPTION);
        }
        return this;
    }

    public TestPublisher<T> completeIfSubscriberActive() {
        Subscriber<? super T> subscriber = this.subscriber.get();
        if (subscriber != null) {
            subscriber.onComplete();
        }
        return this;
    }

    @Override
    public void onSubscribe(Subscription s) {
        onSubscribe0(s);
    }

    @Override
    public void onNext(T t) {
        Subscriber<? super T> subscriber = verifySubscriberAndStart();
        if (requested.get() == 0) {
            throw new IllegalStateException("Subscriber has not requested enough.");
        }
        sent.incrementAndGet();
        subscriber.onNext(t);
    }

    @Override
    public void onError(Throwable t) {
        Subscriber<? super T> sub = preserveSubscriber ? getSubscriberOrFail() : getSubscriberAndReset();
        sub.onError(t);
    }

    @Override
    public void onComplete() {
        Subscriber<? super T> sub = preserveSubscriber ? getSubscriberOrFail() : getSubscriberAndReset();
        sub.onComplete();
    }

    public long getRequested() {
        return requested.get();
    }

    public long getOutstandingRequested() {
        return requested.get() - sent.get();
    }

    public long getSent() {
        return sent.get();
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public TestPublisher<T> verifyRequested(long expected) {
        assertThat("Unexpected items requested.", requested.get(), is(expected));
        return this;
    }

    public TestPublisher<T> verifyOutstanding(long expected) {
        assertThat("Unexpected outstanding requested.", getOutstandingRequested(), is(expected));
        return this;
    }

    public TestPublisher<T> verifyCancelled() {
        assertTrue("Subscriber did not cancel.", isCancelled());
        return this;
    }

    public TestPublisher<T> verifyNotCancelled() {
        assertFalse("Subscriber cancelled.", isCancelled());
        return this;
    }

    public TestPublisher<T> verifySubscribed() {
        assertThat("No subscriber found.", subscriber.get(), is(notNullValue()));
        return this;
    }

    public TestPublisher<T> verifyNotSubscribed() {
        final Subscriber<? super T> sub = this.subscriber.get();
        assertThat("Subscriber found: " + sub, sub, is(nullValue()));
        return this;
    }

    private Subscriber<? super T> verifySubscriberAndStart() {
        Subscriber<? super T> sub = getSubscriberOrFail();
        if (usedSubscription.compareAndSet(null, subscription)) {
            sendOnSubscribe();
        }
        return sub;
    }

    private void onSubscribe0(Subscription s) {
        if (usedSubscription.compareAndSet(null, s)) {
            Subscriber<? super T> sub = subscriber.get();
            if (sub != null) {
                sub.onSubscribe(s);
            }
        } else {
            throw new IllegalStateException("OnSubscribe already sent.");
        }
    }

    private Subscriber<? super T> getSubscriberAndReset() {
        Subscriber<? super T> sub = subscriber.getAndSet(null);
        if (sub == null) {
            throw new IllegalStateException("No Subscriber present.");
        }
        return sub;
    }

    private Subscriber<? super T> getSubscriberOrFail() {
        Subscriber<? super T> sub = subscriber.get();
        if (sub == null) {
            throw new IllegalStateException("No Subscriber present.");
        }
        return sub;
    }
}
