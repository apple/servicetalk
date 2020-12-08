/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

final class PubCompletableOrError<T> extends AbstractPubToCompletable<T> {
    PubCompletableOrError(Publisher<T> source) {
        super(source);
    }

    @Override
    PublisherSource.Subscriber<T> newSubscriber(Subscriber original) {
        return new PubToCompletableOrErrorSubscriber<>(original);
    }

    private static final class PubToCompletableOrErrorSubscriber<T> extends AbstractPubToCompletableSubscriber<T> {
        private boolean terminated;

        PubToCompletableOrErrorSubscriber(final Subscriber subscriber) {
            super(subscriber);
        }

        @Override
        public void onNext(@Nullable final T t) {
            if (!terminated) {
                terminated = true;
                try {
                    // Instead of throwing an exception directly we propagate here an cancel upstream. This is so
                    // upstream operators aren't exposed to the conversion error.
                    super.onError(new IllegalArgumentException("No onNext signals expected, but got: " + t));
                } finally {
                    cancel();
                }
            }
        }

        @Override
        public void onError(final Throwable t) {
            if (!terminated) {
                terminated = true;
                super.onError(t);
            }
        }

        @Override
        public void onComplete() {
            if (!terminated) {
                terminated = true;
                super.onComplete();
            }
        }
    }
}
