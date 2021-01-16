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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.internal.ScalarValueSubscription;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.EmptySubscriptions.EMPTY_SUBSCRIPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class SourceAdaptersTest {

    @Rule
    public final ExpectedException expectedException = ExpectedException.none();

    @Test
    public void publisherToSourceSuccess() {
        PublisherSource.Subscriber<Integer> subscriber = toSourceAndSubscribe(from(1));
        verify(subscriber).onNext(1);
        verify(subscriber).onComplete();
        verifyNoMoreInteractions(subscriber);
    }

    @Test
    public void publisherToSourceError() {
        PublisherSource.Subscriber<Integer> subscriber = toSourceAndSubscribe(Publisher.failed(DELIBERATE_EXCEPTION));
        verify(subscriber).onError(DELIBERATE_EXCEPTION);
        verifyNoMoreInteractions(subscriber);
    }

    @Test
    public void publisherToSourceCancel() {
        TestPublisher<Integer> stPublisher = new TestPublisher<>();
        PublisherSource.Subscriber<Integer> subscriber = toSourceAndSubscribe(stPublisher);
        TestSubscription subscription = new TestSubscription();
        stPublisher.onSubscribe(subscription);
        assertThat("Source not subscribed.", stPublisher.isSubscribed(), is(true));
        ArgumentCaptor<Subscription> subscriptionCaptor = forClass(Subscription.class);
        verify(subscriber).onSubscribe(subscriptionCaptor.capture());
        subscriptionCaptor.getValue().cancel();
        assertThat("Subscription not cancelled.", subscription.isCancelled(), is(true));
    }

    @Test
    public void singleToSourceSuccess() {
        SingleSource.Subscriber<Integer> subscriber = toSourceAndSubscribe(succeeded(1));
        verify(subscriber).onSuccess(1);
        verifyNoMoreInteractions(subscriber);
    }

    @Test
    public void singleToSourceError() {
        SingleSource.Subscriber<Integer> subscriber = toSourceAndSubscribe(Single.failed(DELIBERATE_EXCEPTION));
        verify(subscriber).onError(DELIBERATE_EXCEPTION);
        verifyNoMoreInteractions(subscriber);
    }

    @Test
    public void singleToSourceCancel() {
        LegacyTestSingle<Integer> stSingle = new LegacyTestSingle<>();
        SingleSource.Subscriber<Integer> subscriber = toSourceAndSubscribe(stSingle);
        stSingle.verifyListenCalled();
        ArgumentCaptor<Cancellable> cancellableCaptor = forClass(Cancellable.class);
        verify(subscriber).onSubscribe(cancellableCaptor.capture());
        cancellableCaptor.getValue().cancel();
        stSingle.verifyCancelled();
    }

    @Test
    public void completableToSourceSuccess() {
        CompletableSource.Subscriber subscriber = toSourceAndSubscribe(completed());
        verify(subscriber).onComplete();
        verifyNoMoreInteractions(subscriber);
    }

    @Test
    public void completableToSourceError() {
        CompletableSource.Subscriber subscriber = toSourceAndSubscribe(Completable.failed(DELIBERATE_EXCEPTION));
        verify(subscriber).onError(DELIBERATE_EXCEPTION);
        verifyNoMoreInteractions(subscriber);
    }

    @Test
    public void completableToSourceCancel() {
        LegacyTestCompletable stCompletable = new LegacyTestCompletable();
        CompletableSource.Subscriber subscriber = toSourceAndSubscribe(stCompletable);
        stCompletable.verifyListenCalled();
        ArgumentCaptor<Cancellable> cancellableCaptor = forClass(Cancellable.class);
        verify(subscriber).onSubscribe(cancellableCaptor.capture());
        cancellableCaptor.getValue().cancel();
        stCompletable.verifyCancelled();
    }

    @Test
    public void publisherFromSourceSuccess() throws Exception {
        PublisherSource<Integer> src = s -> s.onSubscribe(new ScalarValueSubscription<>(1, s));
        Integer result = fromSource(src).firstOrElse(() -> null).toFuture().get();
        assertThat("Unexpected result.", result, is(1));
    }

    @Test
    public void publisherFromSourceError() throws Exception {
        PublisherSource<Integer> src = s -> {
            s.onSubscribe(EMPTY_SUBSCRIPTION);
            s.onError(DELIBERATE_EXCEPTION);
        };

        Future<Integer> future = fromSource(src).firstOrElse(() -> null).toFuture();
        expectedException.expect(ExecutionException.class);
        expectedException.expectCause(sameInstance(DELIBERATE_EXCEPTION));
        future.get();
    }

    @Test
    public void publisherFromSourceCancel() {
        PublisherSource.Subscription srcSubscription = mock(PublisherSource.Subscription.class);
        PublisherSource<Integer> source = s -> s.onSubscribe(srcSubscription);

        fromSource(source).firstOrElse(() -> null).toFuture().cancel(true);
        verify(srcSubscription).cancel();
    }

    @Test
    public void singleFromSourceSuccess() throws Exception {
        SingleSource<Integer> src = s -> {
            s.onSubscribe(IGNORE_CANCEL);
            s.onSuccess(1);
        };
        Integer result = fromSource(src).toFuture().get();
        assertThat("Unexpected result.", result, is(1));
    }

    @Test
    public void singleFromSourceError() throws Exception {
        SingleSource<Integer> src = s -> {
            s.onSubscribe(IGNORE_CANCEL);
            s.onError(DELIBERATE_EXCEPTION);
        };

        Future<Integer> future = fromSource(src).toFuture();
        expectedException.expect(ExecutionException.class);
        expectedException.expectCause(sameInstance(DELIBERATE_EXCEPTION));
        future.get();
    }

    @Test
    public void singleFromSourceCancel() {
        Cancellable srcCancellable = mock(Cancellable.class);
        SingleSource<Integer> source = s -> s.onSubscribe(srcCancellable);

        fromSource(source).toFuture().cancel(true);
        verify(srcCancellable).cancel();
    }

    @Test
    public void completableFromSourceSuccess() throws Exception {
        CompletableSource src = s -> {
            s.onSubscribe(IGNORE_CANCEL);
            s.onComplete();
        };
        fromSource(src).toFuture().get();
    }

    @Test
    public void completableFromSourceError() throws Exception {
        CompletableSource src = s -> {
            s.onSubscribe(IGNORE_CANCEL);
            s.onError(DELIBERATE_EXCEPTION);
        };

        Future<Void> future = fromSource(src).toFuture();
        expectedException.expect(ExecutionException.class);
        expectedException.expectCause(sameInstance(DELIBERATE_EXCEPTION));
        future.get();
    }

    @Test
    public void completableFromSourceCancel() {
        Cancellable srcCancellable = mock(Cancellable.class);
        CompletableSource source = s -> s.onSubscribe(srcCancellable);

        fromSource(source).toFuture().cancel(true);
        verify(srcCancellable).cancel();
    }

    private CompletableSource.Subscriber toSourceAndSubscribe(Completable completable) {
        CompletableSource src = toSource(completable);
        CompletableSource.Subscriber subscriber = mock(CompletableSource.Subscriber.class);
        src.subscribe(subscriber);
        verify(subscriber).onSubscribe(any());
        return subscriber;
    }

    private SingleSource.Subscriber<Integer> toSourceAndSubscribe(Single<Integer> single) {
        SingleSource<Integer> src = toSource(single);
        @SuppressWarnings("unchecked")
        SingleSource.Subscriber<Integer> subscriber = mock(SingleSource.Subscriber.class);
        src.subscribe(subscriber);
        verify(subscriber).onSubscribe(any());
        return subscriber;
    }

    private PublisherSource.Subscriber<Integer> toSourceAndSubscribe(final Publisher<Integer> publisher) {
        PublisherSource<Integer> src = toSource(publisher);
        @SuppressWarnings("unchecked")
        PublisherSource.Subscriber<Integer> subscriber = mock(PublisherSource.Subscriber.class);
        src.subscribe(subscriber);
        ArgumentCaptor<Subscription> subscriptionCaptor = forClass(Subscription.class);
        verify(subscriber).onSubscribe(subscriptionCaptor.capture());
        subscriptionCaptor.getValue().request(1);
        return subscriber;
    }
}
