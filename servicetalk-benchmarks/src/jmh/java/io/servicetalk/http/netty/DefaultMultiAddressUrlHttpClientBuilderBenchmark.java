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
package io.servicetalk.http.netty;

import io.servicetalk.http.api.EmptyHttpHeaders;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.HttpRequestMetaData;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMetaDataFactory.newRequestMetaData;
import static io.servicetalk.http.api.HttpRequestMethod.GET;

/*
 * This benchmark measures performance of DefaultMultiAddressUrlHttpClientBuilder features:
 */
@Fork(1)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 5)
@Measurement(iterations = 5, time = 10)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class DefaultMultiAddressUrlHttpClientBuilderBenchmark {

    public static final String URL_LOWERCASE = "http://some-random-service.with.long.url.com:8080/";
    public static final String URL_UPPERCASE = URL_LOWERCASE.toLowerCase(Locale.ENGLISH);

    @Param({"true", "false"})
    private boolean lowercase;
    private HttpRequestMetaData metaData;
    private DefaultMultiAddressUrlHttpClientBuilder.StreamingUrlHttpClient client;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        metaData = newRequestMetaData(HTTP_1_1, GET,
                lowercase ? URL_LOWERCASE : URL_UPPERCASE, EmptyHttpHeaders.INSTANCE);
        client = new DefaultMultiAddressUrlHttpClientBuilder(HttpClients::forSingleAddress)
                .buildStreamingUrlHttpClient();
        // Create a single client to make sure benchmark only queries it and does not create a new client
        client.selectClient(metaData);
    }

    @Benchmark
    public FilterableStreamingHttpClient selectClient() throws Exception {
        return client.selectClient(metaData);
    }
}
