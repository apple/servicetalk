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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Future;

import static java.util.Objects.requireNonNull;

final class FutureToSingle<T> extends Single<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(FutureToSingle.class);
    private final Future<T> future;

    FutureToSingle(Future<T> future) {
        this.future = requireNonNull(future);
    }

    @Override
    protected void handleSubscribe(final Subscriber<? super T> subscriber) {
        try {
            subscriber.onSubscribe(() -> future.cancel(true));
        } catch (Throwable t) {
            LOGGER.debug("Ignoring exception from onSubscribe of Subscriber {}.", subscriber, t);
        }

        final T value;
        try {
            value = future.get();
        } catch (Throwable cause) {
            subscriber.onError(cause);
            return;
        }
        try {
            subscriber.onSuccess(value);
        } catch (Throwable t) {
            LOGGER.debug("Ignoring exception from onSuccess of Subscriber {}.", subscriber, t);
        }
    }
}
