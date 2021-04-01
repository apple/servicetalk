/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.MergeCompletableTest.CompletableHolder;
import io.servicetalk.concurrent.test.internal.TestCompletableSubscriber;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class IterableMergeCompletableDelayErrorTest {

    private final CompletableHolder collectionHolder =
            new CompletableHolder() {
        @Override
        protected Completable createCompletable(Completable[] completables) {
            return new IterableMergeCompletable(true, completables[0],
                    asList(completables).subList(1, completables.length), immediate());
        }
    };

    private final CompletableHolder iterableHolder = new CompletableHolder() {
        @Override
        protected Completable createCompletable(Completable[] completables) {
            return new IterableMergeCompletable(true, completables[0],
                    () -> asList(completables).subList(1, completables.length).iterator(), immediate());
        }
    };

    @Test
    public void testCollectionCompletion() {
        TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
        collectionHolder.init(2).listen(subscriber).completeAll();
        subscriber.awaitOnComplete();
    }

    @Test
    public void testCollectionCompletionFew() {
        TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
        collectionHolder.init(2).listen(subscriber).complete(1, 2);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        collectionHolder.complete(0);
        subscriber.awaitOnComplete();
    }

    @Test
    public void testCollectionFailFirstEvent() {
        TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
        collectionHolder.init(2).listen(subscriber).fail(1);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        collectionHolder.complete(0, 2);
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testCollectionFailLastEvent() {
        TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
        collectionHolder.init(2).listen(subscriber).complete(0, 2);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        collectionHolder.fail(1);
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testCollectionFailMiddleEvent() {
        TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
        collectionHolder.init(2).listen(subscriber).complete(0);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        collectionHolder.fail(1);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        collectionHolder.complete(2);
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testCollectionMergeWithOne() {
        TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
        collectionHolder.init(1).listen(subscriber).completeAll();
        subscriber.awaitOnComplete();
    }

    @Test
    public void testIterableCompletion() {
        TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
        iterableHolder.init(2).listen(subscriber).completeAll();
        subscriber.awaitOnComplete();
    }

    @Test
    public void testIterableCompletionFew() {
        TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
        iterableHolder.init(2).listen(subscriber).complete(1, 2);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        iterableHolder.complete(0);
        subscriber.awaitOnComplete();
    }

    @Test
    public void testIterableFailFirstEvent() {
        TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
        iterableHolder.init(2).listen(subscriber).fail(1);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        iterableHolder.complete(0, 2);
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testIterableFailLastEvent() {
        TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
        iterableHolder.init(2).listen(subscriber).complete(0, 2);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        iterableHolder.fail(1);
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testIterableFailMiddleEvent() {
        TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
        iterableHolder.init(2).listen(subscriber).complete(0);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        iterableHolder.fail(1);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        iterableHolder.complete(2);
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testIterableMergeWithOne() {
        TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
        iterableHolder.init(1).listen(subscriber).completeAll();
        subscriber.awaitOnComplete();
    }

    @Test
    public void mergedCompletablesTerminateSynchronouslyWithDelayErrorDoesNotTerminateTwice()
            throws InterruptedException, ExecutionException {
        ExecutorService executorService = java.util.concurrent.Executors.newCachedThreadPool();
        executorService.submit(() -> { }).get();
        try {
            // The lower the count the more likely we will concurrently complete the subscribe() call while the
            // Subscriber also is completed.
            TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
            iterableHolder.init(1, executorService).listen(subscriber);
            subscriber.awaitOnComplete();
        } finally {
            executorService.shutdown();
        }
    }
}
