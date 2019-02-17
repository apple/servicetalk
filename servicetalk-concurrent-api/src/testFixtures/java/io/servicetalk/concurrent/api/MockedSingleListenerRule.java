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
import io.servicetalk.concurrent.SingleSource;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.stubbing.Answer;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.VerificationTestUtils.verifyOriginalAndSuppressedCauses;
import static io.servicetalk.concurrent.api.VerificationTestUtils.verifySuppressed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

public class MockedSingleListenerRule<T> implements TestRule {
    @Nullable
    private SingleSource.Subscriber<? super T> subscriber;
    @Nullable
    private volatile Cancellable onSubscribeResult;

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @SuppressWarnings("unchecked")
            @Override
            public void evaluate() throws Throwable {
                resetSubscriberMock();
                base.evaluate();
            }
        };
    }

    public MockedSingleListenerRule<T> resetSubscriberMock() {
        subscriber = mock(SingleSource.Subscriber.class);
        doAnswer((Answer<Void>) invocation -> {
            onSubscribeResult = invocation.getArgument(0);
            return null;
        }).when(subscriber).onSubscribe(any(Cancellable.class));
        return this;
    }

    public MockedSingleListenerRule<T> cancel() {
        verifyCancellable();
        Cancellable listenResult = this.onSubscribeResult;
        assert listenResult != null;
        listenResult.cancel();
        return this;
    }

    public MockedSingleListenerRule<T> listen(Single<? extends T> src) {
        assert subscriber != null;
        src.subscribe(subscriber);
        return this;
    }

    public MockedSingleListenerRule<T> verifySuccess(@Nullable T expected) {
        verifyCancellable();
        final InOrder verifier = inOrderVerifier();
        verifier.verify(subscriber).onSuccess(expected);
        verifier.verifyNoMoreInteractions();
        return this;
    }

    public T verifySuccessAndReturn(Class<T> returnClass) {
        verifyCancellable();
        final InOrder verifier = inOrderVerifier();
        ArgumentCaptor<T> captor = ArgumentCaptor.forClass(returnClass);
        verifier.verify(subscriber).onSuccess(captor.capture());
        verifier.verifyNoMoreInteractions();
        return captor.getValue();
    }

    public MockedSingleListenerRule<T> verifyFailure(Throwable cause) {
        verifyCancellable();
        final InOrder verifier = inOrderVerifier();
        verifier.verify(subscriber).onError(cause);
        verifier.verifyNoMoreInteractions();
        return this;
    }

    public MockedSingleListenerRule<T> verifyFailure(ArgumentCaptor<Throwable> causeCaptor) {
        verifyCancellable();
        final InOrder verifier = inOrderVerifier();
        verifier.verify(subscriber).onError(causeCaptor.capture());
        verifier.verifyNoMoreInteractions();
        return this;
    }

    public MockedSingleListenerRule<T> verifyFailure(Class<? extends Throwable> cause) {
        verifyCancellable();
        final InOrder verifier = inOrderVerifier();
        verifier.verify(subscriber).onError(any(cause));
        verifier.verifyNoMoreInteractions();
        return this;
    }

    public MockedSingleListenerRule<T> verifyNoEmissions() {
        assert subscriber != null;
        verify(subscriber).onSubscribe(any());
        verifyZeroInteractions(subscriber);
        return this;
    }

    public MockedSingleListenerRule<T> verifySuppressedFailure(Throwable originalCause, Throwable suppressedCause) {
        verifyCancellable();
        final InOrder verifier = inOrderVerifier();
        ArgumentCaptor<Throwable> throwableCaptor = ArgumentCaptor.forClass(Throwable.class);
        verifier.verify(subscriber).onError(throwableCaptor.capture());
        Throwable actualCause = throwableCaptor.getValue();
        verifyOriginalAndSuppressedCauses(actualCause, originalCause, suppressedCause);
        verifier.verifyNoMoreInteractions();
        return this;
    }

    public MockedSingleListenerRule<T> verifySuppressedFailure(Throwable suppressedCause) {
        verifyCancellable();
        final InOrder verifier = inOrderVerifier();
        ArgumentCaptor<Throwable> throwableCaptor = ArgumentCaptor.forClass(Throwable.class);
        verifier.verify(subscriber).onError(throwableCaptor.capture());
        Throwable actualCause = throwableCaptor.getValue();
        verifySuppressed(actualCause, suppressedCause);
        verifier.verifyNoMoreInteractions();
        return this;
    }

    public MockedSingleListenerRule<T> noMoreInteractions() {
        verifyCancellable();
        assert subscriber != null;
        verifyNoMoreInteractions(subscriber);
        return this;
    }

    private MockedSingleListenerRule<T> verifyCancellable() {
        assertThat("Listen result not found.", onSubscribeResult, is(notNullValue()));
        return this;
    }

    private InOrder inOrderVerifier() {
        assert subscriber != null;
        final InOrder verifier = inOrder(subscriber);
        verifier.verify(subscriber).onSubscribe(any());
        return verifier;
    }
}
