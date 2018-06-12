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

import io.servicetalk.concurrent.Single;

import org.reactivestreams.Subscriber;

import java.util.function.Consumer;

final class NoopSignalOffloader implements SignalOffloader {

    static final NoopSignalOffloader NOOP_SIGNAL_OFFLOADER = new NoopSignalOffloader();

    private NoopSignalOffloader() {
        // Singleton
    }

    @Override
    public <T> Subscriber<? super T> offloadSubscriber(Subscriber<? super T> subscriber) {
        return subscriber;
    }

    @Override
    public <T> Single.Subscriber<? super T> offloadSubscriber(Single.Subscriber<? super T> subscriber) {
        return subscriber;
    }

    @Override
    public Completable.Subscriber offloadSubscriber(Completable.Subscriber subscriber) {
        return subscriber;
    }

    @Override
    public <T> Subscriber<? super T> offloadSubscription(Subscriber<? super T> subscriber) {
        return subscriber;
    }

    @Override
    public <T> Single.Subscriber<? super T> offloadCancellable(Single.Subscriber<? super T> subscriber) {
        return subscriber;
    }

    @Override
    public Completable.Subscriber offloadCancellable(Completable.Subscriber subscriber) {
        return subscriber;
    }

    @Override
    public <T> void offloadSignal(T signal, Consumer<T> signalConsumer) {
        signalConsumer.accept(signal);
    }

    @Override
    public boolean isInOffloadThread() {
        return true;
    }
}
