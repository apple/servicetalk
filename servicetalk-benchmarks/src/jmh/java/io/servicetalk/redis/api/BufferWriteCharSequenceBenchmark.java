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
package io.servicetalk.redis.api;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.netty.BufferAllocators;
import io.servicetalk.buffer.netty.BufferUtil;

import io.netty.buffer.ByteBufUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import static java.nio.charset.StandardCharsets.UTF_8;

/*
 * This benchmark compares various ways of writing a string to a buffer using ST2 APIs, including an internal API
 * leveraging a netty utility.
 *
 * Benchmark                (stringLength)   Mode  Cnt         Score        Error  Units
 * reserveAndWriteUtf8                  10  thrpt    5  28042722.141 ± 652816.060  ops/s
 * reserveAndWriteUtf8                 100  thrpt    5  13794592.473 ± 564785.778  ops/s
 * reserveAndWriteUtf8                1000  thrpt    5   2216999.490 ±  56981.883  ops/s
 * reserveAndWriteUtf8               10000  thrpt    5    237674.222 ±   9971.538  ops/s
 * reserveAndWriteUtf8              100000  thrpt    5     24026.400 ±    692.330  ops/s
 * writeUtf8EnsureCapacity              10  thrpt    5  27398387.639 ± 943934.624  ops/s
 * writeUtf8EnsureCapacity             100  thrpt    5  13662801.224 ± 227767.862  ops/s
 * writeUtf8EnsureCapacity            1000  thrpt    5   2221771.326 ±  21379.371  ops/s
 * writeUtf8EnsureCapacity           10000  thrpt    5    240068.021 ±   4688.891  ops/s
 * writeUtf8EnsureCapacity          100000  thrpt    5     23950.991 ±    855.289  ops/s
 * writeUtf8                            10  thrpt    5  16538842.822 ± 298610.303  ops/s
 * writeUtf8                           100  thrpt    5   8331989.708 ± 341024.752  ops/s
 * writeUtf8                          1000  thrpt    5    851633.554 ±  21709.505  ops/s
 * writeUtf8                         10000  thrpt    5     98150.506 ±   2920.327  ops/s
 * writeUtf8                        100000  thrpt    5      8403.067 ±    217.107  ops/s
 * writeBytes                           10  thrpt    5  12152668.695 ± 348644.599  ops/s
 * writeBytes                          100  thrpt    5   7786008.326 ± 177859.506  ops/s
 * writeBytes                         1000  thrpt    5    813298.026 ±  25889.524  ops/s
 * writeBytes                        10000  thrpt    5     98679.543 ±   2177.489  ops/s
 * writeBytes                       100000  thrpt    5     10226.726 ±    304.920  ops/s
 */
@Fork(value = 1)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 3)
@Measurement(iterations = 5, time = 3)
@BenchmarkMode(Mode.Throughput)
public class BufferWriteCharSequenceBenchmark {

    @Param({"10", "100", "1000", "10000", "100000"})
    public int stringLength;

    String str;
    Buffer target;

    @Setup(Level.Iteration)
    public void setup() {
        str = stringOfLength(stringLength);
    }

    @Setup(Level.Invocation)
    public void perInvocation() {
        target = BufferAllocators.PREFER_HEAP_ALLOCATOR.newBuffer(stringLength);
    }

    private static String stringOfLength(final int len) {
        StringBuilder str = new StringBuilder(len);
        for (int i = 0; i < len; ++i) {
            str.append((char) ('A' + (i % 26)));
        }
        return str.toString();
    }

    @Benchmark
    public Buffer writeUtf8() {
        return target.writeUtf8(str);
    }

    @Benchmark
    public Buffer writeUtf8EnsureCapacity() {
        return target.writeUtf8(str, str.length());
    }

    @Benchmark
    public int reserveAndWriteUtf8() {
        return ByteBufUtil.reserveAndWriteUtf8(BufferUtil.toByteBufNoThrow(target), str, str.length());
    }

    @Benchmark
    public Buffer writeBytes() {
        return target.writeBytes(str.getBytes(UTF_8));
    }
}
