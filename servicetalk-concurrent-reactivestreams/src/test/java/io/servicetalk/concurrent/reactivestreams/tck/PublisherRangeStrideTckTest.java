/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

import static io.servicetalk.concurrent.api.Publisher.range;
import static io.servicetalk.concurrent.reactivestreams.tck.TckUtils.requestNToInt;

@Test
public class PublisherRangeStrideTckTest extends AbstractPublisherTckTest<Integer> {
    @Override
    protected Publisher<Integer> createServiceTalkPublisher(final long elements) {
        return range(0, requestNToInt(elements) * 2, 2);
    }

    @Override
    public long maxElementsFromPublisher() {
        // divide by 2 to avoid overflow above when we expand the boundary in order to deliver the same number
        // of elements with a stride of 2.
        return TckUtils.maxElementsFromPublisher() / 2;
    }
}
