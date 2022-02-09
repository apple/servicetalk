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
package io.servicetalk.http.netty;

import io.servicetalk.http.api.EmptyHttpHeaders;
import io.servicetalk.http.api.HttpResponseMetaData;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
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

import java.util.ArrayDeque;

import static io.servicetalk.http.api.DefaultHttpHeadersFactory.INSTANCE;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpResponseMetaDataFactory.newResponseMetaData;
import static io.servicetalk.http.netty.HttpResponseEncoder.NOOP_ON_RESPONSE;
import static io.servicetalk.http.netty.HttpUtils.status;
import static io.servicetalk.transport.netty.internal.CloseHandler.UNSUPPORTED_PROTOCOL_CLOSE_HANDLER;

/*
 * This benchmark compares encoding of HTTP requests with different status codes (with short and long reason phrase,
 * known and unknown status coders):
 *
 * Benchmark                                            (statusCode)   Mode  Cnt        Score       Error  Units
 * HttpResponseEncoderInitialLineBenchmark.initialLine           200  thrpt    5  1223169.131 ± 83611.469  ops/s
 * HttpResponseEncoderInitialLineBenchmark.initialLine           431  thrpt    5  1236201.596 ± 22024.710  ops/s
 * HttpResponseEncoderInitialLineBenchmark.initialLine           500  thrpt    5  1259922.772 ± 23745.487  ops/s
 * HttpResponseEncoderInitialLineBenchmark.initialLine           600  thrpt    5  1271252.139 ± 17791.329  ops/s
 * HttpResponseEncoderInitialLineBenchmark.initialLine           700  thrpt    5  1220163.698 ± 43243.342  ops/s
 */
@Fork(value = 1)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 3)
@Measurement(iterations = 5, time = 3)
@BenchmarkMode(Mode.Throughput)
public class HttpResponseEncoderInitialLineBenchmark {

    @Param({"200", "431", "500", "600", "700"})
    private int statusCode;

    private HttpResponseMetaData metaData;

    private EmbeddedChannel channel;

    @Setup(Level.Trial)
    public void setup() {
        metaData = newResponseMetaData(HTTP_1_1, status(statusCode), INSTANCE.newHeaders())
                .addHeader(CONTENT_LENGTH, ZERO);

        channel = new EmbeddedChannel(new HttpResponseEncoder(new ArrayDeque<>(), 256, 256,
                UNSUPPORTED_PROTOCOL_CLOSE_HANDLER, NOOP_ON_RESPONSE));
    }

    @Benchmark
    public int initialLine() {
        channel.writeOutbound(metaData);
        channel.writeOutbound(EmptyHttpHeaders.INSTANCE);

        final ByteBuf byteBuf1 = channel.readOutbound();
        final int size1 = byteBuf1.readableBytes();
        byteBuf1.release();
        final ByteBuf byteBuf2 = channel.readOutbound();
        final int size2 = byteBuf2.readableBytes();
        byteBuf2.release();
        return size1 + size2;
    }
}
