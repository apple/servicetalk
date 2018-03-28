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
package io.servicetalk.concurrent.api.publisher;

import io.servicetalk.concurrent.api.BiIntPredicate;
import io.servicetalk.concurrent.api.DeliberateException;
import io.servicetalk.concurrent.api.MockedSubscriberRule;
import io.servicetalk.concurrent.api.TestPublisher;

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
    public MockedSubscriberRule<Integer> subscriberRule = new MockedSubscriberRule<>();
    private TestPublisher<Integer> source;
    private BiIntPredicate<Throwable> shouldRetry;
    private boolean shouldRetryValue;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() throws Exception {
        source = new TestPublisher<>(true);
        source.sendOnSubscribe();
        shouldRetry = (BiIntPredicate<Throwable>) mock(BiIntPredicate.class);
        when(shouldRetry.test(anyInt(), any())).thenAnswer(invocation -> shouldRetryValue);
        subscriberRule.subscribe(source.retry(shouldRetry));
    }

    @Test
    public void testComplete() {
        subscriberRule.request(2);
        source.sendItems(1, 2).onComplete();
        subscriberRule.verifySuccess(1, 2);
        verifyZeroInteractions(shouldRetry);
    }

    @Test
    public void testRetryCount() {
        subscriberRule.request(2);
        source.sendItems(1, 2).fail();
        subscriberRule.verifyItems(1, 2).verifyFailure(DELIBERATE_EXCEPTION);
        verify(shouldRetry).test(1, DELIBERATE_EXCEPTION);
        verifyNoMoreInteractions(shouldRetry);
    }

    @Test
    public void testRequestAcrossRetry() {
        shouldRetryValue = true;
        subscriberRule.request(3);
        source.sendItems(1, 2).fail();
        subscriberRule.verifyItems(1, 2);
        verify(shouldRetry).test(1, DELIBERATE_EXCEPTION);
        source.verifySubscribed().sendItems(3);
        subscriberRule.verifyItems(3).verifyNoEmissions();
    }

    @Test
    public void testTwoFailures() {
        shouldRetryValue = true;
        subscriberRule.request(3);
        source.sendItems(1, 2).fail();
        subscriberRule.verifyItems(1, 2).verifyNoEmissions();
        verify(shouldRetry).test(1, DELIBERATE_EXCEPTION);
        source.verifySubscribed();
        source.sendItems(3).fail();
        verify(shouldRetry).test(2, DELIBERATE_EXCEPTION);
        source.onComplete();
        subscriberRule.verifySuccess(1, 2, 3);
    }

    @Test
    public void testMaxRetries() {
        shouldRetryValue = true;
        subscriberRule.request(3);
        source.sendItems(1, 2).fail();
        subscriberRule.verifyItems(1, 2).verifyNoEmissions();
        verify(shouldRetry).test(1, DELIBERATE_EXCEPTION);
        shouldRetryValue = false;
        DeliberateException fatal = new DeliberateException();
        source.onError(fatal);
        subscriberRule.verifyFailure(fatal);
    }

    @Test
    public void testCancel() {
        subscriberRule.request(2);
        source.sendItems(1, 2);
        subscriberRule.verifyItems(1, 2).cancel();
        source.fail();
        source.verifyCancelled();
    }

    @Test
    public void exceptionInTerminalCallsOnError() {
        DeliberateException ex = new DeliberateException();
        subscriberRule = new MockedSubscriberRule<>();
        source = new TestPublisher<>(true);
        source.sendOnSubscribe();
        subscriberRule.subscribe(source.retry((times, cause) -> {
            throw ex;
        })).request(1);
        source.fail();
        subscriberRule.verifyFailure(ex);
        assertEquals(1, ex.getSuppressed().length);
        assertSame(DELIBERATE_EXCEPTION, ex.getSuppressed()[0]);
    }
}
