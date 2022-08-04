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
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;

import static io.servicetalk.client.api.internal.RequestConcurrencyController.Result.Accepted;
import static io.servicetalk.client.api.internal.RequestConcurrencyController.Result.RejectedPermanently;
import static io.servicetalk.client.api.internal.RequestConcurrencyController.Result.RejectedTemporary;

@Deprecated // FIXME: 0.43 - remove deprecated class
final class ReservableRequestConcurrencyControllerOnlySingle extends AbstractReservableRequestConcurrencyController {
    ReservableRequestConcurrencyControllerOnlySingle(final Publisher<? extends ConsumableEvent<Integer>> maxConcurrency,
                                                     final Completable onClosing) {
        super(maxConcurrency, onClosing);
    }

    @Override
    public Result tryRequest() {
        // No concurrency means we have to have 0 requests!
        if (lastSeenMaxValue(1) > 0) {
            if (casPendingRequests(0, 1)) {
                return Accepted;
            }
            return RejectedTemporary;
        }
        return RejectedPermanently;
    }
}
