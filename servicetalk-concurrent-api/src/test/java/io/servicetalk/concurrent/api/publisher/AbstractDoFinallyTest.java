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

import io.servicetalk.concurrent.api.MockedSubscriberRule;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.PublisherRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.concurrent.atomic.AtomicInteger;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public abstract class AbstractDoFinallyTest {

    @Rule
    public final PublisherRule<String> publisher = new PublisherRule<>();

    @Rule
    public final MockedSubscriberRule<String> subscriber = new MockedSubscriberRule<>();

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    private Runnable doFinally;

    @Before
    public void setUp() throws Exception {
        doFinally = mock(Runnable.class);
    }

    @Test
    public void testForCancelPostEmissions() {
        subscriber.subscribe(doFinally(publisher.getPublisher(), doFinally)).request(1);
        publisher.sendItems("Hello");
        subscriber.verifyItems("Hello").cancel();
        verify(doFinally).run();
        publisher.verifyCancelled();
    }

    @Test
    public void testForCancelNoEmissions() {
        subscriber.subscribe(doFinally(publisher.getPublisher(), doFinally));
        subscriber.cancel();
        verify(doFinally).run();
        publisher.verifyCancelled();
    }

    @Test
    public void testForCancelPostError() {
        subscriber.subscribe(doFinally(publisher.getPublisher(), doFinally));
        publisher.fail();
        subscriber.cancel();
        verify(doFinally).run();
        publisher.verifyCancelled();
    }

    @Test
    public void testForCancelPostComplete() {
        subscriber.subscribe(doFinally(publisher.getPublisher(), doFinally));
        publisher.complete();
        subscriber.cancel();
        verify(doFinally).run();
        publisher.verifyCancelled();
    }

    @Test
    public void testForCompletePostEmissions() {
        subscriber.subscribe(doFinally(publisher.getPublisher(), doFinally)).request(1);
        publisher.sendItems("Hello").complete();
        subscriber.verifySuccess("Hello");
        verify(doFinally).run();
        publisher.verifyNotCancelled();
    }

    @Test
    public void testForCompleteNoEmissions() {
        subscriber.subscribe(doFinally(publisher.getPublisher(), doFinally)).request(1);
        publisher.complete();
        subscriber.verifySuccess();
        verify(doFinally).run();
        publisher.verifyNotCancelled();
    }

    @Test
    public void testForErrorPostEmissions() {
        subscriber.subscribe(doFinally(publisher.getPublisher(), doFinally)).request(1);
        publisher.sendItems("Hello").fail();
        subscriber.verifyItems("Hello").verifyFailure(DELIBERATE_EXCEPTION);
        verify(doFinally).run();
        publisher.verifyNotCancelled();
    }

    @Test
    public void testForErrorNoEmissions() {
        subscriber.subscribe(doFinally(publisher.getPublisher(), doFinally)).request(1);
        publisher.fail();
        subscriber.verifyFailure(DELIBERATE_EXCEPTION);
        verify(doFinally).run();
        publisher.verifyNotCancelled();
    }

    @Test
    public void testCallbackThrowsErrorOnCancel() {
        thrown.expect(is(sameInstance(DELIBERATE_EXCEPTION)));
        AtomicInteger invocationCount = new AtomicInteger();
        try {
            subscriber.subscribe(doFinally(publisher.getPublisher(), () -> {
                invocationCount.incrementAndGet();
                throw DELIBERATE_EXCEPTION;
            }));
            subscriber.cancel();
        } finally {
            assertThat("Unexpected calls to doFinally callback.", invocationCount.get(), is(1));

            publisher.verifyCancelled();
        }
    }

    @Test
    public abstract void testCallbackThrowsErrorOnComplete();

    @Test
    public abstract void testCallbackThrowsErrorOnError();

    protected abstract <T> Publisher<T> doFinally(Publisher<T> publisher, Runnable runnable);
}
