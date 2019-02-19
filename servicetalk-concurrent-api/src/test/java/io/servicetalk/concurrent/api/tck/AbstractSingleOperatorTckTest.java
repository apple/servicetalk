/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api.tck;

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;

import org.testng.annotations.Test;

/**
 * Abstract base class for testing operators provided by {@link Single} for compliance with the
 * <a href="https://github.com/reactive-streams/reactive-streams-jvm/tree/v1.0.1/tck">Reactive Streams TCK</a>.
 * <p>
 * If you need the flexibility to create the {@link Single} by yourself you may need to extend {@link AbstractSingleTckTest} directly.
 */
@Test
public abstract class AbstractSingleOperatorTckTest<T> extends AbstractSingleTckTest<T> {

    @Override
    public Publisher<T> createServiceTalkPublisher(long elements) {
        return composeSingle(Single.success(1)).toPublisher();
    }

    protected abstract Single<T> composeSingle(Single<Integer> single);
}
