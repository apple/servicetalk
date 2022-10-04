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
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

final class PublisherConcatWithCompletableTest {
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
        toSource(propagateCancel ? source.concatPropagateCancel(completable) : source.concat(completable))
                .subscribe(subscriber);
        assertThat("Unexpected termination.", subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        assertThat("Next source subscribed before termination.", completable.isSubscribed(), is(false));
    }

    @ParameterizedTest(name = "{displayName} [{index}] propagateCancel={0}")
    @ValueSource(booleans = {true, false})
    void bothComplete(boolean propagateCancel) {
        setup(propagateCancel);
        subscriber.awaitSubscription().request(1);
        source.onNext(1);
        source.onComplete();
        assertThat("Unexpected items emitted.", subscriber.takeOnNext(), is(1));
        assertThat("Next source not subscribed.", completable.isSubscribed(), is(true));
        assertThat("Unexpected termination.", subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        completable.onComplete();
        subscriber.awaitOnComplete();
    }

    @ParameterizedTest(name = "{displayName} [{index}] propagateCancel={0} completableError={1}")
    @CsvSource(value = {"false,false", "false,true", "true,false", "true,true"})
    void sourceError(boolean propagateCancel, boolean completableError) {
        setup(propagateCancel);
        subscriber.awaitSubscription().request(1);
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));

        if (propagateCancel) {
            completable.awaitSubscribed();
            assertThat(completable.isSubscribed(), is(true));
            // verify duplicate termination is prevented.
            if (completableError) {
                completable.onError(new DeliberateException());
            } else {
                completable.onComplete();
            }
            verifySubscriberErrored();
        } else {
            assertThat(completable.isSubscribed(), is(false));
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] propagateCancel={0}")
    @ValueSource(booleans = {true, false})
    void nextError(boolean propagateCancel) {
        setup(propagateCancel);
        subscriber.awaitSubscription().request(1);
        source.onNext(1);
        source.onComplete();
        assertThat("Unexpected items emitted.", subscriber.takeOnNext(), is(1));
        assertThat("Next source not subscribed.", completable.isSubscribed(), is(true));
        assertThat("Unexpected termination.", subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        completable.onError(DELIBERATE_EXCEPTION);
        verifySubscriberErrored();
    }

    @ParameterizedTest(name = "{displayName} [{index}] propagateCancel={0} onError={1}")
    @CsvSource(value = {"false,false", "false,true", "true,false", "true,true"})
    void sourceCancel(boolean propagateCancel, boolean onError) {
        setup(propagateCancel);
        subscriber.awaitSubscription().cancel();
        assertThat("Source subscription not cancelled.", subscription.isCancelled(), is(true));
        assertThat(completable.isSubscribed(), is(propagateCancel));
        if (onError) {
            source.onError(DELIBERATE_EXCEPTION);
        } else {
            source.onComplete();
        }
        if (propagateCancel) {
            completable.awaitSubscribed();
            assertThat(cancellable.isCancelled(), is(true));
            if (onError) {
                completable.onError(DELIBERATE_EXCEPTION);
            } else {
                completable.onComplete();
            }
            // Cancel before the publisher completes means we don't propagate terminals.
            assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        } else {
            if (!onError) {
                completable.awaitSubscribed();
                completable.onComplete();
                subscriber.awaitOnComplete();
            } else {
                verifySubscriberErrored();
            }
            assertThat("Next cancellable not cancelled.", cancellable.isCancelled(), is(!onError));
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] propagateCancel={0} onError={1}")
    @CsvSource(value = {"false,false", "false,true", "true,false", "true,true"})
    void nextCancel(boolean propagateCancel, boolean onError) {
        setup(propagateCancel);
        if (onError) {
            source.onError(DELIBERATE_EXCEPTION);
        } else {
            source.onComplete();
        }
        if (propagateCancel || !onError) {
            assertThat("Next source not subscribed.", completable.isSubscribed(), is(true));
            subscriber.awaitSubscription().cancel();
            assertThat("Source subscription unexpectedly cancelled.", subscription.isCancelled(), is(false));
            assertThat("Next cancellable not cancelled.", cancellable.isCancelled(), is(true));
        } else {
            assertThat("Next source unexpectedly subscribed.", completable.isSubscribed(), is(false));
            subscriber.awaitSubscription().cancel();
            assertThat("Source subscription not cancelled.", subscription.isCancelled(), is(true));
            assertThat("Next cancellable unexpectedly cancelled.", cancellable.isCancelled(), is(false));
        }

        if (onError) {
            verifySubscriberErrored();
        } else {
            completable.onComplete();
            subscriber.awaitOnComplete();
        }
    }

    void verifySubscriberErrored() {
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }
}
