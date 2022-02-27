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
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.SingleSource;

import static io.servicetalk.concurrent.api.PublisherProcessorSignalHolders.fixedSize;
import static io.servicetalk.concurrent.api.PublisherProcessorSignalHolders.fixedSizeDropHead;
import static io.servicetalk.concurrent.api.PublisherProcessorSignalHolders.fixedSizeDropTail;

/**
 * Static factory methods for creating processor instances for different type of sources. A processor is both a producer
 * and a consumer of data.
 */
public final class Processors {
    private Processors() {
        // no instances
    }

    /**
     * Create a new {@link CompletableSource.Processor} that allows for multiple
     * {@link CompletableSource.Subscriber#subscribe(CompletableSource.Subscriber) subscribes}. The returned
     * {@link CompletableSource.Processor} provides all the expected API guarantees when used as a
     * {@link CompletableSource} but does not expect the same guarantees when used as a
     * {@link CompletableSource.Subscriber}. As an example, users are not expected to call
     * {@link CompletableSource.Subscriber#onSubscribe(Cancellable)} or they can call any of the
     * {@link CompletableSource.Subscriber} methods concurrently and/or multiple times.
     *
     * @return a new {@link CompletableSource.Processor} that allows for multiple
     * {@link CompletableSource.Subscriber#subscribe(CompletableSource.Subscriber) subscribes}.
     */
    public static CompletableSource.Processor newCompletableProcessor() {
        return new CompletableProcessor();
    }

    /**
     * Create a new {@link CompletableSource.Processor} that allows for multiple
     * {@link CompletableSource.Subscriber#subscribe(CompletableSource.Subscriber) subscribes}. The returned
     * {@link CompletableSource.Processor} provides all the expected API guarantees when used as a
     * {@link CompletableSource} but does not expect the same guarantees when used as a
     * {@link CompletableSource.Subscriber}. As an example, users are not expected to call
     * {@link CompletableSource.Subscriber#onSubscribe(Cancellable)} or they can call any of the
     * {@link CompletableSource.Subscriber} methods concurrently and/or multiple times.
     * @param maxSubscribers The maximum amount of {@link CompletableSource.Subscriber} that can be subscribed.
     * @return a new {@link CompletableSource.Processor} that allows for multiple
     * {@link CompletableSource.Subscriber#subscribe(CompletableSource.Subscriber) subscribes}.
     */
    public static CompletableSource.Processor newCompletableProcessor(int maxSubscribers) {
        return new CompletableProcessor(maxSubscribers);
    }

    /**
     * Create a new {@link SingleSource.Processor} that allows for multiple
     * {@link SingleSource.Subscriber#subscribe(SingleSource.Subscriber) subscribes}. The returned
     * {@link SingleSource.Processor} provides all the expected API guarantees when used as a
     * {@link SingleSource} but does not expect the same guarantees when used as a
     * {@link SingleSource.Subscriber}. As an example, users are not expected to call
     * {@link SingleSource.Subscriber#onSubscribe(Cancellable)} or they can call any of the
     * {@link SingleSource.Subscriber} methods concurrently and/or multiple times.
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
     * Create a new {@link SingleSource.Processor} that allows for multiple
     * {@link SingleSource.Subscriber#subscribe(SingleSource.Subscriber) subscribes}. The returned
     * {@link SingleSource.Processor} provides all the expected API guarantees when used as a
     * {@link SingleSource} but does not expect the same guarantees when used as a
     * {@link SingleSource.Subscriber}. As an example, users are not expected to call
     * {@link SingleSource.Subscriber#onSubscribe(Cancellable)} or they can call any of the
     * {@link SingleSource.Subscriber} methods concurrently and/or multiple times.
     * @param maxSubscribers The maximum amount of {@link CompletableSource.Subscriber} that can be subscribed.
     * @param <T> The {@link SingleSource} type and {@link SingleSource.Subscriber} type of the
     * {@link SingleSource.Processor}.
     * @return a new {@link SingleSource.Processor} that allows for multiple
     * {@link SingleSource.Subscriber#subscribe(SingleSource.Subscriber) subscribes}.
     */
    public static <T> SingleSource.Processor<T, T> newSingleProcessor(int maxSubscribers) {
        return new SingleProcessor<>(maxSubscribers);
    }

