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
package io.servicetalk.http.utils;

import java.time.Duration;

/**
 * An interface that enhances any {@link Exception} to provide a constant {@link Duration delay} to be applied when
 * retrying through a {@link RetryingHttpRequesterFilter retrying-filter}.
 * <p>
 * Constant delay returned from {@link #delayMillis()} will only be considered if the
 * {@link RetryingHttpRequesterFilter.Builder#evaluateDelayedRetries(boolean)} is set to {@code true}.
 */
public interface DelayedRetry {

    /**
     * A constant delay to apply in milliseconds.
     * The total delay for the retry logic will be the sum of this value and the result of the
     * {@link io.servicetalk.concurrent.api.RetryStrategies retry-strategy} in-use. Consider using 'full-jitter'
     * flavours from the {@link io.servicetalk.concurrent.api.RetryStrategies retry-strategies} to avoid having
     * another constant delay applied per-retry.
     *
     * @return The {@link Duration} to apply as constant delay when retrying.
     */
    long delayMillis();
}
