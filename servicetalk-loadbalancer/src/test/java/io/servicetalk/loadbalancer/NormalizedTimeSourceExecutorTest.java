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
package io.servicetalk.loadbalancer;

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.TestExecutor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static java.lang.Long.MAX_VALUE;
import static java.lang.Long.MIN_VALUE;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@SuppressWarnings("NumericOverflow")
class NormalizedTimeSourceExecutorTest {

    private TestExecutor testExecutor;
    private Executor executor;

    @AfterEach
    void tearDown() throws Exception {
        testExecutor.closeAsync().toFuture().get();
        executor.closeAsync().toFuture().get();
    }

    void setUp(long initialValue) {
        testExecutor = new TestExecutor(initialValue);
        executor = new NormalizedTimeSourceExecutor(testExecutor);
        assertThat("Unexpected initial value", executor.currentTime(NANOSECONDS), is(0L));
    }

    void advanceAndVerify(long advanceByNanos, long expected) {
        testExecutor.advanceTimeByNoExecuteTasks(advanceByNanos, NANOSECONDS);
        assertThat(executor.currentTime(NANOSECONDS), is(expected));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: initialValue={0}")
    @ValueSource(longs = {MIN_VALUE, -100, 0, 100, MAX_VALUE})
    void minValue(long initialValue) {
        setUp(initialValue);
        advanceAndVerify(10, 10);
        advanceAndVerify(MAX_VALUE - 10, MAX_VALUE);
        advanceAndVerify(10, MAX_VALUE + 10);
        advanceAndVerify(MAX_VALUE - 8, 0);
    }
}
