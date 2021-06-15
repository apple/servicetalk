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

import io.servicetalk.concurrent.test.internal.TestCompletableSubscriber;

import org.junit.jupiter.api.Test;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.Arrays.copyOfRange;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class MergeCompletableDelayErrorTest {
    private final MergeCompletableTest.CompletableHolder holder = new MergeCompletableTest.CompletableHolder() {
        @Override
        protected Completable createCompletable(Completable[] completables) {
            return MergeCompletable.newInstance(true, completables[0], immediate(),
                    copyOfRange(completables, 1, completables.length));
        }
    };

    @Test
    void testCompletion() {
        TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
        holder.init(2).listen(subscriber).completeAll();
        subscriber.awaitOnComplete();
    }

    @Test
    void testCompletionFew() {
        TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
        holder.init(2).listen(subscriber).complete(1, 2);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        holder.complete(0);
        subscriber.awaitOnComplete();
    }

    @Test
    void testFailFirstEvent() {
        TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
        holder.init(2).listen(subscriber).fail(1);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        holder.complete(0, 2);
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void testFailLastEvent() {
        TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
        holder.init(2).listen(subscriber).complete(0, 2);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        holder.fail(1);
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void testFailMiddleEvent() {
        TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
        holder.init(2).listen(subscriber).complete(0);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        holder.fail(1);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        holder.complete(2);
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void testMergeWithOne() {
        TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
        holder.init(1).listen(subscriber).completeAll();
        subscriber.awaitOnComplete();
    }
}
