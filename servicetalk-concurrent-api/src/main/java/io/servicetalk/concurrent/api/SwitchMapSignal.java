/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.PublisherSource.Subscriber;

import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * A signal containing the data from a series of {@link Publisher}s switched in a serial fashion.
 * @param <T> Type of the data.
 * @see Publisher#switchMap(Function)
 */
public interface SwitchMapSignal<T> {
    /**
     * Returns {@code true} on the first signal from a new {@link Publisher}.
     * @return {@code true} on the first signal from a new {@link Publisher}.
     */
    boolean isSwitched();

    /**
     * Get the data that was delivered to {@link Subscriber#onNext(Object)}.
     * @return the data that was delivered to {@link Subscriber#onNext(Object)}.
     */
    @Nullable
    T onNext();

    /**
     * Convert from a regular {@link Function} to a {@link Function} that emits {@link SwitchMapSignal}.
     * <p>
     * <b>This function has state</b>, if used in an operator chain use {@link Publisher#defer(Supplier)} so the state
     * is unique per each subscribe.
     * @param function The original function to convert.
     * @param <T> The input data type.
     * @param <R> The resulting data type.
     * @return a {@link Function} that emits {@link SwitchMapSignal}.
     */
    static <T, R> Function<T, Publisher<? extends SwitchMapSignal<R>>> toSwitchFunction(
            Function<? super T, ? extends Publisher<? extends R>> function) {
        return new Function<T, Publisher<? extends SwitchMapSignal<R>>>() {
            private boolean seenFirstPublisher;

            @Nullable
            @Override
            public Publisher<? extends SwitchMapSignal<R>> apply(T t) {
                final Publisher<? extends R> rawPublisher = function.apply(t);
                if (rawPublisher == null) {
                    return null;
                }
                final boolean localSeenFirstPublisher = seenFirstPublisher;
                seenFirstPublisher = true;
                if (localSeenFirstPublisher) {
                    return Publisher.defer(() -> {
                        final boolean[] seenOnNext = new boolean[1]; // modifiable boolean
                        return rawPublisher.map(res -> {
                            final boolean localSeenOnNext = seenOnNext[0];
                            seenOnNext[0] = true;
                            return new SwitchMapSignal<R>() {
                                @Override
                                public boolean isSwitched() {
                                    return !localSeenOnNext;
                                }

                                @Nullable
                                @Override
                                public R onNext() {
                                    return res;
                                }
                            };
                        }).shareContextOnSubscribe();
                    });
                } else {
                    return rawPublisher.map(res -> new SwitchMapSignal<R>() {
                        @Override
                        public boolean isSwitched() {
                            return false;
                        }

                        @Nullable
                        @Override
                        public R onNext() {
                            return res;
                        }
                    });
                }
            }
        };
    }
}
