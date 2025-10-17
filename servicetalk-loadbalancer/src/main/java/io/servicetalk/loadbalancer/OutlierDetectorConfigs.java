/*
 * Copyright © 2025 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.loadbalancer;

import java.time.Duration;

/**
 * Useful default {@link OutlierDetectorConfig} values.
 *
 * The configuration values can be used as a base layer for configuration and then modified using the
 * {@link OutlierDetectorConfig.Builder} constructor as follows:
 * <pre>{@code
 *     OutlierDetectorConfig config = new OutlierDetectorConfig.Builder(disabled())
 *          .failedConnectionsThreshold(2)
 *          .build();
 * }</pre>
 */
public final class OutlierDetectorConfigs {

    private static final OutlierDetectorConfig L4_ONLY =
            new OutlierDetectorConfig.Builder()
                .ewmaHalfLife(Duration.ZERO)
                .enforcingFailurePercentage(0)
                .enforcingSuccessRate(0)
                .enforcingConsecutive5xx(0)
                .build();

    private static final OutlierDetectorConfig DISABLED =
            new OutlierDetectorConfig.Builder(L4_ONLY)
                    .failedConnectionsThreshold(-1)
                    .build();

    private OutlierDetectorConfigs() {
        // no instances
    }

    /**
     * {@link OutlierDetectorConfig} that only enables the default consecutive connection failure detection.
     * @return the {@link OutlierDetectorConfig}
     */
    public static OutlierDetectorConfig l4Only() {
        return L4_ONLY;
    }

    /**
     * {@link OutlierDetectorConfig} that disables all outlier detection.
     * @return the {@link OutlierDetectorConfig}
     */
    public static OutlierDetectorConfig disabled() {
        return DISABLED;
    }
}
