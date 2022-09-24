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

import io.servicetalk.concurrent.internal.DeliberateException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class PublisherConcatWithCompletablePropagateCancelTest extends AbstractPublisherConcatWithCompletableTest {
    PublisherConcatWithCompletablePropagateCancelTest() {
        setup(true);
    }

    @Test
    @Override
    void sourceCancel() {
        subscriber.awaitSubscription().cancel();
        assertThat("Source subscription not cancelled.", subscription.isCancelled(), is(true));
        assertThat("Next source subscribed on cancellation.", completable.isSubscribed(), is(true));
        source.onComplete();
        completable.awaitSubscribed();
        assertThat("Next cancellable not cancelled.", cancellable.isCancelled(), is(true));
    }

    @Override
    void sourceError() {
        // this test behavior is different and expanded below.
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void sourceError(boolean completableError) {
        subscriber.awaitSubscription().request(1);
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
        completable.awaitSubscribed();
        assertThat("Next cancellable not cancelled.", cancellable.isCancelled(), is(true));
        // verify duplicate termination is prevented.
        if (completableError) {
            completable.onError(new DeliberateException());
        } else {
            completable.onComplete();
        }
        verifySubscriberErrored();
    }
}
