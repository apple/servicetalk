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
package io.servicetalk.concurrent.reactivestreams.tck;

import io.servicetalk.concurrent.api.Publisher;

import org.testng.annotations.Test;

@Test
public class PublisherConcatWithPublisherTckTest extends AbstractPublisherOperatorTckTest<Integer> {

    @Override
    protected Publisher<Integer> createServiceTalkPublisher(long elements) {
        int numElements = TckUtils.requestNToInt(elements);

        if (numElements <= 1) {
            return TckUtils.newPublisher(numElements).concat(Publisher.empty());
        }

        int halfElements = numElements / 2;

        // Calculate the number of elements that will not be emitted by the first publisher so we create another
        // one in composePublisher(...) that will emit these. The sum of both should be == elements.
        return composePublisher(TckUtils.newPublisher(halfElements), numElements - halfElements);
    }

    @Override
    protected Publisher<Integer> composePublisher(Publisher<Integer> publisher, int elements) {
        return publisher.concat(TckUtils.newPublisher(elements));
    }
}
