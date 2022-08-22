/*
 * Copyright © 2018, 2022 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.reactivestreams.tck;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;

/**
 * Abstract base class for testing operators provided by {@link Completable} for compliance with the
 * <a href="https://github.com/reactive-streams/reactive-streams-jvm/tree/v1.0.1/tck">Reactive Streams TCK</a>.
 * <p>
 * If you need the flexibility to create the {@link Completable} by yourself you may need to extend
 * {@link AbstractCompletableTckTest} directly.
 */
public abstract class AbstractCompletableOperatorTckTest extends AbstractCompletableTckTest {

    @Override
    protected final Publisher<Object> createServiceTalkPublisher(long elements) {
        return composeCompletable(Completable.completed()).toPublisher();
    }

    /**
     * Applies composition operators for the provided {@link Completable}.
     *
     * @param completable the provided {@link Completable}
     * @return composed {@link Completable}
     */
    protected abstract Completable composeCompletable(Completable completable);

    @Override
    public final long maxElementsFromPublisher() {
        return 0;
    }
}
