/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.SingleSource;

import javax.annotation.Nullable;

class SingleSubscriber<T> implements SingleSource.Subscriber<T> {

    private final Subscriber<T> subscriber;
    private final Subscription subscription;

    SingleSubscriber(final Subscriber<T> subscriber, final Subscription subscription) {
        this.subscriber = subscriber;
        this.subscription = subscription;
    }

    @Override
    public void onSubscribe(final Cancellable cancellable) {
        subscriber.onSubscribe(new Subscription() {
            @Override
            public void request(final long n) {
                // ignored
            }

            @Override
            public void cancel() {
                cancellable.cancel();
            }
        });
        subscription.request(1);
    }

    @Override
    public void onSuccess(@Nullable final T result) {
        subscriber.onNext(result);
        subscriber.onComplete();
    }

    @Override
    public void onError(final Throwable t) {
        subscriber.onError(t);
    }
}