    /**
     * Create a new {@link PublisherSource.Processor} that allows for a single
     * {@link PublisherSource.Subscriber#subscribe(PublisherSource.Subscriber) subscribe}. The returned
     * {@link PublisherSource.Processor} provides all the expected API guarantees when used as a
     * {@link PublisherSource} but does not expect the same guarantees when used as a
     * {@link PublisherSource.Subscriber}. As an example, users are not expected to call
     * {@link PublisherSource.Subscriber#onSubscribe(Subscription)} or they can call any of the
     * {@link PublisherSource.Subscriber} methods concurrently and/or multiple times.
     * <p>
     * The returned may choose a default strategy to handle the cases when more items are added through
     * {@link PublisherSource.Processor#onNext(Object)} without being delivered to its
     * {@link PublisherSource.Subscriber}. Use other methods here if a specific strategy is required.
     *
     * @param <T> The {@link PublisherSource} type and {@link PublisherSource.Subscriber} type of the
     * {@link PublisherSource.Processor}.
     * @return a new {@link PublisherSource.Processor} that allows for a single
     * {@link PublisherSource.Subscriber#subscribe(PublisherSource.Subscriber) subscribe}.
     * @see #newPublisherProcessor(int)
     * @see #newPublisherProcessor(PublisherProcessorSignalsHolder)
     */
    public static <T> PublisherSource.Processor<T, T> newPublisherProcessor() {
        return newPublisherProcessor(32);
    }

    /**
     * Create a new {@link PublisherSource.Processor} that allows for a single
     * {@link PublisherSource.Subscriber#subscribe(PublisherSource.Subscriber) subscribe}. The returned
     * {@link PublisherSource.Processor} provides all the expected API guarantees when used as a
     * {@link PublisherSource} but does not expect the same guarantees when used as a
     * {@link PublisherSource.Subscriber}. As an example, users are not expected to call
     * {@link PublisherSource.Subscriber#onSubscribe(Subscription)} or they can call any of the
     * {@link PublisherSource.Subscriber} methods concurrently and/or multiple times.
     * <p>
     * Only allows for {@code maxBuffer} number of items to be added through
     * {@link PublisherSource.Processor#onNext(Object)} without being delivered to its
     * {@link PublisherSource.Subscriber}. If more items are added without being delivered, subsequent additions will
     * fail.
     *
     * @param maxBuffer Maximum number of items to buffer.
     * @param <T> The {@link PublisherSource} type and {@link PublisherSource.Subscriber} type of the
     * {@link PublisherSource.Processor}.
     * @return a new {@link PublisherSource.Processor} that allows for a single
     * {@link PublisherSource.Subscriber#subscribe(PublisherSource.Subscriber) subscribe}.
     */
    public static <T> PublisherSource.Processor<T, T> newPublisherProcessor(final int maxBuffer) {
        return newPublisherProcessor(fixedSize(maxBuffer));
    }

    /**
     * Create a new {@link PublisherSource.Processor} that allows for a single
     * {@link PublisherSource.Subscriber#subscribe(PublisherSource.Subscriber) subscribe}. The returned
     * {@link PublisherSource.Processor} provides all the expected API guarantees when used as a
     * {@link PublisherSource} but does not expect the same guarantees when used as a
     * {@link PublisherSource.Subscriber}. As an example, users are not expected to call
     * {@link PublisherSource.Subscriber#onSubscribe(Subscription)} or they can call any of the
     * {@link PublisherSource.Subscriber} methods concurrently and/or multiple times.
     * <p>
     * Only allows for {@code maxBuffer} number of items to be added through
     * {@link PublisherSource.Processor#onNext(Object)} without being delivered to its
     * {@link PublisherSource.Subscriber}. If more items are added without being delivered, the oldest buffered item
     * (head) will be dropped.
     *
     * @param maxBuffer Maximum number of items to buffer.
     * @param <T> The {@link PublisherSource} type and {@link PublisherSource.Subscriber} type of the
     * {@link PublisherSource.Processor}.
     * @return a new {@link PublisherSource.Processor} that allows for a single
     * {@link PublisherSource.Subscriber#subscribe(PublisherSource.Subscriber) subscribe}.
     */
    public static <T> PublisherSource.Processor<T, T> newPublisherProcessorDropHeadOnOverflow(final int maxBuffer) {
        return newPublisherProcessor(fixedSizeDropHead(maxBuffer));
    }

