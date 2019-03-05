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

import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.internal.SignalOffloader;

import java.util.function.Consumer;

final class NoopOffloader implements SignalOffloader {

    static final SignalOffloader NOOP_OFFLOADER = new NoopOffloader();

    private NoopOffloader() {
        // No instances
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Subscriber<T> offloadSubscriber(final Subscriber<? super T> subscriber) {
        return (Subscriber<T>) subscriber;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> SingleSource.Subscriber<T> offloadSubscriber(
            final SingleSource.Subscriber<? super T> subscriber) {
        return (SingleSource.Subscriber<T>) subscriber;
    }

    @Override
    public CompletableSource.Subscriber offloadSubscriber(final CompletableSource.Subscriber subscriber) {
        return subscriber;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Subscriber<T> offloadSubscription(final Subscriber<? super T> subscriber) {
        return (Subscriber<T>) subscriber;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> SingleSource.Subscriber<T> offloadCancellable(final SingleSource.Subscriber<? super T> subscriber) {
        return (SingleSource.Subscriber<T>) subscriber;
    }

    @Override
    public CompletableSource.Subscriber offloadCancellable(final CompletableSource.Subscriber subscriber) {
        return subscriber;
    }

    @Override
    public <T> void offloadSubscribe(final Subscriber<T> subscriber,
                                     final Consumer<Subscriber<T>> handleSubscribe) {
        handleSubscribe.accept(subscriber);
    }

    @Override
    public <T> void offloadSubscribe(final SingleSource.Subscriber<T> subscriber,
                                     final Consumer<SingleSource.Subscriber<T>> handleSubscribe) {
        handleSubscribe.accept(subscriber);
    }

    @Override
    public void offloadSubscribe(final CompletableSource.Subscriber subscriber,
                                 final Consumer<CompletableSource.Subscriber> handleSubscribe) {
        handleSubscribe.accept(subscriber);
    }

    @Override
    public <T> void offloadSignal(final T signal, final Consumer<T> signalConsumer) {
        signalConsumer.accept(signal);
    }
}
