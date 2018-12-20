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

import io.servicetalk.concurrent.Completable;
import io.servicetalk.concurrent.Single;
import io.servicetalk.concurrent.internal.SignalOffloader;

import org.reactivestreams.Subscriber;

import java.util.function.Consumer;

final class NoopOffloader implements SignalOffloader {

    static final SignalOffloader NOOP_OFFLOADER = new NoopOffloader();

    private NoopOffloader() {
        // No instances
    }

    @Override
    public <T> Subscriber<? super T> offloadSubscriber(final Subscriber<? super T> subscriber) {
        return subscriber;
    }

    @Override
    public <T> Single.Subscriber<? super T> offloadSubscriber(final Single.Subscriber<? super T> subscriber) {
        return subscriber;
    }

    @Override
    public Completable.Subscriber offloadSubscriber(final Completable.Subscriber subscriber) {
        return subscriber;
    }

    @Override
    public <T> Subscriber<? super T> offloadSubscription(final Subscriber<? super T> subscriber) {
        return subscriber;
    }

    @Override
    public <T> Single.Subscriber<? super T> offloadCancellable(final Single.Subscriber<? super T> subscriber) {
        return subscriber;
    }

    @Override
    public Completable.Subscriber offloadCancellable(final Completable.Subscriber subscriber) {
        return subscriber;
    }

    @Override
    public <T> void offloadSubscribe(final Subscriber<T> subscriber,
                                     final Consumer<Subscriber<T>> handleSubscribe) {
        handleSubscribe.accept(subscriber);
    }

    @Override
    public <T> void offloadSubscribe(final Single.Subscriber<T> subscriber,
                                     final Consumer<Single.Subscriber<T>> handleSubscribe) {
        handleSubscribe.accept(subscriber);
    }

    @Override
    public void offloadSubscribe(final Completable.Subscriber subscriber,
                                 final Consumer<Completable.Subscriber> handleSubscribe) {
        handleSubscribe.accept(subscriber);
    }

    @Override
    public <T> void offloadSignal(final T signal, final Consumer<T> signalConsumer) {
        signalConsumer.accept(signal);
    }
}
