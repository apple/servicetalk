/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.internal.OffloaderAwareExecutor;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static io.servicetalk.concurrent.api.Executors.from;
import static io.servicetalk.concurrent.api.Executors.newFixedSizeExecutor;
import static io.servicetalk.concurrent.internal.SignalOffloaders.threadBasedOffloaderFactory;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class ReduceOffloadingTest {

    @Test
    void reduceShouldOffloadOnce() throws Exception {
        Executor executor = newFixedSizeExecutor(1);
        AtomicInteger taskCount = new AtomicInteger();
        Executor wrapped = new OffloaderAwareExecutor(from(task -> {
            taskCount.incrementAndGet();
            executor.execute(task);
        }), threadBasedOffloaderFactory());
        int sum = Publisher.from(1, 2, 3, 4).publishOn(wrapped)
                .collect(() -> 0, Integer::sum).toFuture().get();
        assertThat("Unexpected sum.", sum, is(10));
        assertThat("Unexpected tasks submitted.", taskCount.get(), is(1));
        wrapped.closeAsync().toFuture().get();
        executor.closeAsync().toFuture().get();
    }
}
