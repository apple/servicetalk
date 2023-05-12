/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

class ValidateDemandPublisherTest {
    private final TestPublisher<Integer> source = new TestPublisher.Builder<Integer>().build();
    private final TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void validDemandNoop(boolean onError) {
        toSource(source.validateOutstandingDemand()).subscribe(subscriber);
        subscriber.awaitSubscription().request(2);
        source.onNext(1, 2);
        assertThat(subscriber.takeOnNext(2), contains(1, 2));

        if (onError) {
            subscriber.onError(DELIBERATE_EXCEPTION);
            assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
        } else {
            subscriber.onComplete();
            subscriber.awaitOnComplete();
        }
    }

    @Test
    void invalidDemandGenerateError() {
        toSource(source.validateOutstandingDemand()).subscribe(subscriber);
        source.onNext(1);
        assertThat(subscriber.awaitOnError(), instanceOf(IllegalStateException.class));
    }
}
