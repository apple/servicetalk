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
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public abstract class AbstractWhenCancelTest {

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    private final TestPublisher<String> publisher = new TestPublisher<>();
    private final TestPublisherSubscriber<String> subscriber = new TestPublisherSubscriber<>();
    private TestSubscription subscription = new TestSubscription();

    @Test
    public void testCancelAfterEmissions() {
        Runnable onCancel = mock(Runnable.class);
        doCancel(publisher, onCancel).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        subscriber.awaitSubscription().request(1);
        publisher.onNext("Hello");
        assertThat(subscriber.takeOnNext(), is("Hello"));
        subscriber.awaitSubscription().cancel();
        verify(onCancel).run();
        assertTrue(subscription.isCancelled());
    }

    @Test
    public void testCancelNoEmissions() {
        Runnable onCancel = mock(Runnable.class);
        doCancel(publisher, onCancel).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        subscriber.awaitSubscription().cancel();
        verify(onCancel).run();
        assertTrue(subscription.isCancelled());
    }

    @Test
    public void testCallbackThrowsError() {
        thrown.expect(is(sameInstance(DELIBERATE_EXCEPTION)));

        try {
            doCancel(publisher, () -> {
                throw DELIBERATE_EXCEPTION;
            }).subscribe(subscriber);
            publisher.onSubscribe(subscription);
            subscriber.awaitSubscription().cancel();
        } finally {
            assertTrue(subscription.isCancelled());
        }
    }

    protected abstract <T> PublisherSource<T> doCancel(Publisher<T> publisher, Runnable runnable);
}
