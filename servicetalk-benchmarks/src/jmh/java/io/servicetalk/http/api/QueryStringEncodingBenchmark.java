/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.DefaultHttpHeadersFactory.INSTANCE;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static java.nio.charset.StandardCharsets.UTF_8;

@Fork(value = 1)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 5)
@Measurement(iterations = 5, time = 5)
@BenchmarkMode(Mode.Throughput)
public class QueryStringEncodingBenchmark {
    private static final String RELATIVE_URI = "/foo";
    private String value;
    @Param({"100", "1000", "10000"})
    private int length;
    @Param({"false", "true"})
    private boolean needsEncoding;
    private DefaultHttpRequestMetaData stMetaData;
    private final HttpHeaders headers = INSTANCE.newHeaders();

    @Setup(Level.Trial)
    public void setup() {
        StringBuilder sb = new StringBuilder(length);
        if (needsEncoding) {
            final int halfLength = length >>> 1;
            int i = 0;
            for (; i < halfLength; ++i) {
                sb.append('a');
            }
            sb.append(' ');
            for (; i < length; ++i) {
                sb.append('b');
            }
        } else {
            for (int i = 0; i < length; ++i) {
                sb.append('a');
            }
        }
        value = sb.toString();
        stMetaData = new DefaultHttpRequestMetaData(GET, RELATIVE_URI, HTTP_1_1, headers, null);
    }

    @Benchmark
    public HttpRequestMetaData stEncoding() {
        return stMetaData.query(value);
    }

    @Benchmark
    public String jdkURLEncoder() throws UnsupportedEncodingException {
        return jdkBuildURL(RELATIVE_URI, URLEncoder.encode(value, UTF_8.name()));
    }

    private static String jdkBuildURL(final String uri, @Nullable String query) {
        // replicating what is done in DefaultHttpHeadersFactory to build the URI
        StringBuilder sb = query != null ? new StringBuilder(uri.length() + query.length() + 1) :
                                           new StringBuilder(uri.length());
        sb.append(uri);
        if (query != null) {
            sb.append('?').append(query);
        }
        return sb.toString();
    }
}
