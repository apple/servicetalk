/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api.tck;

import io.servicetalk.concurrent.api.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.testng.annotations.Test;

import static java.lang.Math.max;

@Test
public class PublisherMulticastTckTest extends AbstractPublisherOperatorTckTest<Integer> {
    @Override
    protected Publisher<Integer> composePublisher(Publisher<Integer> publisher, int elements) {
        Publisher<Integer> multicastPublisher = publisher.multicast(2, max(elements, 10));

        // The TCK expects a single publisher, and the multicast operator must have the expected number of subscriptions
        // before subscribing to the original Publisher. To facilitate this operator being used by the TCK tests we
        // specify a multicast factor of 2, and we immediately subscribe once here, just to let the TCK tests drive
        // the Subscription and consumption of data.
        multicastPublisher.subscribe(new Subscriber<Integer>() {
            @Override
            public void onSubscribe(Subscription s) {
            }

            @Override
            public void onNext(Integer integer) {
            }

            @Override
            public void onError(Throwable t) {
            }

            @Override
            public void onComplete() {
            }
        });
        return multicastPublisher;
    }
}
