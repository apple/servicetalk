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
package io.servicetalk.concurrent.internal;

import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.SingleSource;

import java.util.function.Consumer;

/**
 * A {@link SignalOffloader} that delegates all calls to another {@link SignalOffloader}.
 */
public class DelegatingSignalOffloader implements SignalOffloader {

    private final SignalOffloader delegate;

    /**
     * Create a new instance.
     *
     * @param delegate {@link SignalOffloader} to delegate all calls.
     */
    public DelegatingSignalOffloader(final SignalOffloader delegate) {
        this.delegate = delegate;
    }

    @Override
    public <T> PublisherSource.Subscriber<? super T> offloadSubscriber(
            final PublisherSource.Subscriber<? super T> subscriber) {
        return delegate.offloadSubscriber(subscriber);
    }

    @Override
    public <T> SingleSource.Subscriber<? super T> offloadSubscriber(
            final SingleSource.Subscriber<? super T> subscriber) {
        return delegate.offloadSubscriber(subscriber);
    }

    @Override
    public CompletableSource.Subscriber offloadSubscriber(final CompletableSource.Subscriber subscriber) {
        return delegate.offloadSubscriber(subscriber);
    }

    @Override
    public <T> PublisherSource.Subscriber<? super T> offloadSubscription(
            final PublisherSource.Subscriber<? super T> subscriber) {
        return delegate.offloadSubscription(subscriber);
    }

    @Override
    public <T> SingleSource.Subscriber<? super T> offloadCancellable(
            final SingleSource.Subscriber<? super T> subscriber) {
        return delegate.offloadCancellable(subscriber);
    }

    @Override
    public CompletableSource.Subscriber offloadCancellable(final CompletableSource.Subscriber subscriber) {
        return delegate.offloadCancellable(subscriber);
    }

    @Override
    public <T> void offloadSubscribe(final PublisherSource.Subscriber<? super T> subscriber,
                                     final Consumer<PublisherSource.Subscriber<? super T>> handleSubscribe) {
        delegate.offloadSubscribe(subscriber, handleSubscribe);
    }

    @Override
    public <T> void offloadSubscribe(final SingleSource.Subscriber<? super T> subscriber,
                                     final Consumer<SingleSource.Subscriber<? super T>> handleSubscribe) {
        delegate.offloadSubscribe(subscriber, handleSubscribe);
    }

    @Override
    public void offloadSubscribe(final CompletableSource.Subscriber subscriber,
                                 final Consumer<CompletableSource.Subscriber> handleSubscribe) {
        delegate.offloadSubscribe(subscriber, handleSubscribe);
    }

    @Override
    public <T> void offloadSignal(final T signal, final Consumer<T> signalConsumer) {
        delegate.offloadSignal(signal, signalConsumer);
    }
}
