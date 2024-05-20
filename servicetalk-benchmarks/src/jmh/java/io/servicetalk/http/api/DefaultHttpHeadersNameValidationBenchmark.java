/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.api;

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
import org.openjdk.jmh.infra.Blackhole;

import java.util.Arrays;

/*
 * This benchmark measures performance of header name validation in DefaultHttpHeaders:
 *
 * ByteProcessor as a method-reference:
 * Benchmark                                            (count)  (invalid)   Mode  Cnt        Score        Error  Units
 * DefaultHttpHeadersNameValidationBenchmark.addHeader        4      false  thrpt    5  8438734.025 ± 165801.745  ops/s
 * DefaultHttpHeadersNameValidationBenchmark.addHeader        4       true  thrpt    5   339720.292 ±   5379.742  ops/s
 * DefaultHttpHeadersNameValidationBenchmark.addHeader        8      false  thrpt    5  4833763.689 ±  31037.631  ops/s
 * DefaultHttpHeadersNameValidationBenchmark.addHeader        8       true  thrpt    5   167820.982 ±    349.719  ops/s
 * DefaultHttpHeadersNameValidationBenchmark.addHeader       16      false  thrpt    5  2537292.742 ±  11400.611  ops/s
 * DefaultHttpHeadersNameValidationBenchmark.addHeader       16       true  thrpt    5    84582.812 ±    168.087  ops/s
 *
 * ByteProcessor as an instance of a static inner class with extra state:
 * Benchmark                                            (count)  (invalid)   Mode  Cnt        Score        Error  Units
 * DefaultHttpHeadersNameValidationBenchmark.addHeader        4      false  thrpt    5  8562586.842 ± 210534.848  ops/s
 * DefaultHttpHeadersNameValidationBenchmark.addHeader        4       true  thrpt    5   274093.669 ±   1818.531  ops/s
 * DefaultHttpHeadersNameValidationBenchmark.addHeader        8      false  thrpt    5  5024017.665 ±  32830.519  ops/s
 * DefaultHttpHeadersNameValidationBenchmark.addHeader        8       true  thrpt    5   135011.887 ±   1832.187  ops/s
 * DefaultHttpHeadersNameValidationBenchmark.addHeader       16      false  thrpt    5  2523554.519 ±   8780.660  ops/s
 * DefaultHttpHeadersNameValidationBenchmark.addHeader       16       true  thrpt    5    68474.840 ±   1154.751  ops/s
 */
@Fork(1)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 5)
@Measurement(iterations = 5, time = 10)
@BenchmarkMode(Mode.Throughput)
public class DefaultHttpHeadersNameValidationBenchmark {

    @Param({"4", "8", "16"})
    private int count;
    @Param({"false", "true"})
    private boolean invalid;
    private String[] names;

    @Setup(Level.Trial)
    public void setup() {
        String baseValue = "header-" + (invalid ? (char) 0 : 'x') + "-name-";
        names = new String[count];
        Arrays.setAll(names, i -> baseValue + i);
    }

    @Benchmark
    public HttpHeaders addHeader(Blackhole blackhole) {
        HttpHeaders headers = DefaultHttpHeadersFactory.INSTANCE.newHeaders();
        for (String name : names) {
            try {
                headers.add(name, "value");
            } catch (IllegalArgumentException e) {
                blackhole.consume(e);
            }
        }
        return headers;
    }
}
