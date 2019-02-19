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

    SimpleSingleSubscriber(Consumer<? super T> resultConsumer) {
        this.resultConsumer = requireNonNull(resultConsumer);
    }

    @Override
    public void onSubscribe(Cancellable cancellable) {
        setNextCancellable(cancellable);
    }

    @Override
    public void onSuccess(@Nullable T result) {
        resultConsumer.accept(result);
    }

    @Override
    public void onError(Throwable t) {
        LOGGER.debug("Received exception from the source.", t);
    }
}
