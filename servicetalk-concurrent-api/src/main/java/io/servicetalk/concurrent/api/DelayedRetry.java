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
package io.servicetalk.concurrent.api;

import java.time.Duration;

/**
 * A marker interface that enhances any type to provide a {@link Duration offset-delay} to be applied
 * in the {@link io.servicetalk.concurrent.api.Publisher#retryWhen(TriLongIntFunction)} or
 * {@link io.servicetalk.concurrent.api.Single#retryWhen(TriLongIntFunction)} or
 * {@link io.servicetalk.concurrent.api.Completable#retryWhen(TriLongIntFunction)} logic.
 */
public interface DelayedRetry {

    /**
     * The offset-delay to apply in milliseconds.
     * @return The {@link Duration} to apply as offset delay when retrying.
     */
    long delayMillis();
}
