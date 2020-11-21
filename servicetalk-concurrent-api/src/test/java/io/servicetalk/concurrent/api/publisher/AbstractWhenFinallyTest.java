/*
 * Copyright © 2018-2020 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.TerminalSignalConsumer;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

abstract class AbstractWhenFinallyTest {

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    final TestPublisher<String> publisher = new TestPublisher<>();
    final TestPublisherSubscriber<String> subscriber = new TestPublisherSubscriber<>();
    final TestSubscription subscription = new TestSubscription();

    private final TerminalSignalConsumer doFinally = mock(TerminalSignalConsumer.class);

    @Test
    public void testForCancelPostEmissions() {
        doFinally(publisher, doFinally).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        subscriber.awaitSubscription().request(1);
        publisher.onNext("Hello");
        assertThat(subscriber.takeOnNext(), is("Hello"));
        subscriber.awaitSubscription().cancel();
        verify(doFinally).cancel();
        verifyNoMoreInteractions(doFinally);
        assertTrue(subscription.isCancelled());
    }

    @Test
    public void testForCancelNoEmissions() {
        doFinally(publisher, doFinally).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        subscriber.awaitSubscription().cancel();
        verify(doFinally).cancel();
        verifyNoMoreInteractions(doFinally);
        assertTrue(subscription.isCancelled());
    }

    @Test
    public void testForCancelPostError() {
        doFinally(publisher, doFinally).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        publisher.onError(DELIBERATE_EXCEPTION);
        subscriber.awaitSubscription().cancel();
        verify(doFinally).onError(DELIBERATE_EXCEPTION);
        verifyNoMoreInteractions(doFinally);
        assertTrue(subscription.isCancelled());
    }

    @Test
    public void testForCancelPostComplete() {
        doFinally(publisher, doFinally).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        assertFalse(subscription.isCancelled());
        publisher.onComplete();
        subscriber.awaitSubscription().cancel();
        verify(doFinally).onComplete();
        verifyNoMoreInteractions(doFinally);
        assertTrue(subscription.isCancelled());
    }

    @Test
    public void testForCompletePostEmissions() {
        doFinally(publisher, doFinally).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        subscriber.awaitSubscription().request(1);
        publisher.onNext("Hello");
        assertFalse(subscription.isCancelled());
        publisher.onComplete();
        assertThat(subscriber.takeOnNext(), is("Hello"));
        subscriber.awaitOnComplete();
        verify(doFinally).onComplete();
        verifyNoMoreInteractions(doFinally);
        assertFalse(subscription.isCancelled());
    }

    @Test
    public void testForCompleteNoEmissions() {
        doFinally(publisher, doFinally).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        subscriber.awaitSubscription().request(1);
        assertFalse(subscription.isCancelled());
        publisher.onComplete();
        subscriber.awaitOnComplete();
        verify(doFinally).onComplete();
        verifyNoMoreInteractions(doFinally);
        assertFalse(subscription.isCancelled());
    }

    @Test
    public void testForErrorPostEmissions() {
        doFinally(publisher, doFinally).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        subscriber.awaitSubscription().request(1);
        publisher.onNext("Hello");
        publisher.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.takeOnNext(), is("Hello"));
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
        verify(doFinally).onError(DELIBERATE_EXCEPTION);
        verifyNoMoreInteractions(doFinally);
        assertFalse(subscription.isCancelled());
    }

    @Test
    public void testForErrorNoEmissions() {
        doFinally(publisher, doFinally).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        subscriber.awaitSubscription().request(1);
        publisher.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
        verify(doFinally).onError(DELIBERATE_EXCEPTION);
        verifyNoMoreInteractions(doFinally);
        assertFalse(subscription.isCancelled());
    }

    @Test
    public void testCallbackThrowsErrorOnCancel() {
        TerminalSignalConsumer mock = throwableMock(DELIBERATE_EXCEPTION);
        try {
            doFinally(publisher, mock).subscribe(subscriber);
            publisher.onSubscribe(subscription);
            thrown.expect(is(sameInstance(DELIBERATE_EXCEPTION)));
            subscriber.awaitSubscription().cancel();
            fail();
        } finally {
            verify(mock).cancel();
            verifyNoMoreInteractions(mock);
            assertTrue(subscription.isCancelled());
        }
    }

    @Test
    public abstract void testCallbackThrowsErrorOnComplete();

    @Test
    public abstract void testCallbackThrowsErrorOnError();

    protected abstract <T> PublisherSource<T> doFinally(Publisher<T> publisher, TerminalSignalConsumer signalConsumer);

    protected TerminalSignalConsumer throwableMock(RuntimeException exception) {
        return mock(TerminalSignalConsumer.class, delegatesTo(new TerminalSignalConsumer() {
            @Override
            public void onComplete() {
                throw exception;
            }

            @Override
            public void onError(final Throwable throwable) {
                throw exception;
            }

            @Override
            public void cancel() {
                throw exception;
            }
        }));
    }
}
