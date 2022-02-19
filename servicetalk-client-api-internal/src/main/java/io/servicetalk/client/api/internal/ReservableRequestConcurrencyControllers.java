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
package io.servicetalk.client.api.internal;

import io.servicetalk.client.api.ConsumableEvent;
import io.servicetalk.client.api.RequestConcurrencyController;
import io.servicetalk.client.api.ReservableRequestConcurrencyController;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;

/**
 * Factory for common {@link ReservableRequestConcurrencyController}s.
 */
public final class ReservableRequestConcurrencyControllers {
    private ReservableRequestConcurrencyControllers() {
        // no instances
    }

    /**
     * Create a new instance of {@link ReservableRequestConcurrencyController}.
     * @param maxConcurrency A {@link Publisher} that provides the maximum allowed concurrency updates.
     * @param onClosing A {@link Completable} that when terminated no more calls to
     * {@link RequestConcurrencyController#tryRequest()} are expected to succeed.
     * @param initialMaxConcurrency The initial maximum value for concurrency, until {@code maxConcurrencySetting}
     * provides data.
     * @return a new instance of {@link ReservableRequestConcurrencyController}.
     */
    public static ReservableRequestConcurrencyController newController(
            final Publisher<? extends ConsumableEvent<Integer>> maxConcurrency, final Completable onClosing,
            final int initialMaxConcurrency) {
        return new ReservableRequestConcurrencyControllerMulti(maxConcurrency, onClosing, initialMaxConcurrency);
    }

    /**
     * Create a {@link ReservableRequestConcurrencyController} that only allows a single outstanding request. Even
     * if {@code maxConcurrencySetting} increases beyond {@code 1} only a single
     * {@link RequestConcurrencyController#tryRequest()} will succeed at any given time. The initial value is assumed
     * to be {@code 1} and only lesser values from {@code maxConcurrencySetting} will impact behavior.
     * @param maxConcurrency A {@link Publisher} that provides the maximum allowed concurrency updates.
     * Only values of {@code <1} will impact behavior.
     * @param onClosing A {@link Completable} that when terminated no more calls to
     * {@link RequestConcurrencyController#tryRequest()} are expected to succeed.
     * @return a {@link ReservableRequestConcurrencyController} that only allows a single outstanding request.
     */
    public static ReservableRequestConcurrencyController newSingleController(
            final Publisher<? extends ConsumableEvent<Integer>> maxConcurrency, final Completable onClosing) {
        return new ReservableRequestConcurrencyControllerOnlySingle(maxConcurrency, onClosing);
    }
}
