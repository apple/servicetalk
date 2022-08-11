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

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;

import org.testng.annotations.Test;

@Test
public class PublisherSubscribeOnTckTest extends AbstractPublisherOffloaderTckTest {

    @Override
    Publisher<Integer> applyOffload(final Publisher<Integer> original, final Executor executor) {
        return original.subscribeOn(executor);
    }

    @Override
    @Test(enabled = false,
            description = "when requested from onSubscribe, synchronous sources MAY emit data in a different thread " +
                    "than onSubscribe.")
    public void stochastic_spec103_mustSignalOnMethodsSequentially() throws Throwable {
        super.stochastic_spec103_mustSignalOnMethodsSequentially();
    }
}
