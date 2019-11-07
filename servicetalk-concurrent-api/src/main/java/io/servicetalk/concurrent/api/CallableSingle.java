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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.Callable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.internal.SubscriberUtils.handleExceptionFromOnSubscribe;

final class CallableSingle<T> extends AbstractSynchronousSingle<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CallableSingle.class);
    private final Callable<T> callable;

    CallableSingle(final Callable<T> callable) {
        Objects.requireNonNull(callable);
        this.callable = callable;
    }

    @Override
    void doSubscribe(final Subscriber<? super T> subscriber) {
        try {
            subscriber.onSubscribe(IGNORE_CANCEL);
        } catch (Throwable t) {
            handleExceptionFromOnSubscribe(subscriber, t);
            return;
        }
        try {
            final T value = callable.call();

            try {
                subscriber.onSuccess(value);
            } catch (Throwable t) {
                LOGGER.info("Ignoring exception from onSuccess of Subscriber {}.", subscriber, t);
            }
        } catch (Throwable t) {
            subscriber.onError(t);
        }
    }
}
