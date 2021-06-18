/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.SingleSource.Subscriber;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deprecated.
 *
 * @deprecated Use {@link TestSingle} instead.
 */
@Deprecated
public class LegacyTestSingle<T> extends Single<T> implements SingleSource.Subscriber<T> {
    private static final Object NULL = new Object();
    private final AtomicInteger subscribeCount = new AtomicInteger();
    private final Queue<Subscriber<? super T>> subscribers = new ConcurrentLinkedQueue<>();
    private final CancellableSet dynamicCancellable = new CancellableSet();
    private final boolean invokeListenerPostCancel;
    private final boolean cacheResults;
    @Nullable
    private Object cachedResult;

    public LegacyTestSingle() {
        this(false);
    }

    public LegacyTestSingle(boolean invokeListenerPostCancel) {
        this(invokeListenerPostCancel, true);
    }

    public LegacyTestSingle(boolean invokeListenerPostCancel, boolean cacheResults) {
        this.invokeListenerPostCancel = invokeListenerPostCancel;
        this.cacheResults = cacheResults;
    }

    @Override
    public synchronized void handleSubscribe(Subscriber<? super T> subscriber) {
        subscribeCount.incrementAndGet();
        subscribers.add(subscriber);
        subscriber.onSubscribe(() -> {
            if (!invokeListenerPostCancel) {
                subscribers.remove(subscriber);
            }
            dynamicCancellable.cancel();
        });
        if (cachedResult != null) {
            subscribers.remove(subscriber);
            if (cachedResult instanceof Throwable) {
                subscriber.onError((Throwable) cachedResult);
            } else if (cachedResult == NULL) {
                subscriber.onSuccess(null);
            } else {
                @SuppressWarnings("unchecked")
                T t = (T) this.cachedResult;
                subscriber.onSuccess(t);
            }
        }
    }

    @Override
    public void onSubscribe(Cancellable cancellable) {
        dynamicCancellable.add(cancellable);
    }

    @Override
    public synchronized void onSuccess(@Nullable T result) {
        List<Subscriber<? super T>> subs = new ArrayList<>(subscribers);
        subscribers.clear();
        for (Subscriber<? super T> sub : subs) {
            sub.onSuccess(result);
        }
        if (cacheResults) {
            cachedResult = result == null ? NULL : result;
        }
    }

    @Override
    public synchronized void onError(Throwable t) {
        List<Subscriber<? super T>> subs = new ArrayList<>(subscribers);
        subscribers.clear();
        for (Subscriber<? super T> sub : subs) {
            sub.onError(t);
        }
        if (cacheResults) {
            cachedResult = t;
        }
    }

    public boolean isCancelled() {
        return dynamicCancellable.isCancelled();
    }

    public LegacyTestSingle<T> verifyListenCalled() {
        assertThat("Listen not called.", subscribers, hasSize(greaterThan(0)));
        return this;
    }

    public LegacyTestSingle<T> verifyListenCalled(int times) {
        int count = subscribeCount.get();
        assertThat("Listen not called " + times + " but instead " + count, count, equalTo(times));
        return this;
    }

    public LegacyTestSingle<T> verifyListenNotCalled() {
        assertThat("Listen called.", subscribers, hasSize(0));
        return this;
    }

    public LegacyTestSingle<T> verifyCancelled() {
        assertTrue(isCancelled(), "Subscriber did not cancel.");
        return this;
    }

    public LegacyTestSingle<T> verifyNotCancelled() {
        assertFalse(isCancelled(), "Subscriber cancelled.");
        return this;
    }
}
