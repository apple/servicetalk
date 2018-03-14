/**
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

import io.servicetalk.concurrent.api.BiIntFunction;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.DeliberateException;
import io.servicetalk.concurrent.api.MockedSingleListenerRule;
import io.servicetalk.concurrent.api.TestCompletable;
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

public class RetryWhenTest {

    @Rule
    public MockedSingleListenerRule<Integer> subscriberRule = new MockedSingleListenerRule<>();
    private TestSingle<Integer> source;
    private BiIntFunction<Throwable, Completable> shouldRetry;
    private TestCompletable retrySignal;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() throws Exception {
        source = new TestSingle<>(false, false);
        shouldRetry = (BiIntFunction<Throwable, Completable>) mock(BiIntFunction.class);
        retrySignal = new TestCompletable();
        when(shouldRetry.apply(anyInt(), any())).thenAnswer(invocation -> {
            retrySignal = new TestCompletable();
            return retrySignal;
        });
        subscriberRule.listen(source.retryWhen(shouldRetry));
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
        subscriberRule.verifyNoEmissions();
        DeliberateException fatal = new DeliberateException();
        retrySignal.onError(fatal); // stop retry
        subscriberRule.verifyFailure(fatal);
        verify(shouldRetry).apply(1, DELIBERATE_EXCEPTION);
        verifyNoMoreInteractions(shouldRetry);
    }

    @Test
    public void testTwoError() {
        source.onError(DELIBERATE_EXCEPTION);
        subscriberRule.verifyNoEmissions();
        verify(shouldRetry).apply(1, DELIBERATE_EXCEPTION);
        retrySignal.onComplete(); // trigger retry
        source.verifyListenCalled();
        source.onError(DELIBERATE_EXCEPTION);
        verify(shouldRetry).apply(2, DELIBERATE_EXCEPTION);
        retrySignal.onComplete(); // trigger retry
        source.onSuccess(1);
        subscriberRule.verifySuccess(1);
    }

    @Test
    public void testMaxRetries() {
        source.onError(DELIBERATE_EXCEPTION);
        retrySignal.onComplete(); // trigger retry
        subscriberRule.verifyNoEmissions();
        verify(shouldRetry).apply(1, DELIBERATE_EXCEPTION);
        source.verifyListenCalled().onError(DELIBERATE_EXCEPTION);
        DeliberateException fatal = new DeliberateException();
        retrySignal.verifyListenCalled().onError(fatal); // stop retry
        subscriberRule.verifyFailure(fatal);
    }

    @Test
    public void testCancelPostErrorButBeforeRetryStart() {
        source.onError(DELIBERATE_EXCEPTION);
        retrySignal.verifyListenCalled();
        subscriberRule.cancel();
        retrySignal.verifyCancelled();
        source.onSuccess(1);
        verify(shouldRetry).apply(1, DELIBERATE_EXCEPTION);
        source.verifyCancelled();
    }

    @Test
    public void testCancelBeforeRetry() {
        subscriberRule.cancel();
        source.onSuccess(1);
        verifyZeroInteractions(shouldRetry);
    }

    @Test
    public void exceptionInTerminalCallsOnError() {
        DeliberateException ex = new DeliberateException();

        subscriberRule = new MockedSingleListenerRule<>();
        source = new TestSingle<>(false, false);
        subscriberRule.resetSubscriberMock().listen(source.retryWhen((times, cause) -> {
            throw ex;
        }));
        source.onError(DELIBERATE_EXCEPTION);
        subscriberRule.verifyFailure(ex);
        assertEquals(1, ex.getSuppressed().length);
        assertSame(DELIBERATE_EXCEPTION, ex.getSuppressed()[0]);
    }

    @Test
    public void nullInTerminalCallsOnError() {
        subscriberRule = new MockedSingleListenerRule<>();
        source = new TestSingle<>(false, false);
        subscriberRule.resetSubscriberMock().listen(source.retryWhen((times, cause) -> null));
        source.onError(DELIBERATE_EXCEPTION);
        subscriberRule.verifyFailure(NullPointerException.class);
    }
}
