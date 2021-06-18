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

import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

final class PubToSingleFirstOrElse<T> extends AbstractPubToSingle<T> {

    private final Supplier<T> defaultValueSupplier;

    PubToSingleFirstOrElse(Publisher<T> source, final Supplier<T> defaultValueSupplier) {
        super(source);
        this.defaultValueSupplier = requireNonNull(defaultValueSupplier);
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
                try {
                    return defaultValueSupplier.get();
                } catch (Throwable t) {
                    return t;
                }
            }

            @Override
            public void onNext(T t) {
                assert subscription != null : "Subscription can not be null.";
                // Since we are in onNext, if cancel() throws, we will get an onError from the source.
                // No need to add specific exception handling here.
                subscription.cancel();

                terminate(t);
            }
        };
    }
}
