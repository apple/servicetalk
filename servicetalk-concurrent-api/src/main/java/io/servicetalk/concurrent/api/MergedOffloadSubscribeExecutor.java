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
import io.servicetalk.concurrent.internal.SignalOffloaderFactory;

import org.reactivestreams.Subscriber;

import java.util.function.Consumer;

import static io.servicetalk.concurrent.internal.SignalOffloaders.hasThreadAffinity;
import static io.servicetalk.concurrent.internal.SignalOffloaders.newOffloaderFor;

final class MergedOffloadSubscribeExecutor extends DelegatingExecutor implements SignalOffloaderFactory {

    private final Executor fallbackExecutor;

    MergedOffloadSubscribeExecutor(final Executor subscribeOnExecutor, final Executor fallbackExecutor) {
        super(subscribeOnExecutor);
        this.fallbackExecutor = fallbackExecutor;
    }

    @Override
    public SignalOffloader newSignalOffloader(final io.servicetalk.concurrent.Executor executor) {
        return new SubscribeOnlySignalOffloader(delegate, fallbackExecutor);
    }

    @Override
    public boolean threadAffinity() {
        return hasThreadAffinity(delegate) && hasThreadAffinity(fallbackExecutor);
    }

    private static final class SubscribeOnlySignalOffloader implements SignalOffloader {

        private final SignalOffloader offloader;
        private final SignalOffloader fallback;

        SubscribeOnlySignalOffloader(final Executor subscribeOnExecutor, final Executor fallbackExecutor) {
            offloader = newOffloaderFor(subscribeOnExecutor);
            fallback = newOffloaderFor(fallbackExecutor);
        }

        @Override
        public <T> Subscriber<? super T> offloadSubscriber(final Subscriber<? super T> subscriber) {
            return fallback.offloadSubscriber(subscriber);
        }

        @Override
        public <T> io.servicetalk.concurrent.Single.Subscriber<? super T> offloadSubscriber(
                final io.servicetalk.concurrent.Single.Subscriber<? super T> subscriber) {
            return fallback.offloadSubscriber(subscriber);
        }

        @Override
        public io.servicetalk.concurrent.Completable.Subscriber offloadSubscriber(
                final io.servicetalk.concurrent.Completable.Subscriber subscriber) {
            return fallback.offloadSubscriber(subscriber);
        }

        @Override
        public <T> Subscriber<? super T> offloadSubscription(final Subscriber<? super T> subscriber) {
            return offloader.offloadSubscription(subscriber);
        }

        @Override
        public <T> io.servicetalk.concurrent.Single.Subscriber<? super T> offloadCancellable(
                final io.servicetalk.concurrent.Single.Subscriber<? super T> subscriber) {
            return offloader.offloadCancellable(subscriber);
        }

        @Override
        public io.servicetalk.concurrent.Completable.Subscriber offloadCancellable(
                final io.servicetalk.concurrent.Completable.Subscriber subscriber) {
            return offloader.offloadCancellable(subscriber);
        }

        @Override
        public <T> void offloadSubscribe(final Subscriber<T> subscriber,
                                         final Consumer<Subscriber<T>> handleSubscribe) {
            offloader.offloadSubscribe(subscriber, handleSubscribe);
        }

        @Override
        public <T> void offloadSubscribe(final io.servicetalk.concurrent.Single.Subscriber<T> subscriber,
                                         final Consumer<Single.Subscriber<T>> handleSubscribe) {
            offloader.offloadSubscribe(subscriber, handleSubscribe);
        }

        @Override
        public void offloadSubscribe(final io.servicetalk.concurrent.Completable.Subscriber subscriber,
                                     final Consumer<Completable.Subscriber> handleSubscribe) {
            offloader.offloadSubscribe(subscriber, handleSubscribe);
        }

        @Override
        public <T> void offloadSignal(final T signal, final Consumer<T> signalConsumer) {
            offloader.offloadSignal(signal, signalConsumer);
        }
    }
}
