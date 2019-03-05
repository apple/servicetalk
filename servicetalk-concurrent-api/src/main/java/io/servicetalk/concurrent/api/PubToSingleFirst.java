/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

import java.util.NoSuchElementException;

final class PubToSingleFirst<T> extends AbstractPubToSingle<T> {

    /**
     * New instance.
     *
     * @param source {@link Publisher} for this {@link Single}.
     */
    PubToSingleFirst(Publisher<T> source) {
        super(source.executor(), source);
    }

    @Override
    PublisherSource.Subscriber<T> newSubscriber(final Subscriber<? super T> original) {
        return new AbstractPubToSingleSubscriber<T>(original) {
            @Override
            int numberOfItemsToRequest() {
                return 1;
            }

            @Override
            Object terminalSignalForComplete() {
                return new NoSuchElementException();
            }

            @Override
            public void onNext(T t) {
                assert subscription != null : "Subscription can not be null.";
                subscription.cancel();

                terminate(t);
            }
        };
    }
}
