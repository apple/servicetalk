/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.traffic.resilience.http;

import io.servicetalk.capacity.limiter.api.CapacityLimiter;
import io.servicetalk.capacity.limiter.api.Classification;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.StreamingHttpRequest;

import javax.annotation.Nullable;

final class NoOpTrafficResiliencyObserver implements TrafficResiliencyObserver {

    static final NoOpTrafficResiliencyObserver INSTANCE = new NoOpTrafficResiliencyObserver();
    static final TicketObserver NO_OP_TICKET_OBSERVER = new NoOpTicketObserver();

    private NoOpTrafficResiliencyObserver() {
        // single instance
    }

    @Override
    public void onRejectedUnmatchedPartition(final StreamingHttpRequest request) {
    }

    @Override
    public void onRejectedLimit(final StreamingHttpRequest request, final String limiterName,
                                final ContextMap meta, final Classification classification) {
    }

    @Override
    public void onRejectedOpenCircuit(final StreamingHttpRequest request, final String breakerName,
                                      final ContextMap meta, final Classification classification) {
    }

    @Override
    public TicketObserver onAllowedThrough(final StreamingHttpRequest request,
                                           @Nullable final CapacityLimiter.LimiterState state) {
        return NO_OP_TICKET_OBSERVER;
    }

    static final class NoOpTicketObserver implements TicketObserver {

        private NoOpTicketObserver() {
        }

        @Override
        public void onComplete() {
        }

        @Override
        public void onCancel() {
        }

        @Override
        public void onError(final Throwable throwable) {
        }
    }
}
