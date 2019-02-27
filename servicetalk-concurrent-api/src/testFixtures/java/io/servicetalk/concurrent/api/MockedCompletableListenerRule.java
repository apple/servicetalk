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

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.api.VerificationTestUtils.verifyOriginalAndSuppressedCauses;
import static io.servicetalk.concurrent.api.VerificationTestUtils.verifySuppressed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class MockedCompletableListenerRule implements TestRule {
    @Nullable
    private CompletableSource.Subscriber subscriber;
    @Nullable
    private Cancellable cancellable;

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                createSubscriber();
                base.evaluate();
            }
        };
    }

    public MockedCompletableListenerRule cancel() {
        verifyCancellable();
        assert cancellable != null;
        cancellable.cancel();
        return this;
    }

    public MockedCompletableListenerRule listen(Completable src) {
        return listen(src, true);
    }

    public MockedCompletableListenerRule listen(Completable src, boolean expectOnSubscribe) {
        createSubscriber();
        assert subscriber != null;
        toSource(src).subscribe(subscriber);
        if (expectOnSubscribe) {
            ArgumentCaptor<Cancellable> cancellableCaptor = forClass(Cancellable.class);
            verify(subscriber).onSubscribe(cancellableCaptor.capture());
            cancellable = cancellableCaptor.getValue();
            return verifyCancellable();
        }
        return this;
    }

    public MockedCompletableListenerRule verifyCancelled() {
        assert cancellable != null;
        verify(cancellable).cancel();
        return this;
    }

    public MockedCompletableListenerRule verifyCompletion() {
        final InOrder verifier = inOrderVerifier();
        verifier.verify(subscriber).onComplete();
        verifier.verifyNoMoreInteractions();
        return this;
    }

    public MockedCompletableListenerRule verifyFailure(Throwable cause) {
        final InOrder verifier = inOrderVerifier();
        verifier.verify(subscriber).onError(cause);
        verifier.verifyNoMoreInteractions();
        return this;
    }

    public MockedCompletableListenerRule verifyFailure(ArgumentCaptor<Throwable> causeCaptor) {
        final InOrder verifier = inOrderVerifier();
        verifier.verify(subscriber).onError(causeCaptor.capture());
        verifier.verifyNoMoreInteractions();
        return this;
    }

    public MockedCompletableListenerRule verifyFailure(Class<? extends Throwable> cause) {
        final InOrder verifier = inOrderVerifier();
        verifier.verify(subscriber).onError(any(cause));
        verifier.verifyNoMoreInteractions();
        return this;
    }

    public MockedCompletableListenerRule verifySuppressedFailure(Throwable originalCause, Throwable suppressedCause) {
        final InOrder verifier = inOrderVerifier();
        ArgumentCaptor<Throwable> throwableCaptor = ArgumentCaptor.forClass(Throwable.class);
        verifier.verify(subscriber).onError(throwableCaptor.capture());
        Throwable actualCause = throwableCaptor.getValue();
        verifyOriginalAndSuppressedCauses(actualCause, originalCause, suppressedCause);
        verifier.verifyNoMoreInteractions();
        return this;
    }

    public MockedCompletableListenerRule verifySuppressedFailure(Class<? extends Throwable> orginalCause, Class<? extends Throwable> suppressedCause) {
        final InOrder verifier = inOrderVerifier();
        ArgumentCaptor<Throwable> throwableCaptor = ArgumentCaptor.forClass(Throwable.class);
        verifier.verify(subscriber).onError(throwableCaptor.capture());
        Throwable actualCause = throwableCaptor.getValue();
        assertThat(actualCause, is(instanceOf(orginalCause)));
        assertThat(actualCause.getSuppressed(), arrayContaining(instanceOf(suppressedCause)));
        verifier.verifyNoMoreInteractions();
        return this;
    }

    public MockedCompletableListenerRule verifySuppressedFailure(Throwable suppressedCause) {
        final InOrder verifier = inOrderVerifier();
        ArgumentCaptor<Throwable> throwableCaptor = ArgumentCaptor.forClass(Throwable.class);
        verifier.verify(subscriber).onError(throwableCaptor.capture());
        Throwable actualCause = throwableCaptor.getValue();
        verifySuppressed(actualCause, suppressedCause);
        verifier.verifyNoMoreInteractions();
        return this;
    }

    public MockedCompletableListenerRule verifyNoEmissions() {
        verify(subscriber).onSubscribe(any());
        verifyZeroInteractions(subscriber);
        return this;
    }

    public MockedCompletableListenerRule reset() {
        subscriber = mock(CompletableSource.Subscriber.class);
        return this;
    }

    private MockedCompletableListenerRule verifyCancellable() {
        assertThat("Cancellable not found.", cancellable, is(notNullValue()));
        return this;
    }

    private void createSubscriber() {
        subscriber = mock(CompletableSource.Subscriber.class);
    }

    private InOrder inOrderVerifier() {
        assert subscriber != null;
        final InOrder verifier = inOrder(subscriber);
        verifier.verify(subscriber).onSubscribe(any());
        return verifier;
    }
}
