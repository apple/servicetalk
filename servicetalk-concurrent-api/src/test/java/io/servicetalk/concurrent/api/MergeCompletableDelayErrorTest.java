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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.test.internal.TestCompletableSubscriber;

import org.junit.Test;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.Arrays.copyOfRange;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class MergeCompletableDelayErrorTest {
    private final MergeCompletableTest.CompletableHolder holder = new MergeCompletableTest.CompletableHolder() {
        @Override
        protected Completable createCompletable(Completable[] completables) {
            return new MergeCompletable(true, completables[0], immediate(),
                    copyOfRange(completables, 1, completables.length));
        }
    };

    @Test
    public void testCompletion() {
        TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
        holder.init(2).listen(subscriber).completeAll();
        subscriber.awaitOnComplete();
    }

    @Test
    public void testCompletionFew() {
        TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
        holder.init(2).listen(subscriber).complete(1, 2);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(false));
        holder.complete(0);
        subscriber.awaitOnComplete();
    }

    @Test
    public void testFailFirstEvent() {
        TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
        holder.init(2).listen(subscriber).fail(1);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(false));
        holder.complete(0, 2);
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testFailLastEvent() {
        TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
        holder.init(2).listen(subscriber).complete(0, 2);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(false));
        holder.fail(1);
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testFailMiddleEvent() {
        TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
        holder.init(2).listen(subscriber).complete(0);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(false));
        holder.fail(1);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(false));
        holder.complete(2);
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testMergeWithOne() {
        TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
        holder.init(1).listen(subscriber).completeAll();
        subscriber.awaitOnComplete();
    }
}
