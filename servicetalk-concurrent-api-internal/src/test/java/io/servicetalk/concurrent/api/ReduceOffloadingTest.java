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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.api.internal.OffloaderAwareExecutor;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.concurrent.atomic.AtomicInteger;

import static io.servicetalk.concurrent.api.Executors.from;
import static io.servicetalk.concurrent.api.Executors.newFixedSizeExecutor;
import static io.servicetalk.concurrent.internal.SignalOffloaders.threadBasedOffloaderFactory;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.Assert.assertThat;

public class ReduceOffloadingTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    @Test
    public void reduceShouldOffloadOnce() throws Exception {
        Executor executor = newFixedSizeExecutor(1);
        AtomicInteger taskCount = new AtomicInteger();
        Executor wrapped = new OffloaderAwareExecutor(from(task -> {
            taskCount.incrementAndGet();
            executor.execute(task);
        }), threadBasedOffloaderFactory());
        int sum = Publisher.from(1, 2, 3, 4).publishAndSubscribeOn(wrapped)
                .reduce(() -> 0, (cumulative, integer) -> cumulative + integer).toFuture().get();
        assertThat("Unexpected sum.", sum, is(10));
        // The Subscriber chain will execute one task, and  Future conversion may also execute another task if the
        // Future is completed by the time we add a listener to the future (e.g. blocking get in the JUnit thread).
        assertThat("Unexpected tasks submitted.", taskCount.get(), lessThanOrEqualTo(2));
        wrapped.closeAsync().toFuture().get();
        executor.closeAsync().toFuture().get();
    }
}
