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
package io.servicetalk.concurrent.api.completable;

import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.concurrent.test.internal.TestCompletableSubscriber;

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
    final TestCompletableSubscriber listener = new TestCompletableSubscriber();
    private CompletableSource.Subscriber subscriber;

    @BeforeEach
    void setUp() {
        subscriber = mock(CompletableSource.Subscriber.class);
    }

    @Test
    void testOnWithOnComplete() {
        toSource(doSubscriber(Completable.completed(), () -> subscriber)).subscribe(listener);
        listener.awaitOnComplete();
        verify(subscriber).onSubscribe(any());
        verify(subscriber).onComplete();
    }

    @Test
    void testOnWithOnError() {
        toSource(doSubscriber(Completable.failed(DELIBERATE_EXCEPTION), () -> subscriber)).subscribe(listener);
        assertThat(listener.awaitOnError(), is(DELIBERATE_EXCEPTION));
        verify(subscriber).onSubscribe(any());
        verify(subscriber).onError(DeliberateException.DELIBERATE_EXCEPTION);
    }

    protected abstract Completable doSubscriber(Completable completable,
                                                Supplier<CompletableSource.Subscriber> subscriberSupplier);
}
