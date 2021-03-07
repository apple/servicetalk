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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource.Subscriber;
import io.servicetalk.concurrent.test.internal.TestCompletableSubscriber;

import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.Arrays.copyOfRange;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class MergeCompletableTest {

    private final CompletableHolder holder = new CompletableHolder() {
        @Override
        protected Completable createCompletable(Completable[] completables) {
            return MergeCompletable.newInstance(false, completables[0], immediate(),
                    copyOfRange(completables, 1, completables.length));
        }
    };

    @Test
    public void testEmpty() {
        TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
        holder.init(0).listen(subscriber).completeAll();
        subscriber.awaitOnComplete();
    }

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
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        holder.complete(0);
        subscriber.awaitOnComplete();
    }

    @Test
    public void testFail() {
        TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
        holder.init(2).listen(subscriber).fail(1);
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
        holder.verifyCancelled(0, 2);
    }

    @Test
    public void testMergeWithOne() {
        TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
        holder.init(1).listen(subscriber).completeAll();
        subscriber.awaitOnComplete();
    }

    abstract static class CompletableHolder {
        Cancellable[] cancellables;
        Subscriber[] subscribers;
        private Completable mergeCompletable;
        private Completable[] completables;

        protected abstract Completable createCompletable(Completable[] completables);

        CompletableHolder init(int count) {
            return init(count, null);
        }

        CompletableHolder init(int count, @Nullable java.util.concurrent.Executor executor) {
            completables = new Completable[count + 1];
            cancellables = new Cancellable[count + 1];
            subscribers = new Subscriber[count + 1];
            for (int i = 0; i < cancellables.length; i++) {
                cancellables[i] = mock(Cancellable.class);
                final int finalI = i;
                completables[i] = new Completable() {
                    @Override
                    protected void handleSubscribe(final Subscriber subscriber) {
                        subscribers[finalI] = subscriber;
                        subscriber.onSubscribe(cancellables[finalI]);
                         if (executor != null) {
                             if (finalI != cancellables.length - 1) {
                                 subscriber.onComplete();
                             } else {
                                 try {
                                     executor.execute(subscriber::onComplete);
                                 } catch (Throwable cause) {
                                     subscriber.onError(cause);
                                 }
                             }
                        }
                    }
                };
            }
            mergeCompletable = createCompletable(completables);
            return this;
        }

        CompletableHolder listen(Subscriber subscriber) {
            toSource(mergeCompletable).subscribe(subscriber);
            return this;
        }

        CompletableHolder completeAll() {
            for (Subscriber subscriber : subscribers) {
                subscriber.onComplete();
            }
            return this;
        }

        CompletableHolder complete(int... toComplete) {
            for (int index : toComplete) {
                validateListenerIndex(index);
                subscribers[index].onComplete();
            }
            return this;
        }

        CompletableHolder fail(int cancellableIndex) {
            validateListenerIndex(cancellableIndex);
            subscribers[cancellableIndex].onError(DELIBERATE_EXCEPTION);
            return this;
        }

        CompletableHolder verifyCancelled(int... cancellableIndices) {
            for (int cancellableIndex : cancellableIndices) {
                validateListenerIndex(cancellableIndex);
                verify(cancellables[cancellableIndex]).cancel();
            }
            return this;
        }

        private void validateListenerIndex(int index) {
            assertThat("Invalid listener index.", index, is(greaterThanOrEqualTo(0)));
            assertThat("Invalid listener index.", index, is(lessThan(subscribers.length)));
        }
    }
}
