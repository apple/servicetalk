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

import io.servicetalk.concurrent.PublisherSource;

import java.util.NoSuchElementException;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SubscriberApiUtils.wrapNull;

final class PubFirstOrError<T> extends AbstractPubToSingle<T> {

    PubFirstOrError(Publisher<T> source) {
        super(source);
    }

    @Override
    PublisherSource.Subscriber<T> newSubscriber(final Subscriber<? super T> original) {
        return new AbstractPubToSingleSubscriber<T>(original) {
            @Nullable
            private Object lastValue;

            @Override
            int numberOfItemsToRequest() {
                // Request 2 items because we want to see if there are multiple items before termination.
                return 2;
            }

            @Override
            public void onNext(T t) {
                if (lastValue == null) {
                    lastValue = wrapNull(t);
                } else {
                    assert subscription != null;
                    // Since we are in onNext, if cancel() throws, we will get an onError from the source.
                    // No need to add specific exception handling here.
                    subscription.cancel();
                    terminate(new IllegalArgumentException(
                            "Only a single item expected, but saw the second value: " + t));
                }
            }

            @Override
            Object terminalSignalForComplete() {
                return lastValue == null ? new NoSuchElementException() : lastValue;
            }

            @Override
            void terminate(final Object terminal) {
                try {
                    super.terminate(terminal);
                } finally {
                    // De-reference the last value to allow for GC.
                    lastValue = null;
                }
            }
        };
    }
}
