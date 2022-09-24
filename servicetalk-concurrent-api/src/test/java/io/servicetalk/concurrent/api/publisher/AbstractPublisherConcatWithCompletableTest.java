/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.TestCancellable;
import io.servicetalk.concurrent.api.TestCompletable;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.api.Test;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

abstract class AbstractPublisherConcatWithCompletableTest {
    final TestSubscription subscription = new TestSubscription();
    final TestCancellable cancellable = new TestCancellable();
    final TestPublisher<Integer> source = new TestPublisher.Builder<Integer>().disableAutoOnSubscribe()
            .build(subscriber1 -> {
                subscriber1.onSubscribe(subscription);
                return subscriber1;
            });
    final TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
    final TestCompletable completable = new TestCompletable.Builder().disableAutoOnSubscribe().build(subscriber1 -> {
        subscriber1.onSubscribe(cancellable);
        return subscriber1;
    });

    void setup(boolean propagateCancel) {
        toSource(source.concat(completable, propagateCancel)).subscribe(subscriber);
        assertThat("Unexpected termination.", subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        assertThat("Next source subscribed before termination.", completable.isSubscribed(), is(false));
    }

    @Test
    void bothComplete() {
        subscriber.awaitSubscription().request(1);
        source.onNext(1);
        source.onComplete();
        assertThat("Unexpected items emitted.", subscriber.takeOnNext(), is(1));
        assertThat("Next source not subscribed.", completable.isSubscribed(), is(true));
        assertThat("Unexpected termination.", subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        completable.onComplete();
        subscriber.awaitOnComplete();
    }

    @Test
    void sourceError() {
        subscriber.awaitSubscription().request(1);
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
        assertThat("Next source subscribed on error.", completable.isSubscribed(), is(false));
        verifySubscriberErrored();
    }

    @Test
    void nextError() {
        subscriber.awaitSubscription().request(1);
        source.onNext(1);
        source.onComplete();
        assertThat("Unexpected items emitted.", subscriber.takeOnNext(), is(1));
        assertThat("Next source not subscribed.", completable.isSubscribed(), is(true));
        assertThat("Unexpected termination.", subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        completable.onError(DELIBERATE_EXCEPTION);
        verifySubscriberErrored();
    }

    @Test
    void sourceCancel() {
        subscriber.awaitSubscription().cancel();
        assertThat("Source subscription not cancelled.", subscription.isCancelled(), is(true));
        assertThat("Next source subscribed on cancellation.", completable.isSubscribed(), is(false));
        source.onComplete();
        completable.awaitSubscribed();
        assertThat("Next cancellable not cancelled.", cancellable.isCancelled(), is(true));
    }

    @Test
    void nextCancel() {
        source.onComplete();
        assertThat("Next source not subscribed.", completable.isSubscribed(), is(true));
        subscriber.awaitSubscription().cancel();
        completable.awaitSubscribed();
        assertThat("Next cancellable not cancelled.", cancellable.isCancelled(), is(true));
    }

    void verifySubscriberErrored() {
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }
}
