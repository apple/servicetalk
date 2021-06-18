/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.util.concurrent.atomic.AtomicInteger;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class From3PublisherTest {
    private final TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();

    @Test
    void request3Complete() {
        toSource(fromPublisher()).subscribe(subscriber);
        subscriber.awaitSubscription().request(3);
        assertThat(subscriber.takeOnNext(3), contains(1, 2, 3));
        subscriber.awaitOnComplete();
    }

    @Test
    void request2FirstComplete() {
        toSource(fromPublisher()).subscribe(subscriber);
        Subscription s = subscriber.awaitSubscription();
        s.request(2);
        assertThat(subscriber.takeOnNext(2), contains(1, 2));
        s.request(1);
        assertThat(subscriber.takeOnNext(), is(3));
        subscriber.awaitOnComplete();
    }

    @Test
    void request2SecondComplete() {
        toSource(fromPublisher()).subscribe(subscriber);
        Subscription s = subscriber.awaitSubscription();
        s.request(1);
        assertThat(subscriber.takeOnNext(), is(1));
        s.request(2);
        assertThat(subscriber.takeOnNext(2), contains(2, 3));
        subscriber.awaitOnComplete();
    }

    @Test
    void request1Complete() {
        toSource(fromPublisher()).subscribe(subscriber);
        Subscription s = subscriber.awaitSubscription();
        s.request(1);
        assertThat(subscriber.takeOnNext(), is(1));
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        s.request(1);
        assertThat(subscriber.takeOnNext(), is(2));
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        s.request(1);
        assertThat(subscriber.takeOnNext(), is(3));
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitOnComplete();
        s.request(1);
    }

    @Test
    void request1Cancel() {
        toSource(fromPublisher()).subscribe(subscriber);
        Subscription s = subscriber.awaitSubscription();
        s.request(1);
        assertThat(subscriber.takeOnNext(), is(1));
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        s.cancel();
        s.request(1);
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
    }

    @Test
    void request2Cancel() {
        toSource(fromPublisher()).subscribe(subscriber);
        Subscription s = subscriber.awaitSubscription();
        s.request(1);
        assertThat(subscriber.takeOnNext(), is(1));
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        s.request(1);
        assertThat(subscriber.takeOnNext(), is(2));
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        s.cancel();
        s.request(1);
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
    }

    @Test
    void throwFirst() {
        throwInOnNext(1);
    }

    @Test
    void throwSecond() {
        throwInOnNext(2);
    }

    @Test
    void throwThird() {
        throwInOnNext(3);
    }

    private void throwInOnNext(int onNexts) {
        @SuppressWarnings("unchecked")
        Subscriber<Integer> mockSubscriber = mock(Subscriber.class);
        AtomicInteger onNextCount = new AtomicInteger();
        doAnswer((Answer<Void>) invocation -> {
            if (onNextCount.incrementAndGet() == onNexts) {
                throw DELIBERATE_EXCEPTION;
            }
            return null;
        }).when(mockSubscriber).onNext(any());
        doAnswer((Answer<Void>) invocation -> {
            Subscription s = invocation.getArgument(0);
            s.request(3);
            return null;
        }).when(mockSubscriber).onSubscribe(any());
        toSource(fromPublisher()).subscribe(mockSubscriber);
        verify(mockSubscriber).onError(eq(DELIBERATE_EXCEPTION));
    }

    @Test
    void invalidRequestNZero() {
        invalidRequestN(0);
    }

    @Test
    void invalidRequestNNeg() {
        invalidRequestN(-1);
    }

    private void invalidRequestN(long n) {
        toSource(fromPublisher()).subscribe(subscriber);
        subscriber.awaitSubscription().request(n);
        assertThat(subscriber.awaitOnError(), instanceOf(newExceptionForInvalidRequestN(n).getClass()));
    }

    private static Publisher<Integer> fromPublisher() {
        return from(1, 2, 3);
    }
}
