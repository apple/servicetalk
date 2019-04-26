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
package io.servicetalk.benchmark;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.CountDownLatch;
import javax.annotation.Nullable;

/*
 * This benchmark verifies the reason of introducing an additional check before invoking latch.await() even if the
 * CountDownLatch value is 0.
 *
 * Benchmark                                  Mode  Cnt          Score         Error  Units
 * CountDownLatchBenchmark.getWithShield     thrpt    5  452938674.099 ± 8088381.004  ops/s
 * CountDownLatchBenchmark.getWithoutShield  thrpt    5  340168846.799 ± 4901839.746  ops/s
 */
@Fork(value = 1)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 3)
@Measurement(iterations = 5, time = 3)
@BenchmarkMode(Mode.Throughput)
public class CountDownLatchBenchmark {

    private static final Object NULL = new Object();

    private CountDownLatch latch = new CountDownLatch(0);

    @Nullable
    private volatile Object value = NULL;

    private boolean isDone() {
        return value != null;
    }

    @Benchmark
    public final Object getWithShield() throws InterruptedException {
        if (!isDone()) {
            latch.await();
        }
        return value;
    }

    @Benchmark
    public final Object getWithoutShield() throws InterruptedException {
        latch.await();
        return value;
    }
}
