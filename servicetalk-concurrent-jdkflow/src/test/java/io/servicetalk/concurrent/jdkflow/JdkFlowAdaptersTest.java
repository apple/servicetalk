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
package io.servicetalk.concurrent.jdkflow;

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.internal.ScalarValueSubscription;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static io.servicetalk.concurrent.api.Publisher.failed;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.EmptySubscriptions.EMPTY_SUBSCRIPTION;
import static io.servicetalk.concurrent.jdkflow.JdkFlowAdapters.fromFlowPublisher;
import static io.servicetalk.concurrent.jdkflow.JdkFlowAdapters.toFlowPublisher;
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

class JdkFlowAdaptersTest {

    @Test
    void fromFlowSuccess() throws Exception {
        Publisher<Integer> flowPublisher = newMockFlowPublisher((subscriber, __) -> {
            subscriber.onNext(1);
            subscriber.onComplete();
        });
        Integer result = fromFlowPublisher(flowPublisher).firstOrElse(() -> null).toFuture().get();
        assertThat("Unexpected result", result, is(1));
    }

    @Test
    void fromFlowError() {
        Publisher<Integer> flowPublisher = newMockFlowPublisher((subscriber, __) ->
                subscriber.onError(DELIBERATE_EXCEPTION));
        Future<Integer> future = fromFlowPublisher(flowPublisher).firstOrElse(() -> null).toFuture();
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertThat(ex.getCause(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void fromFlowCancel() {
        AtomicReference<Subscription> receivedSubscription = new AtomicReference<>();
        Publisher<Integer> flowPublisher = newMockFlowPublisher((__, subscription) ->
                receivedSubscription.set(subscription));
        fromFlowPublisher(flowPublisher).firstOrElse(() -> null).toFuture().cancel(true);
        Subscription subscription = receivedSubscription.get();
        assertThat("Subscription not received.", subscription, is(notNullValue()));
        verify(subscription).cancel();
    }

    @Test
    void toFlowSuccess() {
        verifyFlowSuccess(toFlowPublisherAndSubscribe(from(1)));
    }

    @Test
    void toFlowFromSourceSuccess() {
        PublisherSource<Integer> source = s -> s.onSubscribe(new ScalarValueSubscription<>(1, s));
        verifyFlowSuccess(toFlowPublisherFromSourceAndSubscribe(source));
    }

    private void verifyFlowSuccess(final Subscriber<Integer> subscriber) {
        verify(subscriber).onSubscribe(any());
        verify(subscriber).onNext(1);
        verify(subscriber).onComplete();
        verifyNoMoreInteractions(subscriber);
    }

    @Test
    void toFlowError() {
        verifyFlowError(toFlowPublisherAndSubscribe(failed(DELIBERATE_EXCEPTION)));
    }

    @Test
    void toFlowFromSourceError() {
        PublisherSource<Integer> source = s -> {
            s.onSubscribe(EMPTY_SUBSCRIPTION);
            s.onError(DELIBERATE_EXCEPTION);
        };
        verifyFlowError(toFlowPublisherFromSourceAndSubscribe(source));
    }

    private void verifyFlowError(final Subscriber<Integer> subscriber) {
        verify(subscriber).onSubscribe(any());
        verify(subscriber).onError(DELIBERATE_EXCEPTION);
        verifyNoMoreInteractions(subscriber);
    }

    @Test
    void toFlowCancel() {
        TestPublisher<Integer> stPublisher = new TestPublisher<>();
        Subscriber<Integer> subscriber = toFlowPublisherAndSubscribe(stPublisher);
        TestSubscription subscription = new TestSubscription();
        stPublisher.onSubscribe(subscription);
        assertThat("Source not subscribed.", stPublisher.isSubscribed(), is(true));
        ArgumentCaptor<Subscription> subscriptionCaptor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriber).onSubscribe(subscriptionCaptor.capture());
        subscriptionCaptor.getValue().cancel();
        assertThat("Subscription not cancelled.", subscription.isCancelled(), is(true));
    }

    @Test
    void toFlowFromSourceCancel() {
        PublisherSource.Subscription srcSubscription = mock(PublisherSource.Subscription.class);
        PublisherSource<Integer> source = s -> s.onSubscribe(srcSubscription);
        Subscriber<Integer> subscriber = toFlowPublisherFromSourceAndSubscribe(source);
        ArgumentCaptor<Subscription> flowSubscriptionCaptor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriber).onSubscribe(flowSubscriptionCaptor.capture());
        flowSubscriptionCaptor.getValue().cancel();
        verify(srcSubscription).cancel();
    }

    private Subscriber<Integer> toFlowPublisherAndSubscribe(
            final io.servicetalk.concurrent.api.Publisher<Integer> stPublisher) {
        Publisher<Integer> flowPublisher = toFlowPublisher(stPublisher);
        return subscribeToFlowPublisher(flowPublisher);
    }

    private Subscriber<Integer> toFlowPublisherFromSourceAndSubscribe(final PublisherSource<Integer> source) {
        Publisher<Integer> flowPublisher = toFlowPublisher(source);
        return subscribeToFlowPublisher(flowPublisher);
    }

    private Subscriber<Integer> subscribeToFlowPublisher(final Publisher<Integer> flowPublisher) {
        @SuppressWarnings("unchecked")
        Subscriber<Integer> subscriber = mock(Subscriber.class);
        flowPublisher.subscribe(subscriber);
        ArgumentCaptor<Subscription> subscriptionCaptor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriber).onSubscribe(subscriptionCaptor.capture());
        subscriptionCaptor.getValue().request(1);
        return subscriber;
    }

    private Publisher<Integer> newMockFlowPublisher(
            BiConsumer<Subscriber<? super Integer>, Subscription> subscriberTerminator) {
        @SuppressWarnings("unchecked")
        Publisher<Integer> flowPublisher = mock(Publisher.class);
        doAnswer(invocation -> {
            Subscriber<? super Integer> subscriber = invocation.getArgument(0);
            Subscription subscription = mock(Subscription.class);
            doAnswer(invocation1 -> {
                subscriberTerminator.accept(subscriber, subscription);
                return null;
            }).when(subscription).request(anyLong());
            subscriber.onSubscribe(subscription);
            return null;
        }).when(flowPublisher).subscribe(any());
        return flowPublisher;
    }
}
