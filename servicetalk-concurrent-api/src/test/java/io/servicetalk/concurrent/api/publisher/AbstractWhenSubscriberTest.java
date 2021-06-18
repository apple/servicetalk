/*
 * Copyright © 2018-2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public abstract class AbstractWhenSubscriberTest {
    @SuppressWarnings("unchecked")
    private final Subscriber<String> subscriber = (Subscriber<String>) mock(Subscriber.class);
    private final TestPublisherSubscriber<String> finalSubscriber = new TestPublisherSubscriber<>();

    @Test
    void testOnWithOnComplete() {
        toSource(doSubscriber(from("Hello"), () -> subscriber)).subscribe(finalSubscriber);
        finalSubscriber.awaitSubscription().request(1);
        assertThat(finalSubscriber.takeOnNext(), is("Hello"));
        finalSubscriber.awaitOnComplete();
        verify(subscriber).onNext(eq("Hello"));
        verify(subscriber).onComplete();
    }

    @Test
    void testOnWithOnError() {
        toSource(doSubscriber(Publisher.failed(DeliberateException.DELIBERATE_EXCEPTION), () -> subscriber))
                .subscribe(finalSubscriber);
        assertThat(finalSubscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
        verify(subscriber).onError(eq(DELIBERATE_EXCEPTION));
    }

    protected abstract <T> Publisher<T> doSubscriber(Publisher<T> publisher,
                                                     Supplier<Subscriber<? super T>> subscriberSupplier);
}
