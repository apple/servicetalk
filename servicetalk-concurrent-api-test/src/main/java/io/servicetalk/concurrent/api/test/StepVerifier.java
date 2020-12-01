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

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscriber;

import java.time.Duration;

/**
 * Provides the ability to verify expectations on asynchronous sources.
 */
public interface StepVerifier {
    /**
     * Verify the none of the previously declared expectations are violated.
     * <p>
     * This method will trigger a {@link PublisherSource#subscribe(Subscriber) subscribe} operation.
     * @return The amount of time the declared expectations took to complete.
     * @throws AssertionError if any of the declared expectations fail.
     */
    Duration verify() throws AssertionError;

    /**
     * Verify the none of the previously declared expectations are violated.
     * <p>
     * This method will trigger a {@link PublisherSource#subscribe(Subscriber) subscribe} operation.
     * @param duration The amount of time to wait while executing declared expectations.
     * @return The amount of time the declared expectations took to complete.
     * @throws AssertionError if any of the declared expectations fail, or if the {@code duration} expires.
     */
    Duration verify(Duration duration) throws AssertionError;
}
