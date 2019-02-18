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
package io.servicetalk.client.internal;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;

import static io.servicetalk.client.internal.RequestConcurrencyController.Result.Accepted;
import static io.servicetalk.client.internal.RequestConcurrencyController.Result.RejectedPermanently;
import static io.servicetalk.client.internal.RequestConcurrencyController.Result.RejectedTemporary;

final class RequestConcurrencyControllerMulti extends AbstractRequestConcurrencyController {
    private final int maxRequests;

    RequestConcurrencyControllerMulti(final Publisher<Integer> maxConcurrencySettingStream,
                                      final Completable closeable,
                                      int maxRequests) {
        super(maxConcurrencySettingStream, closeable);
        this.maxRequests = maxRequests;
    }

    @Override
    public Result tryRequest() {
        final int maxConcurrency = lastSeenMaxValue(maxRequests);
        for (;;) {
            final int currentPending = pendingRequests();
            if (currentPending < 0) {
                return RejectedPermanently;
            }
            if (currentPending >= maxConcurrency) {
                return RejectedTemporary;
            }
            if (casPendingRequests(currentPending, currentPending + 1)) {
                return Accepted;
            }
        }
    }
}
