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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.internal.SequentialCancellable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

final class SimpleSingleSubscriber<T> extends SequentialCancellable implements SingleSource.Subscriber<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleSingleSubscriber.class);

    private final Consumer<? super T> resultConsumer;
    @Nullable
    private final Consumer<? super Throwable> errorConsumer;

    SimpleSingleSubscriber(final Consumer<? super T> resultConsumer) {
        this(resultConsumer, null);
    }

    SimpleSingleSubscriber(final Consumer<? super T> resultConsumer,
                           @Nullable final Consumer<? super Throwable> errorConsumer) {
        this.resultConsumer = requireNonNull(resultConsumer);
        this.errorConsumer = errorConsumer;
    }

    @Override
    public void onSubscribe(Cancellable cancellable) {
        nextCancellable(cancellable);
    }

    @Override
    public void onSuccess(@Nullable T result) {
        try {
            resultConsumer.accept(result);
        } catch (Throwable t) {
            LOGGER.debug("Received exception from the result consumer {}.", resultConsumer, t);
        }
    }

    @Override
    public void onError(Throwable t) {
        if (errorConsumer != null) {
            errorConsumer.accept(t);
        } else {
            LOGGER.debug("Received exception from the source.", t);
        }
    }
}
