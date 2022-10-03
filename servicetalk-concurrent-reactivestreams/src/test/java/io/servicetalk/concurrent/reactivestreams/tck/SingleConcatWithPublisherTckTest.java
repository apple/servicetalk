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
import io.servicetalk.concurrent.api.Single;

import org.testng.annotations.Test;

import static io.servicetalk.concurrent.reactivestreams.tck.TckUtils.newPublisher;

@Test
public class SingleConcatWithPublisherTckTest extends AbstractSingleTckTest<Integer> {

    boolean deferSubscribe() {
        return false;
    }

    boolean propagateCancel() {
        return false;
    }

    @Override
    protected Publisher<Integer> createServiceTalkPublisher(long elements) {
        if (elements < 2) {
            return Single.succeeded(1).toPublisher();
        }
        Single<Integer> s = Single.succeeded(1);
        Publisher<Integer> p = newPublisher(TckUtils.requestNToInt(elements) - 1);
        return propagateCancel() ? s.concatPropagateCancel(p) : s.concat(p, deferSubscribe());
    }

    @Override
    public long maxElementsFromPublisher() {
        return TckUtils.maxElementsFromPublisher();
    }
}
