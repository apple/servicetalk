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
package io.servicetalk.concurrent.reactivestreams.tck;

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;

import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.failed;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;

@Test
public class SingleRepeatWhenTckTest extends AbstractPublisherTckTest<Integer> {
    @Override
    public Publisher<Integer> createServiceTalkPublisher(final long elements) {
        AtomicInteger value = new AtomicInteger();
        return Single.defer(() -> Single.succeeded(value.incrementAndGet()))
                .repeatWhen((repeat, v) -> repeat < elements ? completed() : failed(DELIBERATE_EXCEPTION));
    }

    @Override
    public long maxElementsFromPublisher() {
        return 256;
    }
}
