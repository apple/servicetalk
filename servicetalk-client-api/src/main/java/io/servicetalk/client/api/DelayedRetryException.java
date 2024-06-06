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
package io.servicetalk.client.api;

import java.time.Duration;

/**
 * An interface that enhances any {@link Throwable exception} to provide a constant {@link Duration delay} to be applied
 * when it needs to be retried.
 */
public interface DelayedRetryException {

    /**
     * A constant delay to apply.
     *
     * @return The {@link Duration} to apply as constant delay when retrying
     */
    Duration delay();

    /**
     * Returns original {@link Throwable} this {@link DelayedRetryException} represents.
     *
     * @return the original {@link Throwable}
     */
    Throwable throwable();
}
