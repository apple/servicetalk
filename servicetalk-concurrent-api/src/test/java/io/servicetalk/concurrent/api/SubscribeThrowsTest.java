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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.internal.SignalOffloader;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SubscribeThrowsTest {

    @Test
    void publisherSubscriberThrows() {
        Publisher<String> p = new Publisher<String>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super String> subscriber) {
                throw DELIBERATE_EXCEPTION;
            }
        };
        Exception e = assertThrows(ExecutionException.class, () -> p.toFuture().get());
        assertThat(e.getCause(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void publisherSubscriberWithOffloaderThrows() {
        SignalOffloader offloader = ((AbstractOffloaderAwareExecutor) immediate()).newSignalOffloader(immediate());
        @SuppressWarnings("unchecked")
        Subscriber<String> subscriber = (Subscriber<String>) mock(Subscriber.class);
        Publisher<String> p = new Publisher<String>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super String> subscriber) {
                throw DELIBERATE_EXCEPTION;
            }
        };
        AsyncContextProvider provider = AsyncContext.provider();
        p.delegateSubscribe(subscriber, offloader, provider.context(), provider);
        verify(subscriber).onError(DELIBERATE_EXCEPTION);
    }

    @Test
    void singleSubscriberThrows() {
        Single<String> s = new Single<String>() {
            @Override
            protected void handleSubscribe(final SingleSource.Subscriber subscriber) {
                throw DELIBERATE_EXCEPTION;
            }
        };
        Exception e = assertThrows(ExecutionException.class, () -> s.toFuture().get());
        assertThat(e.getCause(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void singleSubscriberWithOffloaderThrows() {
        SignalOffloader offloader = ((AbstractOffloaderAwareExecutor) immediate()).newSignalOffloader(immediate());
        @SuppressWarnings("unchecked")
        SingleSource.Subscriber<String> subscriber =
                (SingleSource.Subscriber<String>) mock(SingleSource.Subscriber.class);
        Single<String> s = new Single<String>() {
            @Override
            protected void handleSubscribe(final SingleSource.Subscriber subscriber) {
                throw DELIBERATE_EXCEPTION;
            }
        };
        AsyncContextProvider provider = AsyncContext.provider();
        s.delegateSubscribe(subscriber, offloader, provider.context(), provider);
        verify(subscriber).onError(DELIBERATE_EXCEPTION);
    }

    @Test
    void completableSubscriberThrows() {
        Completable c = new Completable() {
            @Override
            protected void handleSubscribe(final CompletableSource.Subscriber subscriber) {
                throw DELIBERATE_EXCEPTION;
            }
        };
        Exception e = assertThrows(ExecutionException.class, () -> c.toFuture().get());
        assertThat(e.getCause(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void completableSubscriberWithOffloaderThrows() {
        SignalOffloader offloader = ((AbstractOffloaderAwareExecutor) immediate()).newSignalOffloader(immediate());
        CompletableSource.Subscriber subscriber = mock(CompletableSource.Subscriber.class);
        Completable c = new Completable() {
            @Override
            protected void handleSubscribe(final CompletableSource.Subscriber subscriber) {
                throw DELIBERATE_EXCEPTION;
            }
        };
        AsyncContextProvider provider = AsyncContext.provider();
        c.delegateSubscribe(subscriber, offloader, provider.context(), provider);
        verify(subscriber).onError(DELIBERATE_EXCEPTION);
    }
}
