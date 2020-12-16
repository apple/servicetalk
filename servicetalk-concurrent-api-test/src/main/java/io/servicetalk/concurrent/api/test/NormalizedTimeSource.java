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

import static io.servicetalk.concurrent.api.test.TimeUtils.convert;

interface NormalizedTimeSource extends TimeSource {
    default boolean isExpired(long timeStamp) {
        return currentTime() <= timeStamp;
    }

    default long currentTimePlus(long duration, TimeUnit unit) {
        return timeStampPlus(currentTime(), duration, unit);
    }

    default long currentTimePlus(Duration duration) {
        return timeStampPlus(currentTime(), duration);
    }

    default long timeStampPlus(long timeStamp, long duration, TimeUnit unit) {
        return timeStamp + currentTimeUnits().convert(duration, unit);
    }

    default long timeStampPlus(long timeStamp, Duration duration) {
        return timeStamp + convert(currentTimeUnits(), duration);
    }
}
