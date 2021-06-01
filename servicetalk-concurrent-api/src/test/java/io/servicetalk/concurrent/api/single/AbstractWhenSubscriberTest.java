/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api.single;

import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.concurrent.test.internal.TestSingleSubscriber;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public abstract class AbstractWhenSubscriberTest {
    private final TestSingleSubscriber<String> listener = new TestSingleSubscriber<>();

    private SingleSource.Subscriber<String> subscriber;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        subscriber = mock(SingleSource.Subscriber.class);
    }

    @Test
    void testOnWithOnSuccess() {
        toSource(doSubscriber(Single.succeeded("Hello"), () -> subscriber)).subscribe(listener);
        assertThat(listener.awaitOnSuccess(), is("Hello"));
        verify(subscriber).onSubscribe(any());
        verify(subscriber).onSuccess("Hello");
    }

    @Test
    void testOnWithOnError() {
        toSource(doSubscriber(Single.failed(DELIBERATE_EXCEPTION), () -> subscriber)).subscribe(listener);
        assertThat(listener.awaitOnError(), is(DELIBERATE_EXCEPTION));
        verify(subscriber).onSubscribe(any());
        verify(subscriber).onError(DeliberateException.DELIBERATE_EXCEPTION);
    }

    protected abstract <T> Single<T> doSubscriber(Single<T> single,
                                                  Supplier<SingleSource.Subscriber<? super T>> subscriberSupplier);
}
