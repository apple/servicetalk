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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.After;
import org.junit.Test;

import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static io.servicetalk.concurrent.api.Processors.newPublisherProcessor;
import static io.servicetalk.concurrent.api.PublisherProcessorSignalHolders.fixedSize;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

public class PublisherProcessorConcurrencyTest {

    private final ExecutorService executorService = newCachedThreadPool();

    @After
    public void tearDown() throws Exception {
        executorService.shutdownNow();
    }

    @Test
    public void concurrentEmissionAndConsumption() throws Exception {
        final int items = 10_000;
        final Collection<Integer> expected = Publisher.range(0, items).toFuture().get();
        final CountDownLatch done = new CountDownLatch(1);
        PublisherSource.Processor<Integer, Integer> processor = newPublisherProcessor(fixedSize(items));
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
        toSource(fromSource(processor).afterFinally(done::countDown)).subscribe(subscriber);

        CyclicBarrier barrier = new CyclicBarrier(2);

        Future<Object> producerFuture = executorService.submit(() -> {
            barrier.await();
            for (int i = 0; i < items; i++) {
                processor.onNext(i);
            }
            processor.onComplete();
            return null;
        });

        Future<Object> requesterFuture = executorService.submit(() -> {
            barrier.await();
            for (int i = 0; i < items; i++) {
                subscriber.awaitSubscription().request(1);
            }
            return null;
        });

        producerFuture.get();
        requesterFuture.get();
        done.await();

        assertThat(subscriber.takeOnNext(expected.size()), contains(expected.toArray()));
        subscriber.awaitOnComplete();
    }
}
