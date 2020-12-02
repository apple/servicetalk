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
package io.servicetalk.concurrent.api.single;

import io.servicetalk.concurrent.api.BiIntPredicate;
import io.servicetalk.concurrent.api.LegacyTestSingle;
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.concurrent.test.internal.TestSingleSubscriber;

import org.junit.Before;
import org.junit.Test;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class RetryTest {

    private final TestSingleSubscriber<Integer> subscriber = new TestSingleSubscriber<>();

    private LegacyTestSingle<Integer> source;
    private BiIntPredicate<Throwable> shouldRetry;
    private boolean shouldRetryValue;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() throws Exception {
        setUp(subscriber);
    }

    private void setUp(TestSingleSubscriber<Integer> subscriberRule) {
        source = new LegacyTestSingle<>(false, false);
        shouldRetry = (BiIntPredicate<Throwable>) mock(BiIntPredicate.class);
        when(shouldRetry.test(anyInt(), any())).thenAnswer(invocation -> shouldRetryValue);
        toSource(source.retry(shouldRetry)).subscribe(subscriberRule);
    }

    @Test
    public void testComplete() {
        source.onSuccess(1);
        assertThat(subscriber.awaitOnSuccess(), is(1));
        verifyZeroInteractions(shouldRetry);
    }

    @Test
    public void testRetryCount() {
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
        verify(shouldRetry).test(1, DELIBERATE_EXCEPTION);
        verifyNoMoreInteractions(shouldRetry);
    }

    @Test
    public void testTwoFailures() {
        shouldRetryValue = true;
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        verify(shouldRetry).test(1, DELIBERATE_EXCEPTION);
        source.onError(DELIBERATE_EXCEPTION);
        verify(shouldRetry).test(2, DELIBERATE_EXCEPTION);
        source.onSuccess(1);
        assertThat(subscriber.awaitOnSuccess(), is(1));
    }

    @Test
    public void testMaxRetries() {
        shouldRetryValue = true;
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        verify(shouldRetry).test(1, DELIBERATE_EXCEPTION);
        shouldRetryValue = false;
        DeliberateException fatal = new DeliberateException();
        source.onError(fatal);
        assertThat(subscriber.awaitOnError(), is(fatal));
    }

    @Test
    public void testCancel() {
        subscriber.awaitSubscription().cancel();
        source.onError(DELIBERATE_EXCEPTION);
        verifyZeroInteractions(shouldRetry);
    }

    @Test
    public void exceptionInTerminalCallsOnError() {
        DeliberateException ex = new DeliberateException();
        TestSingleSubscriber<Integer> subscriberRule = new TestSingleSubscriber<>();
        source = new LegacyTestSingle<>(false, false);
        toSource(source.retry((times, cause) -> {
            throw ex;
        })).subscribe(subscriberRule);
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriberRule.awaitOnError(), is(ex));
        assertEquals(1, ex.getSuppressed().length);
        assertSame(DELIBERATE_EXCEPTION, ex.getSuppressed()[0]);
    }
}
