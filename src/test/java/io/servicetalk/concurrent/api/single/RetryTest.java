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
import io.servicetalk.concurrent.api.DeliberateException;
import io.servicetalk.concurrent.api.MockedSingleListenerRule;
import io.servicetalk.concurrent.api.TestSingle;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class RetryTest {

    @Rule
    public MockedSingleListenerRule<Integer> subscriberRule = new MockedSingleListenerRule<>();
    private TestSingle<Integer> source;
    private BiIntPredicate<Throwable> shouldRetry;
    private boolean shouldRetryValue;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() throws Exception {
        source = new TestSingle<>(false, false);
        shouldRetry = (BiIntPredicate<Throwable>) mock(BiIntPredicate.class);
        when(shouldRetry.test(anyInt(), any())).thenAnswer(invocation -> shouldRetryValue);
        subscriberRule.listen(source.retry(shouldRetry));
    }

    @Test
    public void testComplete() {
        source.onSuccess(1);
        subscriberRule.verifySuccess(1);
        verifyZeroInteractions(shouldRetry);
    }

    @Test
    public void testRetryCount() {
        source.onError(DELIBERATE_EXCEPTION);
        subscriberRule.verifyFailure(DELIBERATE_EXCEPTION);
        verify(shouldRetry).test(1, DELIBERATE_EXCEPTION);
        verifyNoMoreInteractions(shouldRetry);
    }

    @Test
    public void testTwoFailures() {
        shouldRetryValue = true;
        source.onError(DELIBERATE_EXCEPTION);
        subscriberRule.verifyNoEmissions();
        verify(shouldRetry).test(1, DELIBERATE_EXCEPTION);
        source.onError(DELIBERATE_EXCEPTION);
        verify(shouldRetry).test(2, DELIBERATE_EXCEPTION);
        source.onSuccess(1);
        subscriberRule.verifySuccess(1);
    }

    @Test
    public void testMaxRetries() {
        shouldRetryValue = true;
        source.onError(DELIBERATE_EXCEPTION);
        subscriberRule.verifyNoEmissions();
        verify(shouldRetry).test(1, DELIBERATE_EXCEPTION);
        shouldRetryValue = false;
        DeliberateException fatal = new DeliberateException();
        source.onError(fatal);
        subscriberRule.verifyFailure(fatal);
    }

    @Test
    public void testCancel() {
        subscriberRule.cancel();
        source.onError(DELIBERATE_EXCEPTION);
        verifyZeroInteractions(shouldRetry);
    }

    @Test
    public void exceptionInTerminalCallsOnError() {
        DeliberateException ex = new DeliberateException();
        subscriberRule = new MockedSingleListenerRule<>();
        source = new TestSingle<>(false, false);
        subscriberRule.resetSubscriberMock().listen(source.retry((times, cause) -> {
            throw ex;
        }));
        source.onError(DELIBERATE_EXCEPTION);
        subscriberRule.verifyFailure(ex);
        assertEquals(1, ex.getSuppressed().length);
        assertSame(DELIBERATE_EXCEPTION, ex.getSuppressed()[0]);
    }
}
