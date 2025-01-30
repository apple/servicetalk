/*
 * Copyright © 2025 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.context.api.ContextMap;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 *
 */
@Fork(1)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 3)
@Measurement(iterations = 5, time = 3)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class AsyncContextProviderBenchmark {

    /**
     * gc profiling of the DefaultAsyncContextProvider shows that the Scope based detachment can be stack allocated
     * at least under some conditions.
     *
     * Benchmark                                                                   Mode  Cnt   Score    Error   Units
     * AsyncContextProviderBenchmark.contextRestoreCost                            avgt    5   3.932 ±  0.022   ns/op
     * AsyncContextProviderBenchmark.contextRestoreCost:gc.alloc.rate              avgt    5  ≈ 10⁻⁴           MB/sec
     * AsyncContextProviderBenchmark.contextRestoreCost:gc.alloc.rate.norm         avgt    5  ≈ 10⁻⁶             B/op
     * AsyncContextProviderBenchmark.contextRestoreCost:gc.count                   avgt    5     ≈ 0           counts
     * AsyncContextProviderBenchmark.contextSaveAndRestoreCost                     avgt    5   1.712 ±  0.005   ns/op
     * AsyncContextProviderBenchmark.contextSaveAndRestoreCost:gc.alloc.rate       avgt    5  ≈ 10⁻⁴           MB/sec
     * AsyncContextProviderBenchmark.contextSaveAndRestoreCost:gc.alloc.rate.norm  avgt    5  ≈ 10⁻⁷             B/op
     * AsyncContextProviderBenchmark.contextSaveAndRestoreCost:gc.count            avgt    5     ≈ 0           counts
     */

    private static final ContextMap.Key<String> KEY = ContextMap.Key.newKey("test-key", String.class);
    private static final String EXPECTED = "hello, world!";

    private static Function<String, String> wrappedFunction;

    @Setup
    public void setup() {
        // This will capture the current context
        wrappedFunction = AsyncContext.wrapFunction(ignored -> AsyncContext.context().get(KEY));
        AsyncContext.context().put(KEY, EXPECTED);
    }

    @Benchmark
    public String contextRestoreCost() {
        return wrappedFunction.apply("ignored");
    }

    @Benchmark
    public String contextSaveAndRestoreCost() {
        return AsyncContext.wrapFunction(Function.<String>identity()).apply("ignored");
    }
}
