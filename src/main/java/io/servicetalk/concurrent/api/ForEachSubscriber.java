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

import io.servicetalk.concurrent.internal.SequentialCancellable;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

final class ForEachSubscriber<T> extends SequentialCancellable implements Subscriber<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ForEachSubscriber.class);

    private final Consumer<T> forEach;

    ForEachSubscriber(Consumer<T> forEach) {
        this.forEach = requireNonNull(forEach);
    }

    @Override
    public void onSubscribe(Subscription s) {
        setNextCancellable(s::cancel);
        s.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(T t) {
        forEach.accept(t);
    }

    @Override
    public void onError(Throwable t) {
        LOGGER.debug("Received exception from the source.", t);
    }

    @Override
    public void onComplete() {
        // No op
    }
}
