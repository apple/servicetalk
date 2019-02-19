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
package io.servicetalk.concurrent.internal;

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;
import static java.util.Objects.requireNonNull;

/**
 * A {@link Subscription} that only emits a single value.
 *
 * @param <T> Type of value emitted by this {@link Subscription}.
 */
public final class ScalarValueSubscription<T> implements Subscription {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScalarValueSubscription.class);

    @Nullable
    private final T value;
    private final Subscriber<? super T> subscriber;

    private boolean deliveredData;

    /**
     * New instance.
     *
     * @param value to be emitted by this {@link Subscription}.
     * @param subscriber to emit the value to when requested.
     */
    public ScalarValueSubscription(@Nullable T value, Subscriber<? super T> subscriber) {
        this.value = value;
        this.subscriber = requireNonNull(subscriber);
    }

    @Override
    public void request(long n) {
        if (!deliveredData) {
            deliveredData = true;
            if (isRequestNValid(n)) {
                try {
                    subscriber.onNext(value);
                } catch (Throwable cause) {
                    try {
                        subscriber.onError(cause);
                    } catch (Throwable t) {
                        LOGGER.debug("Ignoring exception from onError of Subscriber {}.", subscriber, t);
                    }
                    return;
                }
                try {
                    subscriber.onComplete();
                } catch (Throwable t) {
                    LOGGER.debug("Ignoring exception from onComplete of Subscriber {}.", subscriber, t);
                }
            } else {
                try {
                    subscriber.onError(newExceptionForInvalidRequestN(n));
                } catch (Throwable t) {
                    LOGGER.debug("Ignoring exception from onError of Subscriber {}.", subscriber, t);
                }
            }
        }
    }

    @Override
    public void cancel() {
        deliveredData = true;
    }
}
