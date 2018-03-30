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

/**
 * A factory for {@link InOrderExecutor}s.
 */
final class InOrderExecutors {

    private InOrderExecutors() {
        // No instances.
    }

    /**
     * Wraps the passed {@link Executor} and creates a new {@link InOrderExecutor}.
     *
     * @param original {@link Executor} to wrap.
     * @return Newly created {@link InOrderExecutor}.
     */
    static InOrderExecutor newOrderedExecutor(Executor original) {
        // TODO: Implement in-order execution
        return new InOrderExecutor() {

            private final CompletableProcessor onClose = new CompletableProcessor();
            private final Completable close = new Completable() {
                @Override
                protected void handleSubscribe(Subscriber subscriber) {
                    onClose.onComplete();
                    onClose.subscribe(subscriber);
                }
            };

            @Override
            public <T> Subscriber<? super T> wrap(Subscriber<? super T> subscriber) {
                return subscriber;
            }

            @Override
            public <T> Single.Subscriber<? super T> wrap(Single.Subscriber<? super T> subscriber) {
                return subscriber;
            }

            @Override
            public Completable.Subscriber wrap(Completable.Subscriber subscriber) {
                return subscriber;
            }

            @Override
            public Cancellable execute(Runnable task) throws RejectedExecutionException {
                return original.execute(task);
            }

            @Override
            public Completable schedule(long duration, TimeUnit durationUnit) {
                return original.schedule(duration, durationUnit);
            }

            @Override
            public Completable onClose() {
                return onClose;
            }

            @Override
            public Completable closeAsync() {
                return close;
            }
        };
    }
}
