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
import io.servicetalk.concurrent.CompletableSource.Subscriber;

import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

final class CompletableDoOnUtils {
    private CompletableDoOnUtils() {
        // no instances
    }

    static Supplier<Subscriber> doOnSubscribeSupplier(Consumer<Cancellable> onSubscribe) {
        requireNonNull(onSubscribe);
        Subscriber subscriber = new Subscriber() {
            @Override
            public void onSubscribe(Cancellable cancellable) {
                onSubscribe.accept(cancellable);
            }

            @Override
            public void onComplete() {
                // NOOP
            }

            @Override
            public void onError(Throwable t) {
                // NOOP
            }
        };
        return () -> subscriber;
    }

    static Supplier<Subscriber> doOnCompleteSupplier(Runnable onComplete) {
        requireNonNull(onComplete);
        Subscriber subscriber = new Subscriber() {
            @Override
            public void onSubscribe(Cancellable cancellable) {
                // NOOP
            }

            @Override
            public void onComplete() {
                onComplete.run();
            }

            @Override
            public void onError(Throwable t) {
                // NOOP
            }
        };
        return () -> subscriber;
    }

    static Supplier<Subscriber> doOnErrorSupplier(Consumer<Throwable> onError) {
        requireNonNull(onError);
        Subscriber subscriber = new Subscriber() {
            @Override
            public void onSubscribe(Cancellable cancellable) {
                // NOOP
            }

            @Override
            public void onComplete() {
                // NOOP
            }

            @Override
            public void onError(Throwable t) {
                onError.accept(t);
            }
        };
        return () -> subscriber;
    }
}
