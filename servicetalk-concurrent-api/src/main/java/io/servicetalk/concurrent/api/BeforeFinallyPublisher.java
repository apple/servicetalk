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

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static java.util.Objects.requireNonNull;

final class BeforeFinallyPublisher<T> extends AbstractSynchronousPublisherOperator<T, T> {

    private final Runnable runnable;

    BeforeFinallyPublisher(Publisher<T> original, Runnable runnable, Executor executor) {
        super(original, executor);
        this.runnable = requireNonNull(runnable);
    }

    @Override
    public Subscriber<? super T> apply(Subscriber<? super T> subscriber) {
        return new BeforeFinallyPublisherSubscriber<>(subscriber, runnable);
    }

    private static final class BeforeFinallyPublisherSubscriber<T> implements Subscriber<T> {
        private final Subscriber<? super T> original;
        private final Runnable runnable;

        private static final AtomicIntegerFieldUpdater<BeforeFinallyPublisherSubscriber> completeUpdater =
                AtomicIntegerFieldUpdater.newUpdater(BeforeFinallyPublisherSubscriber.class, "complete");
        @SuppressWarnings("unused")
        private volatile int complete;

        BeforeFinallyPublisherSubscriber(Subscriber<? super T> original, Runnable runnable) {
            this.original = original;
            this.runnable = runnable;
        }

        @Override
        public void onSubscribe(Subscription s) {
            original.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    s.request(n);
                }

                @Override
                public void cancel() {
                    try {
                        beforeFinally();
                    } finally {
                        s.cancel();
                    }
                }
            });
        }

        @Override
        public void onNext(T t) {
            original.onNext(t);
        }

        @Override
        public void onComplete() {
            try {
                beforeFinally();
            } catch (Throwable err) {
                original.onError(err);
                return;
            }
            original.onComplete();
        }

        @Override
        public void onError(Throwable cause) {
            try {
                beforeFinally();
            } catch (Throwable err) {
                err.addSuppressed(cause);
                original.onError(err);
                return;
            }
            original.onError(cause);
        }

        private void beforeFinally() {
            if (completeUpdater.compareAndSet(this, 0, 1)) {
                runnable.run();
            }
        }
    }
}
