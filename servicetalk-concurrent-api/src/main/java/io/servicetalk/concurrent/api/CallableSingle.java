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

import io.servicetalk.concurrent.internal.ThreadInterruptingCancellable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;

import static io.servicetalk.concurrent.internal.SubscriberUtils.handleExceptionFromOnSubscribe;
import static java.lang.Thread.currentThread;
import static java.util.Objects.requireNonNull;

final class CallableSingle<T> extends AbstractSynchronousSingle<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CallableSingle.class);
    private final Callable<T> callable;

    CallableSingle(final Callable<T> callable) {
        this.callable = requireNonNull(callable);
    }

    @Override
    void doSubscribe(final Subscriber<? super T> subscriber) {
        final ThreadInterruptingCancellable cancellable = new ThreadInterruptingCancellable(currentThread());
        try {
            subscriber.onSubscribe(cancellable);
        } catch (Throwable t) {
            handleExceptionFromOnSubscribe(subscriber, t);
            return;
        }

        try {
            final T value = callable.call();

            try {
                cancellable.setDone();
                subscriber.onSuccess(value);
            } catch (Throwable t) {
                LOGGER.info("Ignoring exception from onSuccess of Subscriber {}.", subscriber, t);
            }
        } catch (Throwable t) {
            cancellable.setDone(t);
            subscriber.onError(t);
        }
    }
}
