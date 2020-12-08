/*
 * Copyright © 2018-2020 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.PublisherSource;

import javax.annotation.Nullable;

/**
 * A {@link Completable} created from a {@link Publisher}.
 *
 * @param <T> Item type emitted from the original {@link Publisher}.
 */
final class PubToCompletableIgnore<T> extends AbstractPubToCompletable<T> {
    /**
     * New instance.
     *
     * @param source {@link Publisher} from which this {@link Completable} is created.
     */
    PubToCompletableIgnore(Publisher<T> source) {
        super(source);
    }

    @Override
    PublisherSource.Subscriber<T> newSubscriber(Subscriber original) {
        return new PubToCompletableIgnoreSubscriber<>(original);
    }

    private static final class PubToCompletableIgnoreSubscriber<T> extends AbstractPubToCompletableSubscriber<T> {
        PubToCompletableIgnoreSubscriber(final Subscriber subscriber) {
            super(subscriber);
        }

        @Override
        public void onNext(@Nullable final T t) {
            // Ignore elements
        }
    }
}
