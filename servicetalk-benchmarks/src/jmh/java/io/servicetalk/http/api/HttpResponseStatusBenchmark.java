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
package io.servicetalk.http.api;

import io.servicetalk.buffer.api.Buffer;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.PREFER_HEAP_RO_ALLOCATOR;
import static java.nio.charset.StandardCharsets.US_ASCII;

/*
 * This benchmark measures performance of HttpResponseStatus.toString() implemented in two different ways:
 *
 * Benchmark                                           Mode  Cnt         Score         Error  Units
 * HttpResponseStatusBenchmark.toStringConcatenation  thrpt    5  63931127.209 ± 1936778.214  ops/s
 * HttpResponseStatusBenchmark.toStringConvertBuffer  thrpt    5  21250378.081 ±  234963.626  ops/s
 */
@Fork(value = 1)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 5)
@Measurement(iterations = 5, time = 10)
@BenchmarkMode(Mode.Throughput)
public class HttpResponseStatusBenchmark {

    private int statusCode;
    private String reasonPhrase;
    private Buffer buffer;

    @Setup(Level.Trial)
    public void setup() {
        statusCode = 505;
        reasonPhrase = "HTTP Version Not Supported";
        buffer = PREFER_HEAP_RO_ALLOCATOR.fromAscii(statusCode + " " + reasonPhrase);
    }

    @Benchmark
    public String toStringConcatenation() {
        return reasonPhrase.isEmpty() ? Integer.toString(statusCode) : statusCode + " " + reasonPhrase;
    }

    @Benchmark
    public String toStringConvertBuffer() {
        return buffer.toString(US_ASCII);
    }
}
