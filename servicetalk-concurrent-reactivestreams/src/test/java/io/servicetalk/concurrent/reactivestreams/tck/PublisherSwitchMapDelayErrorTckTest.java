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
package io.servicetalk.concurrent.reactivestreams.tck;

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.reactivestreams.tck.PublisherSwitchMapTckTest.SingleUpstreamDemandOperator;

import org.testng.annotations.Ignore;
import org.testng.annotations.Test;

import static io.servicetalk.concurrent.api.Publisher.defer;
import static io.servicetalk.concurrent.api.Publisher.from;

@Test
public class PublisherSwitchMapDelayErrorTckTest extends AbstractPublisherOperatorTckTest<Integer> {
    @Override
    protected Publisher<Integer> composePublisher(Publisher<Integer> publisher, int elements) {
        return defer(() -> {
            final SingleUpstreamDemandOperator<Integer> demandOperator = new SingleUpstreamDemandOperator<>();
            return publisher.liftAsync(demandOperator)
                    .switchMapDelayError(i ->
                            from(i).afterOnNext(x -> demandOperator.subscriberRef.get().decrementDemand()));
        });
    }

    @Ignore("delay error requires termination of outer publisher")
    @Override
    public void required_spec309_requestNegativeNumberMustSignalIllegalArgumentException() {
    }

    @Ignore("delay error requires termination of outer publisher")
    @Override
    public void required_spec309_requestZeroMustSignalIllegalArgumentException() {
    }
}
