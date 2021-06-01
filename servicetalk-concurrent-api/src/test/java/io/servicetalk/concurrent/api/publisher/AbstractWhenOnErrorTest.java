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

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public abstract class AbstractWhenOnErrorTest {

    private final TestPublisher<String> publisher = new TestPublisher<>();
    final TestPublisherSubscriber<String> subscriber = new TestPublisherSubscriber<>();

    @Test
    void testError() {
        @SuppressWarnings("unchecked")
        Consumer<Throwable> onError = mock(Consumer.class);
        doError(publisher, onError).subscribe(subscriber);
        publisher.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));

        verify(onError).accept(DELIBERATE_EXCEPTION);
    }

    @Test
    abstract void testCallbackThrowsError();

    protected abstract <T> PublisherSource<T> doError(Publisher<T> publisher, Consumer<Throwable> consumer);
}
