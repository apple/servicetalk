/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethod.GET;

@Fork(value = 1)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 5)
@Measurement(iterations = 5, time = 5)
@BenchmarkMode(Mode.Throughput)
public class QueryAddBenchmark {
    @Param({"1", "10", "100"})
    private int numValues;
    @Param({"false", "true"})
    private boolean needsEncoding;
    private String[] values;
    private DefaultHttpRequestMetaData stMetaData;
    private final HttpHeaders headers = new DefaultHttpHeadersFactory(false, false).newHeaders();

    @Setup(Level.Trial)
    public void setup() {
        values = new String[numValues];
        String baseValue = needsEncoding ? "my value" : "myxvalue";
        for (int i = 0; i < values.length; ++i) {
            values[i] = baseValue + i;
        }
        stMetaData = new DefaultHttpRequestMetaData(GET, "", HTTP_1_1, headers);
    }

    @Benchmark
    public String addParam() {
        final String name = "name";
        for (final String value : values) {
            stMetaData.addQueryParameter(name, value);
        }
        stMetaData.removeQueryParameters(name);
        return stMetaData.requestTarget();
    }
}
