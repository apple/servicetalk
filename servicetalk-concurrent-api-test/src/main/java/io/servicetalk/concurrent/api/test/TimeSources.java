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

final class TimeSources {
    private static final TimeSource SYSTEM_NANO = System::nanoTime;
    private static final NormalizedTimeSource SYSTEM_NANO_NORMAL = normalize(SYSTEM_NANO);

    private TimeSources() {
    }

    static TimeSource nanoTime() {
        return SYSTEM_NANO;
    }

    static NormalizedTimeSource nanoTimeNormalized() {
        return SYSTEM_NANO_NORMAL;
    }

    static NormalizedTimeSource normalize(TimeSource source) {
        return source instanceof NormalizedTimeSource ? (NormalizedTimeSource) source :
                new DefaultNormalizedTimeSource(source);
    }
}
