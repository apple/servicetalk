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
 * Benchmark                                             (stringLength)   Mode  Cnt         Score        Error  Units
 * BufferWriteCharSequenceBenchmark.reserveAndWriteUtf8              10  thrpt    5  27820193.124 ± 577758.706  ops/s
 * BufferWriteCharSequenceBenchmark.reserveAndWriteUtf8             100  thrpt    5  13866127.961 ± 513311.937  ops/s
 * BufferWriteCharSequenceBenchmark.reserveAndWriteUtf8            1000  thrpt    5   2214050.739 ±  77745.120  ops/s
 * BufferWriteCharSequenceBenchmark.reserveAndWriteUtf8           10000  thrpt    5    238994.492 ±  13351.897  ops/s
 * BufferWriteCharSequenceBenchmark.reserveAndWriteUtf8          100000  thrpt    5     23900.420 ±   1031.012  ops/s
 * BufferWriteCharSequenceBenchmark.writeUtf8                        10  thrpt    5  16793527.903 ± 233528.173  ops/s
 * BufferWriteCharSequenceBenchmark.writeUtf8                       100  thrpt    5   8315460.866 ± 222814.667  ops/s
 * BufferWriteCharSequenceBenchmark.writeUtf8                      1000  thrpt    5    856875.221 ±  25608.423  ops/s
 * BufferWriteCharSequenceBenchmark.writeUtf8                     10000  thrpt    5     97994.457 ±   3454.060  ops/s
 * BufferWriteCharSequenceBenchmark.writeUtf8                    100000  thrpt    5      8346.010 ±    312.199  ops/s
 * BufferWriteCharSequenceBenchmark.writeBytes                       10  thrpt    5  12485948.525 ± 571811.359  ops/s
 * BufferWriteCharSequenceBenchmark.writeBytes                      100  thrpt    5   7973767.545 ± 188221.942  ops/s
 * BufferWriteCharSequenceBenchmark.writeBytes                     1000  thrpt    5    816360.086 ±  15919.584  ops/s
 * BufferWriteCharSequenceBenchmark.writeBytes                    10000  thrpt    5     98705.454 ±   2334.825  ops/s
 * BufferWriteCharSequenceBenchmark.writeBytes                   100000  thrpt    5     10363.267 ±    423.572  ops/s
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

    private String stringOfLength(final int len) {
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
    public int reserveAndWriteUtf8() {
        return ByteBufUtil.reserveAndWriteUtf8(BufferUtil.toByteBufNoThrow(target), str, str.length());
    }

    @Benchmark
    public Buffer writeBytes() {
        return target.writeBytes(str.getBytes(UTF_8));
    }
}
