/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api.test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

final class DefaultModifiableTimeSource implements ModifiableTimeSource, NormalizedTimeSource {
    /**
     * Normalize time to MIN_VALUE, which allows to add durations as offsets without worrying about wrap around.
     */
    static final long MIN_TIME = Long.MIN_VALUE;
    private final AtomicLong timeNs = new AtomicLong(MIN_TIME);

    @Override
    public long currentTime() {
        return timeNs.get();
    }

    @Override
    public void incrementCurrentTime(long duration, TimeUnit unit) {
        timeNs.addAndGet(NANOSECONDS.convert(duration, unit));
    }

    @Override
    public void incrementCurrentTime(Duration duration) {
        timeNs.addAndGet(duration.toNanos());
    }
}
