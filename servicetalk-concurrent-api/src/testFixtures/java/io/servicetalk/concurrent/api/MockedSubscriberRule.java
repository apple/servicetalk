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

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.VerificationTestUtils.verifyOriginalAndSuppressedCauses;
import static io.servicetalk.concurrent.api.VerificationTestUtils.verifySuppressed;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class MockedSubscriberRule<T> implements TestRule {
    @Nullable
    private Subscriber<T> subscriber;
    @Nullable
    private Subscription subscription;

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                newSubscriber();
                base.evaluate();
            }
        };
    }

    public MockedSubscriberRule<T> cancel() {
        assertNotNull(subscription);
        subscription.cancel();
        return this;
    }

    public MockedSubscriberRule<T> request(long n) {
        assertNotNull(subscription);
        subscription.request(n);
        return this;
    }

    public MockedSubscriberRule<T> subscribe(Single<T> src) {
        return subscribe(src.toPublisher());
    }

    public MockedSubscriberRule<T> subscribe(Publisher<T> src) {
        return subscribe(src, true);
    }

    public MockedSubscriberRule<T> subscribe(Publisher<T> src, boolean verifyOnSubscribe) {
        newSubscriber();
        assert subscriber != null;
        src.subscribe(subscriber);
        return verifyOnSubscribe ? verifySubscribe() : this;
    }

    public MockedSubscriberRule<T> verifySubscribe() {
        ArgumentCaptor<Subscription> subscriptionCaptor = forClass(Subscription.class);
        verify(subscriber).onSubscribe(subscriptionCaptor.capture());
        subscription = subscriptionCaptor.getValue();
        assertNotNull(subscription);
        return this;
    }

    public final MockedSubscriberRule<T> verifySuccess() {
        assert subscriber != null;
        verify(subscriber).onComplete();
        return this;
    }

    @SafeVarargs
    public final MockedSubscriberRule<T> verifySuccess(T... expected) {
        if (expected.length > 0) {
            request(expected.length);
        }
        return verifySuccessNoRequestN(expected);
    }

    @SafeVarargs
    public final MockedSubscriberRule<T> verifySuccessNoRequestN(T... expected) {
        verifyItems(expected);
        assert subscriber != null;
        verify(subscriber).onComplete();
        verifyNoMoreInteractions(subscriber);
        return this;
    }

    @SafeVarargs
    public final MockedSubscriberRule<T> verifyItems(T... expected) {
        return verifyItems(Mockito::verify, expected);
    }

    @SafeVarargs
    public final MockedSubscriberRule<T> verifyItems(Function<Subscriber<T>, Subscriber<T>> verifierSupplier, T... expected) {
        return verifyItems(verifierSupplier, 0, expected.length, expected);
    }

    @SafeVarargs
    public final MockedSubscriberRule<T> verifyItems(Function<Subscriber<T>, Subscriber<T>> verifierSupplier, int startIndex, int endIndex, T... expected) {
        assertNotNull(subscription);
        assert subscriber != null;
        verify(subscriber).onSubscribe(any());
        for (; startIndex < endIndex; ++startIndex) {
            T expect = expected[startIndex];
            verifierSupplier.apply(subscriber).onNext(expect);
        }
        return this;
    }

    public MockedSubscriberRule<T> requestAndVerifyFailure(Throwable cause) {
        assertNotNull(subscription);
        subscription.request(1);
        return verifyFailure(cause);
    }

    public MockedSubscriberRule<T> requestAndVerifySuppressedFailure(Throwable originalCause, Throwable suppressedCause) {
        assertNotNull(subscription);
        subscription.request(1);
        return verifySuppressedFailure(originalCause, suppressedCause);
    }

    public MockedSubscriberRule<T> verifyFailure(Throwable cause) {
        assert subscriber != null;
        verify(subscriber).onSubscribe(any());
        verify(subscriber).onError(cause);
        verifyNoMoreInteractions(subscriber);
        return this;
    }

    public MockedSubscriberRule<T> verifyFailure(ArgumentCaptor<Throwable> causeCaptor) {
        assert subscriber != null;
        verify(subscriber).onSubscribe(any());
        verify(subscriber).onError(causeCaptor.capture());
        verifyNoMoreInteractions(subscriber);
        return this;
    }

    public MockedSubscriberRule<T> verifyFailure(Class<? extends Throwable> cause) {
        assert subscriber != null;
        verify(subscriber).onSubscribe(any());
        verify(subscriber).onError(any(cause));
        verifyNoMoreInteractions(subscriber);
        return this;
    }

    public MockedSubscriberRule<T> verifySuppressedFailure(Throwable originalCause, Throwable suppressedCause) {
        assert subscriber != null;
        verify(subscriber).onSubscribe(any());
        ArgumentCaptor<Throwable> throwableCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(subscriber).onError(throwableCaptor.capture());
        Throwable actualCause = throwableCaptor.getValue();
        verifyOriginalAndSuppressedCauses(actualCause, originalCause, suppressedCause);
        verifyNoMoreInteractions(subscriber);
        return this;
    }

    public MockedSubscriberRule<T> verifySuppressedFailure(Throwable suppressedCause) {
        assert subscriber != null;
        verify(subscriber).onSubscribe(any());
        ArgumentCaptor<Throwable> throwableCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(subscriber).onError(throwableCaptor.capture());
        Throwable actualCause = throwableCaptor.getValue();
        verifySuppressed(actualCause, suppressedCause);
        verifyNoMoreInteractions(subscriber);
        return this;
    }

    public MockedSubscriberRule<T> verifyNoEmissions() {
        assert subscriber != null;
        verify(subscriber).onSubscribe(any());
        verifyNoMoreInteractions(subscriber);
        return this;
    }

    public Subscriber<T> getSubscriber() {
        assert subscriber != null;
        return subscriber;
    }

    public Subscription getSubscription() {
        assertNotNull(subscription);
        return subscription;
    }

    @SuppressWarnings("unchecked")
    private void newSubscriber() {
        subscriber = mock(Subscriber.class);
    }
}
