/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.function.LongConsumer;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public abstract class AbstractWhenRequestTest {

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    private final TestPublisher<String> publisher = new TestPublisher<>();
    private final TestPublisherSubscriber<String> subscriber = new TestPublisherSubscriber<>();

    @Test
    public void testSingleRequest() {
        LongConsumer onRequest = mock(LongConsumer.class);

        doRequest(publisher, onRequest).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        publisher.onNext("Hello");
        publisher.onComplete();
        assertThat(subscriber.takeOnNext(), is("Hello"));
        verify(onRequest).accept(1L);
    }

    @Test
    public void testMultiRequest() {
        LongConsumer onRequest = mock(LongConsumer.class);

        doRequest(publisher, onRequest).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        subscriber.awaitSubscription().request(1);
        publisher.onNext("Hello");
        assertThat(subscriber.takeOnNext(), is("Hello"));
        publisher.onNext("Hello1");
        assertThat(subscriber.takeOnNext(), is("Hello1"));
        verify(onRequest, times(2)).accept(1L);
    }

    @Test
    public void testRequestNoEmissions() {
        LongConsumer onRequest = mock(LongConsumer.class);

        doRequest(publisher, onRequest).subscribe(subscriber);
        subscriber.awaitSubscription().request(10);
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        verify(onRequest).accept(10L);
    }

    @Test
    public void testCallbackThrowsError() {
        thrown.expect(is(sameInstance(DELIBERATE_EXCEPTION)));

        doRequest(Publisher.from("Hello"), n -> {
            throw DELIBERATE_EXCEPTION;
        }).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
    }

    protected abstract <T> PublisherSource<T> doRequest(Publisher<T> publisher, LongConsumer consumer);
}
