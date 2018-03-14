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

import java.util.function.Consumer;

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public abstract class AbstractDoErrorTest {

    @Rule
    public final MockedSubscriberRule<String> rule = new MockedSubscriberRule<>();

    @Rule
    public final PublisherRule<String> publisher = new PublisherRule<>();

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    @Test
    public void testError() {
        @SuppressWarnings("unchecked")
        Consumer<Throwable> onError = mock(Consumer.class);
        rule.subscribe(doError(publisher.getPublisher(), onError));
        publisher.fail();
        rule.verifyFailure(DELIBERATE_EXCEPTION);

        verify(onError).accept(DELIBERATE_EXCEPTION);
    }

    @Test
    public abstract void testCallbackThrowsError();

    protected abstract <T> Publisher<T> doError(Publisher<T> publisher, Consumer<Throwable> consumer);
}
