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
import io.servicetalk.concurrent.test.internal.TestCompletableSubscriber;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class OnErrorResumeCompletableTest {

    private TestCompletableSubscriber subscriber;
    private TestCompletable first;
    private TestCompletable second;

    @BeforeEach
    public void setUp() {
        subscriber = new TestCompletableSubscriber();
        first = new TestCompletable();
        second = new TestCompletable();
        toSource(first.onErrorResume(throwable -> second)).subscribe(subscriber);
    }

    @Test
    public void testFirstComplete() {
        first.onComplete();
        subscriber.awaitOnComplete();
    }

    @Test
    public void testFirstErrorSecondComplete() {
        first.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        second.onComplete();
        subscriber.awaitOnComplete();
    }

    @Test
    public void testFirstErrorSecondError() {
        first.onError(new DeliberateException());
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        second.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
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

        TestCancellable secondCancellable = new TestCancellable();
        second.onSubscribe(secondCancellable);
        assertTrue(secondCancellable.isCancelled());

        TestCancellable firstCancellable = new TestCancellable();
        first.onSubscribe(firstCancellable);
        assertFalse(firstCancellable.isCancelled());
    }

    @Test
    public void testErrorSuppressOriginalException() {
        subscriber = new TestCompletableSubscriber();
        first = new TestCompletable();
        DeliberateException ex = new DeliberateException();
        toSource(first.onErrorResume(throwable -> {
            throw ex;
        })).subscribe(subscriber);

        first.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), is(ex));
        assertEquals(1, ex.getSuppressed().length);
        assertSame(DELIBERATE_EXCEPTION, ex.getSuppressed()[0]);
    }
}
