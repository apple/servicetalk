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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpResponseStatus;

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

import static io.netty.handler.codec.http.HttpConstants.SP;
import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.buffer.netty.BufferAllocators.PREFER_DIRECT_ALLOCATOR;
import static io.servicetalk.buffer.netty.BufferUtils.getByteBufAllocator;
import static io.servicetalk.buffer.netty.BufferUtils.toByteBuf;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.netty.HttpObjectEncoder.CRLF_SHORT;
import static io.servicetalk.http.netty.HttpUtils.status;
import static io.servicetalk.transport.netty.internal.CloseHandler.UNSUPPORTED_PROTOCOL_CLOSE_HANDLER;
import static java.nio.charset.StandardCharsets.US_ASCII;

/*
 * This benchmark compares decoding of HTTP requests with different status codes (with short and long reason phrase,
 * known and unknown status codes):
 *
 * Benchmark                                 (statusCode)   Mode  Cnt        Score       Error  Units
 * HttpResponseDecoderBenchmark.initialLine           200  thrpt    5  1039670.340 ± 16842.744  ops/s
 * HttpResponseDecoderBenchmark.initialLine           431  thrpt    5   981914.341 ± 16785.692  ops/s
 * HttpResponseDecoderBenchmark.initialLine           500  thrpt    5   988277.529 ±  8913.071  ops/s
 * HttpResponseDecoderBenchmark.initialLine           600  thrpt    5   965798.581 ± 13396.336  ops/s
 * HttpResponseDecoderBenchmark.initialLine           700  thrpt    5   811948.162 ± 14176.711  ops/s
 */
@Fork(value = 1)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 3)
@Measurement(iterations = 5, time = 3)
@BenchmarkMode(Mode.Throughput)
public class HttpResponseDecoderBenchmark {

    @Param({"200", "431", "500", "600", "700"})
    private int statusCode;

    private ByteBuf responseByteBuf;

    private EmbeddedChannel channel;

    @Setup(Level.Trial)
    public void setup() {
        final HttpResponseStatus status = status(statusCode);
        final Buffer responseBuffer = PREFER_DIRECT_ALLOCATOR.newBuffer(100);
        HTTP_1_1.writeTo(responseBuffer);
        responseBuffer.writeByte(SP);
        status.writeTo(responseBuffer);
        responseBuffer.writeShort(CRLF_SHORT);
        responseBuffer.writeBytes("content-length: 0".getBytes(US_ASCII));
        responseBuffer.writeShort(CRLF_SHORT);
        responseBuffer.writeShort(CRLF_SHORT);
        responseByteBuf = toByteBuf(responseBuffer.slice());

        channel = new EmbeddedChannel(new HttpResponseDecoder(new ArrayDeque<>(), new PollLikePeakArrayDeque<>(),
                getByteBufAllocator(DEFAULT_ALLOCATOR), DefaultHttpHeadersFactory.INSTANCE, 8192, 8192,
                false, false, UNSUPPORTED_PROTOCOL_CLOSE_HANDLER));
    }

    @Benchmark
    public int initialLine() {
        channel.writeInbound(responseByteBuf.duplicate());

        final HttpResponseMetaData response = channel.readInbound();
        final HttpHeaders trailers = channel.readInbound();

        if (response.status().code() != statusCode) {
            throw new IllegalStateException("Unexpected statusCode: " + response.status().code());
        }

        return response.headers().size() + trailers.size();
    }

    private static final class PollLikePeakArrayDeque<T> extends ArrayDeque<T> {
        private static final long serialVersionUID = -8160337336374186819L;

        @Override
        public T poll() {
            return peek();  // Prevent taking an element out of the queue to allow reuse for multiple tests
        }
    }
}
