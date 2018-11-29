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
import io.servicetalk.concurrent.internal.DeliberateException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.reactivestreams.Subscriber;

import java.util.function.Supplier;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public abstract class AbstractDoSubscriberTest {

    @Rule
    public final MockedSubscriberRule<String> rule = new MockedSubscriberRule<>();

    private Subscriber<String> subscriber;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() throws Exception {
        subscriber = mock(Subscriber.class);
    }

    @Test
    public void testOnWithOnComplete() {
        rule.subscribe(doSubscriber(Publisher.just("Hello"), () -> subscriber)).verifySuccess("Hello");
        verify(subscriber).onSubscribe(any());
        verify(subscriber).onNext("Hello");
        verify(subscriber).onComplete();
    }

    @Test
    public void testOnWithOnError() {
        rule.subscribe(doSubscriber(Publisher.error(DeliberateException.DELIBERATE_EXCEPTION), () -> subscriber)).verifyFailure(DELIBERATE_EXCEPTION);
        verify(subscriber).onSubscribe(any());
        verify(subscriber).onError(DeliberateException.DELIBERATE_EXCEPTION);
    }

    protected abstract <T> Publisher<T> doSubscriber(Publisher<T> publisher, Supplier<Subscriber<? super T>> subscriberSupplier);
}
