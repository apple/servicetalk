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

import io.servicetalk.concurrent.Cancellable;

import org.reactivestreams.Subscriber;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.Executors.from;
import static io.servicetalk.concurrent.api.ImmediateExecutor.NoopSignalOffloader.NOOP_SIGNAL_OFFLOADER;

final class ImmediateExecutor extends AbstractOffloaderAwareExecutor {

    private static final Executor IMMEDIATE = from(Runnable::run);
    static final OffloaderAwareExecutor IMMEDIATE_EXECUTOR = new ImmediateExecutor();

    private ImmediateExecutor() {
        // No instances
    }

    @Override
    public SignalOffloader newOffloader() {
        return NOOP_SIGNAL_OFFLOADER;
    }

    @Override
    public Cancellable execute(final Runnable task) throws RejectedExecutionException {
        task.run();
        return IGNORE_CANCEL;
    }

    @Override
    public Cancellable schedule(final Runnable task, final long delay, final TimeUnit unit)
            throws RejectedExecutionException {
        return IMMEDIATE.schedule(task, delay, unit);
    }

    @Override
    void doClose() {
        // Noop
    }

    static final class NoopSignalOffloader implements SignalOffloader {

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
        public <T> void offloadSubscribe(final Subscriber<? super T> subscriber, final Publisher<T> publisher) {
            publisher.handleSubscribe(subscriber);
        }

        @Override
        public <T> void offloadSubscribe(final Single.Subscriber<? super T> subscriber, final Single<T> single) {
            single.handleSubscribe(subscriber);
        }

        @Override
        public void offloadSubscribe(final Completable.Subscriber subscriber, final Completable completable) {
            completable.handleSubscribe(subscriber);
        }

        @Override
        public <T> void offloadSignal(T signal, Consumer<T> signalConsumer) {
            signalConsumer.accept(signal);
        }

        @Override
        public boolean isInOffloadThreadForPublish() {
            return true;
        }

        @Override
        public boolean isInOffloadThreadForSubscribe() {
            return true;
        }
    }
}
