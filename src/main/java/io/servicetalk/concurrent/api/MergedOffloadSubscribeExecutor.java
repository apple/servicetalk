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

import org.reactivestreams.Subscriber;

import java.util.function.Consumer;

final class MergedOffloadSubscribeExecutor extends DelegatingExecutor implements OffloaderAwareExecutor {

    private final Executor fallbackExecutor;

    MergedOffloadSubscribeExecutor(final Executor executor, final Executor fallback) {
        super(executor);
        this.fallbackExecutor = fallback;
    }

    @Override
    public SignalOffloader newOffloader() {
        return new MergedSignalOffloader();
    }

    private final class MergedSignalOffloader implements SignalOffloader {

        private final SignalOffloader offloader;
        private final SignalOffloader fallback;

        MergedSignalOffloader() {
            offloader = new DefaultSignalOffloader(delegate);
            fallback = fallbackExecutor instanceof OffloaderAwareExecutor ?
                    ((OffloaderAwareExecutor) fallbackExecutor).newOffloader() :
                    new DefaultSignalOffloader(fallbackExecutor);
        }

        @Override
        public <T> Subscriber<? super T> offloadSubscriber(final Subscriber<? super T> subscriber) {
            return fallback.offloadSubscriber(subscriber);
        }

        @Override
        public <T> Single.Subscriber<? super T> offloadSubscriber(final Single.Subscriber<? super T> subscriber) {
            return fallback.offloadSubscriber(subscriber);
        }

        @Override
        public Completable.Subscriber offloadSubscriber(final Completable.Subscriber subscriber) {
            return fallback.offloadSubscriber(subscriber);
        }

        @Override
        public <T> Subscriber<? super T> offloadSubscription(final Subscriber<? super T> subscriber) {
            return offloader.offloadSubscription(subscriber);
        }

        @Override
        public <T> Single.Subscriber<? super T> offloadCancellable(final Single.Subscriber<? super T> subscriber) {
            return offloader.offloadCancellable(subscriber);
        }

        @Override
        public Completable.Subscriber offloadCancellable(final Completable.Subscriber subscriber) {
            return offloader.offloadCancellable(subscriber);
        }

        @Override
        public <T> void offloadSubscribe(final Subscriber<? super T> subscriber, final Publisher<T> publisher) {
            offloader.offloadSubscribe(subscriber, publisher);
        }

        @Override
        public <T> void offloadSubscribe(final Single.Subscriber<? super T> subscriber, final Single<T> single) {
            offloader.offloadSubscribe(subscriber, single);
        }

        @Override
        public void offloadSubscribe(final Completable.Subscriber subscriber, final Completable completable) {
            offloader.offloadSubscribe(subscriber, completable);
        }

        @Override
        public <T> void offloadSignal(final T signal, final Consumer<T> signalConsumer) {
            offloader.offloadSignal(signal, signalConsumer);
        }

        @Override
        public boolean isInOffloadThreadForPublish() {
            return fallback.isInOffloadThreadForPublish();
        }

        @Override
        public boolean isInOffloadThreadForSubscribe() {
            return offloader.isInOffloadThreadForSubscribe();
        }
    }
}
