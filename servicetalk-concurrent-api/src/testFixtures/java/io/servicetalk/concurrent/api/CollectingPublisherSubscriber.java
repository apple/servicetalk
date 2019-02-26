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
import io.servicetalk.concurrent.internal.DelayedSubscription;
import io.servicetalk.concurrent.internal.TerminalNotification;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;

public final class CollectingPublisherSubscriber<T> implements Subscriber<T>, Subscription {

    private final List<T> items = new CopyOnWriteArrayList<>();
    private final DelayedSubscription subscription = new DelayedSubscription();
    @Nullable
    private volatile TerminalNotification terminal;
    private volatile boolean subscribed;

    /**
     * Clear received items and any terminal signals.
     * <p>
     * Does not affect subscribed/subscription state.
     */
    public void clear() {
        items.clear();
        terminal = null;
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
    public TerminalNotification terminal() {
        return terminal;
    }

    @Nullable
    public Throwable error() {
        final TerminalNotification terminal = this.terminal;
        if (terminal == null) {
            return null;
        }
        return terminal == TerminalNotification.complete() ? null : terminal.cause();
    }

    public boolean isSubscribed() {
        return subscribed;
    }

    @Nullable
    public Subscription subscription() {
        return subscription;
    }

    public boolean isCompleted() {
        return terminal == TerminalNotification.complete();
    }

    public boolean isErrored() {
        final TerminalNotification terminal = this.terminal;
        return terminal != null && terminal != TerminalNotification.complete();
    }

    public boolean isTerminated() {
        return terminal != null;
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
        subscription.delayedSubscription(s);
    }

    @Override
    public void onNext(final T item) {
        items.add(item);
    }

    @Override
    public void onError(final Throwable t) {
        terminal = TerminalNotification.error(t);
    }

    @Override
    public void onComplete() {
        terminal = TerminalNotification.complete();
    }
}
