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
package io.servicetalk.concurrent.reactivestreams.tck;

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;

import org.testng.annotations.Test;

import javax.annotation.Nullable;

import static org.testng.Assert.assertNull;

@Test
public class PublisherLiftSynchronousPublisherToSingleTckTest extends
                                                              AbstractPublisherToSingleOperatorTckTest<Integer> {
    @Override
    protected Single<Integer> composePublisher(final Publisher<Integer> publisher, final int elements) {
        return publisher.liftSyncToSingle(singleSubscriber -> new PublisherSource.Subscriber<Integer>() {
            private int accumulator;
            private PublisherSource.Subscription subscription;
            @Override
            public void onSubscribe(final PublisherSource.Subscription subscription) {
                assertNull(this.subscription);
                this.subscription = subscription;
                singleSubscriber.onSubscribe(subscription);
                subscription.request(1);
            }

            @Override
            public void onNext(@Nullable final Integer integer) {
                if (integer != null) {
                    accumulator += integer;
                }
                subscription.request(1);
            }

            @Override
            public void onError(final Throwable t) {
                singleSubscriber.onError(t);
            }

            @Override
            public void onComplete() {
                singleSubscriber.onSuccess(accumulator);
            }
        });
    }
}
