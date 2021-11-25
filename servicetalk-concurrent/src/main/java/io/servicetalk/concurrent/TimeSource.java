/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent;

import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * A source of time that can be represented as a {@code long}.
 */
@FunctionalInterface
public interface TimeSource {

    /**
     * Get the current time. The units are determined by {@link #currentTimeUnits()}.
     * @return The current time. The units are determined by {@link #currentTimeUnits()}.
     */
    long currentTime();

    /**
     * Get the units for {@link #currentTime()}.
     * @return The units for {@link #currentTime()}.
     */
    default TimeUnit currentTimeUnits() {
        return NANOSECONDS;
    }
}
