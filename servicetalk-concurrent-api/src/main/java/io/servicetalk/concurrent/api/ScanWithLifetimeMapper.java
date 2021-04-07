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

import io.servicetalk.concurrent.PublisherSource;

import java.util.function.Supplier;

/**
 * Provides the ability to transform (aka map) signals emitted via
 * the {@link Publisher#scanWithLifetime(Supplier)} operator, as well as the ability to cleanup state
 * via {@link #afterFinally}.
 * @param <T> Type of items emitted by the {@link Publisher} this operator is applied.
 * @param <R> Type of items emitted by this operator.
 */
public interface ScanWithLifetimeMapper<T, R> extends ScanWithMapper<T, R> {

    /**
     * Invoked after a terminal signal {@link PublisherSource.Subscriber#onError(Throwable)} or
     * {@link PublisherSource.Subscriber#onComplete()} or {@link PublisherSource.Subscription#cancel()}.
     * No further interaction will occur with the {@link ScanWithLifetimeMapper} to prevent use-after-free
     * on internal state.
     */
    void afterFinally();
}
