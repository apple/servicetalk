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
package io.servicetalk.apple.capacity.limiter.api;

import io.servicetalk.context.api.ContextMap;

import javax.annotation.Nullable;

final class AllowAllCapacityLimiter implements CapacityLimiter {
    static final CapacityLimiter INSTANCE = new AllowAllCapacityLimiter();
    private static final int UNSUPPORTED = -1;
    private final Ticket noOpToken = new Ticket() {
        @Override
        public LimiterState state() {
            return null;
        }

        @Override
        public int completed() {
            return UNSUPPORTED;
        }

        @Override
        public int dropped() {
            return UNSUPPORTED;
        }

        @Override
        public int failed(@Nullable final Throwable error) {
            return UNSUPPORTED;
        }

        @Override
        public int ignored() {
            return UNSUPPORTED;
        }
    };

    private AllowAllCapacityLimiter() {
    }

    @Override
    public String name() {
        return AllowAllCapacityLimiter.class.getSimpleName();
    }

    @Override
    public Ticket tryAcquire(final Classification classification, @Nullable final ContextMap context) {
        return noOpToken;
    }

    @Override
    public String toString() {
        return AllowAllCapacityLimiter.class.getSimpleName();
    }
}
