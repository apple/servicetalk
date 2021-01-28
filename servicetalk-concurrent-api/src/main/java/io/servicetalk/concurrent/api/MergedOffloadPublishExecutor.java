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
import io.servicetalk.concurrent.internal.SignalOffloaderFactory;
import io.servicetalk.concurrent.internal.SignalOffloaders;

import java.util.function.Consumer;

import static io.servicetalk.concurrent.internal.SignalOffloaders.newOffloaderFor;

final class MergedOffloadPublishExecutor extends DelegatingExecutor implements SignalOffloaderFactory {

    private final Executor fallbackExecutor;

    MergedOffloadPublishExecutor(final Executor publishOnExecutor, final Executor fallbackExecutor) {
        super(publishOnExecutor);
        this.fallbackExecutor = fallbackExecutor;
    }

    @Override
    public SignalOffloader newSignalOffloader(final io.servicetalk.concurrent.Executor executor) {
        // This method is weird since we want to keep SignalOffloader internal but it has to be associated with the
        // the Executor. In practice, the Executor passed here should always be self when the SignalOffloaderFactory is
        // an Executor itself.
        assert executor == this;
        return new PublishOnlySignalOffloader(delegate(), fallbackExecutor);
    }

    @Override
    public boolean hasThreadAffinity() {
        return SignalOffloaders.hasThreadAffinity(delegate()) && SignalOffloaders.hasThreadAffinity(fallbackExecutor);
    }

    private static final class PublishOnlySignalOffloader implements SignalOffloader {

        private final SignalOffloader offloader;
        private final SignalOffloader fallback;

        PublishOnlySignalOffloader(final Executor publishOnExecutor, final Executor fallbackExecutor) {
            offloader = newOffloaderFor(publishOnExecutor);
            fallback = newOffloaderFor(fallbackExecutor);
        }

        @Override
        public <T> Subscriber<? super T> offloadSubscriber(final Subscriber<? super T> subscriber) {
            return offloader.offloadSubscriber(subscriber);
        }

        @Override
        public <T> SingleSource.Subscriber<? super T> offloadSubscriber(
                final SingleSource.Subscriber<? super T> subscriber) {
            return offloader.offloadSubscriber(subscriber);
        }

        @Override
        public CompletableSource.Subscriber offloadSubscriber(final CompletableSource.Subscriber subscriber) {
            return offloader.offloadSubscriber(subscriber);
        }

        @Override
        public <T> Subscriber<? super T> offloadSubscription(final Subscriber<? super T> subscriber) {
            return fallback.offloadSubscription(subscriber);
        }

        @Override
        public <T> SingleSource.Subscriber<? super T> offloadCancellable(
                final SingleSource.Subscriber<? super T> subscriber) {
            return fallback.offloadCancellable(subscriber);
        }

        @Override
        public CompletableSource.Subscriber offloadCancellable(final CompletableSource.Subscriber subscriber) {
            return fallback.offloadCancellable(subscriber);
        }

        @Override
        public <T> void offloadSubscribe(final Subscriber<? super T> subscriber,
                                         final Consumer<Subscriber<? super T>> handleSubscribe) {
            fallback.offloadSubscribe(subscriber, handleSubscribe);
        }

        @Override
        public <T> void offloadSubscribe(final SingleSource.Subscriber<? super T> subscriber,
                                         final Consumer<SingleSource.Subscriber<? super T>> handleSubscribe) {
            fallback.offloadSubscribe(subscriber, handleSubscribe);
        }

        @Override
        public void offloadSubscribe(final CompletableSource.Subscriber subscriber,
                                     final Consumer<CompletableSource.Subscriber> handleSubscribe) {
            fallback.offloadSubscribe(subscriber, handleSubscribe);
        }

        @Override
        public <T> void offloadSignal(final T signal, final Consumer<T> signalConsumer) {
            fallback.offloadSignal(signal, signalConsumer);
        }
    }
}
