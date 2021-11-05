/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api.completable;

import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.TestCancellable;
import io.servicetalk.concurrent.api.TestCompletable;
import io.servicetalk.concurrent.test.internal.TestCompletableSubscriber;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.stubbing.Answer;

import java.util.Arrays;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class CompletableConcatWithCompletablesTest {
    private TestCompletableSubscriber subscriber;
    private TestCompletable source;
    private TestCompletable[] nexts;

    @BeforeEach
    void setUp() {
        subscriber = new TestCompletableSubscriber();
        source = new TestCompletable();
    }

    private void initNexts(int num) {
        nexts = new TestCompletable[num];
        Arrays.setAll(nexts, i -> new TestCompletable());
    }

    @Test
    void testSourceSuccessNextEmpty() {
        initNexts(0);
        toSource(source.concat(nexts)).subscribe(subscriber);
        source.onComplete();
        subscriber.awaitOnComplete();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void testSourceSuccessNextSuccess(int num) {
        initNexts(num);
        toSource(source.concat(nexts)).subscribe(subscriber);
        source.onComplete();
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        for (int i = 0; i < nexts.length; ++i) {
            assertTrue(nexts[i].isSubscribed());
            if (i + 1 < nexts.length) {
                assertFalse(nexts[i + 1].isSubscribed());
            }
            nexts[i].onComplete();
        }
        subscriber.awaitOnComplete();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 10000})
    void testSourceSuccessReentrant(int num) {
        Completable[] mockCompletables = new Completable[num];
        for (int i = 0; i < mockCompletables.length; ++i) {
            CompletableSource mockCompletable = mock(CompletableSource.class);
            doAnswer((Answer<Void>) invocation -> {
                CompletableSource.Subscriber sub = invocation.getArgument(0);
                sub.onSubscribe(IGNORE_CANCEL);
                sub.onComplete();
                return null;
            }).when(mockCompletable).subscribe(any());
            mockCompletables[i] = fromSource(mockCompletable);
        }
        toSource(source.concat(mockCompletables)).subscribe(subscriber);
        source.onComplete();
        subscriber.awaitOnComplete();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 100})
    void testSourceSuccessNextError(int num) {
        initNexts(num);
        toSource(source.concat(nexts)).subscribe(subscriber);
        source.onComplete();
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        assertThat("No subscribe for the next item", nexts[0].isSubscribed(), is(true));
        nexts[0].onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
        for (int i = 1; i < nexts.length; ++i) {
            assertFalse(nexts[i].isSubscribed());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void testSourceError(int num) {
        initNexts(num);
        toSource(source.concat(nexts)).subscribe(subscriber);
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
        for (final TestCompletable next : nexts) {
            assertFalse(next.isSubscribed());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void testCancelSource(int num) {
        initNexts(num);
        toSource(source.concat(nexts)).subscribe(subscriber);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitSubscription().cancel();
        TestCancellable cancellable = new TestCancellable();
        source.onSubscribe(cancellable);
        assertTrue(cancellable.isCancelled());
        for (final TestCompletable next : nexts) {
            assertFalse(next.isSubscribed());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void testCancelNext(int num) {
        initNexts(num);
        toSource(source.concat(nexts)).subscribe(subscriber);
        source.onComplete();
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitSubscription().cancel();

        TestCancellable sourceCancellable = new TestCancellable();
        source.onSubscribe(sourceCancellable);
        assertFalse(sourceCancellable.isCancelled());

        TestCancellable nextCancellable = new TestCancellable();
        source.onSubscribe(nextCancellable);
        assertTrue(nextCancellable.isCancelled());
    }
}
