/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.reactivestreams;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestCompletable;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSingle;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.internal.ScalarValueSubscription;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.Publisher.failed;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.EmptySubscriptions.EMPTY_SUBSCRIPTION;
import static io.servicetalk.concurrent.reactivestreams.ReactiveStreamsAdapters.fromReactiveStreamsPublisher;
import static io.servicetalk.concurrent.reactivestreams.ReactiveStreamsAdapters.toReactiveStreamsPublisher;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class ReactiveStreamsAdaptersTest {

    @Test
    void fromRSSuccess() throws Exception {
        Publisher<Integer> rsPublisher = newMockRsPublisher((subscriber, __) -> {
            subscriber.onNext(1);
            subscriber.onComplete();
        });
        Integer result = fromReactiveStreamsPublisher(rsPublisher).firstOrElse(() -> null).toFuture().get();
        assertThat("Unexpected result", result, is(1));
    }

    @Test
    void fromRSError() {
        Publisher<Integer> rsPublisher = newMockRsPublisher((subscriber, __) ->
                subscriber.onError(DELIBERATE_EXCEPTION));
        Future<Integer> future = fromReactiveStreamsPublisher(rsPublisher).firstOrElse(() -> null).toFuture();
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertThat(ex.getCause(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void fromRSCancel() {
        AtomicReference<Subscription> receivedSubscription = new AtomicReference<>();
        Publisher<Integer> rsPublisher = newMockRsPublisher((__, subscription) ->
                receivedSubscription.set(subscription));
        fromReactiveStreamsPublisher(rsPublisher).firstOrElse(() -> null).toFuture().cancel(true);
        Subscription subscription = receivedSubscription.get();
        assertThat("Subscription not received.", subscription, is(notNullValue()));
        verify(subscription).cancel();
    }

    @Test
    void singleToRSSuccess() {
        verifyRSSuccess(toRSPublisherAndSubscribe(succeeded(1)), true);
    }

    @Test
    void singleToRSFromSourceSuccess() {
        SingleSource<Integer> source = s -> {
            s.onSubscribe(IGNORE_CANCEL);
            s.onSuccess(1);
        };
        verifyRSSuccess(toRSPublisherFromSourceAndSubscribe(source), true);
    }

    @Test
    void completableToRSSuccess() {
        verifyRSSuccess(toRSPublisherAndSubscribe(Completable.completed()), false);
    }

    @Test
    void completableToRSFromSourceSuccess() {
        CompletableSource source = s -> {
            s.onSubscribe(IGNORE_CANCEL);
            s.onComplete();
        };
        verifyRSSuccess(toRSPublisherFromSourceAndSubscribe(source), false);
    }

    @Test
    void toRSSuccess() {
        verifyRSSuccess(toRSPublisherAndSubscribe(from(1)), true);
    }

    @Test
    void toRSFromSourceSuccess() {
        PublisherSource<Integer> source = s -> s.onSubscribe(new ScalarValueSubscription<>(1, s));
        verifyRSSuccess(toRSPublisherFromSourceAndSubscribe(source), true);
    }

    private void verifyRSSuccess(final Subscriber<Integer> subscriber, boolean onNext) {
        verify(subscriber).onSubscribe(any());
        if (onNext) {
            verify(subscriber).onNext(1);
        }
        verify(subscriber).onComplete();
        verifyNoMoreInteractions(subscriber);
    }

    @Test
    void singleToRSError() {
        verifyRSError(toRSPublisherAndSubscribe(Single.failed(DELIBERATE_EXCEPTION)));
    }

    @Test
    void completableToRSError() {
        verifyRSError(toRSPublisherAndSubscribe(Completable.failed(DELIBERATE_EXCEPTION)));
    }

    @Test
    void toRSError() {
        verifyRSError(toRSPublisherAndSubscribe(failed(DELIBERATE_EXCEPTION)));
    }

    @Test
    void singleToRSFromSourceError() {
        SingleSource<Integer> source = s -> {
            s.onSubscribe(IGNORE_CANCEL);
            s.onError(DELIBERATE_EXCEPTION);
        };
        verifyRSError(toRSPublisherFromSourceAndSubscribe(source));
    }

    @Test
    void completableToRSFromSourceError() {
        CompletableSource source = s -> {
            s.onSubscribe(EMPTY_SUBSCRIPTION);
            s.onError(DELIBERATE_EXCEPTION);
        };
        verifyRSError(toRSPublisherFromSourceAndSubscribe(source));
    }

    @Test
    void toRSFromSourceError() {
        PublisherSource<Integer> source = s -> {
            s.onSubscribe(EMPTY_SUBSCRIPTION);
            s.onError(DELIBERATE_EXCEPTION);
        };
        verifyRSError(toRSPublisherFromSourceAndSubscribe(source));
    }

    private void verifyRSError(final Subscriber<Integer> subscriber) {
        verify(subscriber).onSubscribe(any());
        verify(subscriber).onError(DELIBERATE_EXCEPTION);
        verifyNoMoreInteractions(subscriber);
    }

    @Test
    void singleToRSCancel() {
        TestSingle<Integer> stSingle = new TestSingle<>();
        Subscriber<Integer> subscriber = toRSPublisherAndSubscribe(stSingle);
        TestSubscription subscription = new TestSubscription();
        stSingle.onSubscribe(subscription);
        assertThat("Source not subscribed.", stSingle.isSubscribed(), is(true));
        ArgumentCaptor<Subscription> subscriptionCaptor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriber).onSubscribe(subscriptionCaptor.capture());
        subscriptionCaptor.getValue().cancel();
        assertThat("Subscription not cancelled.", subscription.isCancelled(), is(true));
    }

    @Test
    void completableToRSCancel() {
        TestCompletable stCompletable = new TestCompletable();
        Subscriber<Integer> subscriber = toRSPublisherAndSubscribe(stCompletable);
        TestSubscription subscription = new TestSubscription();
        stCompletable.onSubscribe(subscription);
        assertThat("Source not subscribed.", stCompletable.isSubscribed(), is(true));
        ArgumentCaptor<Subscription> subscriptionCaptor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriber).onSubscribe(subscriptionCaptor.capture());
        subscriptionCaptor.getValue().cancel();
        assertThat("Subscription not cancelled.", subscription.isCancelled(), is(true));
    }

    @Test
    void toRSCancel() {
        TestPublisher<Integer> stPublisher = new TestPublisher<>();
        Subscriber<Integer> subscriber = toRSPublisherAndSubscribe(stPublisher);
        TestSubscription subscription = new TestSubscription();
        stPublisher.onSubscribe(subscription);
        assertThat("Source not subscribed.", stPublisher.isSubscribed(), is(true));
        ArgumentCaptor<Subscription> subscriptionCaptor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriber).onSubscribe(subscriptionCaptor.capture());
        subscriptionCaptor.getValue().cancel();
        assertThat("Subscription not cancelled.", subscription.isCancelled(), is(true));
    }

    @Test
    void singleToRSFromSourceCancel() {
        Cancellable srcCancellable = mock(Cancellable.class);
        SingleSource<Integer> source = s -> s.onSubscribe(srcCancellable);
        Subscriber<Integer> subscriber = toRSPublisherFromSourceAndSubscribe(source);
        ArgumentCaptor<Subscription> rsSubscriptionCaptor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriber).onSubscribe(rsSubscriptionCaptor.capture());
        rsSubscriptionCaptor.getValue().cancel();
        verify(srcCancellable).cancel();
    }

    @Test
    void completableToRSFromSourceCancel() {
        Cancellable srcCancellable = mock(Cancellable.class);
        CompletableSource source = s -> s.onSubscribe(srcCancellable);
        Subscriber<Integer> subscriber = toRSPublisherFromSourceAndSubscribe(source);
        ArgumentCaptor<Subscription> rsSubscriptionCaptor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriber).onSubscribe(rsSubscriptionCaptor.capture());
        rsSubscriptionCaptor.getValue().cancel();
        verify(srcCancellable).cancel();
    }

    @Test
    void toRSFromSourceCancel() {
        PublisherSource.Subscription srcSubscription = mock(PublisherSource.Subscription.class);
        PublisherSource<Integer> source = s -> s.onSubscribe(srcSubscription);
        Subscriber<Integer> subscriber = toRSPublisherFromSourceAndSubscribe(source);
        ArgumentCaptor<Subscription> rsSubscriptionCaptor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriber).onSubscribe(rsSubscriptionCaptor.capture());
        rsSubscriptionCaptor.getValue().cancel();
        verify(srcSubscription).cancel();
    }

    private static Subscriber<Integer> toRSPublisherAndSubscribe(
            final io.servicetalk.concurrent.api.Single<Integer> stSingle) {
        Publisher<Integer> rsPublisher = toReactiveStreamsPublisher(stSingle);
        return subscribeToRSPublisher(rsPublisher, true);
    }

    private static Subscriber<Integer> toRSPublisherAndSubscribe(
            final io.servicetalk.concurrent.api.Completable stCompletable) {
        Publisher<Integer> rsPublisher = toReactiveStreamsPublisher(stCompletable);
        return subscribeToRSPublisher(rsPublisher, false);
    }

    private static Subscriber<Integer> toRSPublisherAndSubscribe(
            final io.servicetalk.concurrent.api.Publisher<Integer> stPublisher) {
        Publisher<Integer> rsPublisher = toReactiveStreamsPublisher(stPublisher);
        return subscribeToRSPublisher(rsPublisher, true);
    }

    private static Subscriber<Integer> toRSPublisherFromSourceAndSubscribe(final SingleSource<Integer> source) {
        Publisher<Integer> rsPublisher = toReactiveStreamsPublisher(source);
        return subscribeToRSPublisher(rsPublisher, true);
    }

    private static Subscriber<Integer> toRSPublisherFromSourceAndSubscribe(final CompletableSource source) {
        Publisher<Integer> rsPublisher = toReactiveStreamsPublisher(source);
        return subscribeToRSPublisher(rsPublisher, false);
    }

    private static Subscriber<Integer> toRSPublisherFromSourceAndSubscribe(final PublisherSource<Integer> source) {
        Publisher<Integer> rsPublisher = toReactiveStreamsPublisher(source);
        return subscribeToRSPublisher(rsPublisher, true);
    }

    private static Subscriber<Integer> subscribeToRSPublisher(final Publisher<Integer> rsPublisher,
                                                              final boolean forceRequest) {
        @SuppressWarnings("unchecked")
        Subscriber<Integer> subscriber = mock(Subscriber.class);
        rsPublisher.subscribe(subscriber);
        if (forceRequest) {
            ArgumentCaptor<Subscription> subscriptionCaptor = ArgumentCaptor.forClass(Subscription.class);
            verify(subscriber).onSubscribe(subscriptionCaptor.capture());
            subscriptionCaptor.getValue().request(1);
        }
        return subscriber;
    }

    private static Publisher<Integer> newMockRsPublisher(
            BiConsumer<Subscriber<? super Integer>, Subscription> subscriberTerminator) {
        @SuppressWarnings("unchecked")
        Publisher<Integer> rsPublisher = mock(Publisher.class);
        doAnswer(invocation -> {
            Subscriber<? super Integer> subscriber = invocation.getArgument(0);
            Subscription subscription = mock(Subscription.class);
            doAnswer(invocation1 -> {
                subscriberTerminator.accept(subscriber, subscription);
                return null;
            }).when(subscription).request(anyLong());
            subscriber.onSubscribe(subscription);
            return null;
        }).when(rsPublisher).subscribe(any());
        return rsPublisher;
    }
}
