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
package io.servicetalk.concurrent.api.publisher;

import io.servicetalk.concurrent.api.MockedSubscriberRule;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.PublisherRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public abstract class AbstractDoCancelTest {

    @Rule
    public final PublisherRule<String> publisher = new PublisherRule<>();

    @Rule
    public final MockedSubscriberRule<String> subscriber = new MockedSubscriberRule<>();

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    @Test
    public void testCancelAfterEmissions() {
        Runnable onCancel = mock(Runnable.class);
        subscriber.subscribe(doCancel(publisher.getPublisher(), onCancel)).request(1);
        publisher.sendItems("Hello");
        subscriber.verifyItems("Hello").cancel();
        verify(onCancel).run();
        publisher.verifyCancelled();
    }

    @Test
    public void testCancelNoEmissions() {
        Runnable onCancel = mock(Runnable.class);
        subscriber.subscribe(doCancel(publisher.getPublisher(), onCancel));
        subscriber.cancel();
        verify(onCancel).run();
        publisher.verifyCancelled();
    }

    @Test
    public void testCallbackThrowsError() {
        thrown.expect(is(sameInstance(DELIBERATE_EXCEPTION)));

        try {
            subscriber.subscribe(doCancel(publisher.getPublisher(), () -> {
                throw DELIBERATE_EXCEPTION;
            }));
            subscriber.cancel();
        } finally {
            publisher.verifyCancelled();
        }
    }

    protected abstract <T> Publisher<T> doCancel(Publisher<T> publisher, Runnable runnable);
}