    /**
     * Create a new {@link PublisherSource.Processor} that allows for a single
     * {@link PublisherSource.Subscriber#subscribe(PublisherSource.Subscriber) subscribe}. The returned
     * {@link PublisherSource.Processor} provides all the expected API guarantees when used as a
     * {@link PublisherSource} but does not expect the same guarantees when used as a
     * {@link PublisherSource.Subscriber}. As an example, users are not expected to call
     * {@link PublisherSource.Subscriber#onSubscribe(Subscription)} or they can call any of the
     * {@link PublisherSource.Subscriber} methods concurrently and/or multiple times.
     * <p>
     * Only allows for {@code maxBuffer} number of items to be added through
     * {@link PublisherSource.Processor#onNext(Object)} without being delivered to its
     * {@link PublisherSource.Subscriber}. If more items are added without being delivered, the latest buffered item
     * (tail) will be dropped.
     *
     * @param maxBuffer Maximum number of items to buffer.
     * @param <T> The {@link PublisherSource} type and {@link PublisherSource.Subscriber} type of the
     * {@link PublisherSource.Processor}.
     * @return a new {@link PublisherSource.Processor} that allows for a single
     * {@link PublisherSource.Subscriber#subscribe(PublisherSource.Subscriber) subscribe}.
     */
    public static <T> PublisherSource.Processor<T, T> newPublisherProcessorDropTailOnOverflow(final int maxBuffer) {
        return newPublisherProcessor(fixedSizeDropTail(maxBuffer));
    }

    /**
     * Create a new {@link PublisherSource.Processor} that allows for a single
     * {@link PublisherSource.Subscriber#subscribe(PublisherSource.Subscriber) subscribe}. The returned
     * {@link PublisherSource.Processor} provides all the expected API guarantees when used as a
     * {@link PublisherSource}. Users are expected to provide same API guarantees from the passed
     * {@link PublisherProcessorSignalsHolder} as they use the returned {@link PublisherSource.Processor} as a
     * {@link PublisherSource.Subscriber}. As an example, if users call {@link PublisherSource.Subscriber} methods
     * concurrently the passed {@link PublisherProcessorSignalsHolder} should support concurrent invocation of its
     * methods.
     *
     * @param holder A {@link PublisherProcessorSignalsHolder} to store items that are requested to be sent via
     * {@link PublisherSource.Processor#onNext(Object)} but not yet emitted to the {@link PublisherSource.Subscriber}.
     * @param <T> The {@link PublisherSource} type and {@link PublisherSource.Subscriber} type of the
     * {@link PublisherSource.Processor}.
     * @return a new {@link PublisherSource.Processor} that allows for a single
     * {@link PublisherSource.Subscriber#subscribe(PublisherSource.Subscriber) subscribe}.
     */
    public static <T> PublisherSource.Processor<T, T> newPublisherProcessor(
            final PublisherProcessorSignalsHolder<T> holder) {
        return new PublisherProcessor<>(holder);
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
        return new DefaultBlockingIterableProcessor<>(new DefaultBlockingProcessorSignalsHolder<>(maxBufferSize));
    }

    /**
     * Create a new {@link BlockingIterable.Processor}.
     *
     * @param holder A {@link BlockingProcessorSignalsHolder} to store items that are requested to be sent via
     * {@link BlockingIterable.Processor#next(Object)} but not yet emitted from a {@link BlockingIterator}.
     * @param <T> the type of elements emitted by the returned {@link BlockingIterable}.
     * @return a new {@link BlockingIterable.Processor}.
     */
    public static <T> BlockingIterable.Processor<T> newBlockingIterableProcessor(
            final BlockingProcessorSignalsHolder<T> holder) {
        return new DefaultBlockingIterableProcessor<>(holder);
    }
}
