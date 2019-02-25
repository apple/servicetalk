/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;

public final class CollectingPublisherSubscriber<T> implements Subscriber<T>, Subscription {
    public static final Throwable COMPLETE = new Throwable();
    public static final Throwable INCOMPLETE = new Throwable();

    private final List<T> items = new CopyOnWriteArrayList<>();
    private final SequentialSubscription subscription = new SequentialSubscription();
    @Nullable
    private volatile Throwable terminal = INCOMPLETE;
    private volatile boolean subscribed;

    /**
     * Clear received items and any terminal signals.
     * <p>
     * Does not affect subscribed/subscription state.
     */
    public void clear() {
        items.clear();
        terminal = INCOMPLETE;
    }

    public List<T> items() {
        return new ArrayList<>(items);
    }

    public List<T> takeItems() {
        ArrayList<T> items = new ArrayList<>(this.items);
        this.items.clear();
        return items;
    }

    @Nullable
    public Throwable terminal() {
        return terminal;
    }

    @Nullable
    public Throwable error() {
        final Throwable terminal = this.terminal;
        if (terminal == INCOMPLETE) {
            return null;
        }
        return terminal == COMPLETE ? null : terminal;
    }

    public boolean isSubscribed() {
        return subscribed;
    }

    @Nullable
    public Subscription subscription() {
        return subscription;
    }

    public boolean isCompleted() {
        return terminal == COMPLETE;
    }

    public boolean isErrored() {
        final Throwable terminal = this.terminal;
        return terminal != INCOMPLETE && terminal != COMPLETE;
    }

    public boolean isTerminated() {
        return terminal != INCOMPLETE;
    }

    @Override
    public void request(final long n) {
        subscription.request(n);
    }

    @Override
    public void cancel() {
        subscription.cancel();
    }

    @Override
    public void onSubscribe(final Subscription s) {
        subscribed = true;
        subscription.switchTo(s);
    }

    @Override
    public void onNext(final T item) {
        items.add(item);
    }

    @Override
    public void onError(final Throwable t) {
        terminal = t;
    }

    @Override
    public void onComplete() {
        terminal = COMPLETE;
    }
}
