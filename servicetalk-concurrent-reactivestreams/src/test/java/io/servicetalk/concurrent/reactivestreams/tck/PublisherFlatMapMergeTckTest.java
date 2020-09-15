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

import static java.lang.Math.max;

public class PublisherFlatMapMergeTckTest extends AbstractPublisherOperatorTckTest<Integer> {
    @Override
    protected Publisher<Integer> composePublisher(final Publisher<Integer> publisher, final int elements) {
        // flatMapMerge requests the max number of concurrent elements synchronously in onSubscribe. The default
        // publisher (e.g. from(T[]), range(..)) will synchronously complete if demand exceeds the number of items.
        // Some TCK tests (e.g. invalid request N) assume this synchronous completion won't happen so we limit the max
        // concurrency to be less than the total number of elements.
        final int maxConcurrency = max(1, elements - 1);
        return publisher.flatMapMerge(Publisher::from, maxConcurrency);
    }
}
