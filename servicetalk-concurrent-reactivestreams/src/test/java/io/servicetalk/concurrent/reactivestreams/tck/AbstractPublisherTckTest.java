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
package io.servicetalk.concurrent.reactivestreams.tck;

import io.servicetalk.concurrent.api.Publisher;

import org.testng.annotations.Test;

import static io.servicetalk.concurrent.reactivestreams.ReactiveStreamsAdapters.toReactiveStreamsPublisher;

/**
 * Abstract base class for testing {@link Publisher} for compliance with the
 * <a href="https://github.com/reactive-streams/reactive-streams-jvm/tree/v1.0.1/tck">Reactive Streams TCK</a>.
 * <p>
 * If you need the flexibility to create the {@link Publisher} and directly return the {@link Publisher} from
 * {@link #createServiceTalkPublisher(long)} by yourself you should extend this class, otherwise most of the times you
 * want to extend {@link AbstractPublisherOperatorTckTest}.
 */
@Test
public abstract class AbstractPublisherTckTest<T> extends AbstractTckTest<T> {

    @Override
    public final org.reactivestreams.Publisher<T> createPublisher(final long elements) {
        return toReactiveStreamsPublisher(createServiceTalkPublisher(elements));
    }

    public abstract Publisher<T> createServiceTalkPublisher(long elements);

    @Override
    public org.reactivestreams.Publisher<T> createFailedPublisher() {
        return toReactiveStreamsPublisher(TckUtils.newFailedPublisher());
    }
}
