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

import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.concurrent.test.internal.TestSingleSubscriber;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class ResumeSingleTest {

    private TestSingleSubscriber<Integer> subscriber;
    private TestSingle<Integer> first;
    private TestSingle<Integer> second;

    @BeforeEach
    public void setUp() {
        subscriber = new TestSingleSubscriber<>();
        first = new TestSingle<>();
        second = new TestSingle<>();
        toSource(first.recoverWith(throwable -> second)).subscribe(subscriber);
    }

    @Test
    public void testFirstComplete() {
        first.onSuccess(1);
        assertThat(subscriber.awaitOnSuccess(), is(1));
    }

    @Test
    public void testFirstErrorSecondComplete() {
        first.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        second.onSuccess(1);
        assertThat(subscriber.awaitOnSuccess(), is(1));
    }

    @Test
    public void testFirstErrorSecondError() {
        first.onError(new DeliberateException());
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        second.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testCancelFirstActive() {
        subscriber.awaitSubscription().cancel();
        TestCancellable cancellable = new TestCancellable();
        first.onSubscribe(cancellable);
        assertTrue(cancellable.isCancelled());
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
    }

    @Test
    public void testCancelSecondActive() {
        first.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitSubscription().cancel();
        TestCancellable firstCancellable = new TestCancellable();
        TestCancellable secondCancellable = new TestCancellable();
        first.onSubscribe(firstCancellable);
        second.onSubscribe(secondCancellable);
        assertTrue(secondCancellable.isCancelled());
        assertFalse(firstCancellable.isCancelled());
    }

    @Test
    public void testErrorSuppressOriginalException() {
        first = new TestSingle<>();
        subscriber = new TestSingleSubscriber<>();
        DeliberateException ex = new DeliberateException();
        toSource(first.recoverWith(throwable -> {
            throw ex;
        })).subscribe(subscriber);

        first.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), sameInstance(ex));
        assertEquals(1, ex.getSuppressed().length);
        assertSame(DELIBERATE_EXCEPTION, ex.getSuppressed()[0]);
    }
}
