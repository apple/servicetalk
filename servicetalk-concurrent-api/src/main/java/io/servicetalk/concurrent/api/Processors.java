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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.SingleSource;

/**
 * Utility class for {@link SingleSource.Processor} and {@link CompletableSource.Processor}.
 */
public final class Processors {
    private Processors() {
        // no instances
    }

    /**
     * Create a new {@link CompletableSource.Processor} that allows for multiple
     * {@link CompletableSource.Subscriber#subscribe(CompletableSource.Subscriber) subscribes}.
     *
     * @return a new {@link CompletableSource.Processor} that allows for multiple
     * {@link CompletableSource.Subscriber#subscribe(CompletableSource.Subscriber) subscribes}.
     */
    public static CompletableSource.Processor newCompletableProcessor() {
        return new CompletableProcessor();
    }

    /**
     * Create a new {@link SingleSource.Processor} that allows for multiple
     * {@link SingleSource.Subscriber#subscribe(SingleSource.Subscriber) subscribes}.
     *
     * @param <T> The {@link SingleSource} type and {@link SingleSource.Subscriber} type of the
     * {@link SingleSource.Processor}.
     * @return a new {@link SingleSource.Processor} that allows for multiple
     * {@link SingleSource.Subscriber#subscribe(SingleSource.Subscriber) subscribes}.
     */
    public static <T> SingleSource.Processor<T, T> newSingleProcessor() {
        return new SingleProcessor<>();
    }

    /**
     * Create a new {@link PublisherSource.Processor} that allows for a single
     * {@link PublisherSource.Subscriber#subscribe(PublisherSource.Subscriber) subscribe}.
     *
     * @param <T> The {@link PublisherSource} type and {@link PublisherSource.Subscriber} type of the
     * {@link PublisherSource.Processor}.
     * @return a new {@link PublisherSource.Processor} that allows for a single
     * {@link PublisherSource.Subscriber#subscribe(PublisherSource.Subscriber) subscribe}.
     */
    public static <T> PublisherSource.Processor<T, T> newPublisherProcessor() {
        return newPublisherProcessor(PublisherProcessorBuffers.fixedSize(32));
    }

    /**
     * Create a new {@link PublisherSource.Processor} that allows for a single
     * {@link PublisherSource.Subscriber#subscribe(PublisherSource.Subscriber) subscribe}.
     *
     * @param buffer A {@link PublisherProcessorBuffer} to store items that are requested to be sent via
     * {@link PublisherSource.Processor#onNext(Object)} but not yet emitted to the {@link PublisherSource.Subscriber}.
     * @param <T> The {@link PublisherSource} type and {@link PublisherSource.Subscriber} type of the
     * {@link PublisherSource.Processor}.
     * @return a new {@link PublisherSource.Processor} that allows for a single
     * {@link PublisherSource.Subscriber#subscribe(PublisherSource.Subscriber) subscribe}.
     */
    public static <T> PublisherSource.Processor<T, T> newPublisherProcessor(final PublisherProcessorBuffer<T> buffer) {
        return new PublisherProcessor<>(buffer);
    }

    /**
     * Create a new {@link BlockingIterable.Processor}.
     *
     * @param <T> the type of elements emitted by the returned {@link BlockingIterable}.
     * @return a new {@link BlockingIterable.Processor}.
     */
    public static <T> BlockingIterable.Processor<T> newBlockingIterableProcessor() {
        return newBlockingIterableProcessor(32);
    }

    /**
     * Create a new {@link BlockingIterable.Processor}.
     *
     * @param maxBufferSize Maximum number of items that are requested to be sent via
     * {@link BlockingIterable.Processor#next(Object)} but not yet emitted from a {@link BlockingIterator}. If this
     * buffer size is reached a subsequent call to {@link BlockingIterable.Processor#next(Object) emit} will block till
     * an item is emitted from a {@link BlockingIterator}.
     * @param <T> the type of elements emitted by the returned {@link BlockingIterable}.
     * @return a new {@link BlockingIterable.Processor}.
     */
    public static <T> BlockingIterable.Processor<T> newBlockingIterableProcessor(int maxBufferSize) {
        return new DefaultBlockingIterableProcessor<>(new DefaultBlockingProcessorBuffer<>(maxBufferSize));
    }

    /**
     * Create a new {@link BlockingIterable.Processor}.
     *
     * @param buffer A {@link BlockingProcessorBuffer} to store items that are requested to be sent via
     * {@link BlockingIterable.Processor#next(Object)} but not yet emitted from a {@link BlockingIterator}.
     * @param <T> the type of elements emitted by the returned {@link BlockingIterable}.
     * @return a new {@link BlockingIterable.Processor}.
     */
    public static <T> BlockingIterable.Processor<T> newBlockingIterableProcessor(
            final BlockingProcessorBuffer<T> buffer) {
        return new DefaultBlockingIterableProcessor<>(buffer);
    }
}
