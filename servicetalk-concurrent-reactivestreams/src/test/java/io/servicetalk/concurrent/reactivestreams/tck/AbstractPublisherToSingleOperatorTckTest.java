/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;

import static io.servicetalk.concurrent.reactivestreams.ReactiveStreamsAdapters.toReactiveStreamsPublisher;

/**
 * Abstract base class for testing operators provided by {@link Publisher} to {@link Single} for compliance with the
 * <a href="https://github.com/reactive-streams/reactive-streams-jvm/tree/v1.0.1/tck">Reactive Streams TCK</a>.
 * <p>
 * If you need the flexibility to create the {@link Single} by yourself you may need to extend
 * {@link AbstractSingleTckTest} directly.
 */
abstract class AbstractPublisherToSingleOperatorTckTest<T> extends AbstractTckTest<T> {

    private Publisher<T> createServiceTalkPublisher(long elements) {
        int numElements = TckUtils.requestNToInt(elements);
        return composePublisher(TckUtils.newPublisher(numElements), numElements).toPublisher();
    }

    @Override
    public long maxElementsFromPublisher() {
        return 1;
    }

    protected abstract Single<T> composePublisher(Publisher<Integer> publisher, int elements);

    @Override
    public final org.reactivestreams.Publisher<T> createPublisher(final long elements) {
        return toReactiveStreamsPublisher(createServiceTalkPublisher(elements));
    }

    @Override
    public final org.reactivestreams.Publisher<T> createFailedPublisher() {
        return toReactiveStreamsPublisher(TckUtils.newFailedPublisher());
    }
}
