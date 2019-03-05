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

import io.servicetalk.concurrent.api.BiIntFunction;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.LegacyMockedSingleListenerRule;
import io.servicetalk.concurrent.api.LegacyTestCompletable;
import io.servicetalk.concurrent.api.LegacyTestSingle;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.DeliberateException;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.concurrent.ExecutionException;

import static io.servicetalk.concurrent.api.Completable.error;
import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class RetryWhenTest {

    @Rule
    public final ExpectedException expectedException = ExpectedException.none();
    @Rule
    public LegacyMockedSingleListenerRule<Integer> subscriberRule = new LegacyMockedSingleListenerRule<>();

    private LegacyTestSingle<Integer> source;
    private BiIntFunction<Throwable, Completable> shouldRetry;
    private LegacyTestCompletable retrySignal;
    private Executor executor;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() throws Exception {
        source = new LegacyTestSingle<>(false, false);
        shouldRetry = (BiIntFunction<Throwable, Completable>) mock(BiIntFunction.class);
        retrySignal = new LegacyTestCompletable();
        when(shouldRetry.apply(anyInt(), any())).thenAnswer(invocation -> {
            retrySignal = new LegacyTestCompletable();
            return retrySignal;
        });
        subscriberRule.listen(source.retryWhen(shouldRetry));
    }

    @After
    public void tearDown() throws Exception {
        if (executor != null) {
            executor.closeAsync().toFuture().get();
        }
    }

    @Test
    public void publishOnWithRetry() throws Exception {
        // This is an indication of whether we are using the same offloader across different subscribes. If this works,
        // then it does not really matter if we reuse offloaders or not. eg: if tomorrow we do not hold up a thread for
        // the lifetime of the Subscriber, we can reuse the offloader.
        executor = newCachedThreadExecutor();
        Single<Object> source = Single.error(DELIBERATE_EXCEPTION)
                .publishOn(executor)
                .retryWhen((count, t) ->
                        count == 1 ?
                                // If we complete the returned Completable synchronously, then the offloader will not
                                // terminate before we add another entity in the next subscribe. So, we return an
                                // asynchronously completed Completable.
                                executor.submit(() -> { }) : error(t));
        expectedException.expect(instanceOf(ExecutionException.class));
        expectedException.expectCause(is(DELIBERATE_EXCEPTION));
        source.toFuture().get();
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

        subscriberRule = new LegacyMockedSingleListenerRule<>();
        source = new LegacyTestSingle<>(false, false);
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
        subscriberRule = new LegacyMockedSingleListenerRule<>();
        source = new LegacyTestSingle<>(false, false);
        subscriberRule.resetSubscriberMock().listen(source.retryWhen((times, cause) -> null));
        source.onError(DELIBERATE_EXCEPTION);
        subscriberRule.verifyFailure(NullPointerException.class);
    }
}
