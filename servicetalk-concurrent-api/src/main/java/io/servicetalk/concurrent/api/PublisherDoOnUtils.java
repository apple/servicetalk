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

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;

import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

final class PublisherDoOnUtils {
    private PublisherDoOnUtils() {
        // no instances
    }

    static <X> Supplier<Subscriber<? super X>> doOnSubscribeSupplier(Consumer<? super Subscription> onSubscribe) {
        requireNonNull(onSubscribe);
        Subscriber<X> subscriber = new Subscriber<X>() {
            @Override
            public void onSubscribe(Subscription s) {
                onSubscribe.accept(s);
            }

            @Override
            public void onNext(X t) {
                // NOOP
            }

            @Override
            public void onError(Throwable t) {
                // NOOP
            }

            @Override
            public void onComplete() {
                // NOOP
            }
        };
        return () -> subscriber;
    }

    static <X> Supplier<Subscriber<? super X>> doOnNextSupplier(Consumer<X> onNext) {
        requireNonNull(onNext);
        Subscriber<X> subscriber = new Subscriber<X>() {
            @Override
            public void onSubscribe(Subscription s) {
                // NOOP
            }

            @Override
            public void onNext(X t) {
                onNext.accept(t);
            }

            @Override
            public void onError(Throwable t) {
                // NOOP
            }

            @Override
            public void onComplete() {
                // NOOP
            }
        };
        return () -> subscriber;
    }

    static <X> Supplier<Subscriber<? super X>> doOnErrorSupplier(Consumer<Throwable> onError) {
        requireNonNull(onError);
        Subscriber<X> subscriber = new Subscriber<X>() {
            @Override
            public void onSubscribe(Subscription s) {
                // NOOP
            }

            @Override
            public void onNext(X t) {
                // NOOP
            }

            @Override
            public void onError(Throwable t) {
                onError.accept(t);
            }

            @Override
            public void onComplete() {
                // NOOP
            }
        };
        return () -> subscriber;
    }

    static <X> Supplier<Subscriber<? super X>> doOnCompleteSupplier(Runnable onComplete) {
        requireNonNull(onComplete);
        Subscriber<X> subscriber = new Subscriber<X>() {
            @Override
            public void onSubscribe(Subscription s) {
                // NOOP
            }

            @Override
            public void onNext(X t) {
                // NOOP
            }

            @Override
            public void onError(Throwable t) {
                // NOOP
            }

            @Override
            public void onComplete() {
                onComplete.run();
            }
        };
        return () -> subscriber;
    }

    static Supplier<Subscription> doOnRequestSupplier(LongConsumer onRequest) {
        requireNonNull(onRequest);
        Subscription subscription = new Subscription() {
            @Override
            public void request(long n) {
                onRequest.accept(n);
            }

            @Override
            public void cancel() {
                // NOOP
            }
        };
        return () -> subscription;
    }

    static Supplier<Subscription> doOnCancelSupplier(Runnable onCancel) {
        requireNonNull(onCancel);
        Subscription subscription = new Subscription() {
            @Override
            public void request(long n) {
                // NOOP
            }

            @Override
            public void cancel() {
                onCancel.run();
            }
        };
        return () -> subscription;
    }
}
