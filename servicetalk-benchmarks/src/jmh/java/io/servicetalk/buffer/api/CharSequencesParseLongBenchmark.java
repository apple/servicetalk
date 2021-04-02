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
package io.servicetalk.buffer.api;

import io.netty.util.AsciiString;
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

import java.nio.charset.StandardCharsets;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;

/*
 * This benchmark compares CharSequences#parseLong(CharSequence) and Long.parseLong(String):
 *
 * Benchmark                              (value)   Mode  Cnt         Score         Error  Units
 * javaParseLongString       -9223372036854775808  thrpt    5  24524373.658 ±  490417.495  ops/s
 *   stParseLongString       -9223372036854775808  thrpt    5  19168467.144 ±  594280.502  ops/s
 *
 * javaParseLongString        9223372036854775807  thrpt    5  15573374.506 ± 1049847.936  ops/s
 *   stParseLongString        9223372036854775807  thrpt    5  16697590.692 ±  231851.871  ops/s
 *
 * javaParseLongAsciiBuffer  -9223372036854775808  thrpt    5  10066121.790 ±  117298.619  ops/s
 *   stParseLongAsciiBuffer  -9223372036854775808  thrpt    5  18155698.684 ±  436706.324  ops/s
 *
 * javaParseLongAsciiBuffer   9223372036854775807  thrpt    5  10730908.955 ±  116656.679  ops/s
 *   stParseLongAsciiBuffer   9223372036854775807  thrpt    5  19615079.368 ±  459852.132  ops/s
 *
 * javaParseLongAsciiString  -9223372036854775808  thrpt    5  17546166.613 ±  444219.547  ops/s
 *   stParseLongAsciiString  -9223372036854775808  thrpt    5  19169592.065 ±  499213.146  ops/s
 *
 * javaParseLongAsciiString   9223372036854775807  thrpt    5  22611841.803 ±  503643.380  ops/s
 *   stParseLongAsciiString   9223372036854775807  thrpt    5  21140372.163 ± 2921605.423  ops/s
 *
 *
 *
 * javaParseLongString                      -8192  thrpt    5  69974528.501 ± 6167380.442  ops/s
 *   stParseLongString                      -8192  thrpt    5  73735070.747 ± 2968101.803  ops/s
 *
 * javaParseLongString                       8192  thrpt    5  70138556.799 ±  918507.526  ops/s
 *   stParseLongString                       8192  thrpt    5  66549636.755 ± 1023126.881  ops/s
 *
 * javaParseLongAsciiBuffer                 -8192  thrpt    5  15418127.631 ±  271577.020  ops/s
 *   stParseLongAsciiBuffer                 -8192  thrpt    5  58372951.121 ±  920176.976  ops/s
 *
 * javaParseLongAsciiBuffer                  8192  thrpt    5  15203126.170 ±  289559.904  ops/s
 *   stParseLongAsciiBuffer                  8192  thrpt    5  56709314.826 ±  813985.642  ops/s
 *
 * javaParseLongAsciiString                 -8192  thrpt    5  70984579.239 ± 1614728.648  ops/s
 *   stParseLongAsciiString                 -8192  thrpt    5  68602480.185 ± 1052183.360  ops/s
 *
 * javaParseLongAsciiString                  8192  thrpt    5  75324292.593 ±  592329.213  ops/s
 *   stParseLongAsciiString                  8192  thrpt    5  80748587.669 ± 1076241.647  ops/s
 */
@Fork(value = 1)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 3)
@Measurement(iterations = 5, time = 3)
@BenchmarkMode(Mode.Throughput)
public class CharSequencesParseLongBenchmark {

    @Param({"-9223372036854775808", "9223372036854775807", "-8192", "8192"})
    private String value;

    private CharSequence asciiBuffer;
    private CharSequence asciiString;

    @Setup(Level.Trial)
    public void setup() {
        asciiBuffer = newAsciiString(value);
        asciiString = new AsciiString(value.getBytes(StandardCharsets.US_ASCII));
    }

    @Benchmark
    public long javaParseLongString() {
        return Long.parseLong(value);
    }

    @Benchmark
    public long stParseLongString() {
        return CharSequences.parseLong(value);
    }

    @Benchmark
    public long javaParseLongAsciiBuffer() {
        return Long.parseLong(asciiBuffer.toString());
    }

    @Benchmark
    public long stParseLongAsciiBuffer() {
        return CharSequences.parseLong(asciiBuffer);
    }

    @Benchmark
    public long javaParseAsciiString() {
        return Long.parseLong(asciiString.toString());
    }

    @Benchmark
    public long stParseLongAsciiString() {
        return CharSequences.parseLong(asciiString);
    }
}
