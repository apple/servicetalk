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

import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * As returned by {@link Completable#defer(Supplier)}.
 */
final class CompletableDefer extends Completable {

    private final Supplier<Completable> completableFactory;

    CompletableDefer(Supplier<Completable> completableFactory) {
        this.completableFactory = requireNonNull(completableFactory);
    }

    @Override
    protected void handleSubscribe(Subscriber subscriber) {
        // There are technically two sources, one this Completable and the other returned by completableFactory.
        // Since, we are invoking user code (completableFactory) we need this method to be run using an Executor
        // and also use the configured Executor for subscribing to the Completable returned from completableFactory
        completableFactory.get().subscribe(subscriber);
    }
}
