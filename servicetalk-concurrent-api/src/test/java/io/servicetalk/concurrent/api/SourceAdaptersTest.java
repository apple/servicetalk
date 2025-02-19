/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.SourceAdapters.CompletableToCompletableSource;
import io.servicetalk.concurrent.api.SourceAdapters.PublisherToPublisherSource;
import io.servicetalk.concurrent.api.SourceAdapters.SingleToSingleSource;
import io.servicetalk.concurrent.internal.ScalarValueSubscription;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.EmptySubscriptions.EMPTY_SUBSCRIPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class SourceAdaptersTest {

    @ParameterizedTest(name = "{displayName} [{index}] same={0}")
    @ValueSource(booleans = {false, true})
    void publisherToSourceSuccess(boolean same) {
        PublisherSource.Subscriber<Integer> subscriber = toSourceAndSubscribe(Publisher.from(1), same);
        verify(subscriber).onNext(1);
        verify(subscriber).onComplete();
        verifyNoMoreInteractions(subscriber);
    }

    @ParameterizedTest(name = "{displayName} [{index}] same={0}")
    @ValueSource(booleans = {false, true})
    void publisherToSourceError(boolean same) {
        PublisherSource.Subscriber<Integer> subscriber =
                toSourceAndSubscribe(Publisher.failed(DELIBERATE_EXCEPTION), same);
        verify(subscriber).onError(DELIBERATE_EXCEPTION);
        verifyNoMoreInteractions(subscriber);
    }

    @ParameterizedTest(name = "{displayName} [{index}] same={0}")
    @ValueSource(booleans = {false, true})
    void publisherToSourceCancel(boolean same) {
        TestPublisher<Integer> stPublisher = new TestPublisher<>();
        PublisherSource.Subscriber<Integer> subscriber = toSourceAndSubscribe(stPublisher, same);
        TestSubscription subscription = new TestSubscription();
        stPublisher.onSubscribe(subscription);
        assertThat("Source not subscribed.", stPublisher.isSubscribed(), is(true));
        ArgumentCaptor<Subscription> subscriptionCaptor = forClass(Subscription.class);
        verify(subscriber).onSubscribe(subscriptionCaptor.capture());
        subscriptionCaptor.getValue().cancel();
        assertThat("Subscription not cancelled.", subscription.isCancelled(), is(true));
    }

    @ParameterizedTest(name = "{displayName} [{index}] same={0}")
    @ValueSource(booleans = {false, true})
    void singleToSourceSuccess(boolean same) {
        SingleSource.Subscriber<Integer> subscriber = toSourceAndSubscribe(Single.succeeded(1), same);
        verify(subscriber).onSuccess(1);
        verifyNoMoreInteractions(subscriber);
    }

    @ParameterizedTest(name = "{displayName} [{index}] same={0}")
    @ValueSource(booleans = {false, true})
    void singleToSourceError(boolean same) {
        SingleSource.Subscriber<Integer> subscriber = toSourceAndSubscribe(Single.failed(DELIBERATE_EXCEPTION), same);
        verify(subscriber).onError(DELIBERATE_EXCEPTION);
        verifyNoMoreInteractions(subscriber);
    }

    @ParameterizedTest(name = "{displayName} [{index}] same={0}")
    @ValueSource(booleans = {false, true})
    void singleToSourceCancel(boolean same) {
        TestSingle<Integer> stSingle = new TestSingle<>();
        SingleSource.Subscriber<Integer> subscriber = toSourceAndSubscribe(stSingle, same);
        TestCancellable cancellable = new TestCancellable();
        stSingle.onSubscribe(cancellable);
        assertThat("Source not subscribed.", stSingle.isSubscribed(), is(true));
        ArgumentCaptor<Cancellable> cancellableCaptor = forClass(Cancellable.class);
        verify(subscriber).onSubscribe(cancellableCaptor.capture());
        cancellableCaptor.getValue().cancel();
        assertThat("Cancellable not cancelled.", cancellable.isCancelled(), is(true));
    }

    @ParameterizedTest(name = "{displayName} [{index}] same={0}")
    @ValueSource(booleans = {false, true})
    void completableToSourceSuccess(boolean same) {
        CompletableSource.Subscriber subscriber = toSourceAndSubscribe(Completable.completed(), same);
        verify(subscriber).onComplete();
        verifyNoMoreInteractions(subscriber);
    }

    @ParameterizedTest(name = "{displayName} [{index}] same={0}")
    @ValueSource(booleans = {false, true})
    void completableToSourceError(boolean same) {
        CompletableSource.Subscriber subscriber = toSourceAndSubscribe(Completable.failed(DELIBERATE_EXCEPTION), same);
        verify(subscriber).onError(DELIBERATE_EXCEPTION);
        verifyNoMoreInteractions(subscriber);
    }

    @ParameterizedTest(name = "{displayName} [{index}] same={0}")
    @ValueSource(booleans = {false, true})
    void completableToSourceCancel(boolean same) {
        TestCompletable stCompletable = new TestCompletable();
        CompletableSource.Subscriber subscriber = toSourceAndSubscribe(stCompletable, same);
        TestCancellable cancellable = new TestCancellable();
        stCompletable.onSubscribe(cancellable);
        assertThat("Source not subscribed.", stCompletable.isSubscribed(), is(true));
        ArgumentCaptor<Cancellable> cancellableCaptor = forClass(Cancellable.class);
        verify(subscriber).onSubscribe(cancellableCaptor.capture());
        cancellableCaptor.getValue().cancel();
        assertThat("Cancellable not cancelled.", cancellable.isCancelled(), is(true));
    }

    @ParameterizedTest(name = "{displayName} [{index}] same={0}")
    @ValueSource(booleans = {false, true})
    void publisherFromSourceSuccess(boolean same) throws Exception {
        PublisherSource<Integer> src = same ? uncheckedCast(Publisher.from(1)) :
                s -> s.onSubscribe(new ScalarValueSubscription<>(1, s));
        Integer result = fromSourceAndSubscribe(src, same).get();
        assertThat("Unexpected result.", result, is(1));
    }

    @ParameterizedTest(name = "{displayName} [{index}] same={0}")
    @ValueSource(booleans = {false, true})
    void publisherFromSourceError(boolean same) {
        PublisherSource<Integer> src = same ? uncheckedCast(Publisher.failed(DELIBERATE_EXCEPTION)) : s -> {
            s.onSubscribe(EMPTY_SUBSCRIPTION);
            s.onError(DELIBERATE_EXCEPTION);
        };

        Future<Integer> future = fromSourceAndSubscribe(src, same);
        Exception e = assertThrows(ExecutionException.class, future::get);
        assertThat(e.getCause(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @ParameterizedTest(name = "{displayName} [{index}] same={0}")
    @ValueSource(booleans = {false, true})
    void publisherFromSourceCancel(boolean same) {
        PublisherSource.Subscription srcSubscription = mock(PublisherSource.Subscription.class);
        TestPublisher<Integer> testPublisher = new TestPublisher<>();
        PublisherSource<Integer> src = same ? uncheckedCast(testPublisher) : s -> s.onSubscribe(srcSubscription);

        Future<Integer> future = fromSourceAndSubscribe(src, same);
        if (same) {
            testPublisher.onSubscribe(srcSubscription);
        }
        future.cancel(true);
        verify(srcSubscription).cancel();
    }

    @ParameterizedTest(name = "{displayName} [{index}] same={0}")
    @ValueSource(booleans = {false, true})
    void singleFromSourceSuccess(boolean same) throws Exception {
        SingleSource<Integer> src = same ? uncheckedCast(Single.succeeded(1)) : s -> {
            s.onSubscribe(IGNORE_CANCEL);
            s.onSuccess(1);
        };
        Integer result = fromSourceAndSubscribe(src, same).get();
        assertThat("Unexpected result.", result, is(1));
    }

    @ParameterizedTest(name = "{displayName} [{index}] same={0}")
    @ValueSource(booleans = {false, true})
    void singleFromSourceError(boolean same) {
        SingleSource<Integer> src = same ? uncheckedCast(Single.failed(DELIBERATE_EXCEPTION)) : s -> {
            s.onSubscribe(IGNORE_CANCEL);
            s.onError(DELIBERATE_EXCEPTION);
        };

        Future<Integer> future = fromSourceAndSubscribe(src, same);
        Exception e = assertThrows(ExecutionException.class, future::get);
        assertThat(e.getCause(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @ParameterizedTest(name = "{displayName} [{index}] same={0}")
    @ValueSource(booleans = {false, true})
    void singleFromSourceCancel(boolean same) {
        Cancellable srcCancellable = mock(Cancellable.class);
        TestSingle<Integer> testSingle = new TestSingle<>();
        SingleSource<Integer> src = same ? uncheckedCast(testSingle) : s -> s.onSubscribe(srcCancellable);

        Future<Integer> future = fromSourceAndSubscribe(src, same);
        if (same) {
            testSingle.onSubscribe(srcCancellable);
        }
        future.cancel(true);
        verify(srcCancellable).cancel();
    }

    @ParameterizedTest(name = "{displayName} [{index}] same={0}")
    @ValueSource(booleans = {false, true})
    void completableFromSourceSuccess(boolean same) throws Exception {
        CompletableSource src = same ? (CompletableSource) Completable.completed() : s -> {
            s.onSubscribe(IGNORE_CANCEL);
            s.onComplete();
        };
        fromSourceAndSubscribe(src, same).get();
    }

    @ParameterizedTest(name = "{displayName} [{index}] same={0}")
    @ValueSource(booleans = {false, true})
    void completableFromSourceError(boolean same) {
        CompletableSource src = same ? (CompletableSource) Completable.failed(DELIBERATE_EXCEPTION) : s -> {
            s.onSubscribe(IGNORE_CANCEL);
            s.onError(DELIBERATE_EXCEPTION);
        };

        Future<Void> future = fromSourceAndSubscribe(src, same);
        Exception e = assertThrows(ExecutionException.class, future::get);
        assertThat(e.getCause(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @ParameterizedTest(name = "{displayName} [{index}] same={0}")
    @ValueSource(booleans = {false, true})
    void completableFromSourceCancel(boolean same) {
        Cancellable srcCancellable = mock(Cancellable.class);
        TestCompletable testCompletable = new TestCompletable();
        CompletableSource src = same ? testCompletable : s -> s.onSubscribe(srcCancellable);

        Future<Void> future = fromSourceAndSubscribe(src, same);
        if (same) {
            testCompletable.onSubscribe(srcCancellable);
        }
        future.cancel(true);
        verify(srcCancellable).cancel();
    }

    @ParameterizedTest(name = "{displayName} [{index}] same={0}")
    @ValueSource(booleans = {false, true})
    void publisherBackAndForthConversions(boolean same) {
        if (same) {
            Publisher<Integer> original = new TestPublisher<>();
            PublisherSource<Integer> src = toSource(original);
            assertThat(src, is(sameInstance(original)));
            Publisher<Integer> back = fromSource(src);
            assertThat(back, is(sameInstance(src)));
        } else {
            Publisher<Integer> original = new Publisher<Integer>() {

                @Override
                protected void handleSubscribe(PublisherSource.Subscriber<? super Integer> s) {
                    s.onSubscribe(EMPTY_SUBSCRIPTION);
                    s.onComplete();
                }
            };
            PublisherSource<Integer> src = toSource(original);
            assertThat(src, is(not(sameInstance(original))));
            Publisher<Integer> back = fromSource(src);
            assertThat(back, is(sameInstance(src)));
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] same={0}")
    @ValueSource(booleans = {false, true})
    void publisherSourceBackAndForthConversions(boolean same) {
        if (same) {
            PublisherSource<Integer> original = new TestPublisher<>();
            Publisher<Integer> publisher = fromSource(original);
            assertThat(publisher, is(sameInstance(original)));
            PublisherSource<Integer> back = toSource(publisher);
            assertThat(back, is(sameInstance(publisher)));
        } else {
            PublisherSource<Integer> original = s -> {
                s.onSubscribe(EMPTY_SUBSCRIPTION);
                s.onError(DELIBERATE_EXCEPTION);
            };
            Publisher<Integer> publisher = fromSource(original);
            assertThat(publisher, is(not(sameInstance(original))));
            PublisherSource<Integer> back = toSource(publisher);
            assertThat(back, is(sameInstance(publisher)));
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] same={0}")
    @ValueSource(booleans = {false, true})
    void singleBackAndForthConversions(boolean same) {
        if (same) {
            Single<Integer> original = new TestSingle<>();
            SingleSource<Integer> src = toSource(original);
            assertThat(src, is(sameInstance(original)));
            Single<Integer> back = fromSource(src);
            assertThat(back, is(sameInstance(src)));
        } else {
            Single<Integer> original = new Single<Integer>() {

                @Override
                protected void handleSubscribe(SingleSource.Subscriber<? super Integer> s) {
                    s.onSubscribe(EMPTY_SUBSCRIPTION);
                    s.onSuccess(null);
                }
            };
            SingleSource<Integer> src = toSource(original);
            assertThat(src, is(not(sameInstance(original))));
            Single<Integer> back = fromSource(src);
            assertThat(back, is(sameInstance(src)));
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] same={0}")
    @ValueSource(booleans = {false, true})
    void singleSourceBackAndForthConversions(boolean same) {
        if (same) {
            SingleSource<Integer> original = new TestSingle<>();
            Single<Integer> single = fromSource(original);
            assertThat(single, is(sameInstance(original)));
            SingleSource<Integer> back = toSource(single);
            assertThat(back, is(sameInstance(single)));
        } else {
            SingleSource<Integer> original = s -> {
                s.onSubscribe(EMPTY_SUBSCRIPTION);
                s.onSuccess(null);
            };
            Single<Integer> single = fromSource(original);
            assertThat(single, is(not(sameInstance(original))));
            SingleSource<Integer> back = toSource(single);
            assertThat(back, is(sameInstance(single)));
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] same={0}")
    @ValueSource(booleans = {false, true})
    void completableBackAndForthConversions(boolean same) {
        if (same) {
            Completable original = new TestCompletable();
            CompletableSource src = toSource(original);
            assertThat(src, is(sameInstance(original)));
            Completable back = fromSource(src);
            assertThat(back, is(sameInstance(src)));
        } else {
            Completable original = new Completable() {

                @Override
                protected void handleSubscribe(CompletableSource.Subscriber s) {
                    s.onSubscribe(EMPTY_SUBSCRIPTION);
                    s.onComplete();
                }
            };
            CompletableSource src = toSource(original);
            assertThat(src, is(not(sameInstance(original))));
            Completable back = fromSource(src);
            assertThat(back, is(sameInstance(src)));
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] same={0}")
    @ValueSource(booleans = {false, true})
    void completableSourceBackAndForthConversions(boolean same) {
        if (same) {
            CompletableSource original = new TestCompletable();
            Completable completable = fromSource(original);
            assertThat(completable, is(sameInstance(original)));
            CompletableSource back = toSource(completable);
            assertThat(back, is(sameInstance(completable)));
        } else {
            CompletableSource original = s -> {
                s.onSubscribe(EMPTY_SUBSCRIPTION);
                s.onComplete();
            };
            Completable completable = fromSource(original);
            assertThat(completable, is(not(sameInstance(original))));
            CompletableSource back = toSource(completable);
            assertThat(back, is(sameInstance(completable)));
        }
    }

    private CompletableSource.Subscriber toSourceAndSubscribe(Completable completable, boolean same) {
        CompletableSource src;
        if (same) {
            src = toSource(completable);
            assertThat(src, is(sameInstance(completable)));
        } else {
            src = new CompletableToCompletableSource(completable);
            assertThat(src, is(not(sameInstance(completable))));
        }
        CompletableSource.Subscriber subscriber = mock(CompletableSource.Subscriber.class);
        src.subscribe(subscriber);
        verify(subscriber).onSubscribe(any());
        return subscriber;
    }

    private SingleSource.Subscriber<Integer> toSourceAndSubscribe(Single<Integer> single, boolean same) {
        SingleSource<Integer> src;
        if (same) {
            src = toSource(single);
            assertThat(src, is(sameInstance(single)));
        } else {
            src = new SingleToSingleSource<>(single);
            assertThat(src, is(not(sameInstance(single))));
        }
        @SuppressWarnings("unchecked")
        SingleSource.Subscriber<Integer> subscriber = mock(SingleSource.Subscriber.class);
        src.subscribe(subscriber);
        verify(subscriber).onSubscribe(any());
        return subscriber;
    }

    private PublisherSource.Subscriber<Integer> toSourceAndSubscribe(Publisher<Integer> publisher, boolean same) {
        PublisherSource<Integer> src;
        if (same) {
            src = toSource(publisher);
            assertThat(src, is(sameInstance(publisher)));
        } else {
            src = new PublisherToPublisherSource<>(publisher);
            assertThat(src, is(not(sameInstance(publisher))));
        }
        @SuppressWarnings("unchecked")
        PublisherSource.Subscriber<Integer> subscriber = mock(PublisherSource.Subscriber.class);
        src.subscribe(subscriber);
        ArgumentCaptor<Subscription> subscriptionCaptor = forClass(Subscription.class);
        verify(subscriber).onSubscribe(subscriptionCaptor.capture());
        subscriptionCaptor.getValue().request(1);
        return subscriber;
    }

    private static Future<Void> fromSourceAndSubscribe(CompletableSource src, boolean same) {
        final Completable completable = fromSource(src);
        assertThat(completable, is(same ? sameInstance(src) : not(sameInstance(src))));
        return completable.toFuture();
    }

    private static Future<Integer> fromSourceAndSubscribe(SingleSource<Integer> src, boolean same) {
        final Single<Integer> single = fromSource(src);
        assertThat(single, is(same ? sameInstance(src) : not(sameInstance(src))));
        return single.toFuture();
    }

    private static Future<Integer> fromSourceAndSubscribe(PublisherSource<Integer> src, boolean same) {
        final Publisher<Integer> publisher = fromSource(src);
        assertThat(publisher, is(same ? sameInstance(src) : not(sameInstance(src))));
        return publisher.firstOrElse(() -> null).toFuture();
    }

    @SuppressWarnings("unchecked")
    private static <T> PublisherSource<T> uncheckedCast(final Publisher<T> publisher) {
        return (PublisherSource<T>) publisher;
    }

    @SuppressWarnings("unchecked")
    private static <T> SingleSource<T> uncheckedCast(final Single<T> single) {
        return (SingleSource<T>) single;
    }
}
