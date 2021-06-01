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

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.api.Test;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public abstract class AbstractWhenOnCompleteTest {

    private final TestPublisher<String> publisher = new TestPublisher<>();
    private final TestPublisherSubscriber<String> subscriber = new TestPublisherSubscriber<>();

    @Test
    void testComplete() {
        Runnable onComplete = mock(Runnable.class);
        toSource(doComplete(publisher, onComplete)).subscribe(subscriber);
        publisher.onComplete();
        subscriber.awaitOnComplete();
        verify(onComplete).run();
    }

    @Test
    void testCallbackThrowsError() {
        DeliberateException srcEx = new DeliberateException();
        Publisher<String> src = doComplete(Publisher.failed(srcEx), () -> {
            throw DELIBERATE_EXCEPTION;
        });
        toSource(src).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        assertThat(subscriber.awaitOnError(), sameInstance(srcEx));
    }

    protected abstract <T> Publisher<T> doComplete(Publisher<T> publisher, Runnable runnable);
}
