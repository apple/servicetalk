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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource;

import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.Arrays.copyOfRange;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class MergeCompletableTest {

    @Rule
    public final CompletableHolder holder = new CompletableHolder() {
        @Override
        protected Completable createCompletable(Completable[] completables) {
            return new MergeCompletable(false, completables[0], immediate(),
                    copyOfRange(completables, 1, completables.length));
        }
    };

    @Test(expected = IllegalArgumentException.class)
    public void testEmpty() {
        holder.init(0);
    }

    @Test
    public void testCompletion() {
        holder.init(2).listen().completeAll().verifyCompletion();
    }

    @Test
    public void testCompletionFew() {
        holder.init(2).listen().complete(1, 2).verifyNoEmissions().complete(0).verifyCompletion();
    }

    @Test
    public void testFail() {
        holder.init(2).listen().fail(1).verifyFailure(DELIBERATE_EXCEPTION).verifyCancelled(0, 2);
    }

    @Test
    public void testMergeWithOne() {
        holder.init(1).listen().completeAll().verifyCompletion();
    }

    abstract static class CompletableHolder extends MockedCompletableListenerRule {

        Cancellable[] cancellables;
        CompletableSource.Subscriber[] subscribers;
        private Completable mergeCompletable;
        private Completable[] completables;

        protected abstract Completable createCompletable(Completable[] completables);

        CompletableHolder init(int count) {
            return init(count, null, null);
        }

        CompletableHolder init(int count, @Nullable java.util.concurrent.Executor executor,
                               @Nullable CountDownLatch doneLatch) {
            completables = new Completable[count + 1];
            cancellables = new Cancellable[count + 1];
            subscribers = new CompletableSource.Subscriber[count + 1];
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
                                     executor.execute(() -> {
                                         try {
                                             subscriber.onComplete();
                                         } finally {
                                             if (doneLatch != null) {
                                                 doneLatch.countDown();
                                             }
                                         }
                                     });
                                 } catch (Throwable cause) {
                                     try {
                                         subscriber.onError(cause);
                                     } finally {
                                         if (doneLatch != null) {
                                             doneLatch.countDown();
                                         }
                                     }
                                 }
                             }
                        }
                    }
                };
            }
            mergeCompletable = createCompletable(completables);
            return this;
        }

        CompletableHolder listen() {
            super.listen(mergeCompletable);
            return this;
        }

        CompletableHolder completeAll() {
            for (CompletableSource.Subscriber subscriber : subscribers) {
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

        @Override
        public CompletableHolder verifyCompletion() {
            super.verifyCompletion();
            return this;
        }

        @Override
        public CompletableHolder verifyFailure(Throwable cause) {
            super.verifyFailure(cause);
            return this;
        }

        @Override
        public CompletableHolder verifyNoEmissions() {
            super.verifyNoEmissions();
            return this;
        }

        private void validateListenerIndex(int index) {
            assertThat("Invalid listener index.", index, is(greaterThanOrEqualTo(0)));
            assertThat("Invalid listener index.", index, is(lessThan(subscribers.length)));
        }
    }
}
