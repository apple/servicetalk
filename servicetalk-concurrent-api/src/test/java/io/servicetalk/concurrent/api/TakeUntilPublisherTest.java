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
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.stubbing.Answer;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TakeUntilPublisherTest {
    private final TestPublisher<String> publisher = new TestPublisher<>();
    private final TestPublisherSubscriber<String> subscriber = new TestPublisherSubscriber<>();
    private final TestSubscription subscription = new TestSubscription();
    private final TestCompletable completable = new TestCompletable();
    private final TestCancellable cancellable = new TestCancellable();

    @ParameterizedTest(name = "{displayName} [{index}] withError={0}")
    @ValueSource(booleans = {false, true})
    void testUntil(boolean withError) {
        Publisher<String> p = publisher.takeUntil(completable);
        toSource(p).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        subscriber.awaitSubscription().request(4);
        publisher.onNext("Hello1", "Hello2", "Hello3");
        if (withError) {
            completable.onError(DELIBERATE_EXCEPTION);
            assertThat(subscriber.takeOnNext(3), contains("Hello1", "Hello2", "Hello3"));
            assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
        } else {
            completable.onComplete();
            assertThat(subscriber.takeOnNext(3), contains("Hello1", "Hello2", "Hello3"));
            subscriber.awaitOnComplete();
        }
        assertThat(subscription.isCancelled(), is(true));
    }

    @ParameterizedTest(name = "{displayName} [{index}] withError={0}")
    @ValueSource(booleans = {false, true})
    void testTerminates(boolean withError) {
        Publisher<String> p = publisher.takeUntil(completable);
        toSource(p).subscribe(subscriber);
        completable.onSubscribe(cancellable);
        subscriber.awaitSubscription().request(4);
        publisher.onNext("Hello1");
        if (withError) {
            publisher.onError(DELIBERATE_EXCEPTION);
            assertThat(subscriber.takeOnNext(), is("Hello1"));
            assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
        } else {
            publisher.onComplete();
            assertThat(subscriber.takeOnNext(), is("Hello1"));
            subscriber.awaitOnComplete();
        }
        assertThat(cancellable.isCancelled(), is(true));
    }

    @Test
    void testSubCancelled() throws InterruptedException {
        Publisher<String> p = publisher.takeUntil(completable);
        toSource(p).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        completable.onSubscribe(cancellable);
        subscriber.awaitSubscription().request(3);
        publisher.onNext("Hello1", "Hello2");
        assertThat(subscriber.takeOnNext(2), contains("Hello1", "Hello2"));
        subscriber.awaitSubscription().cancel();
        assertThat(subscription.isCancelled(), is(true));
        cancellable.awaitCancelled();
    }

    @ParameterizedTest(name = "{displayName} [{index}] withError={0}")
    @ValueSource(booleans = {false, true})
    void testPublisherTerminalSignalWins(boolean withError) {
        AtomicBoolean completableCancelled = new AtomicBoolean();
        toSource(publisher.takeUntil(new Completable() {
            @Override
            protected void handleSubscribe(final CompletableSource.Subscriber subscriber) {
                subscriber.onSubscribe(() -> {
                    if (completableCancelled.compareAndSet(false, true)) {
                        subscriber.onError(new AssertionError(
                                "Unexpected error propagated before actual terminal signal"));
                    }
                });
            }
        })).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        subscriber.awaitSubscription().request(4);
        publisher.onNext("Hello1", "Hello2");
        if (withError) {
            publisher.onError(DELIBERATE_EXCEPTION);
            assertThat(subscriber.pollAllOnNext(), contains("Hello1", "Hello2"));
            assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
        } else {
            publisher.onComplete();
            assertThat(subscriber.pollAllOnNext(), contains("Hello1", "Hello2"));
            subscriber.awaitOnComplete();
        }
        assertThat(completableCancelled.get(), is(true));
    }

    @ParameterizedTest(name = "{displayName} [{index}] withError={0}")
    @ValueSource(booleans = {false, true})
    void testCompletableTerminalSignalWins(boolean withError) {
        AtomicBoolean publisherCancelled = new AtomicBoolean();
        toSource(new Publisher<String>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super String> subscriber) {
                subscriber.onSubscribe(new Subscription() {
                    @Override
                    public void request(final long n) {
                    }

                    @Override
                    public void cancel() {
                        if (publisherCancelled.compareAndSet(false, true)) {
                            subscriber.onError(new AssertionError(
                                    "Unexpected error propagated before actual terminal signal"));
                        }
                    }
                });
            }
        }.takeUntil(completable)).subscribe(subscriber);
        completable.onSubscribe(cancellable);
        subscriber.awaitSubscription().request(4);
        if (withError) {
            completable.onError(DELIBERATE_EXCEPTION);
            assertThat(subscriber.pollAllOnNext(), is(empty()));
            assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
        } else {
            completable.onComplete();
            assertThat(subscriber.pollAllOnNext(), is(empty()));
            subscriber.awaitOnComplete();
        }
        assertThat(publisherCancelled.get(), is(true));
    }

    @Test
    void resubscribe() throws InterruptedException {
        // Intentionally have publisher outside the defer, we need to extract the TestPublisher from each subscribe.
        final TestResubscribePublisher<String> resubscribePublisher = new TestResubscribePublisher<>();
        final BlockingQueue<CompletableSource.Processor> processors = new LinkedTransferQueue<>();
        Publisher<String> publisher = Publisher.defer(() -> {
            CompletableSource.Processor processor = Processors.newCompletableProcessor();
            processors.add(processor);
            return resubscribePublisher.takeUntil(fromSource(processor));
        });
        @SuppressWarnings("unchecked")
        Subscriber<String> resubscribeSubscriber = mock(Subscriber.class);
        @SuppressWarnings("unchecked")
        Subscriber<String> subscriber = mock(Subscriber.class);
        doAnswer((Answer<Void>) invocation -> {
            toSource(publisher).subscribe(subscriber);
            return null;
        }).when(resubscribeSubscriber).onComplete();
        doAnswer((Answer<Void>) invocation -> {
            Subscription s = invocation.getArgument(0);
            s.request(3);
            return null;
        }).when(resubscribeSubscriber).onSubscribe(any());
        doAnswer((Answer<Void>) invocation -> {
            Subscription s = invocation.getArgument(0);
            s.request(3);
            return null;
        }).when(subscriber).onSubscribe(any());

        toSource(publisher).subscribe(resubscribeSubscriber);

        TestPublisher<String> testPublisher1 = resubscribePublisher.publisher();
        TestSubscription testSubscription1 = resubscribePublisher.subscription();
        CompletableSource.Processor completable1 = processors.take();
        testSubscription1.awaitRequestN(2);
        testPublisher1.onNext("Hello1", "Hello2");

        verify(resubscribeSubscriber).onNext("Hello1");
        verify(resubscribeSubscriber).onNext("Hello2");

        completable1.onComplete();
        testSubscription1.awaitCancelled();

        verify(resubscribeSubscriber).onComplete();
        verify(resubscribeSubscriber, never()).onError(any());

        verify(subscriber, never()).onNext(any());
        verify(subscriber, never()).onComplete();
        verify(subscriber, never()).onError(any());

        TestPublisher<String> testPublisher2 = resubscribePublisher.publisher();
        TestSubscription testSubscription2 = resubscribePublisher.subscription();
        CompletableSource.Processor completable2 = processors.take();
        testSubscription2.awaitRequestN(2);
        testPublisher2.onNext("Hello3", "Hello4");

        completable2.onComplete();
        testSubscription2.awaitCancelled();
        verify(subscriber).onComplete();
        verify(subscriber, never()).onError(any());
    }
}
