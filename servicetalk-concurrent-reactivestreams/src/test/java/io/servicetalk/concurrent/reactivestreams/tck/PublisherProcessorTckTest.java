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

import io.servicetalk.concurrent.PublisherSource.Processor;
import io.servicetalk.concurrent.api.Publisher;

import org.testng.annotations.Test;

import static io.servicetalk.concurrent.api.Processors.newPublisherProcessor;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.reactivestreams.ReactiveStreamsAdapters.toReactiveStreamsPublisher;
import static java.lang.Integer.max;

@Test
public class PublisherProcessorTckTest extends AbstractPublisherTckTest<Integer> {
    private static final int MAX_ITEMS = 128;

    @Override
    protected Publisher<Integer> createServiceTalkPublisher(long elements) {
        assert elements <= MAX_ITEMS;
        Processor<Integer, Integer> processor = newPublisherProcessor(max(1, (int) elements));
        for (int i = 0; i < elements; i++) {
            processor.onNext(i);
        }
        processor.onComplete();
        return fromSource(processor);
    }

    @Override
    public org.reactivestreams.Publisher<Integer> createFailedPublisher() {
        Processor<Integer, Integer> processor = newPublisherProcessor(1);
        processor.onError(DELIBERATE_EXCEPTION);
        return toReactiveStreamsPublisher(fromSource(processor));
    }

    @Override
    public long maxElementsFromPublisher() {
        return MAX_ITEMS;
    }
}
