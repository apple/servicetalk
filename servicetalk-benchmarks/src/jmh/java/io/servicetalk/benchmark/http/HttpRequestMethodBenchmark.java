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
package io.servicetalk.benchmark.http;

import io.servicetalk.http.api.HttpRequestMethod;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/*
 * This benchmark compares two different HttpRequestMethod.of(String) lookup implementations:
 *
 * 1. Foreach over List
 * Benchmark               (methodName)   Mode  Cnt          Score         Error  Units
 * ofStringConstantLookup           GET  thrpt    5  177276356.329 ± 2868635.021  ops/s
 * ofStringConstantLookup          HEAD  thrpt    5  113749921.507 ± 2647626.820  ops/s
 * ofStringConstantLookup          POST  thrpt    5   90800321.614 ± 3375971.325  ops/s
 * ofStringConstantLookup           PUT  thrpt    5   92796289.776 ± 1833249.006  ops/s
 * ofStringConstantLookup        DELETE  thrpt    5   88423329.268 ± 1476014.475  ops/s
 * ofStringConstantLookup       CONNECT  thrpt    5   77317460.879 ± 1868613.180  ops/s
 * ofStringConstantLookup       OPTIONS  thrpt    5   65652997.312 ± 1333416.644  ops/s
 * ofStringConstantLookup         TRACE  thrpt    5   68409662.933 ± 2307558.858  ops/s
 * ofStringConstantLookup         PATCH  thrpt    5   58432905.560 ± 1165572.584  ops/s
 * ofStringConstantLookup       UNKNOWN  thrpt    5   60418690.737 ± 1367858.316  ops/s
 *
 * 2. Switch-case
 * Benchmark               (methodName)   Mode  Cnt          Score          Error  Units
 * ofStringConstantLookup           GET  thrpt    5  150408904.345 ±  7582768.717  ops/s
 * ofStringConstantLookup          HEAD  thrpt    5  126275822.371 ±  4501341.375  ops/s
 * ofStringConstantLookup          POST  thrpt    5  146631042.441 ±  5339511.058  ops/s
 * ofStringConstantLookup           PUT  thrpt    5  145311336.436 ±  2013265.741  ops/s
 * ofStringConstantLookup        DELETE  thrpt    5  124434159.564 ±  1539061.606  ops/s
 * ofStringConstantLookup       CONNECT  thrpt    5  122605162.726 ±  3835958.179  ops/s
 * ofStringConstantLookup       OPTIONS  thrpt    5  130966554.137 ±  3730430.976  ops/s
 * ofStringConstantLookup         TRACE  thrpt    5  149014294.578 ±  1911593.327  ops/s
 * ofStringConstantLookup         PATCH  thrpt    5  145321385.794 ±  4382738.681  ops/s
 * ofStringConstantLookup       UNKNOWN  thrpt    5  189249897.729 ± 10985411.431  ops/s
 */
@Fork(value = 1)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 3)
@Measurement(iterations = 5, time = 3)
@BenchmarkMode(Mode.Throughput)
public class HttpRequestMethodBenchmark {

    @Param({"GET", "HEAD", "POST", "PUT", "DELETE", "CONNECT", "OPTIONS", "TRACE", "PATCH", "UNKNOWN"})
    public String methodName;

    @Benchmark
    public HttpRequestMethod ofStringConstantLookup() {
        return HttpRequestMethod.of(methodName);
    }
}
