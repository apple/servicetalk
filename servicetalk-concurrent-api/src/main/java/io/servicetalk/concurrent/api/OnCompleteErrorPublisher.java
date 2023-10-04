/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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

import java.util.function.Supplier;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

final class OnCompleteErrorPublisher<T> extends AbstractSynchronousPublisherOperator<T, T> {
    private final Supplier<? extends Throwable> errorSupplier;

    OnCompleteErrorPublisher(final Publisher<T> original, final Supplier<? extends Throwable> errorSupplier) {
        super(original);
        this.errorSupplier = requireNonNull(errorSupplier);
    }

    @Override
    public Subscriber<? super T> apply(final Subscriber<? super T> subscriber) {
        return new OnCompleteErrorSubscriber<>(subscriber, errorSupplier);
    }

    private static final class OnCompleteErrorSubscriber<T> implements Subscriber<T> {
        private final Subscriber<? super T> subscriber;
        private final Supplier<? extends Throwable> errorSupplier;

        private OnCompleteErrorSubscriber(final Subscriber<? super T> subscriber,
                                          final Supplier<? extends Throwable> errorSupplier) {
            this.subscriber = subscriber;
            this.errorSupplier = errorSupplier;
        }

        @Override
        public void onSubscribe(final Subscription subscription) {
            subscriber.onSubscribe(subscription);
        }

        @Override
        public void onNext(@Nullable final T t) {
            subscriber.onNext(t);
        }

        @Override
        public void onError(final Throwable t) {
            subscriber.onError(t);
        }

        @Override
        public void onComplete() {
            final Throwable cause;
            try {
                cause = errorSupplier.get();
            } catch (Throwable cause2) {
                subscriber.onError(cause2);
                return;
            }
            if (cause == null) {
                subscriber.onComplete();
            } else {
                subscriber.onError(cause);
            }
        }
    }
}
