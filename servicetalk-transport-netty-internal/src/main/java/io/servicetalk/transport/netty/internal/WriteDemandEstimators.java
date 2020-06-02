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
package io.servicetalk.transport.netty.internal;

/**
 * Utility methods associated with {@link WriteDemandEstimator}.
 */
public final class WriteDemandEstimators {
    private WriteDemandEstimators() {
        // no instance
    }

    /**
     * Returns a new instance of a default implementation of {@link WriteDemandEstimator}.
     *
     * @return A new instance of a default implementation of {@link WriteDemandEstimator}.
     */
    public static WriteDemandEstimator newDefaultEstimator() {
        return new EWMAWriteDemandEstimator();
    }
}
