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

import io.servicetalk.concurrent.Cancellable;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.annotation.Nullable;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestSingle<T> extends Single<T> implements Single.Subscriber<T> {
    private final Queue<Subscriber<? super T>> subscribers = new ConcurrentLinkedQueue<>();
    private final DynamicCompositeCancellable dynamicCancellable = new MapDynamicCompositeCancellable();
    private final boolean invokeListenerPostCancel;
    private final boolean cacheResults;
    @Nullable private Object cachedResult;

    public TestSingle() {
        this(false);
    }

    public TestSingle(boolean invokeListenerPostCancel) {
        this(invokeListenerPostCancel, true);
    }

    public TestSingle(boolean invokeListenerPostCancel, boolean cacheResults) {
        this.invokeListenerPostCancel = invokeListenerPostCancel;
        this.cacheResults = cacheResults;
    }

    @Override
    public synchronized void handleSubscribe(Subscriber<? super T> subscriber) {
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
            cachedResult = result;
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

    public TestSingle<T> verifyListenCalled() {
        assertThat("Listen not called.", subscribers, hasSize(greaterThan(0)));
        return this;
    }

    public TestSingle<T> verifyListenNotCalled() {
        assertThat("Listen called.", subscribers, hasSize(0));
        return this;
    }

    public TestSingle<T> verifyCancelled() {
        assertTrue("Subscriber did not cancel.", isCancelled());
        return this;
    }

    public TestSingle<T> verifyNotCancelled() {
        assertFalse("Subscriber cancelled.", isCancelled());
        return this;
    }
}
