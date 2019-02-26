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
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.internal.TerminalNotification;

import java.util.List;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

public final class TestPublisherSubscriber<T> implements Subscriber<T>, Subscription {

    private final CollectingPublisherSubscriber<T> collector;
    private final Subscriber<T> delegate;

    private TestPublisherSubscriber(final CollectingPublisherSubscriber<T> collector, final Subscriber<T> delegate) {
        this.collector = collector;
        this.delegate = delegate;
    }

    public List<T> items() {
        return collector.items();
    }

    public List<T> takeItems() {
        return collector.takeItems();
    }

    @Nullable
    public TerminalNotification terminal() {
        return collector.terminal();
    }

    @Nullable
    public Throwable error() {
        return collector.error();
    }

    public boolean subscriptionReceived() {
        return collector.subscriptionReceived();
    }

    public Subscription subscription() {
        return collector.subscription();
    }

    public boolean isCompleted() {
        return collector.isCompleted();
    }

    public boolean isErrored() {
        return collector.isErrored();
    }

    public boolean isTerminated() {
        return collector.isTerminated();
    }

    @Override
    public void request(final long n) {
        collector.request(n);
    }

    @Override
    public void cancel() {
        collector.cancel();
    }

    @Override
    public void onSubscribe(final Subscription s) {
        delegate.onSubscribe(s);
    }

    @Override
    public void onNext(final T item) {
        delegate.onNext(item);
    }

    @Override
    public void onError(final Throwable t) {
        delegate.onError(t);
    }

    @Override
    public void onComplete() {
        delegate.onComplete();
    }

    // TODO(derek) remove once the single version is ready
    public SingleSource.Subscriber<T> forSingle() {
        return new SingleSubscriber<>(this, this);
    }

    public void clear() {
        collector.clear();
    }

    public static <T> TestPublisherSubscriber<T> newTestPublisherSubscriber() {
        return new Builder<T>().build();
    }

    public static class Builder<T> {
        private boolean checkDemand = true;
        @Nullable
        private String loggingName;

        public Builder<T> enableDemandCheck() {
            this.checkDemand = true;
            return this;
        }

        public Builder<T> disableDemandCheck() {
            this.checkDemand = false;
            return this;
        }

        public Builder<T> enableLogging() {
            return enableLogging(TestPublisherSubscriber.class.getName());
        }

        public Builder<T> enableLogging(final String loggingName) {
            this.loggingName = requireNonNull(loggingName);
            return this;
        }

        public Builder<T> disableLogging() {
            this.loggingName = null;
            return this;
        }

        public TestPublisherSubscriber<T> build() {
            final CollectingPublisherSubscriber<T> collector = new CollectingPublisherSubscriber<>();
            Subscriber<T> delegate = collector;

            if (checkDemand) {
                delegate = new DemandCheckingSubscriber<>(delegate);
            }
            if (loggingName != null) {
                delegate = new LoggingSubscriber<>(loggingName, delegate);
            }

            return new TestPublisherSubscriber<>(collector, delegate);
        }
    }
}
