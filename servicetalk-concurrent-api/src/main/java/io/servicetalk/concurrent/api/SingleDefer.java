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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static java.util.Objects.requireNonNull;

/**
 * As returned by {@link Single#defer(Supplier)}.
 *
 * @param <T> Type of result of this {@link Single}.
 */
final class SingleDefer<T> extends Single<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SingleDefer.class);

    private final Supplier<? extends Single<T>> singleFactory;

    SingleDefer(Supplier<? extends Single<T>> singleFactory) {
        this.singleFactory = requireNonNull(singleFactory);
    }

    @Override
    protected void handleSubscribe(Subscriber<? super T> subscriber) {
        final Single<T> single;
        try {
            single = requireNonNull(singleFactory.get());
        } catch (Throwable cause) {
            try {
                subscriber.onSubscribe(IGNORE_CANCEL);
            } catch (Throwable t) {
                LOGGER.debug("Ignoring exception from onSubscribe of Subscriber {}.", subscriber, t);
                return;
            }
            try {
                subscriber.onError(cause);
            } catch (Throwable t) {
                LOGGER.debug("Ignoring exception from onError of Subscriber {}.", subscriber, t);
            }
            return;
        }
        // There are technically two sources, this one and the one returned by the factory.
        // Since, we are invoking user code (singleFactory) we need this method to be run using an Executor
        // and also use the configured Executor for subscribing to the Single returned from singleFactory.
        single.subscribe(subscriber);
    }
}
